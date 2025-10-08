package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Item2
import com.hujiayucc.hook.databinding.ActivitySdkBinding
import com.hujiayucc.hook.ui.adapter.AppListAdapter2
import dalvik.system.PathClassLoader
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Locale

class SDKActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySdkBinding
    private lateinit var progressBar: ProgressBar
    private lateinit var listView: ListView
    private val disposables = CompositeDisposable()
    private lateinit var adapter: AppListAdapter2
    private val itemList = ArrayList<Item2>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySdkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        progressBar = binding.progressBar
        listView = binding.appList
        adapter = AppListAdapter2(itemList)
        listView.adapter = adapter
        loadApps()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getAppIcon(packageName: String): Drawable {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: NameNotFoundException) {
            resources.getDrawable(R.mipmap.ic_default, null)
        }
    }

    @SuppressLint("CheckResult", "SetTextI18n")
    private fun loadApps() {
        val apps = packageManager.getInstalledApplications(0).filter { appInfo ->
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }.map { appInfo ->
            Pair(appInfo.loadLabel(packageManager).toString(), appInfo)
        }.sortedBy { (label, _) ->
            label.lowercase(Locale.getDefault())
        }.map { (_, appInfo) ->
            appInfo
        }

        disposables.add(
            Observable.fromIterable(apps)
                .subscribeOn(Schedulers.io())
                .map { appInfo -> getItem(appInfo) }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    progressBar.max = apps.size
                    progressBar.progress = 0
                    progressBar.visibility = View.VISIBLE
                    binding.textView.visibility = View.VISIBLE
                    listView.visibility = View.GONE
                    itemList.clear()
                }
                .subscribe(
                    { item ->
                        if (item.action.isNotEmpty()) {
                            itemList.add(item)
                            adapter.notifyDataSetChanged()
                        }
                        progressBar.progress += 1
                        binding.textView.text = "${progressBar.progress}/${progressBar.max}"
                    },
                    { error ->
                        Toast.makeText(this, "加载应用列表失败: ${error.message}", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        binding.textView.visibility = View.GONE
                        listView.visibility = View.VISIBLE
                    },
                    {
                        progressBar.visibility = View.GONE
                        binding.textView.visibility = View.GONE
                        listView.visibility = View.VISIBLE
                    }
                )
        )
    }

    private fun getItem(appInfo: ApplicationInfo): Item2 {
        val label = appInfo.loadLabel(packageManager).toString()
        val icon = getAppIcon(appInfo.packageName)
        return Item2(
            appName = label,
            packageName = appInfo.packageName,
            action = getSDKs(appInfo),
            appIcon = icon
        )
    }

    fun getSDKs(appInfo: ApplicationInfo): String {
        val list = ArrayList<String>()
        val adMap = mapOf(
            "com.bytedance.sdk.openadsdk.TTAdSdk" to "穿山甲",
            "com.qq.e.comm.DownloadService" to "腾讯广告",
            "com.kwad.sdk.api.KsAdSDK" to "快手广告",
        )
        return try {
            val classLoader = PathClassLoader(appInfo.sourceDir, classLoader)
            for ((className, name) in adMap) {
                try {
                    classLoader.loadClass(className)
                    list.add(name)
                } catch (_: ClassNotFoundException) {
                    continue
                }
            }
            if (list.isNotEmpty()) list.toArray().contentToString() else ""
        } catch (_: Exception) {
            ""
        }
    }
}