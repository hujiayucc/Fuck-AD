package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "瓜子二手车",
    packageName = "com.ganji.android.haoche_c",
    action = "开屏广告"
)
object GuaZiErShouChe : Hooker() {

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        // 获取 MainActivity 类
        val mainAct = "com.cars.guazi.app.home.MainActivity".toClassOrNull() ?: return

        // Hook 广告入口方法，定位 showMainOrSellerFragment 方法字符串 "/app_ad/ad"
        mainAct.method("showMainOrSellerFragment").hook {
            replaceUnit {
                // 获取加载主页的方法，定位 loadMainFragment 方法字符串 "MainActivity loadMainFragment start!"
                val loadMethod = mainAct.method("loadMainFragment", Boolean::class.java)
                loadMethod.isAccessible = true
                // 拦截广告，直接手动加载主页
                loadMethod.invoke(instance, true)
            }
        }
    }
}
