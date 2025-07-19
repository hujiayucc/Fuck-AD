package com.hujiayucc.hook

import android.app.Application
import android.content.Context
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.highcapable.yukihookapi.hook.type.java.ThreadClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.data.Data.prefsData
import com.hujiayucc.hook.hooker.AppShare
import com.hujiayucc.hook.hooker.Bilibili
import com.hujiayucc.hook.hooker.CoolApk
import com.hujiayucc.hook.hooker.HuYa
import com.hujiayucc.hook.hooker.KOOK
import com.hujiayucc.hook.hooker.QiCat
import com.hujiayucc.hook.hooker.Sdks
import com.hujiayucc.hook.hooker.TK
import com.hujiayucc.hook.hooker.YouDaoDict

/** Hook入口 */
@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    companion object {
        init {
            System.loadLibrary("dexkit")
        }

        lateinit var classLoader: ClassLoader
    }

    override fun onInit() = YukiHookAPI.configs {
        debugLog {
            tag = "Fuck AD"
            isEnable = BuildConfig.DEBUG
            isRecord = BuildConfig.DEBUG
        }

        isDebug = BuildConfig.DEBUG
        isEnableModuleAppResourcesCache = true
        isEnableDataChannel = false
    }

    override fun onHook() = YukiHookAPI.encase {
        ApplicationClass.method { name = "attach" }
            .hook {
                after {
                    appClassLoader = (args[0] as Context).classLoader
                    classLoader = appClassLoader!!
                    val context = instance<Application>()
                    if (context.prefsData.getBoolean("tk")) loadHooker(TK(context))
                    loadJGHooker(packageName)
                }
            }
        dispatchUncaughtException()
        if (appContext?.prefsData?.getBoolean("sdk") == true) loadHooker(Sdks)
        loadHooker()
        loadJGHooker(packageName)
    }

    /** 拦截未处理的异常 */
    private fun PackageParam.dispatchUncaughtException() {
        ThreadClass.method { name = "dispatchUncaughtException" }
            .hook {
                replaceUnit {
                    if (!BuildConfig.DEBUG) return@replaceUnit
                    val param = args[0] as Throwable?
                    param?.message?.let { YLog.error(it, param) }
                }
            }
    }

    /** 普通 Hook */
    private fun PackageParam.loadHooker() {
        when (packageName) {
            "info.muge.appshare" -> loadHooker(AppShare)
            "tv.danmaku.bili" -> loadHooker(Bilibili)
            "com.duowan.kiwi" -> loadHooker(HuYa)
            "com.kmxs.reader" -> loadHooker(QiCat)
        }
    }

    /** 加固 Hook */
    private fun PackageParam.loadJGHooker(packageName: String) {
        when (packageName) {
            "com.coolapk.market" -> loadHooker(CoolApk)
            "cn.kaiheila" -> loadHooker(KOOK)
            "com.youdao.dict" -> loadHooker(YouDaoDict)
        }
    }
}