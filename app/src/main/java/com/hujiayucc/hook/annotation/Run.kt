package com.hujiayucc.hook.annotation

import kotlin.annotation.AnnotationTarget.CLASS

@Target(CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Run(
    /** App名称 */
    val appName: String = "",
    /** 包名 */
    val packageName: String,
    /** 适配版本，留空为通用 */
    val versions: Array<String> = []
)
