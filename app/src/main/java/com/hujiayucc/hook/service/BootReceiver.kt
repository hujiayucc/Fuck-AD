package com.hujiayucc.hook.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import com.hujiayucc.hook.R
import com.hujiayucc.hook.utils.Log

class BootReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (isAccessibilitySettingsOn(context, SkipService::class.java.canonicalName!!)) {
            Log.d("Open AutoSkip Service")
            val intents = Intent(context, SkipService::class.java)
            context?.startService(intents)
            Toast.makeText(context, context?.getString(R.string.service_open_success), Toast.LENGTH_SHORT).show()
        }
    }

    /** 检测辅助功能是否开启 */
    private fun isAccessibilitySettingsOn(context: Context? ,serviceName: String): Boolean {
        var accessibilityEnabled = 0
        // 对应的服务
        val service = "${context?.packageName}/$serviceName"
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context?.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            Log.i("accessibilityEnabled = $accessibilityEnabled")
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("Error finding setting, default accessibility to not found: " + e.message)
        }
        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            Log.i("***ACCESSIBILITY IS ENABLED***")
            val settingValue: String = Settings.Secure.getString(
                context?.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            mStringColonSplitter.setString(settingValue)
            while (mStringColonSplitter.hasNext()) {
                val accessibilityService = mStringColonSplitter.next()
                Log.i(" > accessibilityService :: $accessibilityService $service")
                if (accessibilityService.equals(service,true)) {
                    Log.i("We've found the correct setting - accessibility is switched on!")
                    return true
                }
            }
        } else {
            Log.i("***ACCESSIBILITY IS DISABLED***")
        }
        return false
    }
}