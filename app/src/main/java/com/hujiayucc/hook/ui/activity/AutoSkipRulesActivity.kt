package com.hujiayucc.hook.ui.activity

import android.Manifest
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hujiayucc.hook.R
import com.hujiayucc.hook.autoskip.AutoSkipAccessibilityService
import com.hujiayucc.hook.autoskip.AutoSkipAppMode
import com.hujiayucc.hook.autoskip.AutoSkipDaemonManager
import com.hujiayucc.hook.autoskip.AutoSkipHealth
import com.hujiayucc.hook.autoskip.AutoSkipHitLog
import com.hujiayucc.hook.autoskip.AutoSkipRule
import com.hujiayucc.hook.autoskip.AutoSkipRuleRepository
import com.hujiayucc.hook.autoskip.AutoSkipRuleSource
import com.hujiayucc.hook.autoskip.AutoSkipRuleSourceConfig
import com.hujiayucc.hook.autoskip.AutoSkipRuleStats
import com.hujiayucc.hook.autoskip.AutoSkipSettings
import com.hujiayucc.hook.autoskip.AutoSkipTapStrategy
import com.hujiayucc.hook.autoskip.AutoSkipUpdateResult
import com.hujiayucc.hook.utils.PrivilegedPermissionGrantor
import com.hujiayucc.hook.databinding.ActivityAutoSkipRulesBinding
import com.hujiayucc.hook.databinding.DialogAutoSkipRuleEditorBinding
import com.hujiayucc.hook.ui.adapter.AutoSkipRuleAdapter
import io.github.libxposed.service.XposedService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

