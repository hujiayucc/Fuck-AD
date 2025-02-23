package com.hujiayucc.hook.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.base.ModuleAppCompatActivity
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Config
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.mapper
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.service.ClickService
import com.hujiayucc.hook.ui.adapter.AppListAdapter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers

class MainActivity : ModuleAppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var listView: ListView
    private val disposables = CompositeDisposable()

    private val notifyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> startService()
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                    showPermissionSettingsGuide()
            }
        }

    private val allAppPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> loadAppList()
                shouldShowRequestPermissionRationale(Manifest.permission.QUERY_ALL_PACKAGES) ->
                    showEssentialPermissionRationale()

                else -> showEssentialPermissionSettingsGuide()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeUI()
        checkEssentialPermissions()
    }

    private fun initializeUI() {
        setSupportActionBar(binding.toolbar)
        with(binding) {
            mainActiveStatus.background = AppCompatResources.getDrawable(
                this@MainActivity,
                R.drawable.bg_header
            )
            mainVersion.text = getString(R.string.main_version).format(
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
            mainDate.text = getString(R.string.build_time, Data.buildTime)
            listView = appList
        }
        updateFrameworkStatus()
    }

    private fun setupClickListeners() {
        binding.mainActiveStatus.setOnClickListener {
            Toast.makeText(this, "Active Status", Toast.LENGTH_SHORT).show()
        }
        binding.mainStatus.setOnClickListener {
            Toast.makeText(this, "Module Status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFrameworkStatus() {
        if (YukiHookAPI.Status.isModuleActive) {
            with(binding) {
                mainImgStatus.setImageResource(R.drawable.ic_success)
                mainStatus.text = getString(R.string.is_active)
                mainFramework.visibility = View.VISIBLE
                mainFramework.text = getString(R.string.main_framework).format(
                    YukiHookAPI.Status.Executor.name,
                    "API ${YukiHookAPI.Status.Executor.apiLevel}"
                )
            }
        }
        setupClickListeners()
    }

    private fun checkEssentialPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.QUERY_ALL_PACKAGES
            ) == PERMISSION_GRANTED -> {
                loadAppList()
                checkNotificationPermission()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.QUERY_ALL_PACKAGES) ->
                showEssentialPermissionRationale()

            else -> {
                requestAppListPermissionWithVisibility()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            handleNotificationPermission()
        } else {
            startService()
        }
    }

    @SuppressLint("CheckResult")
    private fun loadAppList() {
        disposables.add(
            Observable.fromCallable {
                mapper.readValue(assets.open("default.json"), Config::class.java)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { config -> listView.adapter = AppListAdapter(config.rules) },
                    { error -> showDataLoadError(error) }
                )
        )
    }

    private fun handleNotificationPermission() {
        when {
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                showRationaleWithDelay()

            else -> notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showRationaleWithDelay() {
        binding.root.postDelayed({
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.grant) { _, _ ->
                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton(R.string.deny, null)
                .show()
        }, 300)
    }

    private fun showEssentialPermissionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.essential_permission_title)
            .setMessage(R.string.essential_permission_message)
            .setPositiveButton(R.string.understand_and_grant) { _, _ ->
                allAppPermission.launch(Manifest.permission.QUERY_ALL_PACKAGES)
            }
            .setNegativeButton(R.string.limited_function) { _, _ ->
                showLimitedFunctionWarning()
            }
            .show()
    }

    private fun showLimitedFunctionWarning() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.function_limited_title)
            .setMessage(R.string.function_limited_message)
            .setPositiveButton(R.string.retry) { _, _ ->
                allAppPermission.launch(Manifest.permission.QUERY_ALL_PACKAGES)
            }
            .setNegativeButton(R.string.exit_app, null)
            .show()
    }

    private fun showEssentialPermissionSettingsGuide() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_denied_forever_title)
            .setMessage(R.string.permission_denied_forever_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        this,
                        getString(R.string.settings_open_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPermissionSettingsGuide() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_denied_forever_title)
            .setMessage(getString(R.string.permission_denied_forever_message))
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            }
            .show()
    }

    private fun startService() {
        startForegroundService(Intent(this, ClickService::class.java))
    }

    private fun showDataLoadError(error: Throwable) {
        Toast.makeText(
            this,
            getString(R.string.data_load_failed, error.localizedMessage),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun requestAppListPermissionWithVisibility() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            allAppPermission.launch(Manifest.permission.QUERY_ALL_PACKAGES)
        }
    }

    override fun onDestroy() {
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (binding.search.visibility != View.GONE) {
                    binding.search.setText("")
                    binding.search.visibility = View.GONE
                    false
                } else {
                    finish()
                    true
                }
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
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

            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 隐藏最近任务列表视图 */
    private fun excludeFromRecent(exclude: Boolean) {
        try {
            val manager: ActivityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (appTask in manager.appTasks) {
                @Suppress("DEPRECATION")
                if (appTask.taskInfo.id == taskId) {
                    appTask.setExcludeFromRecents(exclude)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}