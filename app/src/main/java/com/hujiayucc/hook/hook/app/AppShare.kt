package com.hujiayucc.hook.hook.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass

object AppShare : YukiBaseHooker() {
    override fun onHook() {
        ApplicationClass.hook {
            injectMember {
                method { name = "onCreate" }
                afterHook {
                    val context = instance<Application>()
                    val intent = Intent(context, "info.muge.appshare.view.main.MainActivity".toClass(appClassLoader))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }

        findClass("info.muge.appshare.view.launch.LaunchActivity").hook {
            injectMember {
                allMembers(type = MembersType.METHOD)

                beforeHook {
                    val activity = instance<Activity>()
                    activity.finish()
                }
            }
        }

        findClass("info.muge.appshare.view.main.MainActivity").hook {
            injectMember {
                method { name = "onBackPressed" }
                replaceUnit {
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_HOME)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    instance<Activity>().startActivity(intent)
                }
            }
        }
    }
}