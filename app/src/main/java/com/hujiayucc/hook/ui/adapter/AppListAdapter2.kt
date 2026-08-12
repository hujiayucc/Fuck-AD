package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.CheckBox
import android.widget.LinearLayout
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data.prefsBridge
import com.hujiayucc.hook.data.Item2
import com.hujiayucc.hook.data.SdkHookerConfig
import com.hujiayucc.hook.databinding.AppRuleBinding
import com.hujiayucc.hook.ui.activity.AppInfoActivity
import com.hujiayucc.hook.ui.activity.BaseActivity
import java.util.*

class AppListAdapter2(private var appList: List<Item2>) : BaseAdapter(), Filterable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val expandedPackages = mutableSetOf<String>()
    private var displayList: List<Item2> = appList
    private var filteredList: List<Item2> = appList
    private var currentFilterQuery: String = ""

    init {
        sortByScope()
        applyFilter(currentFilterQuery)
    }

    fun updateData(newList: List<Item2>) {
        appList = newList
        refreshSorted()
    }

    fun updateSortedData(newList: List<Item2>) {
        appList = newList
        displayList = newList
        applyFilter(currentFilterQuery)
        notifyDataSetChanged()
    }

    fun refreshScopeState() {
        refreshSorted()
    }

    override fun getCount(): Int = filteredList.size
    override fun getItem(position: Int): Item2 = filteredList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    private fun sortByScope(scopedPackages: Set<String>? = null) {
        displayList = ScopeAdapterUtils.sortByScope(
            items = appList,
            scopedPackages = scopedPackages,
            packageNameOf = { it.packageName },
            appNameOf = { it.appName }
        )
    }

    private fun applyFilter(query: String) {
        val filterPattern = query.lowercase(Locale.getDefault()).trim()
        filteredList = if (filterPattern.isEmpty()) {
            displayList
        } else {
            displayList.filter { item ->
                item.appName.lowercase(Locale.getDefault()).contains(filterPattern) ||
                        item.packageName.lowercase(Locale.getDefault()).contains(filterPattern)
            }
        }
    }

    private fun refreshSorted(scopedPackages: Set<String>? = null) {
        ScopeAdapterUtils.runOnMain(mainHandler) {
            sortByScope(scopedPackages)
            applyFilter(currentFilterQuery)
            notifyDataSetChanged()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.let {
            AppRuleBinding.bind(it)
        } ?: run {
            val view = LayoutInflater.from(parent?.context)
                .inflate(R.layout.app_rule, parent, false)
            AppRuleBinding.bind(view)
        }
        val context = parent?.context ?: binding.root.context

        val rule = getItem(position)
        binding.apply {
            appIcon.setImageDrawable(rule.appIcon)
            appName.text = rule.appName
            appPackage.text = rule.packageName
            action.text = rule.action
            ScopeAdapterUtils.bindScopeSwitch(context, switchButton, rule.packageName, ::refreshSorted)
            bindSdkSwitches(context, sdkSwitchContainer, rule)
            infoButton.setOnClickListener {
                openAppInfo(context, rule)
            }
            root.setOnClickListener {
                toggleExpanded(rule.packageName)
            }
        }

        return binding.root
    }

    private fun openAppInfo(context: android.content.Context, rule: Item2) {
        (context as? BaseActivity<*>)?.preparePreviousPagePreview(AppInfoActivity::class.java)
        val intent = Intent(context, AppInfoActivity::class.java)
        intent.putExtra("packageName", rule.packageName)
        intent.putExtra("appName", rule.appName)
        context.startActivity(intent)
    }

    private fun bindSdkSwitches(context: android.content.Context, container: LinearLayout, rule: Item2) {
        container.removeAllViews()
        container.visibility = if (expandedPackages.contains(rule.packageName)) View.VISIBLE else View.GONE
        if (container.visibility != View.VISIBLE) return

        rule.sdkIds.forEach { sdkId ->
            val sdkName = SdkHookerConfig.sdkNames[sdkId] ?: sdkId
            val checkBox = CheckBox(context).apply {
                text = sdkName
                isChecked = SdkHookerConfig.isEnabled(context.prefsBridge, rule.packageName, sdkId)
                setOnClickListener { view ->
                    val enabled = (view as CheckBox).isChecked
                    SdkHookerConfig.setEnabled(context.prefsBridge, rule.packageName, sdkId, enabled)
                }
            }
            container.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun toggleExpanded(packageName: String) {
        if (!expandedPackages.add(packageName)) {
            expandedPackages.remove(packageName)
        }
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val query = constraint?.toString() ?: ""
                val base = displayList
                val filterPattern = query.lowercase(Locale.getDefault()).trim()
                val filtered = if (filterPattern.isEmpty()) {
                    base
                } else {
                    base.filter { item ->
                        item.appName.lowercase(Locale.getDefault()).contains(filterPattern) ||
                                item.packageName.lowercase(Locale.getDefault()).contains(filterPattern)
                    }
                }
                results.values = filtered
                results.count = filtered.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as? List<Item2> ?: displayList
                currentFilterQuery = constraint?.toString() ?: ""
                notifyDataSetChanged()
            }
        }
    }
}