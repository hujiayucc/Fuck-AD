package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Item2
import com.hujiayucc.hook.databinding.ActivitySdkBinding
import com.hujiayucc.hook.ui.adapter.AppListAdapter2
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

class SDKActivity : BaseActivity<ActivitySdkBinding>() {
    private lateinit var binding: ActivitySdkBinding
    private lateinit var progressBar: ProgressBar
    private lateinit var listView: ListView
    private lateinit var textView: TextView
    private lateinit var searchView: SearchView
    private val disposables = CompositeDisposable()
    private lateinit var adapter: AppListAdapter2
    private val itemList = ArrayList<Item2>()
    private val saveExecutor = Executors.newSingleThreadScheduledExecutor()
    private var pendingSave: ScheduledFuture<*>? = null

    private data class AppEntry(val appInfo: ApplicationInfo, val label: String)
    private data class CacheItem(val appName: String, val packageName: String, val action: String)
    private data class CachedResult(val packages: Set<String>, val items: List<Item2>)

    private val sdkComponentPrefixMap = linkedMapOf(
        "com.bytedance.sdk.openadsdk." to "穿山甲",
        "com.qq.e.ads" to "腾讯广告",
        "com.kwad.sdk." to "快手广告",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySdkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        progressBar = binding.progressBar
        listView = binding.appList
        textView = binding.textView
        searchView = binding.searchView
        adapter = AppListAdapter2(itemList)
        listView.adapter = adapter
        setupSearchView()
        hideListView()
        disposables.add(
            loadCachedItemsAsync().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    if (result.items.isNotEmpty()) {
                        itemList.clear()
                        itemList.addAll(result.items)
                        itemList.sortBy { it.appName.lowercase(Locale.getDefault()) }
                        adapter.updateData(itemList)
                        showListView()
                    }
                    if (result.packages.isNotEmpty()) updateFromDiffAsync(result.packages)
                    else loadApps()
                }, {
                    loadApps()
                })
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
        pendingSave?.cancel(false)
        saveExecutor.shutdownNow()
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
        val maxConcurrency = min(4, max(2, Runtime.getRuntime().availableProcessors() - 1))
        val processedCount = AtomicInteger(0)
        val lastUiUpdateAt = AtomicLong(0L)

