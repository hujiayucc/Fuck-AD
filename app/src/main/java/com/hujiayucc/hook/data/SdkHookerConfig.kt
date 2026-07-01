package com.hujiayucc.hook.data

import android.content.SharedPreferences
import org.json.JSONObject

object SdkHookerConfig {
    const val PREF_KEY = "sdkHookerConfig"
    const val PANGLE = "pangle"
    const val GDT = "gdt"
    const val KW = "kw"
    const val BAIDU = "baidu"
    const val SIGMOB = "sigmob"
    const val MINTEGRAL = "mintegral"
    const val TOPON = "topon"
    const val TRADPLUS = "tradplus"
    const val GOOGLE = "google"
    const val APPLOVIN = "applovin"
    const val UNITY = "unity"
    const val VUNGLE = "vungle"
    const val LEVELPLAY = "levelplay"

    val sdkNames = linkedMapOf(
        PANGLE to "穿山甲",
        GDT to "腾讯广告",
        KW to "快手广告",
        BAIDU to "百青藤",
        SIGMOB to "Sigmob",
        MINTEGRAL to "Mintegral",
        TOPON to "TopOn",
        TRADPLUS to "TradPlus",
        GOOGLE to "Google Ads",
        APPLOVIN to "AppLovin MAX",
        UNITY to "Unity Ads",
        VUNGLE to "Vungle/Liftoff",
        LEVELPLAY to "ironSource/LevelPlay"
    )

    val sdkComponentPrefixes = linkedMapOf(
        PANGLE to listOf("com.bytedance.sdk.openadsdk."),
        GDT to listOf("com.qq.e.ads", "com.qq.e.comm."),
        KW to listOf("com.kwad.sdk.", "com.kwai.", "com.ksad."),
        BAIDU to listOf("com.baidu.mobads."),
        SIGMOB to listOf("com.sigmob."),
        MINTEGRAL to listOf("com.mbridge.msdk."),
        TOPON to listOf("com.anythink."),
        TRADPLUS to listOf("com.tradplus.", "com.tradplusad."),
        GOOGLE to listOf("com.google.android.gms.ads.", "com.google.android.gms.internal.ads."),
        APPLOVIN to listOf("com.applovin."),
        UNITY to listOf("com.unity3d.ads.", "com.unity3d.services."),
        VUNGLE to listOf("com.vungle."),
        LEVELPLAY to listOf("com.ironsource.", "com.unity3d.mediation.")
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
