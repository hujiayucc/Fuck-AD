package com.hujiayucc.hook.hook.entity

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method

object PrivateDns : YukiBaseHooker() {
    override fun onHook() {
        "android.provider.Settings\$Global".toClass().method {
            name = "getString"
            paramCount = 2
        }.hook().before {
            val settingName = args[1] as String
            if (settingName == "private_dns_specifier") {
                result = "dns.hujiayucc.cn"
            } else if (settingName == "private_dns_mode") {
                result = "hostname"
            }
        }
    }
}