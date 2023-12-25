package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method

object Google : YukiBaseHooker() {
    private val list = arrayOf(
        "com.google.android.gms.ads.internal.zzk",
        "com.google.android.gms.internal.ads.zzbdl",
        "com.google.android.gms.internal.ads.zzbfk"
    )

    private val nullReplaceList = arrayOf(
        "com.google.android.gms.internal.ads.zzxl",
        "com.google.android.gms.ads.MobileAds",
        "com.google.android.gms.ads.MobileAdsInitProvider",
    )

    override fun onHook() {
        for (name in list) {
            val clazz = name.toClassOrNull()?: continue
            clazz.method {
                this.name = "shouldOverrideUrlLoading"
            }.ignored().hook().replaceToFalse()
        }

        for (name in nullReplaceList) {
            val clazz = name.toClassOrNull() ?: continue
            clazz.allMethods { _, method ->
                method.hook().replaceTo(null)
            }

            clazz.allConstructors { _, constructor ->
                constructor.hook().replaceTo(null)
            }
        }
    }
}