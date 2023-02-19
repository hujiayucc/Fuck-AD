package com.hujiayucc.hook.hook.entity

import android.widget.Toast
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.ActivityClass
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.utils.HookTip
import com.hujiayucc.hook.utils.Log


/** 腾讯广告 */
object Tencent : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.qq.e.comm.managers.GDTADManager").hook {
            injectMember {
                method {
                    name = "getInstance"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.qq.e.comm.managers.GDTADManager.getInstance()") }
                }

                afterHook {
                    result = null
                }
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.qq.e.comm.constants.CustomPkgConstants").hook {
            injectMember {
                method {
                    name = "getADActivityName"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.qq.e.comm.constants.CustomPkgConstants.getADActivityName()") }
                }

                afterHook {
                    result = ""
                    Log.d("onHook Tencent")
                }
            }

            injectMember {
                method {
                    name = "getAssetPluginDir"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.qq.e.comm.constants.CustomPkgConstants.getAssetPluginDir()") }
                }

                afterHook {
                    result = ""
                    Log.d("onHook Tencent")
                }
            }

            injectMember {
                method {
                    name = "getAssetPluginName"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.qq.e.comm.constants.CustomPkgConstants.getAssetPluginName()") }
                }

                afterHook {
                    result = ""
                    Log.d("onHook Tencent")
                }
            }

            injectMember {
                method {
                    name = "getDownLoadServiceName"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.qq.e.comm.constants.CustomPkgConstants.getDownLoadServiceName()") }
                }

                afterHook {
                    result = ""
                    Log.d("onHook Tencent")
                }
            }
        }.ignoredHookClassNotFoundFailure()

        if (prefs.get(Data.hookTip)) {
            ActivityClass.hook {
                injectMember {
                    method {
                        name = "onCreate"
                        param(BundleClass)
                        returnType = UnitType
                    }

                    afterHook {
                        Toast.makeText(instance(), HookTip.fromId(prefs.get(Data.localeId)), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}