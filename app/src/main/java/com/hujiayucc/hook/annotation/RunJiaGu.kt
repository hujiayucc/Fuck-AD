package com.hujiayucc.hook.annotation

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * 加固应用Hooker运行注解
 * @param appName App名称
 * @param packageName 包名
 * @param action 启动动作
 * @param versions 适配版本，留空为通用
 */
@Target(CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RunJiaGu(
    val appName: String = "",
    val packageName: String,
    val action: String,
    val versions: Array<String> = []
)
