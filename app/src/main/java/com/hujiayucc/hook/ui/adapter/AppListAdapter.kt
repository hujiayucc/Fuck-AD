package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Action.Companion.toAction
import com.hujiayucc.hook.data.Clicker
import com.hujiayucc.hook.data.Rule
import com.hujiayucc.hook.data.Type
import com.hujiayucc.hook.databinding.AppRuleBinding
import com.hujiayucc.hook.utils.AppInfoUtil.appIcon
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers

class AppListAdapter(private val rules: List<Rule>) : BaseAdapter() {
    private val disposables = CompositeDisposable()

    override fun getCount(): Int = rules.size
    override fun getItem(position: Int): Rule = rules[position]
    override fun getItemId(position: Int): Long = position.toLong()

    @SuppressLint("CheckResult", "ViewHolder", "UseCompatLoadingForDrawables")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.let {
            AppRuleBinding.bind(it)
        } ?: run {
            val view = LayoutInflater.from(parent?.context)
                .inflate(R.layout.app_rule, parent, false)
            AppRuleBinding.bind(view)
        }

        val rule = getItem(position)

        disposables.add(
            Observable.fromCallable { parent?.context?.appIcon(rule.packageName) as Drawable }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { icon -> binding.appIcon.setImageDrawable(icon) },
                    { _ -> binding.appIcon.setImageResource(R.drawable.ic_default_app) }
                )
        )

        with(binding) {
            appName.text = rule.name
            appPackage.text = rule.packageName
            root.setOnClickListener {
                parent?.context?.showDetailDialog(rule)
            }
        }

        return binding.root
    }

    private fun Context.showDetailDialog(rule: Rule) {
        MaterialAlertDialogBuilder(this)
            .setTitle("规则列表")
            .setCancelable(false)
            .setMessage(buildString {
                append("▌基础信息\n\n")
                append("应用名称：${rule.name}\n")
                append("应用包名：${rule.packageName}\n\n")
                append("▌ 共 ${rule.items.size} 个行为\n")
                rule.items.forEachIndexed { index, item ->
                    append("\n行为 ${index + 1}\n")
                    append(
                        "  页面：${rule.packageName}/${
                            item.action.toAction<String>().replace(rule.packageName, "")
                        }\n"
                    )
                    append("  名称：${item.name}\n")
                    append("  描述：${item.desc}\n")
                    if (item.type == Type.CLICK)
                        append("  关键词：${item.action.toAction<Clicker>().view}\n")
                    append("  适用版本：${item.versionName} (${item.versionCode})\n")
                    append("  类型：${item.type}\n")
                    append("  延迟：${item.action.toAction<Int>()}ms\n")
                }
            }).setPositiveButton("关闭") { dialog, _ ->
                dialog.dismiss()
            }.setNeutralButton("原始数据") { dialog, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("原始数据")
                    .setCancelable(false)
                    .setMessage(rule.toString())
                    .setPositiveButton("关闭", null)
                    .show()
            }.show()
    }
}