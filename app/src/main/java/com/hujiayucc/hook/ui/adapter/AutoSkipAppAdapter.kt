package com.hujiayucc.hook.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.hujiayucc.hook.R
import com.hujiayucc.hook.databinding.ItemAutoSkipAppBinding
import com.hujiayucc.hook.ui.activity.AutoSkipAppEntry

class AutoSkipAppAdapter(
    private var apps: List<AutoSkipAppEntry>,
    private val onAppEnabledChanged: (AutoSkipAppEntry, Boolean) -> Unit
) : BaseAdapter() {
    private class ViewHolder(val binding: ItemAutoSkipAppBinding)

    fun updateData(newApps: List<AutoSkipAppEntry>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): AutoSkipAppEntry = apps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder = if (convertView != null) {
            convertView.tag as ViewHolder
        } else {
            val view = LayoutInflater.from(parent?.context).inflate(R.layout.item_auto_skip_app, parent, false)
            val binding = ItemAutoSkipAppBinding.bind(view)
            ViewHolder(binding).also { binding.root.tag = it }
        }
        val app = getItem(position)
        holder.binding.apply {
            appIcon.setImageDrawable(app.icon)
            appName.text = app.name
            appPackage.text = app.packageName
            appSwitch.setOnCheckedChangeListener(null)
            appSwitch.isChecked = app.enabled
            appSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (app.enabled != isChecked) {
                    app.enabled = isChecked
                    onAppEnabledChanged(app, isChecked)
                }
            }
            root.setOnClickListener { appSwitch.isChecked = !appSwitch.isChecked }
        }
        return holder.binding.root
    }
}