package com.hujiayucc.hook.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.hujiayucc.hook.R
import com.hujiayucc.hook.autoskip.AutoSkipRule
import com.hujiayucc.hook.autoskip.AutoSkipRuleSource
import com.hujiayucc.hook.autoskip.AutoSkipTapStrategy
import com.hujiayucc.hook.databinding.ItemAutoSkipRuleBinding

class AutoSkipRuleAdapter(
    private var rules: List<AutoSkipRule>,
    private val onRuleEnabledChanged: (AutoSkipRule, Boolean) -> Unit,
    private val onRuleClicked: (AutoSkipRule) -> Unit
) : BaseAdapter() {
    private class ViewHolder(val binding: ItemAutoSkipRuleBinding)

    fun updateData(newRules: List<AutoSkipRule>) {
        rules = newRules
        notifyDataSetChanged()
    }

    override fun getCount(): Int = rules.size

    override fun getItem(position: Int): AutoSkipRule = rules[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder = if (convertView != null) {
            convertView.tag as ViewHolder
        } else {
            val view = LayoutInflater.from(parent?.context).inflate(R.layout.item_auto_skip_rule, parent, false)
            val binding = ItemAutoSkipRuleBinding.bind(view)
            ViewHolder(binding).also { binding.root.tag = it }
        }
        val rule = getItem(position)
        val binding = holder.binding
        val context = binding.root.context
        binding.ruleName.text = rule.name
        binding.ruleSummary.text = buildSummary(rule, context)
        binding.ruleSource.text = buildSource(rule, context)
        binding.ruleSwitch.setOnCheckedChangeListener(null)
        binding.ruleSwitch.isChecked = rule.enabled
        binding.ruleSwitch.setOnCheckedChangeListener { _, isChecked ->
            onRuleEnabledChanged(rule, isChecked)
        }
        binding.root.setOnClickListener { onRuleClicked(rule) }
        return binding.root
    }

    private fun buildSummary(rule: AutoSkipRule, context: android.content.Context): String {
        val matcher = listOf(
            rule.match.text.takeIf { it.isNotEmpty() }?.joinToString(prefix = "${context.getString(R.string.auto_skip_summary_text)}: "),
            rule.match.desc.takeIf { it.isNotEmpty() }?.joinToString(prefix = "${context.getString(R.string.auto_skip_summary_desc)}: "),
            rule.match.resourceId.takeIf { it.isNotEmpty() }?.joinToString(prefix = "${context.getString(R.string.auto_skip_summary_id)}: "),
            rule.match.className.takeIf { it.isNotEmpty() }?.joinToString(prefix = "${context.getString(R.string.auto_skip_summary_class)}: ")
        ).filterNotNull().joinToString(" / ")
        return context.getString(
            R.string.auto_skip_rule_summary,
            rule.packageName,
            rule.activity,
            matcher,
            rule.action.tapStrategy.displayName(context)
        )
    }

    private fun buildSource(rule: AutoSkipRule, context: android.content.Context): String {
        val source = when (rule.source) {
            AutoSkipRuleSource.BUILTIN -> context.getString(R.string.auto_skip_filter_builtin)
            AutoSkipRuleSource.SUBSCRIPTION -> context.getString(R.string.auto_skip_filter_subscription)
            AutoSkipRuleSource.LOCAL -> context.getString(R.string.auto_skip_filter_local)
        }
        return context.getString(R.string.auto_skip_rule_source_summary, source, rule.priority, rule.cooldownMs)
    }

    private fun AutoSkipTapStrategy.displayName(context: android.content.Context): String {
        return when (this) {
            AutoSkipTapStrategy.CENTER -> context.getString(R.string.auto_skip_tap_strategy_center)
            AutoSkipTapStrategy.TOP_RIGHT -> context.getString(R.string.auto_skip_tap_strategy_top_right)
            AutoSkipTapStrategy.BOTTOM_RIGHT -> context.getString(R.string.auto_skip_tap_strategy_bottom_right)
            AutoSkipTapStrategy.CUSTOM_RATIO -> context.getString(R.string.auto_skip_tap_strategy_custom_ratio)
            AutoSkipTapStrategy.PROBE -> context.getString(R.string.auto_skip_tap_strategy_probe)
        }
    }
}