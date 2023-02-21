package com.hujiayucc.hook.hook.entity

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.hujiayucc.hook.data.PackageName
import com.hujiayucc.hook.hook.app.DragonRead
import com.hujiayucc.hook.hook.app.ZuiYou

/** 单独适配三方应用 */
object APPList : YukiBaseHooker() {
    override fun onHook() {
        /** 最右 */
        if (packageName.equals(PackageName.ZuiYou)) loadHooker(ZuiYou)
        /** 番茄小说 */
        if (packageName.equals(PackageName.DragonRead)) loadHooker(DragonRead)
    }
}