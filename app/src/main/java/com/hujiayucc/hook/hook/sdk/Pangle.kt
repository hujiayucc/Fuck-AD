package com.hujiayucc.hook.hook.sdk

import android.widget.TextView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.listOfClasses
import com.highcapable.yukihookapi.hook.factory.onLoadClass
import com.highcapable.yukihookapi.hook.type.android.TextViewClass
import com.hujiayucc.hook.utils.Log
import de.robv.android.xposed.XposedBridge


/** 穿山甲广告 */
object Pangle : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.bytedance.sdk.openadsdk.TTAdConfig").hook {
            injectMember {
                method { name = "getSdkInfo" }
                replaceTo(null)
            }.ignoredAllFailure()
        }.ignoredHookClassNotFoundFailure()

        findClass("com.bytedance.sdk.openadsdk.TTAdSdk").hook {
            injectMember {
                method { name = "init" }
                beforeHook { resultNull() }
            }
            injectMember {
                method { name = "isInitSuccess" }
                replaceToFalse()
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.bytedance.sdk.openadsdk.AdSlot").hook {
            injectMember {
                allMembers()
                replaceTo(null)
            }.ignoredAllFailure()
        }.ignoredHookClassNotFoundFailure()

        findClass("com.bytedance.sdk.openadsdk.AdSlot.Builder").hook {
            injectMember {
                allMembers()
                replaceTo(null)
            }.ignoredAllFailure()
        }.ignoredHookClassNotFoundFailure()

        findClass("com.dragon.read.reader.ad.readflow.ui.ReadFlowDynamicAdLine").hook {
            injectMember {
                allMembers()
                replaceTo(null)
            }.ignoredAllFailure()
        }.ignoredHookClassNotFoundFailure()
    }
}