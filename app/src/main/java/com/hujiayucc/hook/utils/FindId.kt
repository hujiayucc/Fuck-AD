package com.hujiayucc.hook.utils

enum class FindId(val packageName: String, val id: String) {
    ZuiYou("cn.xiaochuankeji.tieba","cn.xiaochuankeji.tieba:id/btn_skip");

    companion object {
        /** 通过包名获取对应id */
        fun fromPackageName(packageName: String): String? {
            return values().find { it.packageName == packageName }?.id
        }
    }
}