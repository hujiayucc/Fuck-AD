package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import com.hujiayucc.hook.R
import com.hujiayucc.hook.autoskip.AutoSkipSettings
import com.hujiayucc.hook.databinding.ActivityAutoSkipAppListBinding
import com.hujiayucc.hook.ui.adapter.AutoSkipAppAdapter
import com.hujiayucc.hook.utils.LanguageUtils
import io.github.libxposed.service.XposedService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.text.Collator
import java.util.Comparator
import java.util.Locale

class AutoSkipAppListActivity : BaseActivity<ActivityAutoSkipAppListBinding>() {
    private lateinit var binding: ActivityAutoSkipAppListBinding
    private lateinit var adapter: AutoSkipAppAdapter
    private val disposables = CompositeDisposable()
    private var allApps: List<AutoSkipAppEntry> = emptyList()
    private var searchQuery = ""
    private var filterAppsDisposable: Disposable? = null
    private var filterAppsRequestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutoSkipAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        adapter = AutoSkipAppAdapter(
            emptyList(),
            onAppEnabledChanged = { app, enabled -> updateAppEnabled(app, enabled) }
        )
        binding.appList.adapter = adapter
        loadApps()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sdk, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView
        searchView?.queryHint = getString(R.string.sdk_search_hint)
        searchView?.setQuery(searchQuery, false)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty().trim()
                refreshApps()
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty().trim()
                refreshApps()
                return true
            }
        })
        return true
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    override fun onServiceStateChanged(service: XposedService?) {
    }

    private fun loadApps() {
        showLoading()
        disposables.add(
            Observable.fromCallable { buildApps() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ apps ->
                    allApps = apps
                    refreshApps()
                    showList()
                }, { error ->
                    Toast.makeText(this, error.message ?: getString(R.string.data_load_failed, ""), Toast.LENGTH_LONG).show()
                    showList()
                })
        )
    }

    private fun refreshApps() {
        val requestId = ++filterAppsRequestId
        val currentApps = allApps
        val query = searchQuery.trim()
        filterAppsDisposable?.dispose()
        val disposable = Observable.fromCallable {
            currentApps.filterAndSort(query)
        }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ filteredApps ->
                if (requestId != filterAppsRequestId) return@subscribe
                adapter.updateData(filteredApps)
            }, { error ->
                if (requestId != filterAppsRequestId) return@subscribe
                Toast.makeText(this, error.message ?: getString(R.string.data_load_failed, ""), Toast.LENGTH_LONG).show()
            })
        filterAppsDisposable = disposable
        disposables.add(disposable)
    }

    private fun List<AutoSkipAppEntry>.filterAndSort(query: String): List<AutoSkipAppEntry> {
        val filtered = if (query.isEmpty()) {
            this
        } else {
            filter { app -> app.matchesSearchQuery(query) }
        }
        return filtered.sortedWith(createAppComparator())
    }

    private fun updateAppEnabled(app: AutoSkipAppEntry, enabled: Boolean) {
        allApps.firstOrNull { it.packageName == app.packageName }?.enabled = enabled
        refreshApps()
        disposables.add(
            Observable.fromCallable {
                AutoSkipSettings.setAppEnabled(this, app.packageName, enabled)
                true
            }
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, { error ->
                    Toast.makeText(this, error.message ?: getString(R.string.data_load_failed, ""), Toast.LENGTH_LONG).show()
                })
        )
    }

    private fun AutoSkipAppEntry.matchesSearchQuery(query: String): Boolean {
        return name.contains(query, ignoreCase = true) || packageName.contains(query, ignoreCase = true)
    }

    private fun buildApps(): List<AutoSkipAppEntry> {
        val enabledPackages = AutoSkipSettings.enabledPackages(this)
        return packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { appInfo -> (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { appInfo -> appInfo.packageName != packageName }
            .map { appInfo ->
                AutoSkipAppEntry(
                    name = LanguageUtils.localizedAppLabel(this, appInfo),
                    packageName = appInfo.packageName,
                    icon = getAppIcon(appInfo.packageName),
                    enabled = enabledPackages.contains(appInfo.packageName)
                )
            }
            .sortedWith(createAppComparator())
            .toList()
    }

    private fun createAppComparator(): Comparator<AutoSkipAppEntry> {
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        return Comparator { first, second ->
            val enabledCompare = first.enabledSortValue.compareTo(second.enabledSortValue)
            if (enabledCompare != 0) {
                enabledCompare
            } else {
                val nameCompare = collator.compare(first.name, second.name)
                if (nameCompare != 0) nameCompare else first.packageName.compareTo(second.packageName, ignoreCase = true)
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getAppIcon(packageName: String): Drawable {
        return runCatching { packageManager.getApplicationIcon(packageName) }
            .getOrDefault(resources.getDrawable(R.mipmap.ic_default, null))
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.textView.visibility = View.VISIBLE
        binding.appList.visibility = View.GONE
    }

    private fun showList() {
        binding.progressBar.visibility = View.GONE
        binding.textView.visibility = View.GONE
        binding.appList.visibility = View.VISIBLE
    }
}

data class AutoSkipAppEntry(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var enabled: Boolean
) {
    val enabledSortValue: Int
        get() = if (enabled) 0 else 1
}