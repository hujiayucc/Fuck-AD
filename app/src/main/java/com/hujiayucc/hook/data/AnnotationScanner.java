package com.hujiayucc.hook.data;

import android.content.Context;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import dalvik.system.DexFile;

public class AnnotationScanner {

    /**
     * 扫描指定包名下包含特定注解的类
     *
     * @param context         上下文
     * @param targetPackage   要扫描的包名（如："com.hujiayucc.xxx"）
     * @param annotationClass 要查找的注解类型（如：Annotation.class或Annotation::class.java）
     * @return 包含注解的类集合（Class对象）
     */
    public static Set<Class<?>> scanClassesWithAnnotation(
            Context context,
            String targetPackage,
            Class<? extends Annotation> annotationClass
    ) {
        Set<Class<?>> result = new HashSet<>();
        try {
            // 1. 获取当前应用的ClassLoader
            ClassLoader classLoader = context.getClassLoader();

            // 2. 获取APK文件路径并加载Dex文件
            String apkPath = context.getApplicationInfo().sourceDir;
            DexFile dexFile = DexFile.loadDex(apkPath, null, 0);

            // 3. 遍历Dex文件中的所有类名
            Enumeration<String> entries = dexFile.entries();
            while (entries.hasMoreElements()) {
                String className = entries.nextElement();

                // 4. 过滤目标包名下的类
                if (!className.startsWith(targetPackage)) continue;

                try {
                    // 5. 加载类对象（不初始化）
                    Class<?> clazz = Class.forName(className, false, classLoader);

                    // 6. 检查类是否包含指定注解
                    if (clazz.isAnnotationPresent(annotationClass)) {
                        result.add(clazz);
                    }

                    // 7. 检查构造函数的注解
                    for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                        if (constructor.isAnnotationPresent(annotationClass)) {
                            result.add(clazz);
                            break; // 避免重复添加
                        }
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // 忽略加载失败的类
                }
            }
            dexFile.close();
        } catch (IOException e) {
            throw new RuntimeException("Dex文件扫描失败", e);
        }
        return result;
    }
}