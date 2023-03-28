package com.hujiayucc.hook.update

import android.os.StrictMode
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL


object Update {
    const val json = "https://gitee.com/hujiayucc/com.hujiayucc.hook/raw/main/version.json"
    fun checkUpdate(): JSONObject? {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val url = URL(json)
            val bufferedReader = BufferedReader(InputStreamReader(url.openStream()))
            val string = StringBuilder()
            var str: String?
            while (bufferedReader.readLine().also { str = it } != null) {
                string.append(str)
            }
            bufferedReader.close()
            return if (string.toString().isNotEmpty()) JSONObject(string.toString()) else null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
