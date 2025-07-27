package com.hujiayucc.hook.utils

import android.content.Context
import dalvik.system.DexFile
import java.io.IOException

object AnnotationScanner {
    /**
     * 扫描指定包名下包含特定注解的类
     *
     * @param context         上下文
     * @param targetPackage   要扫描的包名（如："com.hujiayucc.xxx"）
     * @param annotationClass 要查找的注解类型（如：Annotation.class或Annotation::class.java）
     * @return 包含注解的类集合（Class对象）
     */
    fun scanClassesWithAnnotation(
        context: Context,
        targetPackage: String,
        annotationClass: Class<out Annotation>
    ): Set<Class<*>> {
        val result: MutableSet<Class<*>> = HashSet()
        try {
            val classLoader = context.classLoader

            val apkPath = context.applicationInfo.sourceDir
            val dexFile = DexFile.loadDex(apkPath, null, 0)

            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                val className = entries.nextElement()

                if (!className.startsWith(targetPackage)) continue

                try {
                    val clazz = Class.forName(className, false, classLoader)

                    if (clazz.isAnnotationPresent(annotationClass)) {
                        result.add(clazz)
                    }

                    for (constructor in clazz.declaredConstructors) {
                        if (constructor.isAnnotationPresent(annotationClass)) {
                            result.add(clazz)
                            break
                        }
                    }
                } catch (_: Exception) {
                }
            }
            dexFile.close()
        } catch (e: IOException) {
            throw RuntimeException("Dex文件扫描失败", e)
        }
        return result
    }
}