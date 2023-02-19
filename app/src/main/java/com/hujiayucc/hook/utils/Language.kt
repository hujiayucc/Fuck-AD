package com.hujiayucc.hook.utils

import android.annotation.SuppressLint
import java.util.*

/** 多语言枚举 */
@SuppressLint("ConstantLocale")
enum class Language(val id: Int, val locale: Locale) {
    DEFAULT(0, Locale.getDefault()),
    ENGLISH(1, Locale.ENGLISH),
    CHINESE(2, Locale.CHINESE);

    companion object {
        /** 通过id获取对应语言 */
        fun fromId(id: Int): Locale {
            //id不存在返回 Locale.getDefault()
            return values().find { it.id == id }?.locale ?: Locale.getDefault()
        }
    }
}