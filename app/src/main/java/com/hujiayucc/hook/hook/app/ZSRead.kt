package com.hujiayucc.hook.hook.app

import android.app.Application
import android.content.Context
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.hujiayucc.hook.hook.sdk.Pangle

/** 追书小说阅读 */
object ZSRead: YukiBaseHooker() {
    override fun onHook() {
        findClass("com.wrapper.proxyapplication.WrapperProxyApplication").hook {
            injectMember {
                method { name = "attachBaseContext" }
                afterHook {
                    val context = args[0] as Context
                    appClassLoader = context.classLoader
                    loadHooker(Pangle)

                    ApplicationClass.hook {
                        injectMember {
                            method { name = "onCreate" }
                            afterHook {
                                val application = instance<Application>()
                                val intent = Intent(application, "com.biquge.ebook.app.ui.activity.MainActivity".toClass(appClassLoader))
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                application.startActivity(intent)
                            }
                        }.ignoredAllFailure()
                    }
                }
            }
        }.ignoredHookClassNotFoundFailure()
    }
}