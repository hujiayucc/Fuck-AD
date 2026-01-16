package com.hujiayucc.hook.hooker

import com.hujiayucc.hook.annotation.RunJiaGu

@RunJiaGu(
    appName = "疯狂刷题",
    packageName = "com.yaerxing.fkst",
    action = "开屏广告"
)
object FengKuangShuaTi: Base() {
    override fun onStart() {
        loadSdk(pangle = true)
    }
}