package com.hujiayucc.hook.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hujiayucc.hook.R
import com.hujiayucc.hook.autoskip.AutoSkipRule
import com.hujiayucc.hook.autoskip.AutoSkipRuleSource
import com.hujiayucc.hook.autoskip.AutoSkipTapStrategy
import com.hujiayucc.hook.databinding.ItemAutoSkipRuleBinding

class AutoSkipRuleAdapter(
    rules: List<AutoSkipRule>,
    private val onRuleEnabledChanged: (AutoSkipRule, Boolean) -> Unit,
    private val onRuleClicked: (AutoSkipRule) -> Unit
) : ListAdapter<AutoSkipRule, AutoSkipRuleAdapter.RuleViewHolder>(DiffCallback) {
    init {
        submitList(rules)
    }

    fun updateData(newRules: List<AutoSkipRule>) {
        submitList(newRules)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemAutoSkipRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RuleViewHolder(private val binding: ItemAutoSkipRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: AutoSkipRule) {
            val context = binding.root.context
            binding.ruleName.text = rule.name
            binding.ruleSummary.text = buildSummary(rule, context)
            binding.ruleSource.text = buildSource(rule, context)
            binding.ruleSwitch.setOnCheckedChangeListener(null)
            binding.ruleSwitch.isChecked = rule.enabled
            binding.ruleSwitch.setOnCheckedChangeListener { _, isChecked ->
                val currentRule = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { getItem(it) } ?: rule
                onRuleEnabledChanged(currentRule, isChecked)
            }
            binding.root.setOnClickListener {
                val currentRule = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { getItem(it) } ?: rule
                onRuleClicked(currentRule)
            }
        }
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

    private object DiffCallback : DiffUtil.ItemCallback<AutoSkipRule>() {
        override fun areItemsTheSame(oldItem: AutoSkipRule, newItem: AutoSkipRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AutoSkipRule, newItem: AutoSkipRule): Boolean {
            return oldItem == newItem
        }
    }
}