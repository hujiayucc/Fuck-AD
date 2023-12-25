package com.hujiayucc.hook.hook.entity

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.allMethods

/** 禁用广告SDK Provider */
object Provider : YukiBaseHooker() {
    private val list = arrayOf(
        "com.bytedance.pangle.FileProvider",
        "com.bytedance.pangle.provider.MainProcessProviderProxy",
        "com.bytedance.sdk.openadsdk.TTFileProvider",
        "com.bytedance.sdk.openadsdk.multipro.TTMultiProvider",
        "com.bytedance.sdk.openadsdk.stub.server.DownloaderServerManager",
        "com.bytedance.sdk.openadsdk.stub.server.MainServerManager",
        "com.kwad.sdk.api.proxy.app.AdSdkFileProvider",
        "com.qq.e.comm.GDTFileProvider"
    )

    override fun onHook() {
        for (clazz in list) {
            clazz.toClassOrNull()?.allMethods { _, method -> method.hook().replaceTo(null) }
            clazz.toClassOrNull()?.allConstructors { _, constructor -> constructor.hook().replaceTo(null) }
        }
    }
}