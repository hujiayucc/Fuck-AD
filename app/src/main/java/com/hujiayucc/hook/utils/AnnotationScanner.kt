package com.hujiayucc.hook.utils

import android.annotation.SuppressLint
import android.content.Context
import dalvik.system.BaseDexClassLoader
import dalvik.system.DexFile
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

object AnnotationScanner {
    private data class CacheKey(
        val classLoaderIdentity: Int,
        val targetPackage: String,
        val annotationNames: String
    )

    private data class FieldKey(
        val clazz: Class<*>,
        val name: String
    )

    private val classNameCache = ConcurrentHashMap<CacheKey, Set<String>>()
    private val fieldCache = ConcurrentHashMap<FieldKey, Field>()

    /**
     * 扫描指定包名下包含特定注解的类
     *
     * @param context         上下文
     * @param targetPackage   要扫描的包名（如："xxx.xxx.xxx"）
     * @param annotationClasses 要查找的注解类型（如：Annotation.class或Annotation::class.java）
     * @return 包含注解的类集合（Class对象）
     */
    fun scanClassesWithAnnotation(
        context: Context,
        targetPackage: String,
        annotationClasses: Collection<Class<out Annotation>>
    ): Set<Class<*>> {
        return scanClassesWithAnnotation(context.classLoader, targetPackage, annotationClasses)
    }

    /**
     * 扫描指定包名下包含特定注解的类
     *
     * @param classLoader     类加载器
     * @param targetPackage   要扫描的包名（如："xxx.xxx.xxx"）
     * @param annotationClasses 要查找的注解类型（如：Annotation.class或Annotation::class.java）
     * @return 包含注解的类集合（Class对象）
     */
    fun scanClassesWithAnnotation(
        classLoader: ClassLoader,
        targetPackage: String,
        annotationClasses: Collection<Class<out Annotation>>
    ): Set<Class<*>> {
        if (annotationClasses.isEmpty()) return emptySet()

        val annotationNameSet = annotationClasses.asSequence()
            .map { it.name }
            .toSet()
        val annotationKey = annotationNameSet.asSequence()
            .sorted()
            .joinToString(separator = "|")
        val cacheKey = CacheKey(System.identityHashCode(classLoader), targetPackage, annotationKey)

        classNameCache[cacheKey]?.let { cachedClassNames ->
            return loadClassesByName(classLoader, cachedClassNames)
        }

        val matchedClasses = scanMatchedClasses(classLoader, targetPackage, annotationNameSet)
        classNameCache[cacheKey] = matchedClasses.asSequence().map { it.name }.toCollection(LinkedHashSet())
        return matchedClasses
    }

    @Suppress("DEPRECATION")
    private fun scanMatchedClasses(
        classLoader: ClassLoader,
        targetPackage: String,
        annotationNames: Set<String>
    ): Set<Class<*>> {
        val result = LinkedHashSet<Class<*>>()
        extractDexFiles(classLoader).forEach { dexFile ->
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                val className = entries.nextElement()
                if (!className.startsWith(targetPackage)) continue

                val clazz = loadClassOrNull(classLoader, className) ?: continue
                if (clazz.hasAnyTargetAnnotation(annotationNames)) result.add(clazz)
            }
        }
        return result
    }

    private fun loadClassesByName(classLoader: ClassLoader, classNames: Set<String>): Set<Class<*>> {
        val result = LinkedHashSet<Class<*>>(classNames.size)
        classNames.forEach { className ->
            loadClassOrNull(classLoader, className)?.let { result.add(it) }
        }
        return result
    }

    private fun loadClassOrNull(classLoader: ClassLoader, className: String): Class<*>? {
        return try {
            Class.forName(className, false, classLoader)
        } catch (_: Throwable) {
            null
        }
    }

    private fun Class<*>.hasAnyTargetAnnotation(annotationNames: Set<String>): Boolean {
        if (annotations.any { it.annotationClass.java.name in annotationNames }) return true
        return declaredConstructors.any { constructor ->
            constructor.annotations.any { it.annotationClass.java.name in annotationNames }
        }
    }

    private fun extractDexFiles(classLoader: ClassLoader): List<DexFile> {
        if (classLoader !is BaseDexClassLoader) {
            return emptyList()
        }
        return try {
            @SuppressLint("DiscouragedPrivateApi")
            val dexPathList = cachedDeclaredField(BaseDexClassLoader::class.java, "pathList").get(classLoader)
            val dexElements = cachedDeclaredField(dexPathList.javaClass, "dexElements").get(dexPathList) as Array<*>
            dexElements.mapNotNull { element ->
                element ?: return@mapNotNull null
                cachedDeclaredField(element.javaClass, "dexFile").get(element) as? DexFile
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun cachedDeclaredField(clazz: Class<*>, name: String): Field {
        val key = FieldKey(clazz, name)
        return fieldCache.getOrPut(key) {
            clazz.getDeclaredField(name).apply { isAccessible = true }
        }
    }
}