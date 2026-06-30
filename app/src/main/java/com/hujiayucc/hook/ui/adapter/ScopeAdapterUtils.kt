package com.hujiayucc.hook.ui.adapter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import com.hujiayucc.hook.R
import com.hujiayucc.hook.application.XYApplication
import io.github.libxposed.service.XposedService
import java.text.Collator
import java.util.Locale

object ScopeAdapterUtils {
    private val appNameCollator = Collator.getInstance(Locale.CHINA)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun <T> sortByScope(
        items: List<T>,
        scopedPackages: Set<String>? = null,
        packageNameOf: (T) -> String,
        appNameOf: (T) -> String
    ): List<T> {
        val currentScoped = scopedPackages ?: XYApplication.mService?.let { currentScopeSet(it) }.orEmpty()
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
        val currentService = XYApplication.mService
        if (currentService == null) {
            switchButton.setOnClickListener(null)
            switchButton.visibility = View.VISIBLE
            switchButton.isEnabled = true
            switchButton.isChecked = false
            switchButton.setOnClickListener {
                switchButton.isChecked = false
                Toast.makeText(context, R.string.scope_service_unavailable, Toast.LENGTH_SHORT).show()
            }
            return
        }

        switchButton.visibility = View.VISIBLE
        switchButton.isEnabled = true
        switchButton.setOnClickListener(null)
        switchButton.isChecked = packageName in currentScopeSet(currentService)
        switchButton.setOnClickListener {
            if (switchButton.isChecked) {
                switchButton.isChecked = false
                switchButton.isEnabled = false
                currentService.requestScope(listOf(packageName), object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String?>) {
                        runOnMain(mainHandler) {
                            val scopedPackages = approvedScopeSet(currentService, approved)
                            switchButton.isEnabled = true
                            switchButton.isChecked = packageName in scopedPackages
                            refreshSorted(scopedPackages)
                        }
                    }

                    override fun onScopeRequestFailed(message: String) {
                        runOnMain(mainHandler) {
                            val scopedPackages = currentScopeSet(currentService)
                            switchButton.isEnabled = true
                            switchButton.isChecked = packageName in scopedPackages
                            refreshSorted(scopedPackages)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            } else {
                currentService.removeScope(listOf(packageName))
                refreshSorted(predictedScopeWithout(currentService, packageName))
            }
        }
    }

    fun runOnMain(handler: Handler, action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }

    private fun currentScopeSet(service: XposedService): MutableSet<String> {
        return service.scope.filterNotNull().toMutableSet()
    }

    private fun predictedScopeWithout(service: XposedService, packageName: String): Set<String> {
        return currentScopeSet(service).apply { remove(packageName) }
    }

    private fun approvedScopeSet(service: XposedService, approved: List<String?>): Set<String> {
        val scoped = currentScopeSet(service)
        scoped.addAll(approved.filterNotNull())
        return scoped
    }
}