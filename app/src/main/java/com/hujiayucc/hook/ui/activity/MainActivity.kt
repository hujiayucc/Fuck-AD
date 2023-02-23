package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication.Companion.appContext
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data.buildTime
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.data.Data.localeId
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.ui.adapter.ViewPagerAdapter
import com.hujiayucc.hook.ui.fragment.MainFragment
import com.hujiayucc.hook.update.Update.checkUpdate
import com.hujiayucc.hook.utils.Language
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager
    private val fragmentList = ArrayList<Fragment>()
    private lateinit var adapter: ViewPagerAdapter

    private var mExitTime: Long = 0L
    private var localeID = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        localeID = modulePrefs.get(localeId)
        if (localeID != 0) checkLanguage(Language.fromId(localeID))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        initView()
    }

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    private fun initView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        tabLayout = binding.tabLayout
        viewPager = binding.viewPager
        val userBundle = Bundle()
        userBundle.putBoolean("system", false)
        val user = Fragment.instantiate(appContext, MainFragment::class.java.name, userBundle)
        val systemBundle = Bundle()
        systemBundle.putBoolean("system", true)
        val system = Fragment.instantiate(appContext, MainFragment::class.java.name, systemBundle)

        fragmentList.add(user)
        fragmentList.add(system)
        tabLayout.setupWithViewPager(viewPager)
        val title = arrayOf(
            getString(R.string.user_app),
            getString(R.string.system_app)
        )
        adapter = ViewPagerAdapter(supportFragmentManager, fragmentList, title)
        viewPager.adapter = adapter
        viewPager.currentItem = 0

        if (YukiHookAPI.Status.isModuleActive) {
            binding.mainImgStatus.setImageResource(R.drawable.ic_success)
            binding.mainStatus.text = getString(R.string.is_active)
            binding.mainActiveStatus.background = getDrawable(R.drawable.is_active)
        }
        binding.mainVersion.text = getString(R.string.main_version)
            .format(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        binding.mainDate.text = "Build Time：${buildTime}"
        Update()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun Update() {
        Thread {
            val info = checkUpdate()
            if ((info != null) && (info.toString().length > 10)) {
                runOnUiThread {
                    binding.mainStatus.text = getString(R.string.has_update)
                    binding.mainActiveStatus.background = getDrawable(R.drawable.has_update_version)
                    binding.mainActiveStatus.setOnClickListener {
                        val url = Uri.parse(info.toString())
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        startActivity(intent)
                    }
                }
            } else if (info != 0) runOnUiThread {
                Toast.makeText(
                    appContext,
                    getString(R.string.check_update_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
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