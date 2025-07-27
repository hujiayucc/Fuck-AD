package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Item
import com.hujiayucc.hook.databinding.AppRuleBinding

class AppListAdapter(private val appList: List<Item>) : BaseAdapter() {
    override fun getCount(): Int = appList.size
    override fun getItem(position: Int): Item = appList[position]
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
        val version = if (rule.versions.isNotEmpty()) rule.versions.contentToString() else "通用"
        binding.apply {
            appIcon.setImageDrawable(rule.appIcon)
            appName.text = rule.appName
            appPackage.text = rule.packageName
            action.text = "${rule.action} $version"
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
}