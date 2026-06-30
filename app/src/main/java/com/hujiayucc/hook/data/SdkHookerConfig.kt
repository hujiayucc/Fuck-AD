package com.hujiayucc.hook.data

import android.content.SharedPreferences
import org.json.JSONObject

object SdkHookerConfig {
    const val PREF_KEY = "sdkHookerConfig"
    const val PANGLE = "pangle"
    const val GDT = "gdt"
    const val KW = "kw"

    val sdkNames = linkedMapOf(
        PANGLE to "穿山甲",
        GDT to "腾讯广告",
        KW to "快手广告"
    )

    fun isEnabled(prefs: SharedPreferences, packageName: String, sdkId: String): Boolean {
        return packageConfig(prefs, packageName)?.optBoolean(sdkId, true) ?: true
    }

    fun setEnabled(prefs: SharedPreferences, packageName: String, sdkId: String, enabled: Boolean) {
        val root = rootConfig(prefs)
        val packageConfig = root.optJSONObject(packageName) ?: JSONObject()
        packageConfig.put(sdkId, enabled)
        root.put(packageName, packageConfig)
        prefs.edit().putString(PREF_KEY, root.toString()).apply()
    }

    fun actionText(sdkIds: List<String>): String {
        if (sdkIds.isEmpty()) return ""
        return sdkIds.mapNotNull { sdkNames[it] }.joinToString(prefix = "[", postfix = "]", separator = "][")
    }

    fun idsFromAction(action: String): List<String> {
        return sdkNames.filterValues { name -> action.contains(name) }.keys.toList()
    }

    private fun packageConfig(prefs: SharedPreferences, packageName: String): JSONObject? {
        return rootConfig(prefs).optJSONObject(packageName)
    }

    private fun rootConfig(prefs: SharedPreferences): JSONObject {
        val config = prefs.getString(PREF_KEY, "")
        if (config.isNullOrEmpty()) return JSONObject()
        return runCatching { JSONObject(config) }.getOrDefault(JSONObject())
    }
}
