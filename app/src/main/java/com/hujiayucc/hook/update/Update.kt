package com.hujiayucc.hook.update

import android.os.StrictMode
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


object Update {
    const val json = "https://gitee.com/hujiayucc/com.hujiayucc.hook/raw/main/version.json"
    fun checkUpdate(): JSONObject? {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            // 创建OkHttpClient对象
            val client = OkHttpClient()
            // 创建Request对象
            val request: Request = Request.Builder().url(json).build()
            // 创建Call对象
            val call: Call = client.newCall(request)
            // 发起请求并处理响应
            return call.execute().body?.string()?.let { JSONObject(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
