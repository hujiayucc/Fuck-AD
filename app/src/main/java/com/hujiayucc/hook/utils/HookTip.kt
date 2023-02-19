package com.hujiayucc.hook.utils

enum class HookTip(val id: Int, val tip: String) {
    DEFAULT(0, "Hook 成功"),
    ENGLISH(1, "Hook Success"),
    CHINESE(2, "Hook 成功");

    companion object {
        /** 通过id获取对应提示 */
        fun fromId(id: Int): String {
            //id不存在返回 Locale.getDefault()
            return values().find { it.id == id }?.tip ?: "Hook Success"
        }
    }
}