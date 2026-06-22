package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Item
import com.hujiayucc.hook.databinding.AppRuleBinding

class AppListAdapter(private val appList: List<Item>) : BaseAdapter() {
    private class ViewHolder(val binding: AppRuleBinding)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var displayList: List<Item> = appList

    init {
        sortByScope()
    }

    override fun getCount(): Int = displayList.size
    override fun getItem(position: Int): Item = displayList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    private fun sortByScope(scopedPackages: Set<String>? = null) {
        displayList = ScopeAdapterUtils.sortByScope(
            items = appList,
            scopedPackages = scopedPackages,
            packageNameOf = { it.packageName },
            appNameOf = { it.appName }
        )
    }

    private fun refreshSorted(scopedPackages: Set<String>? = null) {
        ScopeAdapterUtils.runOnMain(mainHandler) {
            sortByScope(scopedPackages)
            notifyDataSetChanged()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder = if (convertView != null) {
            convertView.tag as ViewHolder
        } else {
            val view = LayoutInflater.from(parent?.context).inflate(R.layout.app_rule, parent, false)
            val binding = AppRuleBinding.bind(view)
            ViewHolder(binding).also { binding.root.tag = it }
        }
        val binding = holder.binding
        val context = parent?.context ?: binding.root.context

        val rule = getItem(position)
        val version = if (rule.versions.isNotEmpty()) rule.versions.contentToString() else "通用"
        binding.apply {
            appIcon.setImageDrawable(rule.appIcon)
            appName.text = rule.appName
            appPackage.text = rule.packageName
            action.text = "${rule.action} $version"
            ScopeAdapterUtils.bindScopeSwitch(context, switchButton, rule.packageName, ::refreshSorted)
            root.setOnClickListener {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(rule.packageName)
                if (launchIntent == null) {
                    Toast.makeText(context, "Open ${rule.appName} failed.", Toast.LENGTH_SHORT).show()
                } else {
                    context.startActivity(launchIntent)
                }
            }
        }

        return binding.root
    }
}