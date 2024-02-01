@file:Suppress("DEPRECATION")

package com.hujiayucc.hook.ui.base

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.base.ModuleAppCompatActivity
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.BuildConfig.SERVICE_NAME
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.checkRoot
import com.hujiayucc.hook.data.Data.hideOrShowLauncherIcon
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.Data.isLauncherIconShowing
import com.hujiayucc.hook.data.Data.runService
import com.hujiayucc.hook.data.Data.setSpan
import com.hujiayucc.hook.data.Data.stopService
import com.hujiayucc.hook.data.Data.updateConfig
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.service.SkipService
import com.hujiayucc.hook.ui.activity.MainActivity
import com.hujiayucc.hook.ui.adapter.AppInfo
import com.hujiayucc.hook.ui.adapter.ViewPagerAdapter
import com.hujiayucc.hook.ui.fragment.MainFragment
import com.hujiayucc.hook.utils.FormatJson.formatJson
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Log
import com.hujiayucc.hook.utils.Update
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.model.AspectRatio
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.*


open class BaseActivity: ModuleAppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager
    private val fragmentList = ArrayList<MainFragment>()
    private lateinit var adapter: ViewPagerAdapter
    private var alertimageView: ImageView? = null
    private var localeID = 0
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        localeID = prefs().get(Data.localeId)
        if (localeID != 0) checkLanguage(Language.fromId(localeID))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
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
        menu.findItem(R.id.menu_global).isChecked = prefs().get(Data.global)
        menu.findItem(R.id.menu_show_hook_success).isChecked = prefs().get(Data.hookTip)
        menu.findItem(R.id.menu_hide_icon).isChecked = isLauncherIconShowing.not()
        menu.findItem(R.id.menu_auto_skip).isChecked = isAccessibilitySettingsOn(SERVICE_NAME)

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
        val isChecked = isAccessibilitySettingsOn(SERVICE_NAME)
        menu?.findItem(R.id.menu_auto_skip)?.isChecked = isChecked
        if (isChecked) {
            intent = Intent(applicationContext, SkipService::class.java)
            startService(intent)
        }
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

    private fun checkUpdate(show: Boolean) {
        Thread {
            val info = Update.checkUpdate()
            if (info != null) {
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
                    if (show) runOnUiThread {
                        Toast.makeText(applicationContext, getString(R.string.latest_version), Toast.LENGTH_SHORT).show()
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

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    fun initView() {
        tabLayout = binding.tabLayout
        viewPager = binding.viewPager
        initBackGround()
        val userBundle = Bundle()
        userBundle.putBoolean("system", false)
        val user = Fragment.instantiate(applicationContext, MainFragment::class.java.name, userBundle) as MainFragment
        val systemBundle = Bundle()
        systemBundle.putBoolean("system", true)
        val system = Fragment.instantiate(applicationContext, MainFragment::class.java.name, systemBundle) as MainFragment
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
            binding.mainFramework.visibility = View.VISIBLE
            binding.mainFramework.text = getString(R.string.main_framework).format(YukiHookAPI.Status.Executor.name, "API ${YukiHookAPI.Status.Executor.apiLevel}")
        }
        binding.mainActiveStatus.background = getDrawable(R.drawable.bg_header)
        binding.mainVersion.text = getString(R.string.main_version)
            .format(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        binding.mainActiveStatus.setOnClickListener {
            checkUpdate(true)
        }
        binding.mainDate.text = "Build Time：${Data.buildTime}"
        checkUpdate(false)
        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                MainActivity.searchText = s.toString()
                search(MainActivity.searchText)
            }
        })

        tabLayout.setOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // 当前选中的标签页
                if (MainActivity.searchText.isNotEmpty()) {
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
                listView?.let {
                    if (it.firstVisiblePosition != 0) it.smoothScrollToPosition(0)
                    else it.smoothScrollToPosition(it.adapter.count)
                }
            }
        })
    }

    fun search(text: String) {
        val fragment = fragmentList[tabLayout.selectedTabPosition]
        val list: ArrayList<AppInfo> = ArrayList()
        val texts = text.lowercase(Locale.CHINESE)
        if (MainActivity.searchText.isEmpty()) {
            for (app in fragment.list) {
                val appName = app.appName.toString().lowercase(Locale.CHINESE)
                val appPackage = app.packageName.lowercase(Locale.CHINESE)
                if (appName.contains(texts) or appPackage.contains(texts))
                    list.add(app)
            }
        } else {
            for (app in fragment.list) {
                val appName = app.appName.toString().lowercase(Locale.CHINESE)
                val appPackage = app.packageName.lowercase(Locale.CHINESE)
                if (appName.contains(texts) or appPackage.contains(texts))
                    list.add(app)
            }
            fragment.searchList.clear()
            fragment.searchList.addAll(list)
        }
        fragment.showList(list)
    }

    @SuppressLint("MissingInflatedId")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (prefs().getString("session").isBlank()) return false
        return when (item.itemId) {
            R.id.menu_global -> {
                item.isChecked = !item.isChecked
                prefs().edit { put(Data.global, item.isChecked) }
                true
            }

            R.id.menu_show_hook_success -> {
                item.isChecked = !item.isChecked
                prefs().edit { put(Data.hookTip, item.isChecked) }
                updateConfig(prefs().all())
                true
            }

            R.id.menu_language_defualt -> {
                item.isChecked = true
                checkLanguage(Locale.getDefault())
                prefs().edit { put(Data.localeId, 0) }
                updateConfig(prefs().all())
                true
            }

            R.id.menu_language_en -> {
                item.isChecked = true
                checkLanguage(Locale.ENGLISH)
                prefs().edit { put(Data.localeId, 1) }
                updateConfig(prefs().all())
                true
            }

            R.id.menu_language_zh -> {
                item.isChecked = true
                checkLanguage(Locale.CHINESE)
                prefs().edit { put(Data.localeId, 2) }
                updateConfig(prefs().all())
                true
            }

            R.id.menu_search -> {
                val inputMethodManager: InputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                binding.search.visibility = View.VISIBLE
                binding.search.requestFocus()
                inputMethodManager.showSoftInput(binding.search, InputMethodManager.SHOW_IMPLICIT)
                true
            }

            R.id.menu_qq_group -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Data.QQ_GROUP))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    applicationContext.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, getString(R.string.failed_to_open_qq), Toast.LENGTH_SHORT).show()
                }
                true
            }

            R.id.menu_hide_icon -> {
                hideOrShowLauncherIcon(!item.isChecked)
                super.finish()
                true
            }

            R.id.menu_background -> {
                val filename = "background.png"
                alertimageView = ImageView(applicationContext)
                alertimageView?.setPadding(0, 50, 0, 0)
                alertimageView?.setImageURI(File(filesDir, filename).toUri())
                val dialog = AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setTitle(getString(R.string.alert_choose_image_title))
                    .setNeutralButton(getString(R.string.alert_choose_image),null)
                    .setPositiveButton(getString(R.string.alert_done)) { dialog, _ ->
                        binding.root.background = alertimageView?.drawable
                        try {
                            val file = File(filesDir, filename)
                            val image = (alertimageView?.drawable as BitmapDrawable).bitmap
                            val fileOutputStream = FileOutputStream(file)
                            image.compress(Bitmap.CompressFormat.PNG,100, fileOutputStream)
                            fileOutputStream.flush()
                            fileOutputStream.close()
                            prefs().edit { put(Data.background, file.path) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        dialog.dismiss()
                        super.recreate()
                    }
                    .setNegativeButton(getString(R.string.alert_reset)) { dialog, _ ->
                        try {
                            val file = File(filesDir, filename)
                            if (file.exists())
                                file.delete()
                            super.recreate()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        dialog?.dismiss()
                    }
                    .setView(alertimageView)
                    .create()
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    } else {
                        openGallery()
                    }
                    return@setOnClickListener
                }
                true
            }

            R.id.menu_auto_skip -> {
                if (checkRoot()) {
                    if (!applicationContext.isAccessibilitySettingsOn(SERVICE_NAME)) {
                        applicationContext.runService()
                    } else {
                        applicationContext.stopService()
                    }
                } else {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                Handler().postDelayed({
                    val isChecked = isAccessibilitySettingsOn(SERVICE_NAME)
                    menu?.findItem(R.id.menu_auto_skip)?.isChecked = isChecked
                },500)
                true
            }

            R.id.menu_minimize -> {
                try {
                    val view = layoutInflater.inflate(R.layout.progress_dialog, null)
                    view.findViewById<TextView>(R.id.progress_text).text = getString(R.string.saving_configs)
                    MaterialAlertDialogBuilder(this)
                        .setView(view)
                        .setCancelable(false)
                        .create().show()

                    Thread {
                        val config = File(filesDir, "config.json")
                        val inputStream = config.inputStream()
                        val byte = ByteArray(config.length().toInt())
                        inputStream.read(byte)
                        inputStream.close()
                        val outputStream = FileOutputStream(config)
                        outputStream.write(JSONObject(String(byte)).formatJson())
                        outputStream.flush()
                        outputStream.close()
                        super.finishAndRemoveTask()
                        if (!isAccessibilitySettingsOn(SERVICE_NAME) &&
                            menu?.findItem(R.id.menu_auto_skip)?.isChecked == true
                        ) {
                            runService()
                        }
                        Looper.prepare()
                        Process.killProcess(Process.myPid())
                    }.start()
                } catch (e : Exception) {
                    e.message?.let { Log.e("minimize", it) }
                }
                true
            }

            R.id.menu_github -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hujiayucc/Fuck-AD"))
                    startActivity(intent)
                } catch (e : Exception) {
                    e.printStackTrace()
                }
                true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun initBackGround() {
        val imageView = ImageView(applicationContext)
        try {
            val background = prefs().get(Data.background)
            imageView.setImageBitmap(BitmapFactory.decodeFile(background))
            binding.root.background = imageView.drawable
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            alertimageView?.setImageURI(it)
            startCropActivity(uri)
        }
    }

    // 启动裁剪 Activity 的功能
    private fun startCropActivity(inputUri: Uri) {
        val destinationUri = Uri.fromFile(File(filesDir, "background.png"))
        val options = UCrop.Options()
        val aspectRatioX = resources.displayMetrics.widthPixels.toFloat()
        val aspectRatioY = resources.displayMetrics.heightPixels.toFloat()
        options.setAspectRatioOptions(0, AspectRatio("${aspectRatioX.toInt()}x${aspectRatioY.toInt()}", aspectRatioX, aspectRatioY))

        UCrop.of(inputUri, destinationUri)
            .withOptions(options)
            .start(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP) {
            alertimageView?.setImageURI(File(filesDir, "background.png").toUri())
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // 权限被授予，继续打开相册
            openGallery()
        }
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
}