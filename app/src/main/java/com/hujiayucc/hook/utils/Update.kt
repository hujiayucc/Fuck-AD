package com.hujiayucc.hook.utils

import android.os.StrictMode
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL


object Update {
    private const val json = "https://fkad.hujiayucc.cn/version"
    fun checkUpdate(): JSONObject? {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        return try {
            val url = URL(json)
            val bufferedReader = BufferedReader(InputStreamReader(url.openStream()))
            val string = StringBuilder()
            var str: String?
            while (bufferedReader.readLine().also { str = it } != null) {
                string.append(str)
            }
            bufferedReader.close()
            if (string.toString().isNotEmpty()) JSONObject(string.toString()) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
