package com.hujiayucc.hook.hooker.util

import io.github.libxposed.api.XposedInterface

interface HookCallback {
    val chain: XposedInterface.Chain
    val thisObject: Any?
    val args: List<Any?>
    val hasResult: Boolean
    var result: Any?

    fun proceed(): Any?
    fun proceedWith(args: Array<Any?>): Any?
    fun proceedWith(thisObject: Any?, args: Array<Any?>): Any?
}