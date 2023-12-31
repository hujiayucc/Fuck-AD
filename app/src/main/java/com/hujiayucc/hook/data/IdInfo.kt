package com.hujiayucc.hook.data

/**
 * 软件按钮规则
 *
 * @param packageName 软件包名
 * @param resId 跳过控件ID组
 * @param wait 等待延迟点击时长
 */
data class IdInfo (
    val packageName: String,
    val resId: ArrayList<String> = ArrayList()
)
