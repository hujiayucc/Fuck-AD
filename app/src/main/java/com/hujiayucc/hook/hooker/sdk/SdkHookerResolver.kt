package com.hujiayucc.hook.hooker.sdk

import java.util.WeakHashMap

enum class SdkMatchConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class SdkHookerMatch(
    val target: SdkHookerTarget,
    val confidence: SdkMatchConfidence,
    val matchedMarkerClasses: List<String>
)

object SdkHookerResolver {
    private val matchCache = WeakHashMap<ClassLoader, MutableMap<String, List<SdkHookerMatch>>>()
    private val presentClassCache = WeakHashMap<ClassLoader, MutableSet<String>>()

    fun resolve(packageName: String, classLoader: ClassLoader): List<SdkHookerMatch> {
        synchronized(matchCache) {
            matchCache[classLoader]?.get(packageName)?.let { return it }
        }

        val matches = SdkHookerRegistry.targets.mapNotNull { target ->
            target.match(classLoader)
        }
        if (matches.isNotEmpty()) {
            synchronized(matchCache) {
                matchCache.getOrPut(classLoader, ::HashMap)[packageName] = matches
            }
        }
        return matches
    }

    fun describe(matches: List<SdkHookerMatch>): String {
        return if (matches.isEmpty()) {
            "none"
        } else {
            matches.joinToString { match ->
                "${match.target.name}[${match.confidence.name.lowercase()}, markers=${match.matchedMarkerClasses.size}]"
            }
        }
    }

    private fun SdkHookerTarget.match(classLoader: ClassLoader): SdkHookerMatch? {
        val coreMatches = coreMarkerClasses.filter { classLoader.hasClass(it) }
        val strongMatches = strongMarkerClasses.filter { classLoader.hasClass(it) }
        val compatibilityMatches = compatibilityMarkerClasses.filter { classLoader.hasClass(it) }
        val confidence = sdkMatchConfidence(
            coreMatchCount = coreMatches.size,
            strongMatchCount = strongMatches.size,
            compatibilityMatchCount = compatibilityMatches.size
        ) ?: return null
        return SdkHookerMatch(
            target = this,
            confidence = confidence,
            matchedMarkerClasses = coreMatches + strongMatches + compatibilityMatches
        )
    }

    private fun ClassLoader.hasClass(className: String): Boolean {
        synchronized(presentClassCache) {
            if (presentClassCache[this]?.contains(className) == true) return true
        }
        return runCatching { loadClass(className) }
            .isSuccess
            .also { exists ->
                if (exists) {
                    synchronized(presentClassCache) {
                        presentClassCache.getOrPut(this, ::HashSet) += className
                    }
                }
            }
    }
}

internal fun sdkMatchConfidence(
    coreMatchCount: Int,
    strongMatchCount: Int,
    compatibilityMatchCount: Int
): SdkMatchConfidence? {
    return when {
        coreMatchCount > 0 || strongMatchCount >= 2 -> SdkMatchConfidence.HIGH
        strongMatchCount > 0 -> SdkMatchConfidence.MEDIUM
        compatibilityMatchCount >= 2 -> SdkMatchConfidence.LOW
        else -> null
    }
}
