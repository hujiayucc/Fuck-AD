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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.edit
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data.prefsBridge
import com.hujiayucc.hook.data.Item2
import com.hujiayucc.hook.data.SdkHookerConfig
import com.hujiayucc.hook.databinding.ActivitySdkBinding
import com.hujiayucc.hook.ui.adapter.AppListAdapter2
import com.hujiayucc.hook.utils.LanguageUtils
import io.github.libxposed.service.XposedService
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

class SDKActivity : BaseActivity<ActivitySdkBinding>() {
    private lateinit var binding: ActivitySdkBinding
    private lateinit var progressBar: ProgressBar
    private lateinit var listView: ListView
    private lateinit var textView: TextView
    private val disposables = CompositeDisposable()
    private lateinit var adapter: AppListAdapter2
    private val itemList = ArrayList<Item2>()
    private val saveExecutor = Executors.newSingleThreadScheduledExecutor()
    private var pendingSave: ScheduledFuture<*>? = null
    private var pendingSaveSnapshot: PendingSave? = null
    private var searchMenuItem: MenuItem? = null

    private data class AppEntry(val appInfo: ApplicationInfo, val label: String)
    private data class CacheItem(
        val appName: String,
        val packageName: String,
        val action: String,
        val sdkIds: List<String>
    )
    private data class PendingSave(val items: List<CacheItem>, val scannedPackages: Set<String>?)
    private data class CachedResult(
        val scannedPackages: Set<String>,
        val installedPackages: Set<String>,
        val items: List<Item2>,
        val hasItemCache: Boolean,
        val hasScanCache: Boolean,
        val isSignatureCurrent: Boolean
    )
    private data class PackageDiff(val addedPackages: Set<String>, val removedPackages: Set<String>) {
        val hasChanges: Boolean get() = addedPackages.isNotEmpty() || removedPackages.isNotEmpty()
    }

