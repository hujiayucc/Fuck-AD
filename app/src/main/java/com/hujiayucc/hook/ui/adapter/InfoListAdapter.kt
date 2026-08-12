package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import com.hujiayucc.hook.databinding.ItemInfoBinding
import java.util.Locale

class InfoListAdapter(private var componentList: List<ComponentItem>) : BaseAdapter(), Filterable {
    private data class SearchableComponent(
        val item: ComponentItem,
        val normalizedName: String
    )

    private var searchableList: List<SearchableComponent> = componentList.toSearchableComponents()
    private var filteredList: List<ComponentItem> = componentList
    private var currentFilterQuery: String = ""
    private val componentFilter = ComponentFilter()

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
        searchableList = newList.toSearchableComponents()
        updateFilteredList(filterComponents(currentFilterQuery))
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

    override fun getFilter(): Filter = componentFilter

    private fun List<ComponentItem>.toSearchableComponents(): List<SearchableComponent> {
        return map { item -> SearchableComponent(item, item.name.normalizeFilterText()) }
    }

    private fun filterComponents(query: String): List<ComponentItem> {
        val filterPattern = query.normalizeFilterText()
        if (filterPattern.isEmpty()) return componentList

        return searchableList.asSequence()
            .filter { searchable -> searchable.normalizedName.contains(filterPattern) }
            .map { searchable -> searchable.item }
            .toList()
    }

    private fun updateFilteredList(newFilteredList: List<ComponentItem>) {
        if (filteredList == newFilteredList) return
        filteredList = newFilteredList
        notifyDataSetChanged()
    }

    private fun String.normalizeFilterText(): String = lowercase(Locale.ROOT).trim()

    private inner class ComponentFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filtered = filterComponents(constraint?.toString().orEmpty())
            return FilterResults().apply {
                values = filtered
                count = filtered.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            currentFilterQuery = constraint?.toString().orEmpty()
            val newFilteredList = results?.values as? List<ComponentItem> ?: filterComponents(currentFilterQuery)
            updateFilteredList(newFilteredList)
        }
    }
}
