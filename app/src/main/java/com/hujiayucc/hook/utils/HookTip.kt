package com.hujiayucc.hook.utils

import android.widget.Toast
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.android.ActivityClass
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.hook.app.DragonRead.hook

enum class HookTip(val id: Int, val tip: String) {
    DEFAULT(0, "Hook 成功"),
    ENGLISH(1, "Hook Success"),
    CHINESE(2, "Hook 成功");

    companion object {
        /** 通过id获取对应提示 */
        fun fromId(id: Int): String {
            //id不存在返回 "Hook Success"
            return values().find { it.id == id }?.tip ?: "Hook Success"
        }

        /** 显示Hook成功提示 */
        fun show(packageParam: PackageParam) {
            ActivityClass.hook {
                injectMember {
                    method {
                        name = "onCreate"
                        param(BundleClass)
                        returnType = UnitType
                    }

                    afterHook {
                        Toast.makeText(
                            instance(),
                            HookTip.fromId(packageParam.prefs.get(Data.localeId)),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}