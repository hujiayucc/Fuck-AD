package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import com.hujiayucc.hook.databinding.ItemInfoBinding
import java.util.*

class InfoListAdapter(private var componentList: List<ComponentItem>) : BaseAdapter(), Filterable {
    private var filteredList: List<ComponentItem> = componentList
    private var currentFilterQuery: String? = null

    data class ComponentItem(
        val name: String,
        val type: ComponentType,
        val exported: Boolean
    )

    enum class ComponentType : java.io.Serializable {
        ACTIVITY,
        SERVICE
    }

    fun updateData(newList: List<ComponentItem>) {
        componentList = newList
        if (currentFilterQuery.isNullOrEmpty()) {
            filteredList = newList
            notifyDataSetChanged()
        } else {
            filter.filter(currentFilterQuery)
        }
    }

    override fun getCount(): Int = filteredList.size
    override fun getItem(position: Int): ComponentItem = filteredList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.let {
            ItemInfoBinding.bind(it)
        } ?: run {
            val view = LayoutInflater.from(parent?.context)
                .inflate(com.hujiayucc.hook.R.layout.item_info, parent, false)
            ItemInfoBinding.bind(view)
        }

        val item = getItem(position)
        binding.apply {
            componentName.text = item.name
            exportedStatus.text = if (item.exported) "导出" else "未导出"
        }

        return binding.root
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val filterPattern = constraint?.toString()?.lowercase(Locale.getDefault())?.trim() ?: ""

                if (filterPattern.isEmpty()) {
                    results.values = componentList
                    results.count = componentList.size
                } else {
                    val filtered = componentList.filter { item ->
                        item.name.lowercase(Locale.getDefault()).contains(filterPattern)
                    }
                    results.values = filtered
                    results.count = filtered.size
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as? List<ComponentItem> ?: componentList
                currentFilterQuery = constraint?.toString()
                notifyDataSetChanged()
            }
        }
    }
}

