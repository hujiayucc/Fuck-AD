package com.hujiayucc.hook.hooker.app

import android.os.Handler
import android.os.Looper
import android.view.View
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "MockGps",
    packageName = "com.huolala.mockgps",
    action = "开屏广告, 底部广告",
    versions = [
        "2.6.1"
    ]
)
object MockGps : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        loadSdk(this, kw = true)
        "androidx.appcompat.widget.AppCompatImageView".toClassOrNull()
            ?.method("setImageDrawable")
            ?.hook {
                after {
                    val view = instance as View
                    if (view.id == 0x7f080254) {
                        runMainDelayed(200) {
                            view.performClick()
                        }
                    }
                }
            }
    }
}