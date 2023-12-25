package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.method
import de.robv.android.xposed.XposedHelpers


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
            clazz.toClass().method {
                name = "init"
            }.hook().replaceTo(null)
        }

        "cn.xiaochuankeji.tieba.background.data.ServerVideo".toClass().allConstructors { _, constructor ->
            constructor.hook().after {
                val ob = instance
                val a = XposedHelpers.getObjectField(ob, "url")
                XposedHelpers.setObjectField(ob, "downloadUrl", a)
            }
        }
    }
}