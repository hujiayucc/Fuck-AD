package com.hujiayucc.hook.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
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
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.buildTime
import com.hujiayucc.hook.data.Data.checkRoot
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.data.Data.hideOrShowLauncherIcon
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.Data.isLauncherIconShowing
import com.hujiayucc.hook.data.Data.localeId
import com.hujiayucc.hook.data.Data.openService
import com.hujiayucc.hook.data.Data.themes
import com.hujiayucc.hook.data.DataConst.QQ_GROUP
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.service.BootReceiver
import com.hujiayucc.hook.service.SkipService
import com.hujiayucc.hook.ui.adapter.ViewPagerAdapter
import com.hujiayucc.hook.ui.fragment.MainFragment
import com.hujiayucc.hook.update.Update.checkUpdate
import com.hujiayucc.hook.utils.Language
import top.defaults.colorpicker.ColorPickerPopup
import java.io.File
import java.io.FileOutputStream
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager
    private val fragmentList = ArrayList<MainFragment>()
    private lateinit var adapter: ViewPagerAdapter
    private var localeID = 0
    private lateinit var imageView: ImageView
    private var alert_imageView: ImageView? = null
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        localeID = modulePrefs.get(localeId)
        if (localeID != 0) checkLanguage(Language.fromId(localeID))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        initView()
        // 检查是否已经授权该权限
        if (checkSelfPermission(Manifest.permission.CHANGE_CONFIGURATION) != PackageManager.PERMISSION_GRANTED) {
            // 请求授权该权限
            requestPermissions(arrayOf(Manifest.permission.CHANGE_CONFIGURATION), 2001)
        }
        val filter = IntentFilter()
        filter.addAction("com.hujiayucc.hook.service.StartService")
        registerReceiver(BootReceiver(), filter)
        val intent = Intent("com.hujiayucc.hook.service.StartService")
        sendBroadcast(intent)
    }

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    private fun initView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        tabLayout = binding.tabLayout
        viewPager = binding.viewPager
        initBackGround()
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
        }
        binding.mainActiveStatus.background = getDrawable(R.drawable.bg_header)
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
                search(searchText)
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
                val fragment = fragmentList[viewPager.currentItem]
                val listView = fragment.listView
                Thread {
                    while (listView.firstVisiblePosition != 0) {
                        runOnUiThread { listView.setSelection(listView.firstVisiblePosition - 1) }
                        Thread.sleep(10)
                    }
                }.start()
            }
        })
    }

    fun search(text: String) {
        val fragment = fragmentList[tabLayout.selectedTabPosition]
        val list: ArrayList<AppInfo> = ArrayList()
        if (searchText.isEmpty()) {
            for (app in fragment.list) {
                if (app.app_name.contains(text) or app.app_package.contains(text))
                    list.add(app)
            }
        } else {
            for (app in fragment.list) {
                if (app.app_name.contains(text) or app.app_package.contains(text))
                    list.add(app)
            }
            fragment.searchList.clear()
            fragment.searchList.addAll(list)
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
        menu.findItem(R.id.menu_hide_icon).isChecked = isLauncherIconShowing.not()
        menu.findItem(R.id.menu_auto_skip).isChecked = isAccessibilitySettingsOn(SkipService::class.java.canonicalName!!)
        val group = menu.findItem(R.id.menu_language_settings).subMenu?.item
        when (localeID) {
            0 -> group?.subMenu?.findItem(R.id.menu_language_defualt)?.isChecked = true
            1 -> group?.subMenu?.findItem(R.id.menu_language_en)?.isChecked = true
            2 -> group?.subMenu?.findItem(R.id.menu_language_zh)?.isChecked = true
        }
        this.menu = menu
        return true
    }

    override fun onStart() {
        super.onStart()
        val isChecked = isAccessibilitySettingsOn(SkipService::class.java.canonicalName!!)
        menu?.findItem(R.id.menu_auto_skip)?.isChecked = isChecked
        if (isChecked) {
            intent = Intent(appContext, SkipService::class.java)
            startService(intent)
        }
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
                } catch (e: Exception) {
                    Toast.makeText(appContext, getString(R.string.failed_to_open_qq), Toast.LENGTH_SHORT).show()
                }
                true
            }

            R.id.menu_color -> {
                ColorPickerPopup.Builder(this)
                    .initialColor(modulePrefs.get(themes))
                    .enableBrightness(true)
                    .enableAlpha(true)
                    .okTitle(getString(R.string.popup_done))
                    .cancelTitle(getString(R.string.popup_cancel))
                    .showIndicator(true)
                    .showValue(false)
                    .build()
                    .show(item.actionView, object : ColorPickerPopup.ColorPickerObserver() {
                        @SuppressLint("UnspecifiedImmutableFlag")
                        override fun onColorPicked(color: Int) {
                            modulePrefs.put(themes, color)
                            Thread {
                                Thread.sleep(300)
                                var intents = packageManager.getLaunchIntentForPackage(packageName)
                                if (intents != null)
                                    intents.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                else intents = Intent(appContext, MainActivity::class.java)
                                startActivity(intents)
                                //杀掉以前进程
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }.start()
                        }
                    })
                true
            }

            R.id.menu_hide_icon -> {
                hideOrShowLauncherIcon(!item.isChecked)
                super.finish()
                true
            }

            R.id.menu_background -> {
                val filename = "background.png"
                alert_imageView = ImageView(appContext)
                alert_imageView!!.setPadding(0, 50, 0, 0)
                try {
                    alert_imageView!!.setImageBitmap((imageView.drawable as BitmapDrawable).toBitmap())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val dialog = AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setTitle(getString(R.string.alert_choose_image_title))
                    .setNeutralButton(getString(R.string.alert_choose_image), null)
                    .setPositiveButton(getString(R.string.alert_done)) { dialog, which ->
                        binding.root.background = alert_imageView!!.drawable
                        try {
                            val file = File(filesDir, filename)
                            val image = (alert_imageView!!.drawable as BitmapDrawable).toBitmap()
                            val fileOutputStream = FileOutputStream(file)
                            image.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                            fileOutputStream.flush()
                            fileOutputStream.close()
                            modulePrefs.put(Data.background, file.path)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        dialog.dismiss()
                        recreate()
                    }
                    .setNegativeButton(getString(R.string.alert_reset)) { dialog, which ->
                        try {
                            val file = File(filesDir, filename)
                            if (file.exists())
                                file.delete()
                            recreate()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        dialog?.dismiss()
                    }
                    .setView(alert_imageView)
                    .create()
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    val intent = Intent(Intent.ACTION_PICK, null)
                    intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                    startActivityForResult(intent, 2)
                    return@setOnClickListener
                }
                true
            }

            R.id.menu_auto_skip -> {
                if (checkRoot()) {
                    openService()
                    val isChecked = isAccessibilitySettingsOn(SkipService::class.java.canonicalName!!)
                    menu?.findItem(R.id.menu_auto_skip)?.isChecked = isChecked
                    if (isChecked) {
                        intent = Intent(appContext, SkipService::class.java)
                        startService(intent)
                    }
                }
                else {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                true
            }

            R.id.menu_minimize -> {
                try {
                    super.finishAndRemoveTask()
                    val intent = Intent("com.hujiayucc.hook.service.StartService")
                    sendBroadcast(intent)
                    Handler().postDelayed({
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },1000)
                } catch (e : Exception) {
                    e.printStackTrace()
                }
                false
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("ResourceAsColor")
    private fun initBackGround() {
        imageView = ImageView(appContext)
        try {
            val background = modulePrefs.get(Data.background)
            imageView.setImageBitmap(BitmapFactory.decodeFile(background))
            binding.root.background = imageView.drawable
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2) {
            // 从相册返回的数据
            if (data != null) {
                // 得到图片的全路径并设置
                val uri = data.data
                alert_imageView!!.setImageURI(data.data)
            }
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
                    finish()
                    true
                }
            }

            else -> super.onKeyDown(keyCode, event)
        }
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

    /** 隐藏最近任务列表视图 */
    private fun excludeFromRecent(exclude: Boolean) {
        try {
            val manager: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (appTask in manager.appTasks) {
                if (appTask.taskInfo.id == taskId) {
                    appTask.setExcludeFromRecents(exclude)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkLanguage(language: Locale) {
        val configuration = resources.configuration
        configuration.setLocale(language)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        val locale = resources.configuration.locale
        if (Language.fromId(localeID) != locale) recreate()
    }

    override fun recreate() {
        super.finish()
        val intent = Intent(appContext, this::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        appContext.startActivity(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    companion object {
        var searchText = ""
    }
}