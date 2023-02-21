package com.hujiayucc.hook.update

import android.widget.Toast
import com.alibaba.fastjson.JSON
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication.Companion.appContext
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request


object Update {
    var url = ""
    val json = "https://gitee.com/hujiayucc/com.hujiayucc.hook/raw/main/version.json"
    fun isLast(): Boolean {
        try {
            // 创建OkHttpClient对象
            val client = OkHttpClient()
            // 创建Request对象
            val request: Request = Request.Builder().url(json).build()
            // 创建Call对象
            val call: Call = client.newCall(request)
            // 发起请求并处理响应
            val jsonObject = JSON.parseObject(call.execute().body?.string() ?: "")
            val versionCode = jsonObject.getIntValue("versionCode")
            if (versionCode > BuildConfig.VERSION_CODE) {
                url = jsonObject.getString("url")
                return false
            }
        } catch (e: Exception) {
            Toast.makeText(appContext, appContext.getString(R.string.check_update_failed), Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
        return true
    }
}
