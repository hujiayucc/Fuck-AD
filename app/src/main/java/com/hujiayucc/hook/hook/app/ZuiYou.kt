package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.hujiayucc.hook.utils.Log

/** 最右 */
object ZuiYou : YukiBaseHooker() {
    private val list = arrayOf(
        "cn.xiaochuankeji.hermes.core.provider.ADParam",
        "cn.xiaochuankeji.hermes.bjxingu.BJXinguADProvider",
        "cn.xiaochuankeji.hermes.klevin.KlevinADProvider",
        "cn.xiaochuankeji.hermes.kuaishou.KuaishouADProvider",
        "cn.xiaochuankeji.hermes.mimo.MimoADProvider",
        "cn.xiaochuankeji.hermes.pangle.PangleADProvider",
        "cn.xiaochuankeji.hermes.qumeng.QuMengADProvider",
        "cn.xiaochuankeji.hermes.tencent.TencentADProvider",
        "cn.xiaochuankeji.hermes.xcad.XcADProvider",
        "cn.xiaochuankeji.hermes.xingu.XinguADProvider"
    )

    override fun onHook() {
        for (clazz in list) {
            findClass(clazz).hook {
                injectMember {
                    method {
                        name = "init"
                    }
                    replaceTo(null)
                    Log.d("Hook Provider: $clazz")
                }
            }.ignoredHookClassNotFoundFailure()
        }
    }
}