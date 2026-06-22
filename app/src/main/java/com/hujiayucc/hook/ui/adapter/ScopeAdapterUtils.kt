package com.hujiayucc.hook.ui.adapter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import com.hujiayucc.hook.application.XYApplication
import io.github.libxposed.service.XposedService
import java.text.Collator
import java.util.Locale

object ScopeAdapterUtils {
    private val appNameCollator = Collator.getInstance(Locale.CHINA)

    fun <T> sortByScope(
        items: List<T>,
        scopedPackages: Set<String>? = null,
        packageNameOf: (T) -> String,
        appNameOf: (T) -> String
    ): List<T> {
        val currentScoped = scopedPackages ?: currentScopeSet()
        return items.sortedWith { a, b ->
            val scopeCompare = compareValues(packageNameOf(a) !in currentScoped, packageNameOf(b) !in currentScoped)
            if (scopeCompare != 0) scopeCompare else appNameCollator.compare(appNameOf(a), appNameOf(b))
        }
    }

    fun bindScopeSwitch(
        context: Context,
        switchButton: MaterialSwitch,
        packageName: String,
        refreshSorted: (Set<String>?) -> Unit
    ) {
        XYApplication.mService?.apply {
            switchButton.visibility = View.VISIBLE
            switchButton.setOnClickListener(null)
            switchButton.isChecked = packageName in scope
            switchButton.setOnClickListener {
                if (switchButton.isChecked) {
                    switchButton.isChecked = false
                    requestScope(listOf(packageName), object : XposedService.OnScopeEventListener {
                        override fun onScopeRequestApproved(approved: List<String?>) {
                            refreshSorted(approvedScopeSet(approved))
                        }

                        override fun onScopeRequestFailed(message: String) {
                            refreshSorted(null)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    removeScope(listOf(packageName))
                    refreshSorted(predictedScopeWithout(packageName))
                }
            }
        } ?: run {
            switchButton.setOnClickListener(null)
            switchButton.visibility = View.GONE
        }
    }

    fun runOnMain(handler: Handler, action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }

    private fun currentScopeSet(): MutableSet<String> {
        return XYApplication.mService?.scope?.filterNotNull()?.toMutableSet() ?: mutableSetOf()
    }

    private fun predictedScopeWithout(packageName: String): Set<String> {
        return currentScopeSet().apply { remove(packageName) }
    }

    private fun approvedScopeSet(approved: List<String?>): Set<String> {
        val scoped = currentScopeSet()
        scoped.addAll(approved.filterNotNull())
        return scoped
    }
}