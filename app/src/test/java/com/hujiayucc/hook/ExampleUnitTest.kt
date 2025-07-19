package com.hujiayucc.hook

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.hujiayucc.hook.data.Item
import org.junit.Test
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        val mapper = JsonMapper()
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        val json = mapper.writeValueAsString(getRules())
        println("> Task :Preview JSON:\n$json")
        val file = File("src/main/assets/default.json")
        file.writeText(json, Charsets.UTF_8)
        println("> Task :Write to ${file.absolutePath}")
    }

    private fun getRules(): List<Item> {
        return listOf(
            Item(
                appName = "AppShare",
                packageName = "info.muge.appshare",
                action = "开屏广告",
                versionList = listOf("4.0.4", "4.0.5")
            ),
            Item(
                appName = "虎牙直播",
                packageName = "com.duowan.kiwi",
                action = "开屏广告",
                versionList = listOf("15.5.72")
            ),
            Item(
                appName = "七猫免费小说",
                packageName = "com.kmxs.reader",
                action = "开屏广告",
                versionList = listOf("7.67")
            ),
            Item(
                appName = "哔哩哔哩",
                packageName = "tv.danmaku.bili",
                action = "开屏广告",
                versionList = listOf("8.40.0")
            ),
            Item(
                appName = "网易有道词典",
                packageName = "com.youdao.dict",
                action = "开屏广告",
                versionList = listOf("10.2.11")
            ),
        )
    }
}