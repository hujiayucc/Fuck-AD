package com.hujiayucc.hook.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication.Companion.appContext
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.adapter.ListViewAdapter
import com.hujiayucc.hook.bean.AppInfo
import com.hujiayucc.hook.data.Data.buildTime
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.data.Data.localeId
import com.hujiayucc.hook.data.Data.showSystemApp
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.update.Update
import com.hujiayucc.hook.update.Update.url
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Log
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var refresh: SwipeRefreshLayout
    private var mExitTime: Long = 0L
    private val list = ArrayList<AppInfo>()
    private var localeID = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        localeID = modulePrefs.get(localeId)
        if (localeID != 0) checkLanguage(Language.fromId(localeID))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        initView()
        loadAppList(modulePrefs.get(showSystemApp))
    }

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    private fun initView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        listView = binding.list
        progressBar = binding.progress
        refresh = binding.refresh

        refresh.setOnRefreshListener {
            loadAppList(modulePrefs.get(showSystemApp))
            refresh.isRefreshing = false
        }

        if (YukiHookAPI.Status.isModuleActive) {
            binding.mainImgStatus.setImageResource(R.drawable.ic_success)
            binding.mainStatus.text = getString(R.string.is_active)
            binding.mainActiveStatus.background = getDrawable(R.drawable.is_active)
        }
        binding.mainVersion.text = getString(R.string.main_version)
            .format(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        binding.mainDate.text = "Build Time：${buildTime}"

        if (!Update.isLast()) {
            binding.mainStatus.text = getString(R.string.has_update)
            binding.mainActiveStatus.background = getDrawable(R.drawable.has_update_version)
            binding.mainActiveStatus.setOnClickListener {
                val url = Uri.parse(url)
                val intent = Intent(ACTION_VIEW, url)
                startActivity(intent)
            }
        }
    }

    private fun loadAppList(showSysApp: Boolean = false) {
        list.clear()
        progressBar.progress = 0
        refresh.visibility = View.GONE
        binding.progress.visibility = View.VISIBLE
        Thread {
            try {
                val apps =
                    packageManager.getInstalledApplications(PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
                var i = 0
                progressBar.max = apps.size
                for (info in apps) {
                    if (!info.packageName.equals(BuildConfig.APPLICATION_ID) && !info.sourceDir.equals("/system/app/HybridPlatform/HybridPlatform.apk")) {
                        val icon = info.loadIcon(packageManager)
                        val label = packageManager.getApplicationLabel(info)
                        val appinfo = AppInfo(icon, label, info.packageName)
                        val flag = info.flags
                        if (!showSysApp) {
                            if ((flag and ApplicationInfo.FLAG_SYSTEM) == 0) list.add(appinfo)
                        } else {
                            list.add(appinfo)
                        }
                    }
                    progressBar.progress = i
                    i++
                    if (i == apps.size) showList(list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun showList(list: ArrayList<AppInfo>) {
        val instance = java.text.Collator.getInstance(Locale.CHINA)
        list.sortWith { o1, o2 ->
            val o1Boolean = modulePrefs.getBoolean(o1.app_package!!, false)
            val o2Boolean = modulePrefs.getBoolean(o2.app_package!!, false)

            /** 排序 */
            if (o1Boolean && !o2Boolean) -1
            else if (!o1Boolean && o2Boolean) 0
            else instance.compare(o1.app_name!!, o2.app_name!!)
        }
        runOnUiThread {
            val adapter = ListViewAdapter(applicationContext, list, modulePrefs)
            listView.adapter = adapter
            progressBar.visibility = View.GONE
            refresh.visibility = View.VISIBLE
            Log.d("加载完毕")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.menu_show_sys_app).isChecked = modulePrefs.get(showSystemApp)
        menu.findItem(R.id.menu_global).isChecked = modulePrefs.get(global)
        menu.findItem(R.id.menu_show_hook_success).isChecked = modulePrefs.get(hookTip)
        val group = menu.findItem(R.id.menu_language_settings).subMenu?.item
        when (localeID) {
            0 -> group?.subMenu?.findItem(R.id.menu_language_defualt)?.isChecked = true
            1 -> group?.subMenu?.findItem(R.id.menu_language_en)?.isChecked = true
            2 -> group?.subMenu?.findItem(R.id.menu_language_zh)?.isChecked = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_show_sys_app -> {
                if (refresh.visibility == View.GONE) {
                    Toast.makeText(appContext, getString(R.string.wait_loading), Toast.LENGTH_SHORT).show()
                    return false
                }
                item.isChecked = !item.isChecked
                modulePrefs.put(showSystemApp, item.isChecked)
                loadAppList(item.isChecked)
                true
            }

            R.id.menu_global -> {
                item.isChecked = !item.isChecked
                modulePrefs.put(global, item.isChecked)
                true
            }

            R.id.menu_show_hook_success -> {
                item.isChecked = !item.isChecked
                modulePrefs.put(hookTip, item.isChecked)
                true
            }

            R.id.menu_language_defualt -> {
                item.isChecked = true
                checkLanguage(Locale.getDefault())
                modulePrefs.put(localeId, 0)
                true
            }

            R.id.menu_language_en -> {
                item.isChecked = true
                checkLanguage(Locale.ENGLISH)
                modulePrefs.put(localeId, 1)
                true
            }

            R.id.menu_language_zh -> {
                item.isChecked = true
                checkLanguage(Locale.CHINESE)
                modulePrefs.put(localeId, 2)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (System.currentTimeMillis() - mExitTime > 2000) {
                    Toast.makeText(applicationContext, getString(R.string.exit_tip), Toast.LENGTH_SHORT).show()
                    mExitTime = System.currentTimeMillis()
                } else {
                    finish()
                    Thread {
                        Thread.sleep(500)
                        System.exit(0)
                    }.start()
                }
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun checkLanguage(language: Locale) {
        val configuration = resources.configuration
        configuration.setLocale(language)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        val locale = resources.configuration.locale
        if (Language.fromId(localeID) != locale) recreate()
    }
}