package com.hujiayucc.hook.update

import com.alibaba.fastjson.JSON
import com.hujiayucc.hook.BuildConfig
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request


object Update {
    val json = "https://gitee.com/hujiayucc/com.hujiayucc.hook/raw/main/version.json"
    fun checkUpdate(): Any? {
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
                return jsonObject.get("url")
            }
            return 0
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
