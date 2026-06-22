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
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Item2
import com.hujiayucc.hook.databinding.AppRuleBinding
import com.hujiayucc.hook.ui.activity.AppInfoActivity
import java.util.*

class AppListAdapter2(private var appList: List<Item2>) : BaseAdapter(), Filterable {
    private val mainHandler = Handler(Looper.getMainLooper())
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
            root.setOnClickListener {
                val intent = Intent(context, AppInfoActivity::class.java)
                intent.putExtra("packageName", rule.packageName)
                intent.putExtra("appName", rule.appName)
                context.startActivity(intent)
            }
        }

        return binding.root
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