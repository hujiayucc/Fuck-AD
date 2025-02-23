package com.hujiayucc.hook

import com.fasterxml.jackson.databind.SerializationFeature
import com.hujiayucc.hook.data.Clicker
import com.hujiayucc.hook.data.Config
import com.hujiayucc.hook.data.Data.mapper
import com.hujiayucc.hook.data.Item
import com.hujiayucc.hook.data.Rule
import com.hujiayucc.hook.data.Type
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
        val config = Config(
            version = 1,
            rules = getRules()
        )

        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        val json = mapper.writeValueAsString(config)
        println("> Task :Preview JSON:\n$json")
        val file = File("src/main/assets/default.json")
        file.writeText(json, Charsets.UTF_8)
        println("> Task :Write to ${file.absolutePath}")
    }

    private fun getRules(): ArrayList<Rule> {
        return arrayListOf(
            Rule(
                name = "Fuck AD",
                packageName = "com.hujiayucc.hook",
                items = arrayListOf(
                    Item(
                        name = "测试",
                        desc = "测试1",
                        versionName = "2.0",
                        versionCode = 7000,
                        type = Type.CLICK,
                        action = Clicker(
                            activity = "com.hujiayucc.hook.ui.activity.Home",
                            view = "0x${R.id.main_active_status.toString(16)}"
                        )
                    ),
                    Item(
                        name = "测试",
                        desc = "测试2",
                        versionName = "2.0",
                        versionCode = 7000,
                        type = Type.CLICK,
                        action = Clicker(
                            activity = "com.hujiayucc.hook.ui.activity.Home",
                            sleep = 5000,
                            view = "Activated"
                        )
                    )
                )
            ),
            Rule(
                name = "虎牙直播",
                packageName = "com.duowan.kiwi",
                items = arrayListOf(
                    Item(
                        name = "启动页广告",
                        desc = "虎牙直播启动页广告",
                        versionName = "12.5.6",
                        versionCode = 100135,
                        type = Type.CLICK,
                        action = Clicker(
                            activity = "com.duowan.kiwi.homepage.Homepage",
                            sleep = 0,
                            view = "跳过"
                        )
                    ),
                    Item(
                        name = "首页广告",
                        desc = "虎牙直播首页弹窗广告",
                        versionName = "12.5.6",
                        versionCode = 100135,
                        type = Type.CLICK,
                        action = Clicker(
                            activity = "com.duowan.kiwi.homepage.Homepage",
                            sleep = 0,
                            view = "0x7f092eba"
                        )
                    )
                )
            ),
            Rule(
                name = "哔哩哔哩",
                packageName = "tv.danmaku.bili",
                items = arrayListOf(
                    Item(
                        name = "启动页广告",
                        desc = "哔哩哔哩启动页广告",
                        versionName = "8.34.0",
                        versionCode = 8340200,
                        type = Type.CLICK,
                        action = Clicker(
                            activity = "tv.danmaku.bili.MainActivityV2",
                            sleep = 0,
                            view = "^跳过(.*)\\d",
                            isRegex = true
                        )
                    )
                )
            )
        )
    }
}