        disposables.add(
            Observable.fromCallable { buildAppsList() }.subscribeOn(Schedulers.io())
                .doOnSubscribe { hideListView() }.observeOn(AndroidSchedulers.mainThread())
                .doOnNext { apps -> clearProgress(apps.size) }.observeOn(Schedulers.io())
                .flatMap { apps: List<AppEntry> ->
                    val total = apps.size
                    Observable.fromIterable(apps).concatMapEager({ entry: AppEntry ->
                        Observable.defer {
                            val action = getSDKs(entry.appInfo)
                            val current = processedCount.incrementAndGet()
                            updateProgressThrottled(current, total, lastUiUpdateAt)
                            if (action.isNotEmpty()) {
                                val icon = getAppIcon(entry.appInfo.packageName)
                                Observable.just(
                                    Item2(
                                        appName = entry.label,
                                        packageName = entry.appInfo.packageName,
                                        action = action,
                                        appIcon = icon
                                    )
                                )
                            } else {
                                Observable.empty()
                            }
                        }.subscribeOn(Schedulers.io())
                    }, maxConcurrency, 128)
                }.buffer(32).observeOn(AndroidSchedulers.mainThread()).subscribe({ batch ->
                    if (batch.isNotEmpty()) {
                        itemList.addAll(batch)
                        adapter.updateData(itemList)
                    }
                }, { error ->
                    Toast.makeText(this, "加载应用列表失败: ${error.message}", Toast.LENGTH_SHORT).show()
                    showListView()
                }, {
                    showListView()
                    requestSaveItems()
                })
        )
    }

    private fun clearProgress(total: Int) {
        progressBar.max = total
        progressBar.progress = 0
        itemList.clear()
        adapter.updateData(itemList)
    }

    private fun showListView() {
        progressBar.visibility = View.GONE
        textView.visibility = View.GONE
        listView.visibility = View.VISIBLE
    }

    private fun hideListView() {
        progressBar.visibility = View.VISIBLE
        textView.visibility = View.VISIBLE
        listView.visibility = View.GONE
    }

    fun getSDKs(appInfo: ApplicationInfo): String {
        return try {
            val pkgInfo = getPackageInfoCompat(appInfo.packageName)
            val componentNames = extractComponentClassNames(pkgInfo)

            val hits = LinkedHashSet<String>()
            for ((prefix, name) in sdkComponentPrefixMap) {
                if (componentNames.any { it.startsWith(prefix) }) hits.add(name)
            }
            if (hits.isNotEmpty()) hits.joinToString(prefix = "[", postfix = "]") else ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 读取缓存并优先展示（后台线程）。返回缓存中的包名集合用于后续增量对比。
     */
    private fun loadCachedItemsAsync(): Observable<CachedResult> {
        return Observable.fromCallable {
            val packages = LinkedHashSet<String>()
            val items = ArrayList<Item2>()

            try {
                val jsonStr = MainActivity.prefs.getString("sdkItems", "")
                if (jsonStr.isNotEmpty()) {
                    val currentInstalled = currentInstalledPackages()
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val pkg = obj.optString("packageName")
                        val name = obj.optString("appName")
                        val action = obj.optString("action")
                        if (pkg.isNotEmpty() && currentInstalled.contains(pkg)) {
                            packages.add(pkg)
                            val icon = getAppIcon(pkg)
                            items.add(
                                Item2(
                                    appName = name, packageName = pkg, action = action, appIcon = icon
                                )
                            )
                        }
                    }
                    if (items.isNotEmpty()) {
                        return@fromCallable CachedResult(packages = packages, items = items)
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val listStr = MainActivity.prefs.getString("sdkList", "")
                if (listStr.isNotEmpty()) {
                    val arr = JSONArray(listStr)
                    for (i in 0 until arr.length()) {
                        val pkg = arr.optString(i)
                        if (!pkg.isNullOrEmpty()) packages.add(pkg)
                    }
                }
            } catch (_: Exception) {
            }

            CachedResult(packages = packages, items = emptyList())
        }
    }

    /**
     * 异步对比新增/卸载并做增量更新。
     */
    @SuppressLint("CheckResult")
    private fun updateFromDiffAsync(savedPackages: Set<String>) {
        disposables.add(
            Observable.fromCallable {
                val current = currentInstalledPackages()
                val added = current.minus(savedPackages)
                val removed = savedPackages.minus(current)
                Pair(added, removed)
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).doOnNext { (_, removed) ->
                if (removed.isNotEmpty()) {
                    val iterator = itemList.iterator()
                    var changed = false
                    while (iterator.hasNext()) {
                        val it = iterator.next()
                        if (removed.contains(it.packageName)) {
                            iterator.remove()
                            changed = true
                        }
                    }
                    if (changed) adapter.updateData(itemList)
                }
            }.observeOn(Schedulers.io()).flatMap { (added, _) ->
                if (added.isEmpty()) Observable.just(emptyList())
                else scanSpecificPackages(added)
            }.observeOn(AndroidSchedulers.mainThread()).subscribe({ newItems ->
                if (newItems.isNotEmpty()) {
                    itemList.addAll(newItems)
                    itemList.sortBy { it.appName.lowercase(Locale.getDefault()) }
                    adapter.updateData(itemList)
                }
                requestSaveItems()
            }, { _ ->
            })
        )
    }

    /**
     * 扫描指定包集合，返回命中的 Item2 列表。
     */
    private fun scanSpecificPackages(packages: Set<String>): Observable<List<Item2>> {
        val entries = packages.mapNotNull { pkg ->
            try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                AppEntry(info, info.loadLabel(packageManager).toString())
            } catch (_: Exception) {
                null
            }
        }
        if (entries.isEmpty()) return Observable.just(emptyList())

        val maxConcurrency = min(4, max(2, Runtime.getRuntime().availableProcessors() - 1))
        return Observable.fromIterable(entries).concatMapEager({ entry ->
            Observable.defer {
                val action = getSDKs(entry.appInfo)
                if (action.isNotEmpty()) {
                    val icon = getAppIcon(entry.appInfo.packageName)
                    Observable.just(
                        Item2(
                            appName = entry.label,
                            packageName = entry.appInfo.packageName,
                            action = action,
                            appIcon = icon
                        )
                    )
                } else {
                    Observable.empty()
                }
            }.subscribeOn(Schedulers.io())
        }, maxConcurrency, 64).buffer(16).reduce(ArrayList<Item2>()) { acc, list ->
            acc.apply { addAll(list) }
        }.map { it.toList() }.toObservable()
    }

    private fun currentInstalledPackages(): Set<String> {
        return packageManager.getInstalledApplications(0).asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }.map { it.packageName }.toSet()
    }

    private fun requestSaveItems(delayMs: Long = 600L) {
        pendingSave?.cancel(false)
        val snapshot = itemList.map { CacheItem(it.appName, it.packageName, it.action) }
        pendingSave = saveExecutor.schedule({
            try {
                val pkgArr = JSONArray(snapshot.map { it.packageName })
                val itemArr = JSONArray()
                snapshot.forEach { item ->
                    val obj = JSONObject()
                    obj.put("appName", item.appName)
                    obj.put("packageName", item.packageName)
                    obj.put("action", item.action)
                    itemArr.put(obj)
                }
                MainActivity.prefs.edit {
                    putString("sdkList", pkgArr.toString())
                    putString("sdkItems", itemArr.toString())
                    apply()
                }
            } catch (_: Exception) {
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })
    }

    private fun buildAppsList(): List<AppEntry> {
        return packageManager.getInstalledApplications(0).asSequence()
            .filter { appInfo -> (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { appInfo -> AppEntry(appInfo, appInfo.loadLabel(packageManager).toString()) }
            .sortedBy { entry -> entry.label.lowercase(Locale.getDefault()) }.toList()
    }

    private fun getPackageInfoCompat(packageName: String): PackageInfo {
        val flags = (PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES).toLong()
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
        } else {
            packageManager.getPackageInfo(packageName, flags.toInt())
        }
    }

    private fun extractComponentClassNames(pkgInfo: PackageInfo): Sequence<String> = sequence {
        pkgInfo.activities?.forEach { it.name?.let { n -> yield(n) } }
        pkgInfo.services?.forEach { it.name?.let { n -> yield(n) } }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgressThrottled(current: Int, total: Int, lastUiUpdateAt: AtomicLong) {
        val now = SystemClock.uptimeMillis()
        val prev = lastUiUpdateAt.get()
        val shouldUpdate = current == total || (now - prev) >= 60L
        if (!shouldUpdate) return
        if (current != total && !lastUiUpdateAt.compareAndSet(prev, now)) return
        runOnUiThread {
            progressBar.progress = current
            textView.text = "${progressBar.progress}/${progressBar.max}"
        }
    }
}