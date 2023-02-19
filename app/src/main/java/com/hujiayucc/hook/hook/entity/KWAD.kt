package com.hujiayucc.hook.hook.entity

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.hujiayucc.hook.utils.Log

object KWAD : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.kwad.sdk.core.network.BaseResultData").hook {
            injectMember {
                method {
                    name = "isResultOk"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.kwad.sdk.core.network.BaseResultData.isResultOk()") }
                }

                afterHook {
                    result = false
                    Log.d("onHook KWAD")
                }
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.kwad.components.offline.api.core.network.model.BaseOfflineCompoResultData").hook {
            injectMember {
                method {
                    name = "isResultOk"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.kwad.components.offline.api.core.network.model.BaseOfflineCompoResultData.isResultOk()") }
                }

                afterHook {
                    result = false
                    Log.d("onHook KWAD")
                }
            }
        }.ignoredHookClassNotFoundFailure()
    }
}