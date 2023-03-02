package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType

object Google : YukiBaseHooker() {
    private val list = arrayOf(
        "com.google.android.gms.ads.internal.zzk",
        "com.google.android.gms.internal.ads.zzbdl",
        "com.google.android.gms.internal.ads.zzbfk"
    )

    override fun onHook() {
        for (clazz in list) {
            findClass(clazz).hook {
                injectMember {
                    method { name = "shouldOverrideUrlLoading" }
                    replaceToFalse()
                }
            }.ignoredHookClassNotFoundFailure()
        }

        findClass("com.google.android.gms.internal.ads.zzxl").hook {
            injectMember {
                allMembers(type = MembersType.ALL)
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()
    }
}