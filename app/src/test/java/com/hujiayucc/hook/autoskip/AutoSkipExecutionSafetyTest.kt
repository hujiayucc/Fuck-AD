package com.hujiayucc.hook.autoskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipExecutionSafetyTest {
    private val packageName = "com.example.video"
    private val rule = AutoSkipRule(
        id = "skip-ad",
        name = "Skip ad",
        enabled = true,
        packageName = packageName,
        activity = "*",
        priority = 100,
        cooldownMs = 3_000L,
        delayMs = 1_000L,
        match = AutoSkipMatch(text = listOf("Skip")),
        action = AutoSkipAction(),
        source = AutoSkipRuleSource.LOCAL
    )

    @Test
    fun delayedRuleIsRejectedWhenAutoSkipWasDisabled() {
        val currentConfig = config(enabled = false, generation = 7L)

        assertFalse(
            isRuleExecutionCurrent(
                expectedGeneration = 7L,
                currentConfig = currentConfig,
                packageName = packageName,
                rule = rule,
                executableRules = listOf(rule)
            )
        )
    }

    @Test
    fun delayedRuleIsRejectedWhenRuleGenerationChanged() {
        val currentConfig = config(enabled = true, generation = 8L)

        assertFalse(
            isRuleExecutionCurrent(
                expectedGeneration = 7L,
                currentConfig = currentConfig,
                packageName = packageName,
                rule = rule,
                executableRules = listOf(rule)
            )
        )
    }

    @Test
    fun delayedRuleIsRejectedWhenRuleIsNoLongerExecutable() {
        val currentConfig = config(enabled = true, generation = 7L)

        assertFalse(
            isRuleExecutionCurrent(
                expectedGeneration = 7L,
                currentConfig = currentConfig,
                packageName = packageName,
                rule = rule,
                executableRules = emptyList()
            )
        )
    }

    @Test
    fun unchangedDelayedRuleRemainsExecutable() {
        val currentConfig = config(enabled = true, generation = 7L)

        assertTrue(
            isRuleExecutionCurrent(
                expectedGeneration = 7L,
                currentConfig = currentConfig,
                packageName = packageName,
                rule = rule,
                executableRules = listOf(rule)
            )
        )
    }

    @Test
    fun invalidAttemptGuardStopsFallbackChain() {
        val executed = mutableListOf<String>()
        var guardChecks = 0

        runAutoSkipAttemptFlow(
            attempts = listOf("first", "second", "third"),
            startIndex = 0,
            isEnabled = { true },
            shouldContinue = {
                guardChecks += 1
                guardChecks == 1
            },
            execute = { attempt ->
                executed += attempt
                false
            },
            verifier = null,
            deferVerification = false
        )

        assertTrue(executed == listOf("first"))
    }

    @Test
    fun clickBudgetSkipsAttemptsThatCannotFinishWithinDeadline() {
        val now = 1_000L
        val deadline = 2_500L

        assertFalse(hasAutoSkipAttemptBudget(now, deadline, AutoSkipClickExecutorType.ROOT_INPUT))
        assertTrue(hasAutoSkipAttemptBudget(now, deadline, AutoSkipClickExecutorType.ACCESSIBILITY_GESTURE))
        assertTrue(hasAutoSkipAttemptBudget(now, deadline, AutoSkipClickExecutorType.ACCESSIBILITY_ACTION))
    }

    private fun config(enabled: Boolean, generation: Long): AutoSkipRuntimeConfig {
        return AutoSkipRuntimeConfig(
            enabled = enabled,
            appMode = AutoSkipAppMode.WHITELIST,
            appPackages = setOf(packageName),
            useShizukuInput = true,
            useRootInput = true,
            ruleDataGeneration = generation
        )
    }
}
