package com.hujiayucc.hook.hooker.sdk

import java.util.concurrent.ConcurrentHashMap

object SdkHookerResolver {
    private val targetCache = ConcurrentHashMap<String, List<SdkHookerTarget>>()

    fun resolve(packageName: String, classLoader: ClassLoader): List<SdkHookerTarget> {
        targetCache[packageName]?.let { return it }

        val matchedTargets = SdkHookerRegistry.targets.filter { target ->
            target.markerClasses.any { className -> classLoader.hasClass(className) }
        }
        targetCache[packageName] = matchedTargets
        return matchedTargets
    }

    fun describe(targets: List<SdkHookerTarget>): String {
        return if (targets.isEmpty()) "none" else targets.joinToString { it.name }
    }

    private fun ClassLoader.hasClass(className: String): Boolean {
        return runCatching { loadClass(className) }.isSuccess
    }
}
