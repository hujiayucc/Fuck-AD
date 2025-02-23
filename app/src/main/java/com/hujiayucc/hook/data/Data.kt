package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import com.fasterxml.jackson.databind.ObjectMapper
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date

object Data {
    /** 获取项目编译完成的时间戳 (当前本地时间) */
    @SuppressLint("SimpleDateFormat")
    val buildTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        .format(Date(YukiHookAPI.Status.compiledTimestamp))
    var mapper: ObjectMapper = ObjectMapper()
    val PackageParam.config: String
        get() {
        try {
            moduleAppResources.assets.open("default.json").use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonContent = reader.readText()
                return jsonContent
            }
        } catch (e: IOException) {
            YLog.error("读取文件失败", e)
        }
            return ""
    }

    const val ACTION = "com.hujiayucc.hook.service.ClickService"
}