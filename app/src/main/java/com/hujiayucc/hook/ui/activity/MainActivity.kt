package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.author.Author
import com.hujiayucc.hook.data.AppList
import com.hujiayucc.hook.data.Data.prefsBridge
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.ui.adapter.AppListAdapter
import com.hujiayucc.hook.utils.LanguageUtils
import com.hujiayucc.hook.utils.PrivilegedPermissionGrantor
import com.hujiayucc.hook.utils.PrivilegedPermissionGrantor.GrantResult
import io.github.libxposed.service.XposedService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : BaseActivity<ActivityMainBinding>() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var listView: ListView
    private val disposables = CompositeDisposable()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initializeRunnable = Runnable { initializeOnce() }
    private lateinit var author: Author
    private var appListAdapter: AppListAdapter? = null
    private var initialized = false
    private var appListLoading = false
    private var autoGrantInProgress = false
    private var shizukuListenersRegistered = false
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == PrivilegedPermissionGrantor.SHIZUKU_PERMISSION_REQUEST_CODE) {
            runAutoGrantQueryAllPackages()
        }
    }
    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkPermissions()
    }
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        autoGrantInProgress = false
    }

    // QUERY_ALL_PACKAGES is handled by Shizuku/root AppOps instead of runtime permission APIs.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        if (initialized) {
            refreshStatus()
        } else {
            mainHandler.postDelayed(initializeRunnable, 500)
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun initializeOnce() {
        if (initialized) {
            refreshStatus()
            return
        }
        initializeUI()
        author = Author(this, true, prefsBridge)
        setupClickListeners()
        initialized = true
        checkPermissions()
        refreshStatus()
    }

    private fun refreshStatus() {
        if (!::author.isInitialized) return
        val currentService = service
        if (currentService == null) {
            author.check(binding.mainActiveStatus, binding.mainStatus, BuildConfig.VERSION_CODE)
        } else {
            updateFrameworkStatus(currentService.frameworkName, currentService.apiVersion)
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun initializeUI() {
        setSupportActionBar(binding.toolbar)
        with(binding) {
            mainActiveStatus.background = AppCompatResources.getDrawable(
                this@MainActivity, R.drawable.bg_header
            )
            mainVersion.text = getString(R.string.main_version).format(
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
            )
            mainDate.text = getString(
                R.string.build_time,
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(BuildConfig.BUILD_TIME))
            )
            listView = appList
        }
        registerShizukuListeners()
    }

    private fun registerShizukuListeners() {
        if (shizukuListenersRegistered) return
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
        runCatching { Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(shizukuBinderDeadListener) }
        shizukuListenersRegistered = true
    }

    private fun setupClickListeners() {
        binding.mainActiveStatus.setOnClickListener {
            Toast.makeText(this, getString(R.string.check_version_update), Toast.LENGTH_SHORT).show()
            author.check(
                binding.mainActiveStatus, binding.mainStatus, BuildConfig.VERSION_CODE, true
            )
        }
    }

    private fun updateFrameworkStatus(name: String, apiLevel: Int) {
        with(binding) {
            mainImgStatus.setImageResource(R.drawable.ic_success)
            mainStatus.text = getString(R.string.is_active)
            mainFramework.visibility = View.VISIBLE
            mainFramework.text = getString(R.string.main_framework).format(name, "API $apiLevel")
        }
        author.check(binding.mainActiveStatus, binding.mainStatus, BuildConfig.VERSION_CODE)
    }

    private fun checkPermissions() {
        when {
            hasQueryAllPackagesPermission() -> loadAppList()
            autoGrantInProgress -> Unit
            else -> tryAutoGrantQueryAllPackages()
        }
    }

    private fun hasQueryAllPackagesPermission(): Boolean {
        return PrivilegedPermissionGrantor.hasQueryAllPackages(this)
    }

    private fun tryAutoGrantQueryAllPackages() {
        if (PrivilegedPermissionGrantor.requestShizukuPermissionIfNeeded()) {
            handleAutoGrantResult(GrantResult.WAITING_FOR_SHIZUKU)
            return
        }
        runAutoGrantQueryAllPackages()
    }

    private fun runAutoGrantQueryAllPackages() {
        autoGrantInProgress = true
        disposables.add(
            Observable.fromCallable {
                PrivilegedPermissionGrantor.ensureQueryAllPackages(applicationContext)
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    handleAutoGrantResult(result)
                }, {
                    handleAutoGrantResult(GrantResult.FAILED)
                })
        )
    }

    private fun handleAutoGrantResult(result: GrantResult) {
        when {
            result == GrantResult.GRANTED || hasQueryAllPackagesPermission() -> {
                autoGrantInProgress = false
                loadAppList()
            }

            result == GrantResult.WAITING_FOR_SHIZUKU -> {
                autoGrantInProgress = false
            }

            else -> {
                autoGrantInProgress = false
                showEssentialPermissionSettingsGuide()
            }
        }
    }

    private fun loadAppList() {
        if (appListAdapter != null || appListLoading) return
        appListLoading = true
        disposables.add(
            Observable.fromCallable {
                AppList(applicationContext).appList
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ list ->
                    appListLoading = false
                    appListAdapter = AppListAdapter(list)
                    listView.adapter = appListAdapter
                }, { error ->
                    appListLoading = false
                    showDataLoadError(error)
                })
        )
    }

    private fun showEssentialPermissionSettingsGuide() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.permission_denied_forever_title)
            .setMessage(R.string.permission_denied_forever_message).setPositiveButton(R.string.open_settings) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        this, getString(R.string.settings_open_failed), Toast.LENGTH_LONG
                    ).show()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showDataLoadError(error: Throwable) {
        Log.e(TAG, getString(R.string.data_load_failed, error.localizedMessage), error)
        Toast.makeText(
            this, getString(R.string.data_load_failed, error.localizedMessage), Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        runCatching { Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(shizukuBinderDeadListener) }
        mainHandler.removeCallbacks(initializeRunnable)
        appListAdapter = null
        disposables.clear()
        super.onDestroy()
    }

    override fun finish() {
        excludeFromRecent(true)
        super.finish()
    }

    override fun onResume() {
        super.onResume()
        excludeFromRecent(false)
    }

    private fun updateLanguageSelection(menu: Menu, languageTag: String) {
        val menuItemId = when (languageTag) {
            "system" -> R.id.menu_language_system
            Locale.ENGLISH.toLanguageTag() -> R.id.menu_language_en
            Locale.SIMPLIFIED_CHINESE.toLanguageTag() -> R.id.menu_language_zh_cn
            Locale.TRADITIONAL_CHINESE.toLanguageTag() -> R.id.menu_language_zh_tr
            else -> R.id.menu_language_system
        }
        menu.findItem(menuItemId)?.isChecked = true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        prefsBridge.getString(LANGUAGE_PREF_KEY, "system")?.let { updateLanguageSelection(menu, it) }
        menu.findItem(R.id.menu_click_info)?.isChecked = prefsBridge.getBoolean("clickInfo", false)
        menu.findItem(R.id.menu_stack_track)?.isChecked = prefsBridge.getBoolean("stackTrack", false)
        menu.findItem(R.id.menu_error_log)?.isChecked = prefsBridge.getBoolean("errorLog", false)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun saveLanguage(locale: Locale? = null) {
        val languageTag = locale?.toLanguageTag() ?: "system"
        prefsBridge.edit(commit = true) { putString(LANGUAGE_PREF_KEY, languageTag) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_minimize -> {
                finish()
                Handler(Looper.getMainLooper()).postDelayed({
                    Process.killProcess(Process.myPid())
                }, 300)
                true
            }

            R.id.menu_logout -> {
                author.logout()
                true
            }

            R.id.menu_join_qq_group -> {
                val url = "https://qm.qq.com/q/ACNWVPbfq0"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = url.toUri()
                }
                startActivity(intent)
                true
            }


            R.id.menu_click_info -> {
                prefsBridge.edit { putBoolean("clickInfo", !item.isChecked) }
                item.isChecked = !item.isChecked
                true
            }

            R.id.menu_stack_track -> {
                prefsBridge.edit { putBoolean("stackTrack", !item.isChecked) }
                item.isChecked = !item.isChecked
                true
            }

            R.id.menu_language_system -> {
                item.isChecked = true
                saveLanguage()
                LanguageUtils.resetToSystemLanguage(this)
                true
            }

            R.id.menu_language_en -> {
                item.isChecked = true
                saveLanguage(Locale.ENGLISH)
                LanguageUtils.setAppLanguage(this, Locale.ENGLISH)
                true
            }

            R.id.menu_language_zh_cn -> {
                item.isChecked = true
                saveLanguage(Locale.SIMPLIFIED_CHINESE)
                LanguageUtils.setAppLanguage(this, Locale.SIMPLIFIED_CHINESE)
                true
            }

            R.id.menu_language_zh_tr -> {
                item.isChecked = true
                saveLanguage(Locale.TRADITIONAL_CHINESE)
                LanguageUtils.setAppLanguage(this, Locale.TRADITIONAL_CHINESE)
                true
            }

            R.id.menu_sdk -> {
                preparePreviousPagePreview(SDKActivity::class.java)
                val intent = Intent(this, SDKActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.menu_auto_skip_rules -> {
                preparePreviousPagePreview(AutoSkipRulesActivity::class.java)
                val intent = Intent(this, AutoSkipRulesActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.menu_error_log -> {
                prefsBridge.edit { putBoolean("errorLog", !item.isChecked) }
                item.isChecked = !item.isChecked
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onServiceStateChanged(service: XposedService?) {
        super.onServiceStateChanged(service)
        appListAdapter?.refreshScopeState()
        if (::author.isInitialized) {
            service?.apply { updateFrameworkStatus(frameworkName, apiVersion) }
        }
    }

    /** 隐藏最近任务列表视图 */
    private fun excludeFromRecent(exclude: Boolean) {
        try {
            val manager: ActivityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (appTask in manager.appTasks) {
                if (appTask.taskInfo?.taskId == taskId) {
                    appTask.setExcludeFromRecents(exclude)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val TAG = "MainActivity"
        private const val LANGUAGE_PREF_KEY = "language"
    }
}