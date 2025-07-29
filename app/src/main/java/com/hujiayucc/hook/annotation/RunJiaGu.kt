package com.hujiayucc.hook.annotation

import kotlin.annotation.AnnotationTarget.CLASS

@Target(CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RunJiaGu(
    /** App名称 */
    val appName: String = "",
    /** 包名 */
    val packageName: String,
    /** 启动动作 */
    val action: String,
    /** 适配版本，留空为通用 */
    val versions: Array<String> = []
)
