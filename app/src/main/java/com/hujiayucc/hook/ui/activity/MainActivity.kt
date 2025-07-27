package com.hujiayucc.hook.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.icu.text.SimpleDateFormat
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
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.base.ModuleAppCompatActivity
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.author.Author
import com.hujiayucc.hook.data.AppList
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.ui.adapter.AppListAdapter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Date
import kotlin.system.exitProcess

class MainActivity : ModuleAppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var listView: ListView
    private val disposables = CompositeDisposable()
    private lateinit var author: Author

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
        author = Author(this, true)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeUI()
        setupClickListeners()
        checkPermissions()
        author.check(binding.mainActiveStatus, binding.mainStatus, BuildConfig.VERSION_CODE)
    }

    @SuppressLint("SimpleDateFormat")
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
            mainDate.text = getString(
                R.string.build_time, SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(Date(YukiHookAPI.Status.compiledTimestamp))
            )
            listView = appList
        }
    }

    private fun setupClickListeners() {
        binding.mainActiveStatus.setOnClickListener {
            Toast.makeText(this, getString(R.string.check_version_update), Toast.LENGTH_SHORT)
                .show()
            author.check(
                binding.mainActiveStatus,
                binding.mainStatus,
                BuildConfig.VERSION_CODE,
                true
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
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.QUERY_ALL_PACKAGES
            ) == PERMISSION_GRANTED -> {
                loadAppList()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.QUERY_ALL_PACKAGES) ->
                showEssentialPermissionRationale()
        }
    }

    private fun loadAppList() {
        disposables.add(
            Observable.fromCallable {
                AppList(applicationContext).appList
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { list -> listView.adapter = AppListAdapter(list) },
                    { error -> showDataLoadError(error) }
                )
        )
    }

    private fun showEssentialPermissionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.essential_permission_title)
            .setMessage(R.string.essential_permission_message)
            .setPositiveButton(R.string.understand_and_grant) { _, _ ->
                allAppPermission.launch(Manifest.permission.QUERY_ALL_PACKAGES)
            }
            .setNegativeButton(R.string.exit_app) { _, _ ->
                finish()
                Handler(Looper.getMainLooper()).postDelayed({
                    exitProcess(0)
                }, 1000)
            }
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

    private fun showDataLoadError(error: Throwable) {
        YLog.error(getString(R.string.data_load_failed, error.localizedMessage), error)
        Toast.makeText(
            this,
            getString(R.string.data_load_failed, error.localizedMessage),
            Toast.LENGTH_LONG
        ).show()
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
        menu?.findItem(R.id.menu_dump_dex)?.isChecked = prefs().getBoolean("dump_dex", false)
        menu?.findItem(R.id.menu_out_info)?.isChecked = prefs().getBoolean("out_info", false)
        menu?.findItem(R.id.menu_exception)?.isChecked = prefs().getBoolean("exception", false)
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

            R.id.menu_logout -> {
                Author(this).logout()
                true
            }

            R.id.menu_dump_dex -> {
                prefs().edit().putBoolean("dump_dex", !item.isChecked).apply()
                item.isChecked = !item.isChecked
                true
            }

            R.id.menu_out_info -> {
                prefs().edit().putBoolean("out_info", !item.isChecked).apply()
                item.isChecked = !item.isChecked
                true
            }

            R.id.menu_exception -> {
                prefs().edit().putBoolean("exception", !item.isChecked).apply()
                item.isChecked = !item.isChecked
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
                if (appTask.taskInfo.taskId == taskId) {
                    appTask.setExcludeFromRecents(exclude)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}