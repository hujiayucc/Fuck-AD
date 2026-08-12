package com.hujiayucc.hook.hooker.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface


@Run(
    appName = "北通游戏厅",
    packageName = "com.zuoyou.center",
    action = "开屏广告"
)
object BeiTongYouXiTing : Hooker() {
    private var appContext: Context? = null

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        // 步骤1: 通过 hook Application.attach 获取宿主 Context
        Application::class.java.method("attach", Context::class.java)
            .hook {
                after {
                    // 只获取一次
                    if (appContext == null) {
                        appContext = args[0] as? Context
                    }
                }
            }

        // 步骤2: Hook SplashActivity.onCreate，修改 splash_img_path 值
        "com.zuoyou.center.ui.activity.SplashActivity"
            .toClassOrNull()
            ?.method("onCreate")
            ?.hook {
                before {
                    appContext
                        ?.getSharedPreferences("zuoyou_sharePrefs_default", Context.MODE_PRIVATE)
                        ?.edit {
                            putString("splash_img_path", "")
                        }
                }
            }
    }
}
