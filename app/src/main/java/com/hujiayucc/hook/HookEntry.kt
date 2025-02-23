package com.hujiayucc.hook

import android.widget.Toast
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.data.Action.Companion.toAction
import com.hujiayucc.hook.data.Clicker
import com.hujiayucc.hook.data.Config
import com.hujiayucc.hook.data.Data.config
import com.hujiayucc.hook.data.Data.mapper
import com.hujiayucc.hook.data.Hooker
import com.hujiayucc.hook.data.Type
import com.hujiayucc.hook.utils.AppInfoUtil.appName
import com.hujiayucc.hook.utils.AppInfoUtil.appVersionCode
import com.hujiayucc.hook.utils.AppInfoUtil.appVersionName

/** Hook入口 */
@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        runModule()
    }

    private fun PackageParam.runModule() {
        onAppLifecycle(isOnFailureThrowToApp = false) {
            attachBaseContext { context, _ ->
                YLog.debug("应用名：${context.appName} 包名：$packageName 版本名：${context.appVersionName} 版本号：${context.appVersionCode}")
                if (config.isEmpty()) {
                    Toast.makeText(context, "模块配置加载失败", Toast.LENGTH_LONG).show()
                } else if (isFirstApplication) {
                    val config = mapper.readValue<Config>(config, Config::class.java)
                    val clickers: ArrayList<Clicker> = arrayListOf()
                    val hooks: ArrayList<Hooker> = arrayListOf()
                    for (rule in config.rules) {
                        if (rule.packageName == packageName) {
                            for (item in rule.items) {
                                when (item.type) {
                                    Type.CLICK -> clickers.add(item.action.toAction<Clicker>())
                                    Type.HOOK -> hooks.add(item.action.toAction<Hooker>())
                                }
                            }
                        }
                    }
                    if (clickers.isNotEmpty()) loadHooker(Click(clickers))
                    if (hooks.isNotEmpty()) loadHooker(Hook(hooks))
                }
            }
        }
    }
}