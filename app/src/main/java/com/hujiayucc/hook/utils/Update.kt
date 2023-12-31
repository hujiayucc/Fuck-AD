package com.hujiayucc.hook.utils

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL


object Update {
    private const val URL = "https://fkad.hujiayucc.cn/version"
    fun checkUpdate(): JSONObject? {
        return try {
            val url = URL(URL)
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
