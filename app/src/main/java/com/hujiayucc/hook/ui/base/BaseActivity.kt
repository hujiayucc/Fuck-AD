@file:Suppress("DEPRECATION")

package com.hujiayucc.hook.ui.base

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.service.SkipService
import com.hujiayucc.hook.utils.Data
import com.hujiayucc.hook.utils.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.utils.Data.isLauncherIconShowing
import com.hujiayucc.hook.utils.Data.setSpan
import com.hujiayucc.hook.utils.Data.updateConfig
import com.hujiayucc.hook.utils.HotFixUtils
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Update
import com.hujiayucc.hook.utils.Update.updateHotFix
import java.util.*

open class BaseActivity: AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private var localeID = 0
    var menu: Menu? = null

    override fun attachBaseContext(newBase: Context?) {
        runCatching { newBase?.classLoader?.let { HotFixUtils().doHotFix(it) } }
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        localeID = modulePrefs.get(Data.localeId)
        if (localeID != 0) checkLanguage(Language.fromId(localeID))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        // 检查是否已经授权该权限
        if (checkSelfPermission(Manifest.permission.CHANGE_CONFIGURATION) != PackageManager.PERMISSION_GRANTED) {
            // 请求授权该权限
            requestPermissions(arrayOf(Manifest.permission.CHANGE_CONFIGURATION), 2001)
        }
        // 适配 Android13 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.areNotificationsEnabled()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2000)
            }
        }
    }

    fun checkLanguage(language: Locale) {
        val configuration = resources.configuration
        configuration.setLocale(language)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        val locale = resources.configuration.locale
        if (Language.fromId(localeID) != locale) recreate()
    }

    override fun recreate() {
        super.finish()
        val intent = Intent(applicationContext, this::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        applicationContext.startActivity(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun finish() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        excludeFromRecent(true)
    }

    override fun onResume() {
        super.onResume()
        excludeFromRecent(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.menu_global).isChecked = modulePrefs.get(Data.global)
        menu.findItem(R.id.menu_show_hook_success).isChecked = modulePrefs.get(Data.hookTip)
        menu.findItem(R.id.menu_hide_icon).isChecked = isLauncherIconShowing.not()
        menu.findItem(R.id.menu_auto_skip).isChecked = isAccessibilitySettingsOn(BuildConfig.SERVICE_NAME)

        val group = menu.findItem(R.id.menu_language_settings).subMenu?.item
        when (localeID) {
            0 -> group?.subMenu?.findItem(R.id.menu_language_defualt)?.isChecked = true
            1 -> group?.subMenu?.findItem(R.id.menu_language_en)?.isChecked = true
            2 -> group?.subMenu?.findItem(R.id.menu_language_zh)?.isChecked = true
        }
        menu.findItem(R.id.menu_language_settings).subMenu?.setHeaderTitle(getString(R.string.language_settings).setSpan(resources.getColor(
            R.color.theme)))
        menu.findItem(R.id.menu_theme_settings).subMenu?.setHeaderTitle(getString(R.string.menu_theme_settings).setSpan(resources.getColor(
            R.color.theme)))
        menu.findItem(R.id.menu_module_settings).subMenu?.setHeaderTitle(getString(R.string.menu_module_settings).setSpan(resources.getColor(
            R.color.theme)))
        this.menu = menu
        return true
    }

    override fun onStart() {
        super.onStart()
        checkUpdate(false)
        val isChecked = isAccessibilitySettingsOn(BuildConfig.SERVICE_NAME)
        menu?.findItem(R.id.menu_auto_skip)?.isChecked = isChecked
        if (isChecked) {
            intent = Intent(applicationContext, SkipService::class.java)
            startService(intent)
        }
        updateConfig(modulePrefs.all())
    }

    /** 隐藏最近任务列表视图 */
    private fun excludeFromRecent(exclude: Boolean) {
        try {
            val manager: ActivityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (appTask in manager.appTasks) {
                if (appTask.taskInfo.id == taskId) {
                    appTask.setExcludeFromRecents(exclude)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkUpdate(show: Boolean) {
        Thread {
            val info = Update.checkUpdate()
            var hotFix = false
            if (info != null) {
                if (info.getInt("hotFixVersion") > BuildConfig.HOT_VERSION) {
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle("发现热更新")
                            .setMessage("${info.getString("hotFixName")}\n\n${info.getString("updateLog")}")
                            .setPositiveButton("升级") { dialog, _ ->
                                dialog?.dismiss()
                                kotlin.runCatching {
                                    updateHotFix(info)
                                }
                            }.setNegativeButton("关闭") { dialog, _ -> dialog?.dismiss() }.setCancelable(false).show()
                    }
                    hotFix = true
                }

                if (info.getInt("versionCode") > BuildConfig.VERSION_CODE) {
                    val url = Uri.parse(info.getString("url"))
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    runOnUiThread {
                        binding.mainStatus.text = getString(R.string.has_update)
                        binding.mainActiveStatus.setOnClickListener {
                            startActivity(intent)
                        }
                    }
                } else {
                    runOnUiThread {
                        if (show && !hotFix) Toast.makeText(applicationContext, getString(R.string.latest_version), Toast.LENGTH_SHORT).show()
                    }
                }
            } else runOnUiThread {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.check_update_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }
}