package com.hujiayucc.hook.utils

import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

internal object ShizukuProcessExecutor {
    @Volatile
    private var cachedNewProcessMethod: Method? = null

    fun run(command: Array<String>, timeoutSeconds: Long): Boolean {
        return runCatching {
            newProcessMethod()
                .invoke(null, command, null, null)
                .let { it as Process }
                .waitForSuccess(timeoutSeconds)
        }.getOrDefault(false)
    }

    private fun newProcessMethod(): Method {
        cachedNewProcessMethod?.let { return it }
        return synchronized(this) {
            cachedNewProcessMethod ?: Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
                .also { cachedNewProcessMethod = it }
        }
    }
}

internal fun Process.waitForSuccess(timeoutSeconds: Long): Boolean {
    val finished = waitFor(timeoutSeconds, TimeUnit.SECONDS)
    if (!finished) {
        destroy()
        return false
    }
    return exitValue() == 0
}