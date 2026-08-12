package com.hujiayucc.hook.hooker.app

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "哔哩哔哩",
    packageName = "tv.danmaku.bili",
    action = "开屏广告",
    versions = [
        "8.54.0"
    ]
)
object Bilibili : Hooker() {
    @SuppressLint("ResourceType")
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "tv.danmaku.bili.ui.splash.ad.page.FullImageSplash".toClassOrNull()
            ?.methods("y6")
            ?.hook {
                after {
                    val view = getField(instance(), "v") as View
                    runMainDelayed(100) {
                        view.performClick()
                    }
                }
            }

        "androidx.appcompat.widget.AppCompatTextView".toClassOrNull()
            ?.methods("setTextSize")?.hook {
                after {
                    val view = instance<TextView>()
                    if (view.id == 0x7f090ca8) {
                        runMain { view.performClick() }
                    }
                }
            }
    }
}