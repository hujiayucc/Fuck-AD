package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.hujiayucc.hook.R
import com.hujiayucc.hook.databinding.ActivityAppInfoBinding
import com.hujiayucc.hook.ui.adapter.InfoPagerAdapter

class AppInfoActivity : BaseActivity<ActivityAppInfoBinding>() {
    private lateinit var binding: ActivityAppInfoBinding
    private var packageName: String = ""
    private var lastQuery: String = ""
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            // 切换 Tab 时把当前搜索词再派发一次，保证新页面立即应用过滤
            dispatchSearchQuery(lastQuery)
        }
    }

    companion object {
        private const val FR_SEARCH_KEY_ACTIVITY = "app_info_search_activity"
        private const val FR_SEARCH_KEY_SERVICE = "app_info_search_service"
        private const val FR_QUERY_KEY = "query"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        packageName = intent.getStringExtra("packageName") ?: ""
        val appName = intent.getStringExtra("appName") ?: packageName

        supportActionBar?.title = appName

        checkPackageAndSetupViewPager()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_info, menu)
        val item = menu.findItem(R.id.action_search)
        val sv = item.actionView as? SearchView

        sv?.queryHint = getString(R.string.app_info_search_hint)
        // 恢复上次输入（比如旋转屏/重建）
        if (lastQuery.isNotEmpty()) {
            item.expandActionView()
            sv?.setQuery(lastQuery, false)
        }

        sv?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                lastQuery = newText.orEmpty()
                dispatchSearchQuery(lastQuery)
                return true
            }
        })

        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                lastQuery = ""
                dispatchSearchQuery("")
                return true
            }
        })
        return true
    }

    @SuppressLint("SetTextI18n")
    private fun checkPackageAndSetupViewPager() {
        if (packageName.isEmpty()) {
            binding.progressBar.visibility = View.GONE
            binding.textView.text = "包名为空"
            binding.textView.visibility = View.VISIBLE
            return
        }

        try {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
            )

            setupViewPager()
            binding.progressBar.visibility = View.GONE
            binding.textView.visibility = View.GONE
        } catch (_: NameNotFoundException) {
            binding.progressBar.visibility = View.GONE
            binding.textView.text = "应用不存在或无法访问"
            binding.textView.visibility = View.VISIBLE
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.textView.text = "加载失败: ${e.message}"
            binding.textView.visibility = View.VISIBLE
        }
    }

    private fun setupViewPager() {
        val adapter = InfoPagerAdapter(supportFragmentManager, lifecycle, packageName)
        binding.viewPager.adapter = adapter
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Activity"
                1 -> "Service"
                else -> ""
            }
        }.attach()
    }

    private fun dispatchSearchQuery(query: String) {
        val payload = Bundle().apply { putString(FR_QUERY_KEY, query) }
        // 分开 key，避免“旧 Fragment 先消费导致新 Fragment 收不到”的问题
        supportFragmentManager.setFragmentResult(FR_SEARCH_KEY_ACTIVITY, payload)
        supportFragmentManager.setFragmentResult(FR_SEARCH_KEY_SERVICE, payload)
    }
}