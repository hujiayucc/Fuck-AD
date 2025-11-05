package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.view.View
import com.google.android.material.tabs.TabLayoutMediator
import com.hujiayucc.hook.databinding.ActivityAppInfoBinding
import com.hujiayucc.hook.ui.adapter.InfoPagerAdapter

class AppInfoActivity : BaseActivity() {
    private lateinit var binding: ActivityAppInfoBinding
    private var packageName: String = ""

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

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Activity"
                1 -> "Service"
                else -> ""
            }
        }.attach()
    }
}