class AutoSkipRulesActivity : BaseActivity<ActivityAutoSkipRulesBinding>() {
    private lateinit var binding: ActivityAutoSkipRulesBinding
    private lateinit var repository: AutoSkipRuleRepository
    private lateinit var adapter: AutoSkipRuleAdapter
    private val disposables = CompositeDisposable()
    private var filter = RuleFilter.ALL
    private var searchQuery = ""
    private var autoEnableAccessibilityInProgress = false
    private var notificationPermissionRequestInProgress = false
    private var keepAliveSwitchUpdateInProgress = false
    private var serviceKeepAliveRequestPending = false
    private var daemonRepairInProgress = false
    private var autoLoadSubscriptionsStarted = false
    private var refreshRulesRequestId = 0
    private var filterRulesRequestId = 0
    private var allRules: List<AutoSkipRule> = emptyList()
    private var ruleStats: AutoSkipRuleStats? = null
    private var reloadRulesDisposable: Disposable? = null
    private var filterRulesDisposable: Disposable? = null
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PrivilegedPermissionGrantor.SHIZUKU_PERMISSION_REQUEST_CODE && autoEnableAccessibilityInProgress) {
            runOnUiThread {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    runAutoEnableAccessibilityService()
                } else {
                    autoEnableAccessibilityInProgress = false
                    Toast.makeText(this, R.string.auto_skip_auto_enable_accessibility_failed, Toast.LENGTH_LONG).show()
                    refreshRules()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutoSkipRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        repository = AutoSkipRuleRepository(this)
        adapter = AutoSkipRuleAdapter(
            rules = emptyList(),
            onRuleEnabledChanged = { rule, enabled ->
                runBackgroundTask { AutoSkipSettings.setRuleEnabled(this, rule.id, enabled) }
            },
            onRuleClicked = { rule -> showRuleDetail(rule) }
        )
        binding.ruleList.layoutManager = LinearLayoutManager(this)
        binding.ruleList.itemAnimator = null
        binding.ruleList.adapter = adapter
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
        setupSwitches()
        setupFilters()
        autoLoadSubscriptionRulesIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_auto_skip_rules, menu)
        val searchItem = menu.findItem(R.id.menu_search_rules)
        val searchView = searchItem.actionView as? SearchView
        searchView?.queryHint = getString(R.string.auto_skip_search_rules_hint)
        searchView?.setQuery(searchQuery, false)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty().trim()
                filterRules()
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty().trim()
                filterRules()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_update_rules -> {
                updateRules()
                true
            }
            R.id.menu_open_accessibility -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                true
            }
            R.id.menu_app_switches -> {
                preparePreviousPagePreview(AutoSkipAppListActivity::class.java)
                startActivity(Intent(this, AutoSkipAppListActivity::class.java))
                true
            }
            R.id.menu_add_subscription -> {
                showAddSourceDialog()
                true
            }
            R.id.menu_manage_subscriptions -> {
                showManageSourcesDialog()
                true
            }
            R.id.menu_add_local_rule -> {
                showLocalRuleEditor(null)
                true
            }
            R.id.menu_import_rules -> {
                showImportRulesDialog()
                true
            }
            R.id.menu_export_rules -> {
                showExportRulesDialog()
                true
            }
            R.id.menu_clear_rule_cache -> {
                confirmClearCache()
                true
            }
            R.id.menu_hit_logs -> {
                showHitLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun finish() {
        if (isTaskRoot) excludeCurrentTaskFromRecents()
        super.finish()
    }

    private fun excludeCurrentTaskFromRecents() {
        runCatching {
            val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            manager.appTasks.firstOrNull { task -> task.taskInfo?.taskId == taskId }
                ?.setExcludeFromRecents(true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (serviceKeepAliveRequestPending) {
            serviceKeepAliveRequestPending = false
            AutoSkipSettings.setServiceKeepAliveEnabled(
                this,
                AutoSkipSettings.isBatteryUsageUnrestricted(this)
            )
        }
        refreshServiceKeepAliveSwitch()
        refreshAppModeSelection()
        val autoSkipEnabled = AutoSkipSettings.isEnabled(this)
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val canShowNotification = if (autoSkipEnabled || accessibilityEnabled) {
            requestNotificationPermissionIfNeeded()
        } else {
            false
        }
        if (autoSkipEnabled && !accessibilityEnabled) {
            autoEnableAccessibilityServiceIfNeeded()
        }
        if (accessibilityEnabled && canShowNotification) {
            AutoSkipAccessibilityService.refreshRunningNotification(this)
        }
        repairDaemonIfStatusMissing()
        refreshKeepAliveStatus()
        ruleStats?.let { stats -> updateAccessibilityStatusAndStats(stats) } ?: refreshRules()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            notificationPermissionRequestInProgress = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && isAccessibilityServiceEnabled()) {
                AutoSkipAccessibilityService.refreshRunningNotification(this)
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        disposables.clear()
        super.onDestroy()
    }

    override fun onServiceStateChanged(service: XposedService?) {
    }

    private fun setupSwitches() {
        binding.autoSkipSwitch.isChecked = AutoSkipSettings.isEnabled(this)
        val appModes = AutoSkipAppMode.entries
        binding.appModeInput.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                appModes.map(::appModeLabel)
            )
        )
        refreshAppModeSelection()
        binding.appModeInput.setOnItemClickListener { _, _, position, _ ->
            val mode = appModes[position]
            if (mode != AutoSkipSettings.appMode(this)) {
                AutoSkipSettings.setAppMode(this, mode)
            }
        }
        binding.shizukuSwitch.isChecked = AutoSkipSettings.useShizukuInput(this)
        binding.rootSwitch.isChecked = AutoSkipSettings.useRootInput(this)
        binding.serviceKeepAliveSwitch.isChecked = AutoSkipSettings.serviceKeepAliveEnabled(this)
        binding.daemonKeepAliveSwitch.isChecked = AutoSkipSettings.daemonKeepAliveEnabled(this)
        binding.accessibilityStatusChip.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.autoSkipSwitch.setOnCheckedChangeListener { _, isChecked ->
            AutoSkipSettings.setEnabled(this, isChecked)
            if (isChecked) {
                requestNotificationPermissionIfNeeded()
                autoEnableAccessibilityServiceIfNeeded()
            }
            refreshRules()
        }
        binding.shizukuSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) PrivilegedPermissionGrantor.requestShizukuPermissionIfNeeded()
            AutoSkipSettings.setUseShizukuInput(this, isChecked)
        }
        binding.rootSwitch.setOnCheckedChangeListener { _, isChecked ->
            AutoSkipSettings.setUseRootInput(this, isChecked)
        }
        binding.serviceKeepAliveSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (keepAliveSwitchUpdateInProgress) return@setOnCheckedChangeListener
            if (isChecked && !AutoSkipSettings.isBatteryUsageUnrestricted(this)) {
                AutoSkipSettings.setServiceKeepAliveEnabled(this, false)
                refreshServiceKeepAliveSwitch()
                requestUnrestrictedBatteryUsage()
                return@setOnCheckedChangeListener
            }
            AutoSkipSettings.setServiceKeepAliveEnabled(this, isChecked)
            AutoSkipDaemonManager.writeConfig(this)
            AutoSkipAccessibilityService.refreshRunningNotification(this)
            refreshKeepAliveStatus()
        }
        binding.daemonKeepAliveSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!keepAliveSwitchUpdateInProgress) setDaemonKeepAliveEnabled(isChecked)
        }
        refreshKeepAliveStatus()
    }

    private fun refreshAppModeSelection() {
        binding.appModeInput.setText(appModeLabel(AutoSkipSettings.appMode(this)), false)
    }

    private fun appModeLabel(mode: AutoSkipAppMode): String {
        return getString(
            if (mode == AutoSkipAppMode.WHITELIST) {
                R.string.auto_skip_whitelist_mode
            } else {
                R.string.auto_skip_blacklist_mode
            }
        )
    }

    private fun refreshServiceKeepAliveSwitch() {
        keepAliveSwitchUpdateInProgress = true
        binding.serviceKeepAliveSwitch.isChecked = AutoSkipSettings.serviceKeepAliveEnabled(this)
        keepAliveSwitchUpdateInProgress = false
    }

    private fun requestUnrestrictedBatteryUsage() {
        serviceKeepAliveRequestPending = true
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching {
            startActivity(requestIntent.takeIf { it.resolveActivity(packageManager) != null } ?: settingsIntent)
        }.onFailure {
            serviceKeepAliveRequestPending = false
        }
    }

    private fun setDaemonKeepAliveEnabled(enabled: Boolean) {
        binding.daemonKeepAliveSwitch.isEnabled = false
        disposables.add(
            Observable.fromCallable {
                AutoSkipSettings.setDaemonKeepAliveEnabled(this, enabled)
                val result = if (enabled) {
                    AutoSkipDaemonManager.installOrUpdate(this)
                } else {
                    AutoSkipDaemonManager.stopAndUninstall(this)
                }
                if (enabled && !result.success) {
                    AutoSkipSettings.setDaemonKeepAliveEnabled(this, false)
                    AutoSkipDaemonManager.writeConfig(this)
                }
                result
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    AutoSkipAccessibilityService.refreshRunningNotification(this)
                    binding.daemonKeepAliveSwitch.isEnabled = true
                    keepAliveSwitchUpdateInProgress = true
                    binding.daemonKeepAliveSwitch.isChecked = AutoSkipSettings.daemonKeepAliveEnabled(this)
                    keepAliveSwitchUpdateInProgress = false
                    Toast.makeText(
                        this,
                        getString(
                            if (result.success) R.string.auto_skip_daemon_operation_success else R.string.auto_skip_daemon_operation_failed,
                            result.message
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    refreshKeepAliveStatus()
                    binding.keepAliveStatusText.postDelayed({ refreshKeepAliveStatus() }, 1_500L)
                }, { error ->
                    AutoSkipSettings.setDaemonKeepAliveEnabled(this, false)
                    AutoSkipDaemonManager.writeConfig(this)
                    AutoSkipAccessibilityService.refreshRunningNotification(this)
                    binding.daemonKeepAliveSwitch.isEnabled = true
                    keepAliveSwitchUpdateInProgress = true
                    binding.daemonKeepAliveSwitch.isChecked = false
                    keepAliveSwitchUpdateInProgress = false
                    Toast.makeText(this, error.message ?: getString(R.string.auto_skip_daemon_operation_failed, "unknown"), Toast.LENGTH_LONG).show()
                    refreshKeepAliveStatus()
                })
        )
    }

    private fun repairDaemonIfStatusMissing() {
        if (!AutoSkipSettings.daemonKeepAliveEnabled(this) || daemonRepairInProgress) return
        val status = AutoSkipDaemonManager.readStatus(this)
        val now = System.currentTimeMillis()
        val statusFresh = status != null && now - status.lastCheckAt <= 90_000L
        if (statusFresh) return
        daemonRepairInProgress = true
        disposables.add(
            Observable.fromCallable { AutoSkipDaemonManager.installOrUpdate(this) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    daemonRepairInProgress = false
                    if (!result.success) {
                        Toast.makeText(
                            this,
                            getString(R.string.auto_skip_daemon_operation_failed, result.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    refreshKeepAliveStatus()
                    binding.keepAliveStatusText.postDelayed({ refreshKeepAliveStatus() }, 1_500L)
                }, { error ->
                    daemonRepairInProgress = false
                    Toast.makeText(this, error.message ?: getString(R.string.auto_skip_daemon_operation_failed, "unknown"), Toast.LENGTH_LONG).show()
                    refreshKeepAliveStatus()
                })
        )
    }

    private fun refreshKeepAliveStatus() {
        val health = AutoSkipHealth.read(this)
        val heartbeat = health?.lastHeartbeatAt?.takeIf { it > 0L }?.let { time ->
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
        } ?: getString(R.string.auto_skip_never_updated)
        val lastError = health?.lastError?.takeIf { it.isNotBlank() } ?: "-"
        val serviceState = if (AutoSkipSettings.serviceKeepAliveEnabled(this)) {
            getString(R.string.auto_skip_source_state_on)
        } else {
            getString(R.string.auto_skip_source_state_off)
        }
        val daemonEnabled = AutoSkipSettings.daemonKeepAliveEnabled(this)
        val daemonState = if (daemonEnabled) {
            getString(R.string.auto_skip_source_state_on)
        } else {
            getString(R.string.auto_skip_source_state_off)
        }
        val keepAliveStatus = if (daemonEnabled) {
            val daemonStatus = AutoSkipDaemonManager.readStatus(this)?.let { status ->
                val lastCheck = status.lastCheckAt.takeIf { it > 0L }?.let { time ->
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
                } ?: getString(R.string.auto_skip_never_updated)
                getString(
                    R.string.auto_skip_daemon_runtime_status,
                    status.processName.ifBlank { "fkad-daemon" },
                    status.pid,
                    if (status.connected) getString(R.string.auto_skip_source_state_on) else getString(R.string.auto_skip_source_state_off),
                    if (status.heartbeatAgeSeconds >= 0L) "${status.heartbeatAgeSeconds}s" else "-",
                    status.recoverCount,
                    lastCheck,
                    status.lastAction.ifBlank { "-" }
                )
            } ?: getString(R.string.auto_skip_never_updated)
            getString(
                R.string.auto_skip_keep_alive_status,
                serviceState,
                daemonState,
                heartbeat,
                lastError,
                daemonStatus
            )
        } else {
            getString(
                R.string.auto_skip_keep_alive_status_no_daemon_status,
                serviceState,
                daemonState,
                heartbeat,
                lastError
            )
        }
        binding.keepAliveStatusText.text = keepAliveStatus
    }

    private fun setupFilters() {
        binding.filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.filter_builtin -> RuleFilter.BUILTIN
                R.id.filter_subscription -> RuleFilter.SUBSCRIPTION
                R.id.filter_local -> RuleFilter.LOCAL
                R.id.filter_disabled -> RuleFilter.DISABLED
                else -> RuleFilter.ALL
            }
            filterRules()
        }
    }

    private fun refreshRules() {
        val requestId = ++refreshRulesRequestId
        filterRulesRequestId++
        reloadRulesDisposable?.dispose()
        filterRulesDisposable?.dispose()
        val disposable = Observable.fromCallable {
            val rules = repository.rules()
            RuleCacheState(rules, repository.stats(rules))
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ state ->
                if (requestId != refreshRulesRequestId) return@subscribe
                allRules = state.rules
                ruleStats = state.stats
                updateAccessibilityStatusAndStats(state.stats)
                filterRules()
            }, { error ->
                if (requestId != refreshRulesRequestId) return@subscribe
                Toast.makeText(this, error.message ?: getString(R.string.data_load_failed, ""), Toast.LENGTH_LONG).show()
            })
        reloadRulesDisposable = disposable
        disposables.add(disposable)
    }

    private fun filterRules() {
        if (ruleStats == null) return
        val requestId = ++filterRulesRequestId
        val currentRules = allRules
        val currentFilter = filter
        val currentQuery = searchQuery
        filterRulesDisposable?.dispose()
        val disposable = Observable.fromCallable {
            currentRules.filterBy(currentFilter, currentQuery)
        }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ visibleRules ->
                if (requestId != filterRulesRequestId) return@subscribe
                adapter.updateData(visibleRules)
            }, { error ->
                if (requestId != filterRulesRequestId) return@subscribe
                Toast.makeText(this, error.message ?: getString(R.string.data_load_failed, ""), Toast.LENGTH_LONG).show()
            })
        filterRulesDisposable = disposable
        disposables.add(disposable)
    }

    private fun updateAccessibilityStatusAndStats(stats: AutoSkipRuleStats) {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val accessibilityStateText = if (accessibilityEnabled) getString(R.string.is_active) else getString(R.string.not_active)
        updateAccessibilityStatus(accessibilityEnabled)
        updateStatsText(stats, accessibilityStateText)
    }

    private fun updateStatsText(stats: AutoSkipRuleStats, accessibilityStateText: String) {
        val lastUpdate = if (stats.lastUpdateTime > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(stats.lastUpdateTime))
        } else {
            getString(R.string.auto_skip_never_updated)
        }
        binding.statsText.text = getString(
            R.string.auto_skip_stats,
            stats.total,
            stats.enabled,
            stats.builtin,
            stats.subscription,
            stats.local,
            stats.sourceCount,
            lastUpdate,
            accessibilityStateText
        )
    }

    private fun autoLoadSubscriptionRulesIfNeeded() {
        if (autoLoadSubscriptionsStarted) return
        autoLoadSubscriptionsStarted = true
        disposables.add(
            Observable.fromCallable {
                val enabledSources = repository.sources().filter { it.enabled }
                val cachedRules = if (enabledSources.isEmpty()) {
                    emptyMap()
                } else {
                    AutoSkipSettings.subscriptionRules(this, enabledSources)
                }
                enabledSources.isNotEmpty() && enabledSources.any { source -> cachedRules[source.id].isNullOrEmpty() }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ needsLoad ->
                    if (needsLoad) updateRules()
                }, { error ->
                    autoLoadSubscriptionsStarted = false
                    Toast.makeText(this, error.message ?: getString(R.string.auto_skip_update_failed), Toast.LENGTH_LONG).show()
                })
        )
    }

    private fun List<AutoSkipRule>.filterBy(ruleFilter: RuleFilter, queryText: String): List<AutoSkipRule> {
        val filtered = when (ruleFilter) {
            RuleFilter.ALL -> this
            RuleFilter.BUILTIN -> filter { it.source == AutoSkipRuleSource.BUILTIN }
            RuleFilter.SUBSCRIPTION -> filter { it.source == AutoSkipRuleSource.SUBSCRIPTION }
            RuleFilter.LOCAL -> filter { it.source == AutoSkipRuleSource.LOCAL }
            RuleFilter.DISABLED -> filter { !it.enabled }
        }
        val query = queryText.trim()
        if (query.isEmpty()) return filtered
        return filtered.filter { it.matchesSearchQuery(query) }
    }

    private fun AutoSkipRule.matchesSearchQuery(query: String): Boolean {
        return searchableValues().any { value -> value.contains(query, ignoreCase = true) }
    }

    private fun AutoSkipRule.searchableValues(): List<String> {
        return listOf(
            id,
            name,
            packageName,
            activity,
            source.name,
            sourceId
        ) + match.text +
            match.desc +
            match.resourceId +
            match.className +
            match.excludeText +
            match.excludeDesc +
            match.excludeResourceId +
            match.gkdSelectors +
            match.excludeGkdSelectors
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return PrivilegedPermissionGrantor.isAccessibilityServiceEnabled(this, expectedAccessibilityServiceName())
    }

    private fun updateAccessibilityStatus(enabled: Boolean) {
        binding.accessibilityStatusChip.setText(
            if (enabled) R.string.auto_skip_accessibility_status_on else R.string.auto_skip_accessibility_status_off
        )
        binding.accessibilityStatusChip.setChipIconResource(if (enabled) R.drawable.ic_success else R.drawable.ic_warn)
    }

    private fun requestNotificationPermissionIfNeeded(): Boolean {
        if (canPostNotifications()) return true
        if (!notificationPermissionRequestInProgress) {
            notificationPermissionRequestInProgress = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
        }
        return false
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun autoEnableAccessibilityServiceIfNeeded() {
        if (isAccessibilityServiceEnabled() || autoEnableAccessibilityInProgress) return
        autoEnableAccessibilityInProgress = true
        if (PrivilegedPermissionGrantor.requestShizukuPermissionIfNeeded()) {
            Toast.makeText(this, R.string.auto_skip_auto_enable_accessibility_waiting, Toast.LENGTH_LONG).show()
            return
        }
        runAutoEnableAccessibilityService()
    }

    private fun runAutoEnableAccessibilityService() {
        disposables.add(
            Observable.fromCallable {
                PrivilegedPermissionGrantor.ensureAccessibilityServiceEnabled(this, AutoSkipAccessibilityService::class.java)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    autoEnableAccessibilityInProgress = false
                    when (result) {
                        PrivilegedPermissionGrantor.GrantResult.GRANTED -> {
                            Toast.makeText(this, R.string.auto_skip_auto_enable_accessibility_success, Toast.LENGTH_SHORT).show()
                            scheduleAccessibilityStatusRefresh()
                        }
                        PrivilegedPermissionGrantor.GrantResult.WAITING_FOR_SHIZUKU -> {
                            Toast.makeText(this, R.string.auto_skip_auto_enable_accessibility_waiting, Toast.LENGTH_LONG).show()
                        }
                        PrivilegedPermissionGrantor.GrantResult.FAILED -> {
                            Toast.makeText(this, R.string.auto_skip_auto_enable_accessibility_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                    refreshRules()
                }, {
                    autoEnableAccessibilityInProgress = false
                    Toast.makeText(this, R.string.auto_skip_auto_enable_accessibility_failed, Toast.LENGTH_LONG).show()
                    refreshRules()
                })
        )
    }

    private fun scheduleAccessibilityStatusRefresh() {
        disposables.add(
            Observable.timer(800L, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (isAccessibilityServiceEnabled() && canPostNotifications()) {
                        AutoSkipAccessibilityService.refreshRunningNotification(this)
                    }
                    refreshRules()
                }
        )
    }

    private fun expectedAccessibilityServiceName(): String {
        return ComponentName(this, AutoSkipAccessibilityService::class.java).flattenToString()
    }

    private fun updateRules() {
        runUpdateOperation { repository.updateSources() }
    }

    private fun runUpdateOperation(operation: () -> AutoSkipUpdateResult) {
        disposables.add(
            Observable.fromCallable { operation() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    showUpdateResult(result)
                    refreshRules()
                }, { error ->
                    Toast.makeText(this, error.message ?: getString(R.string.auto_skip_update_failed), Toast.LENGTH_LONG).show()
                    refreshRules()
                })
        )
    }

    private fun runBackgroundTask(task: () -> Unit) {
        disposables.add(
            Observable.fromCallable {
                task()
                true
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    refreshRules()
                }, { error ->
                    Toast.makeText(this, error.message ?: getString(R.string.auto_skip_update_failed), Toast.LENGTH_LONG).show()
                    refreshRules()
                })
        )
    }

    private fun showAddSourceDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/auto-skip-rules.json"
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_add_subscription)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                addSourceAndUpdate(input.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showManageSourcesDialog() {
        val sources = repository.sources()
        if (sources.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auto_skip_manage_subscriptions)
                .setMessage(R.string.auto_skip_no_subscriptions)
                .setPositiveButton(R.string.close, null)
                .show()
            return
        }
        val items = sources.map { source ->
            val state = getString(if (source.enabled) R.string.auto_skip_source_state_on else R.string.auto_skip_source_state_off)
            "$state ${compactUrl(source.url)}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_manage_subscriptions)
            .setItems(items) { _, which -> showSourceActions(sources[which]) }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showSourceActions(source: AutoSkipRuleSourceConfig) {
        val message = buildString {
            append(source.url)
            append("\n\n").append(getString(R.string.auto_skip_source_id_label)).append(": ").append(source.id)
            append("\n").append(getString(R.string.auto_skip_source_enabled_label)).append(": ")
                .append(getString(if (source.enabled) R.string.auto_skip_source_state_enabled else R.string.auto_skip_source_state_disabled))
            append("\n").append(getString(R.string.auto_skip_source_updated_label)).append(": ").append(formatTime(source.lastUpdateTime))
            append("\n").append(getString(R.string.auto_skip_source_result_label)).append(": ").append(source.lastResult.ifBlank { "-" })
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_source_detail)
            .setMessage(message)
            .setPositiveButton(if (source.enabled) R.string.auto_skip_disable_source else R.string.auto_skip_enable_source) { _, _ ->
                runUpdateOperation { repository.setSourceEnabled(source.id, !source.enabled) }
            }
            .setNegativeButton(R.string.auto_skip_delete_source) { _, _ -> confirmDeleteSource(source) }
            .setNeutralButton(R.string.close, null)
            .show()
    }

    private fun confirmDeleteSource(source: AutoSkipRuleSourceConfig) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_delete_source)
            .setMessage(getString(R.string.auto_skip_delete_subscription_confirm, source.url))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runUpdateOperation { repository.deleteSource(source.id) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showImportRulesDialog() {
        val input = EditText(this).apply {
            minLines = 6
            maxLines = 12
            hint = "[{\"id\":\"local.example\",\"name\":\"Example\",\"match\":{\"text\":[\"Skip\"]}}]"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_import_rules)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text?.toString().orEmpty().trim()
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    addSourceAndUpdate(text)
                } else {
                    runUpdateOperation {
                        runCatching { repository.importLocalRules(text) }
                            .getOrElse { error -> AutoSkipUpdateResult(false, error.message ?: getString(R.string.auto_skip_import_failed), 0, 0, 1) }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addSourceAndUpdate(url: String) {
        runUpdateOperation { repository.addSourceAndUpdate(url) }
    }

    private fun showLocalRuleEditor(rule: AutoSkipRule?) {
        val editor = DialogAutoSkipRuleEditorBinding.inflate(LayoutInflater.from(this))
        setupRuleEditor(editor, rule)
        MaterialAlertDialogBuilder(this)
            .setTitle(if (rule == null) R.string.auto_skip_add_local_rule else R.string.auto_skip_edit_rule)
            .setView(editor.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val localRuleJson = buildLocalRuleJson(editor, rule)
                runUpdateOperation {
                    runCatching { repository.saveLocalRule(localRuleJson, rule?.id) }
                        .getOrElse { error -> AutoSkipUpdateResult(false, error.message ?: getString(R.string.auto_skip_save_failed), 0, 0, 1) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.auto_skip_advanced_json) { _, _ -> showLocalRuleJsonEditor(rule) }
            .show()
    }

    private fun showLocalRuleJsonEditor(rule: AutoSkipRule?) {
        val input = EditText(this).apply {
            minLines = 10
            maxLines = 18
            setHorizontallyScrolling(true)
            setText(rule?.toJson()?.toString(2) ?: defaultLocalRuleJson())
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_advanced_json)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val localRuleJson = input.text?.toString().orEmpty()
                runUpdateOperation {
                    runCatching { repository.saveLocalRule(localRuleJson, rule?.id) }
                        .getOrElse { error -> AutoSkipUpdateResult(false, error.message ?: getString(R.string.auto_skip_save_failed), 0, 0, 1) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupRuleEditor(editor: DialogAutoSkipRuleEditorBinding, rule: AutoSkipRule?) {
        val regionItems = listOf("any", "top_right", "bottom_right", "top", "bottom")
        val strategyItems = AutoSkipTapStrategy.values().map { it.name.lowercase() }
        editor.regionInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, regionItems))
        editor.tapStrategyInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, strategyItems))
        val seedId = rule?.id ?: "local.${System.currentTimeMillis()}"
        editor.ruleIdInput.setText(seedId)
        editor.ruleNameInput.setText(rule?.name ?: getString(R.string.auto_skip_default_local_rule_name))
        editor.packageInput.setText(rule?.packageName ?: "*")
        editor.activityInput.setText(rule?.activity ?: "*")
        editor.textInput.setText(rule?.match?.text?.joinToString(", ") ?: "跳过, Skip")
        editor.descInput.setText(rule?.match?.desc?.joinToString(", ") ?: "跳过, Skip")
        editor.resourceIdInput.setText(rule?.match?.resourceId?.joinToString(", ").orEmpty())
        editor.classNameInput.setText(rule?.match?.className?.joinToString(", ").orEmpty())
        editor.excludeTextInput.setText(rule?.match?.excludeText?.joinToString(", ").orEmpty())
        editor.excludeDescInput.setText(rule?.match?.excludeDesc?.joinToString(", ").orEmpty())
        editor.excludeResourceIdInput.setText(rule?.match?.excludeResourceId?.joinToString(", ").orEmpty())
        editor.gkdSelectorInput.setText(rule?.match?.gkdSelectors?.joinToString("\n").orEmpty())
        editor.excludeGkdSelectorInput.setText(rule?.match?.excludeGkdSelectors?.joinToString("\n").orEmpty())
        editor.priorityInput.setText((rule?.priority ?: 50).toString())
        editor.cooldownInput.setText((rule?.cooldownMs ?: 3000L).toString())
        editor.delayInput.setText((rule?.delayMs ?: 200L).toString())
        editor.regionInput.setText(rule?.match?.region?.name ?: if (rule == null) "top_right" else "any", false)
        editor.tapStrategyInput.setText(rule?.action?.tapStrategy?.name?.lowercase() ?: "probe", false)
    }

    private fun buildLocalRuleJson(editor: DialogAutoSkipRuleEditorBinding, oldRule: AutoSkipRule?): String {
        val id = editor.ruleIdInput.textValue().ifBlank { oldRule?.id ?: "local.${System.currentTimeMillis()}" }
        val match = JSONObject().apply {
            put("text", JSONArray(editor.textInput.textList()))
            put("desc", JSONArray(editor.descInput.textList()))
            put("resourceId", JSONArray(editor.resourceIdInput.textList()))
            put("className", JSONArray(editor.classNameInput.textList()))
            put("excludeText", JSONArray(editor.excludeTextInput.textList()))
            put("excludeDesc", JSONArray(editor.excludeDescInput.textList()))
            put("excludeResourceId", JSONArray(editor.excludeResourceIdInput.textList()))
            put("gkdSelectors", JSONArray(editor.gkdSelectorInput.selectorList()))
            put("excludeGkdSelectors", JSONArray(editor.excludeGkdSelectorInput.selectorList()))
            put("visible", true)
            val region = editor.regionInput.textValue().ifBlank { "any" }
            if (region != "any") put("bounds", JSONObject().put("region", region))
        }
        val action = JSONObject().apply {
            put("tapStrategy", editor.tapStrategyInput.textValue().ifBlank { "probe" })
        }
        return JSONObject().apply {
            put("id", id)
            put("name", editor.ruleNameInput.textValue().ifBlank { id })
            put("enabled", true)
            put("packageName", editor.packageInput.textValue().ifBlank { "*" })
            put("activity", editor.activityInput.textValue().ifBlank { "*" })
            put("priority", editor.priorityInput.textValue().toIntOrNull() ?: oldRule?.priority ?: 50)
            put("cooldownMs", editor.cooldownInput.textValue().toLongOrNull()?.coerceAtLeast(500L) ?: oldRule?.cooldownMs ?: 3000L)
            put("delayMs", editor.delayInput.textValue().toLongOrNull()?.coerceIn(0L, 5000L) ?: oldRule?.delayMs ?: 200L)
            put("match", match)
            put("action", action)
            put("source", "local")
            put("sourceId", "local")
        }.toString()
    }

    private fun TextView.textValue(): String {
        return text?.toString()?.trim().orEmpty()
    }

    private fun TextView.textList(): List<String> {
        return textValue()
            .split(',', '\n', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun TextView.selectorList(): List<String> {
        return textValue()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun confirmDeleteLocalRule(rule: AutoSkipRule) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_delete_rule)
            .setMessage(getString(R.string.auto_skip_delete_rule_confirm, rule.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runUpdateOperation { repository.deleteLocalRule(rule.id) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExportRulesDialog() {
        disposables.add(
            Observable.fromCallable {
                repository.rules().joinToString(prefix = "[", postfix = "]") { it.toJson().toString() }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ body ->
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.auto_skip_export_rules)
                        .setMessage(body.take(MAX_DIALOG_CHARS))
                        .setPositiveButton(R.string.close, null)
                        .setNeutralButton(R.string.auto_skip_copy_json) { _, _ -> copyToClipboard(body) }
                        .show()
                }, { error ->
                    Toast.makeText(this, error.message ?: getString(R.string.data_load_failed, ""), Toast.LENGTH_LONG).show()
                })
        )
    }

    private fun confirmClearCache() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_clear_cache)
            .setMessage(R.string.auto_skip_clear_cache_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runBackgroundTask { repository.clearSubscriptionCache() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showHitLogs() {
        disposables.add(
            Observable.fromCallable { AutoSkipSettings.hitLogs(this) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ logs ->
                    val message = if (logs.isEmpty()) {
                        getString(R.string.auto_skip_no_logs)
                    } else {
                        logs.joinToString("\n\n") { it.format() }.take(MAX_DIALOG_CHARS)
                    }
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.auto_skip_hit_logs)
                        .setMessage(message)
                        .setPositiveButton(R.string.close, null)
                        .setNegativeButton(R.string.auto_skip_clear_logs) { _, _ ->
                            runBackgroundTask { AutoSkipSettings.clearHitLogs(this) }
                        }
                        .show()
                }, { error ->
                    Toast.makeText(this, error.message ?: getString(R.string.data_load_failed, ""), Toast.LENGTH_LONG).show()
                })
        )
    }

    private fun showRuleDetail(rule: AutoSkipRule) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(rule.name)
            .setMessage(rule.toJson().toString(2))
            .setPositiveButton(R.string.close, null)
        if (rule.source == AutoSkipRuleSource.LOCAL) {
            builder.setNegativeButton(R.string.auto_skip_edit_rule) { _, _ -> showLocalRuleEditor(rule) }
            builder.setNeutralButton(R.string.auto_skip_delete_rule) { _, _ -> confirmDeleteLocalRule(rule) }
        } else {
            builder.setNeutralButton(R.string.auto_skip_copy_json) { _, _ -> copyToClipboard(rule.toJson().toString(2)) }
        }
        builder.show()
    }

    private fun showUpdateResult(result: AutoSkipUpdateResult) {
        val body = buildString {
            append(result.message)
            append("\n\n").append(getString(R.string.auto_skip_result_added_label)).append(": ").append(result.added)
            append("\n").append(getString(R.string.auto_skip_result_changed_label)).append(": ").append(result.changed)
            append("\n").append(getString(R.string.auto_skip_result_removed_label)).append(": ").append(result.removed)
            append("\n").append(getString(R.string.auto_skip_result_failed_label)).append(": ").append(result.failed)
            if (result.details.isNotBlank()) {
                append("\n\n").append(result.details)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_skip_update_result)
            .setMessage(body.take(MAX_DIALOG_CHARS))
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.auto_skip_rules), text))
        Toast.makeText(this, R.string.auto_skip_copied, Toast.LENGTH_SHORT).show()
    }

    private fun AutoSkipHitLog.format(): String {
        val timeText = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
        return "$timeText\n$packageName\n$ruleName / $executor / ($x,$y)\n$result"
    }

    private fun formatTime(time: Long): String {
        if (time <= 0L) return getString(R.string.auto_skip_never_updated)
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
    }

    private fun compactUrl(url: String): String {
        return url.removePrefix("https://").removePrefix("http://").take(80)
    }

    private fun defaultLocalRuleJson(): String {
        return """
            {
              "id": "local.skip.example",
              "name": ${JSONObject.quote(getString(R.string.auto_skip_default_local_rule_name))},
              "enabled": true,
              "packageName": "*",
              "activity": "*",
              "priority": 50,
              "cooldownMs": 3000,
              "delayMs": 200,
              "match": {
                "text": ["跳过", "Skip"],
                "desc": ["跳过", "Skip"],
                "gkdSelectors": ["[text*=\"跳过\"][visibleToUser=true]"],
                "visible": true,
                "bounds": { "region": "top_right" }
              },
              "action": {
                "tapStrategy": "probe"
              },
              "source": "local",
              "sourceId": "local"
            }
        """.trimIndent()
    }

    private data class RuleCacheState(
        val rules: List<AutoSkipRule>,
        val stats: AutoSkipRuleStats
    )

    private enum class RuleFilter {
        ALL,
        BUILTIN,
        SUBSCRIPTION,
        LOCAL,
        DISABLED
    }

    companion object {
        private const val MAX_DIALOG_CHARS = 12000
        private const val REQUEST_POST_NOTIFICATIONS = 2602
    }
}