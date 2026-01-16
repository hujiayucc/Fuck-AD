package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.Base

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