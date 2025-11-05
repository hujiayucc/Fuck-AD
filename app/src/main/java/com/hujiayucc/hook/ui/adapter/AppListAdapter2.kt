package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Item2
import com.hujiayucc.hook.databinding.AppRuleBinding
import java.util.*

class AppListAdapter2(private var appList: List<Item2>) : BaseAdapter(), Filterable {
    private var filteredList: List<Item2> = appList
    private var currentFilterQuery: String? = null
    
    fun updateData(newList: List<Item2>) {
        appList = newList
        if (currentFilterQuery.isNullOrEmpty()) {
            filteredList = newList
            notifyDataSetChanged()
        } else {
            filter.filter(currentFilterQuery)
        }
    }
    
    override fun getCount(): Int = filteredList.size
    override fun getItem(position: Int): Item2 = filteredList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.let {
            AppRuleBinding.bind(it)
        } ?: run {
            val view = LayoutInflater.from(parent?.context)
                .inflate(R.layout.app_rule, parent, false)
            AppRuleBinding.bind(view)
        }

        val rule = getItem(position)
        binding.apply {
            appIcon.setImageDrawable(rule.appIcon)
            appName.text = rule.appName
            appPackage.text = rule.packageName
            action.text = rule.action
            root.setOnClickListener {
                try {
                    parent?.context?.startActivity(
                        parent.context?.packageManager?.getLaunchIntentForPackage(rule.packageName)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return binding.root
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val filterPattern = constraint?.toString()?.lowercase(Locale.getDefault())?.trim() ?: ""

                if (filterPattern.isEmpty()) {
                    results.values = appList
                    results.count = appList.size
                } else {
                    val filtered = appList.filter { item ->
                        item.appName.lowercase(Locale.getDefault()).contains(filterPattern) ||
                        item.packageName.lowercase(Locale.getDefault()).contains(filterPattern)
                    }
                    results.values = filtered
                    results.count = filtered.size
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as? List<Item2> ?: appList
                currentFilterQuery = constraint?.toString()
                notifyDataSetChanged()
            }
        }
    }
}