package com.hujiayucc.hook.hook.entity

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.hujiayucc.hook.hook.app.AppShare.hook
import com.hujiayucc.hook.utils.Log

enum class Jiagu(val packageName: String, val type: String) {
    Jiagu360("com.stub.StubApp","360加固"),
    Tencent("com.wrapper.proxyapplication.WrapperProxyApplication","腾讯御安全"),
    NETEASE("com.netease.nis.wrapper.MyApplication","网易易盾");

    companion object {
        /** 加载加固 */
        fun jiaguClassLoader(packageParam: PackageParam): ClassLoader? {
            var classLoader: ClassLoader? = null
            for (type in Jiagu.entries) {
                type.packageName.toClassOrNull(packageParam.appClassLoader)?.let {
                    Log.d(type.type)
                    it.method { name = "attachBaseContext" }.ignored().hook().after {
                        classLoader = (args[0] as Context).classLoader
                    }
                    return classLoader
                }
            }
            return null
        }
    }
}