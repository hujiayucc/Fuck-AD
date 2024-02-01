package com.hujiayucc.hook.data

import android.content.Context
import com.hujiayucc.hook.utils.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL

class FindId(private val context: Context) {
    private var rules: JSONObject? = null

    init {
        Thread {
            context.init()
        }.start()
    }

    private fun findId(packageName: String): IdInfo? {
        val groups = rules?.getJSONArray(packageName) ?: return null
        return groups.getRules()?.let { IdInfo(packageName, it) }
    }

    fun findId(packageName: CharSequence): IdInfo? {
        return findId(packageName.toString())
    }

    private fun Context.init() {
        initFile()
        val local = JSONObject(localRules)
        onlineRules?.let {
            val online = JSONObject(it)
            if (online.getInt("version") > local.getInt("version")) {
                saveRules(it)
                rules = online
                return
            }
        }

        local.let {
            rules = it
            Log.e("加载完毕：${it.length()}")
        }
    }

    private fun Context.initFile() {
        if (rulesFile.exists().not()) {
            rulesFile.createNewFile()
            onlineRules?.let { saveRules(it) }
        }
    }

    companion object {
        private const val URL = "https://fkad.hujiayucc.cn/rules"
        val Context.rulesFile: File get() = File(filesDir,"rules.json")
        val onlineRules: String? get() = try {
            val url = URL(URL)
            val bufferedReader = BufferedReader(InputStreamReader(url.openStream()))
            val string = StringBuilder()
            var str: String?
            while (bufferedReader.readLine().also { str = it } != null) {
                string.append(str)
            }
            bufferedReader.close()
            string.toString().ifEmpty { null }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        private fun Context.saveRules(json: String) {
            val outputStream = FileOutputStream(rulesFile)
            outputStream.write(JSONObject(json).toString().toByteArray())
            outputStream.flush()
            outputStream.close()
        }

        private val Context.localRules: String get() {
            val inputStream = rulesFile.inputStream()
            val byte = ByteArray(rulesFile.length().toInt())
            inputStream.read(byte)
            inputStream.close()
            return String(byte)
        }

        private fun JSONArray.getRules(): ArrayList<String>? {
            val array = ArrayList<String>()
            for (i in 0 until length()) {
                array.add(getString(i))
            }
            if (array.isNotEmpty()) return array
            return null
        }
    }
}