package com.hujiayucc.hook.hook.entity

import android.widget.Toast
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.type.android.ActivityClass
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.data.DataConst
import com.hujiayucc.hook.utils.Log

object Tencent : YukiBaseHooker() {
    override fun onHook() {
        if (prefs.get(hookTip)) {
            ActivityClass.hook {
                injectMember {
                    method {
                        name = "onCreate"
                        param(BundleClass)
                        returnType = UnitType
                    }

                    afterHook {
                        Toast.makeText(instance(), "Hook 成功", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findClass("com.qq.e.comm.constants.CustomPkgConstants").hook {
            injectMember {
                method {
                    name = "getADActivityName"
                }

                afterHook {
                    result = ""
                    Log.d("onHook Tencent")
                }
            }

            injectMember {
                method {
                    name = "getAssetPluginDir"
                }

                afterHook {
                    result = ""
                    Log.d("onHook Tencent")
                }
            }

            injectMember {
                method {
                    name = "getAssetPluginName"
                }

                afterHook {
                    result = ""
                    Log.d("onHook Tencent")
                }
            }
        }.onHookClassNotFoundFailure {
            it.message?.let { it1 -> loggerE(DataConst.TAG, it1) }
            it.printStackTrace()
        }
    }
}