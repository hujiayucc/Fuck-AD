package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication.Companion.appContext
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.bean.AppInfo
import com.hujiayucc.hook.data.Data.buildTime
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.data.Data.localeId
import com.hujiayucc.hook.data.DataConst.QQ_GROUP
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
    private val fragmentList = ArrayList<MainFragment>()
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
        val user = Fragment.instantiate(appContext, MainFragment::class.java.name, userBundle) as MainFragment
        val systemBundle = Bundle()
        systemBundle.putBoolean("system", true)
        val system = Fragment.instantiate(appContext, MainFragment::class.java.name, systemBundle) as MainFragment

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
        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                searchText = s.toString()
                Thread {
                    search(searchText)
                }.start()
            }
        })

        tabLayout.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // 当前选中的标签页
                if (searchText.isNotEmpty()) {
                    search(binding.search.text.toString())
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // 标签页从选中状态变为非选中状态时触发
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // 标签页已经选中，并且再次被选中时触发
            }
        })
    }

    fun search(text: String) {
        val fragment = fragmentList[tabLayout.selectedTabPosition]
        val list: ArrayList<AppInfo> = ArrayList()
        for (app in fragment.list) {
            if (app.app_name.contains(text) or app.app_package.contains(text)) list.add(app)
        }
        fragment.showList(list)
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

            R.id.menu_search -> {
                val inputMethodManager: InputMethodManager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                binding.search.visibility = View.VISIBLE
                binding.search.requestFocus()
                inputMethodManager.showSoftInput(binding.search, InputMethodManager.SHOW_IMPLICIT)
                true
            }

            R.id.menu_qq_group -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    appContext.startActivity(intent)
                } catch (e : Exception) {
                    Toast.makeText(appContext, getString(R.string.failed_to_start_qq), Toast.LENGTH_SHORT).show()
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("ResourceType")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (binding.search.visibility != View.GONE) {
                    binding.search.setText("")
                    binding.search.visibility = View.GONE
                    false
                } else {
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

    companion object {
        var searchText = ""
    }
}