    private val sdkComponentPrefixMap = SdkHookerConfig.sdkComponentPrefixes
        .flatMap { (sdkId, prefixes) -> prefixes.map { prefix -> prefix to sdkId } }
        .toMap(LinkedHashMap())
    private val sdkScanSignature = SdkHookerConfig.sdkComponentPrefixes
        .entries.joinToString("|") { (sdkId, prefixes) -> "$sdkId:${prefixes.joinToString(",")}" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySdkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        progressBar = binding.progressBar
        listView = binding.appList
        textView = binding.textView
        adapter = AppListAdapter2(itemList)

        listView.adapter = adapter
        hideListView()
        loadSdkItems()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sdk, menu)
        val item = menu.findItem(R.id.action_search)
        searchMenuItem = item
        val sv = item.actionView as? SearchView
        sv?.queryHint = getString(R.string.sdk_search_hint)
        sv?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filterByQuery(newText)
                return true
            }
        })

        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                setCustomBackEnabled(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                adapter.filterByQuery("")
                setCustomBackEnabled(false)
                return true
            }
        })
        return true
    }

    override fun onBackAction(): Boolean {
        return if (searchMenuItem?.isActionViewExpanded == true) {
            searchMenuItem?.collapseActionView()
            false
        } else {
            super.onBackAction()
        }
    }

    override fun onDestroy() {
        disposables.clear()
        flushPendingSave()
        shutdownSaveExecutor()
        super.onDestroy()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getAppIcon(packageName: String): Drawable {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: NameNotFoundException) {
            resources.getDrawable(R.mipmap.ic_default, null)
        }
    }

    private fun loadSdkItems() {
        disposables.add(
            loadCachedItemsAsync().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    if (result.hasItemCache && result.isSignatureCurrent) showCachedItems(result.items)
                    if (result.hasItemCache && result.hasScanCache && result.isSignatureCurrent) updateFromDiffAsync(result)
                    else loadApps()
                }, {
                    loadApps()
                })
        )
    }

    private fun showCachedItems(items: List<Item2>) {
        itemList.clear()
        itemList.addAll(items)
        itemList.sortBy { it.appName.lowercase(Locale.getDefault()) }
        adapter.updateData(itemList)
        showListView()
    }

    @SuppressLint("CheckResult", "SetTextI18n")
    private fun loadApps() {
        val maxConcurrency = min(4, max(2, Runtime.getRuntime().availableProcessors() - 1))
        val processedCount = AtomicInteger(0)
        val lastUiUpdateAt = AtomicLong(0L)
        val scannedPackages = AtomicReference<Set<String>>(emptySet())

        disposables.add(
            Observable.fromCallable { buildAppsList() }.subscribeOn(Schedulers.io())
                .doOnSubscribe { hideListView() }.observeOn(AndroidSchedulers.mainThread())
                .doOnNext { apps ->
                    scannedPackages.set(apps.map { it.appInfo.packageName }.toSet())
                    clearProgress(apps.size)
                }.observeOn(Schedulers.io())
                .flatMap { apps: List<AppEntry> ->
                    val total = apps.size
                    Observable.fromIterable(apps).concatMapEager({ entry: AppEntry ->
                        Observable.defer {
                            val sdkIds = getSDKIds(entry.appInfo)
                            val current = processedCount.incrementAndGet()
                            updateProgressThrottled(current, total, lastUiUpdateAt)
                            if (sdkIds.isNotEmpty()) {
                                val icon = getAppIcon(entry.appInfo.packageName)
                                Observable.just(
                                    Item2(
                                        appName = entry.label,
                                        packageName = entry.appInfo.packageName,
                                        action = SdkHookerConfig.actionText(sdkIds),
                                        sdkIds = sdkIds,
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
                    requestSaveItems(scannedPackages.get())
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
        return SdkHookerConfig.actionText(getSDKIds(appInfo))
    }

    private fun getSDKIds(appInfo: ApplicationInfo): List<String> {
        return try {
            val pkgInfo = getPackageInfoCompat(appInfo.packageName)
            val componentNames = extractComponentClassNames(pkgInfo).toList()

            val hits = LinkedHashSet<String>()
            for ((prefix, sdkId) in sdkComponentPrefixMap) {
                if (componentNames.any { it.startsWith(prefix) }) hits.add(sdkId)
            }
            hits.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadCachedItemsAsync(): Observable<CachedResult> {
        return Observable.fromCallable {
            val currentInstalled = currentInstalledPackages()
            val (hasScanCache, scannedPackages) = readPackageSet(PREF_SDK_SCANNED_PACKAGES)
            val isSignatureCurrent = prefsBridge.getString(PREF_SDK_SCAN_SIGNATURE, "") == sdkScanSignature &&
                prefsBridge.getString(PREF_SDK_LABEL_LANGUAGE, "") == LanguageUtils.appLanguageSignature()
            val items = ArrayList<Item2>()
            var hasItemCache = false

            try {
                val jsonStr = prefsBridge.getString(PREF_SDK_ITEMS, "")
                if (jsonStr?.isNotEmpty() == true) {
                    hasItemCache = true
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val pkg = obj.optString("packageName")
                        val name = obj.optString("appName")
                        val action = obj.optString("action")
                        val sdkIds = readSdkIds(obj, action)
                        if (pkg.isNotEmpty() && currentInstalled.contains(pkg)) {
                            val icon = getAppIcon(pkg)
                            items.add(
                                Item2(
                                    appName = name,
                                    packageName = pkg,
                                    action = action,
                                    sdkIds = sdkIds,
                                    appIcon = icon
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                hasItemCache = false
                items.clear()
            }

            CachedResult(
                scannedPackages = scannedPackages,
                installedPackages = currentInstalled,
                items = items,
                hasItemCache = hasItemCache,
                hasScanCache = hasScanCache,
                isSignatureCurrent = isSignatureCurrent
            )
        }
    }

    private fun readSdkIds(obj: JSONObject, action: String): List<String> {
        val sdkIds = obj.optJSONArray("sdkIds") ?: return SdkHookerConfig.idsFromAction(action)
        return (0 until sdkIds.length()).mapNotNull { index ->
            sdkIds.optString(index).takeIf { it.isNotEmpty() }
        }
    }

    @SuppressLint("CheckResult")
    private fun updateFromDiffAsync(result: CachedResult) {
        disposables.add(
            Observable.fromCallable {
                PackageDiff(
                    addedPackages = result.installedPackages.minus(result.scannedPackages),
                    removedPackages = result.scannedPackages.minus(result.installedPackages)
                )
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).doOnNext { diff ->
                if (diff.removedPackages.isNotEmpty()) removeCachedItems(diff.removedPackages)
            }.observeOn(Schedulers.io()).flatMap { diff ->
                val scan = if (diff.addedPackages.isEmpty()) {
                    Observable.just(emptyList())
                } else {
                    scanSpecificPackages(diff.addedPackages)
                }
                scan.map { newItems -> diff to newItems }
            }.observeOn(AndroidSchedulers.mainThread()).subscribe({ (diff, newItems) ->
                if (newItems.isNotEmpty()) {
                    itemList.addAll(newItems)
                    itemList.sortBy { it.appName.lowercase(Locale.getDefault()) }
                    adapter.updateData(itemList)
                }
                if (diff.hasChanges) requestSaveItems(result.installedPackages)
                showListView()
            }, {
                showListView()
            })
        )
    }

    private fun removeCachedItems(packages: Set<String>) {
        val iterator = itemList.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (packages.contains(item.packageName)) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) adapter.updateData(itemList)
    }

    private fun scanSpecificPackages(packages: Set<String>): Observable<List<Item2>> {
        val entries = packages.mapNotNull { pkg ->
            try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                AppEntry(info, LanguageUtils.localizedAppLabel(this, info))
            } catch (_: Exception) {
                null
            }
        }
        if (entries.isEmpty()) return Observable.just(emptyList())

        val maxConcurrency = min(4, max(2, Runtime.getRuntime().availableProcessors() - 1))
        return Observable.fromIterable(entries).concatMapEager({ entry ->
            Observable.defer {
                val sdkIds = getSDKIds(entry.appInfo)
                if (sdkIds.isNotEmpty()) {
                    val icon = getAppIcon(entry.appInfo.packageName)
                    Observable.just(
                        Item2(
                            appName = entry.label,
                            packageName = entry.appInfo.packageName,
                            action = SdkHookerConfig.actionText(sdkIds),
                            sdkIds = sdkIds,
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

    private fun requestSaveItems(scannedPackages: Set<String>? = null, delayMs: Long = 600L) {
        pendingSave?.cancel(false)
        val pending = PendingSave(
            items = itemList.map { CacheItem(it.appName, it.packageName, it.action, it.sdkIds) },
            scannedPackages = scannedPackages
        )
        pendingSaveSnapshot = pending
        pendingSave = saveExecutor.schedule({
            try {
                val packageSnapshot = pending.scannedPackages ?: currentInstalledPackages()
                saveItemsSnapshot(pending.items, packageSnapshot)
                if (pendingSaveSnapshot === pending) pendingSaveSnapshot = null
            } catch (_: Exception) {
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun flushPendingSave() {
        val pending = pendingSaveSnapshot ?: return
        pendingSave?.cancel(false)
        val packageSnapshot = pending.scannedPackages ?: currentInstalledPackages()
        saveItemsSnapshot(pending.items, packageSnapshot, commit = true)
        pendingSaveSnapshot = null
        pendingSave = null
    }

    private fun shutdownSaveExecutor() {
        pendingSave = null
        saveExecutor.shutdown()
    }

    private fun saveItemsSnapshot(items: List<CacheItem>, scannedPackages: Set<String>, commit: Boolean = false) {
        val pkgArr = JSONArray(items.map { it.packageName })
        val scannedPkgArr = JSONArray(scannedPackages.sorted())
        val itemArr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("appName", item.appName)
            obj.put("packageName", item.packageName)
            obj.put("action", item.action)
            obj.put("sdkIds", JSONArray(item.sdkIds))
            itemArr.put(obj)
        }
        prefsBridge.edit(commit = commit) {
            putString(PREF_SDK_LIST, pkgArr.toString())
            putString(PREF_SDK_ITEMS, itemArr.toString())
            putString(PREF_SDK_SCANNED_PACKAGES, scannedPkgArr.toString())
            putString(PREF_SDK_SCAN_SIGNATURE, sdkScanSignature)
            putString(PREF_SDK_LABEL_LANGUAGE, LanguageUtils.appLanguageSignature())
            putLong(PREF_SDK_CACHE_UPDATED_AT, System.currentTimeMillis())
        }
    }

    private fun readPackageSet(key: String): Pair<Boolean, Set<String>> {
        val value = prefsBridge.getString(key, "")
        if (value.isNullOrEmpty()) return false to emptySet()
        return try {
            val packages = LinkedHashSet<String>()
            val arr = JSONArray(value)
            for (i in 0 until arr.length()) {
                val pkg = arr.optString(i)
                if (!pkg.isNullOrEmpty()) packages.add(pkg)
            }
            true to packages
        } catch (_: Exception) {
            false to emptySet()
        }
    }

    private fun buildAppsList(): List<AppEntry> {
        return packageManager.getInstalledApplications(0).asSequence()
            .filter { appInfo -> (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { appInfo -> AppEntry(appInfo, LanguageUtils.localizedAppLabel(this, appInfo)) }
            .sortedBy { entry -> entry.label.lowercase(Locale.getDefault()) }.toList()
    }

    private fun getPackageInfoCompat(packageName: String): PackageInfo {
        val flags = (PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES).toLong()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
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

    override fun onServiceStateChanged(service: XposedService?) {
        super.onServiceStateChanged(service)
        adapter.refreshScopeState()
    }

    companion object {
        private const val PREF_SDK_LIST = "sdkList"
        private const val PREF_SDK_ITEMS = "sdkItems"
        private const val PREF_SDK_SCANNED_PACKAGES = "sdkScannedPackages"
        private const val PREF_SDK_SCAN_SIGNATURE = "sdkScanSignature"
        private const val PREF_SDK_LABEL_LANGUAGE = "sdkLabelLanguage"
        private const val PREF_SDK_CACHE_UPDATED_AT = "sdkCacheUpdatedAt"
    }
}
