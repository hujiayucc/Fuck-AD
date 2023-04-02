package com.hujiayucc.hook.utils

import android.widget.Toast
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.hujiayucc.hook.hook.app.DragonRead.hook
import com.hujiayucc.hook.hook.entity.Jiagu

enum class HookTip(val id: Int, val tip: String) {
    DEFAULT(0, "Hook 成功"),
    ENGLISH(1, "Hook Success"),
    CHINESE(2, "Hook 成功");

    companion object {
        /** 通过id获取对应提示 */
        private fun fromId(id: Int): String {
            //id不存在返回 "Hook Success"
            return values().find { it.id == id }?.tip ?: "Hook Success"
        }

        /** 显示Hook成功提示 */
        fun show(packageParam: PackageParam) {
            for (type in Jiagu.values()) {
                if (type.packageName.toClassOrNull(packageParam.appClassLoader) != null) {
                    Log.d(type.type)
                    packageParam.findClass(type.packageName).hook {
                        injectMember {
                            method { name = "onCreate" }
                            afterHook {
                                Toast.makeText(
                                    instance(),
                                    HookTip.fromId(packageParam.prefs.get(Data.localeId)),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }.ignoredAllFailure()
                    }.ignoredHookClassNotFoundFailure()
                    return
                }
            }

            ApplicationClass.hook {
                injectMember {
                    method {
                        name = "onCreate"
                    }

                    afterHook {
                        Toast.makeText(
                            instance(),
                            HookTip.fromId(packageParam.prefs.get(Data.localeId)),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.ignoredAllFailure()
            }.ignoredHookClassNotFoundFailure()
        }
    }
}