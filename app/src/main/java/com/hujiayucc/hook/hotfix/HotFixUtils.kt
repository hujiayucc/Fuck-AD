package com.hujiayucc.hook.hotfix

import android.annotation.SuppressLint
import android.content.Context
import com.hujiayucc.hook.utils.Log
import dalvik.system.DexClassLoader
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Field

/**
 * 热更新加载类
 *
 * Created by https://github.com/lxbnjupt/HotFixDemo on 2018/10/31.
 * @property Class
 * @author liuxiaobo
 */
class HotFixUtils {
    @Throws(IllegalAccessException::class, NoSuchFieldException::class, ClassNotFoundException::class)
    fun doHotFix(context: Context) {
        val dexFile = File(context.filesDir, DEX_DIR)
        if (!dexFile.exists()) {
            Log.e("热更新补丁目录不存在")
            return
        }
        val odexFile: File = context.getDir(OPTIMIZE_DEX_DIR, Context.MODE_PRIVATE)
        if (!odexFile.exists()) {
            odexFile.mkdir()
        }
        val listFiles: Array<File>? = dexFile.listFiles()
        if (listFiles.isNullOrEmpty()) {
            return
        }
        val dexPath = getPatchDexPath(listFiles)
        val odexPath: String = odexFile.absolutePath
        // 获取PathClassLoader
        val pathClassLoader = context.classLoader as PathClassLoader
        // 构建DexClassLoader，用于加载补丁dex
        val dexClassLoader = DexClassLoader(dexPath, odexPath, null, pathClassLoader)
        // 获取PathClassLoader的Element数组
        val pathElements = getDexElements(pathClassLoader)
        // 获取构建的DexClassLoader的Element数组
        val dexElements = getDexElements(dexClassLoader)
        // 合并Element数组
        val combineElementArray = dexElements?.let { pathElements?.let { it1 -> combineElementArray(it1, it) } }
        // 通过反射，将合并后的Element数组赋值给PathClassLoader中pathList里面的dexElements变量
        combineElementArray?.let { setDexElements(pathClassLoader, it) }
    }

    /**
     * 获取补丁dex文件路径集合
     * @param listFiles
     * @return
     */
    private fun getPatchDexPath(listFiles: Array<File>): String {
        val sb = StringBuilder()
        for (i in listFiles.indices) {
            // 遍历查找文件中.dex .jar .apk .zip结尾的文件
            val file: File = listFiles[i]
            if (file.name.endsWith(DEX_SUFFIX)
                || file.name.endsWith(APK_SUFFIX)
                || file.name.endsWith(JAR_SUFFIX)
                || file.name.endsWith(ZIP_SUFFIX)
            ) {
                if (i != 0 && i != listFiles.size - 1) {
                    // 多个dex路径 添加默认的:分隔符
                    sb.append(File.pathSeparator)
                }
                sb.append(file.absolutePath)
            }
        }
        return sb.toString()
    }

    /**
     * 合并Element数组，将补丁dex放在最前面
     * @param pathElements PathClassLoader中pathList里面的Element数组
     * @param dexElements 补丁dex数组
     * @return 合并之后的Element数组
     */
    private fun combineElementArray(pathElements: Any, dexElements: Any): Any? {
        val componentType = pathElements.javaClass.componentType
        val i: Int = java.lang.reflect.Array.getLength(pathElements) // 原dex数组长度
        val j: Int = java.lang.reflect.Array.getLength(dexElements) // 补丁dex数组长度
        val k = i + j // 总数组长度（原dex数组长度 + 补丁dex数组长度)
        val result: Any? =
            componentType?.let { java.lang.reflect.Array.newInstance(it, k) } // 创建一个类型为componentType，长度为k的新数组
        result?.let { System.arraycopy(dexElements, 0, it, 0, j) } // 补丁dex数组在前
        result?.let { System.arraycopy(pathElements, 0, it, j, i) } // 原dex数组在后
        return result
    }

    /**
     * 获取Element数组
     * @param classLoader 类加载器
     * @return
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @SuppressLint("DiscouragedPrivateApi")
    @Throws(ClassNotFoundException::class, NoSuchFieldException::class, IllegalAccessException::class)
    private fun getDexElements(classLoader: ClassLoader): Any? {
        // 获取BaseDexClassLoader，是PathClassLoader以及DexClassLoader的父类
        val baseDexClassLoaderClazz = Class.forName(NAME_BASE_DEX_CLASS_LOADER)
        // 获取pathList字段，并设置为可以访问
        val pathListField: Field? = baseDexClassLoaderClazz.getDeclaredField(FIELD_PATH_LIST)
        pathListField?.isAccessible = true
        // 获取DexPathList对象
        val dexPathList: Any? = pathListField?.get(classLoader)
        // 获取dexElements字段，并设置为可以访问
        val dexElementsField: Field? = dexPathList?.javaClass?.getDeclaredField(FIELD_DEX_ELEMENTS)
        dexElementsField?.isAccessible = true
        // 获取Element数组，并返回
        return dexElementsField?.get(dexPathList)
    }

    /**
     * 通过反射，将合并后的Element数组赋值给PathClassLoader中pathList里面的dexElements变量
     * @param classLoader PathClassLoader类加载器
     * @param value 合并后的Element数组
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @SuppressLint("DiscouragedPrivateApi")
    @Throws(ClassNotFoundException::class, NoSuchFieldException::class, IllegalAccessException::class)
    private fun setDexElements(classLoader: ClassLoader, value: Any) {
        // 获取BaseDexClassLoader，是PathClassLoader以及DexClassLoader的父类
        val baseDexClassLoaderClazz = Class.forName(NAME_BASE_DEX_CLASS_LOADER)
        // 获取pathList字段，并设置为可以访问
        val pathListField: Field = baseDexClassLoaderClazz.getDeclaredField(FIELD_PATH_LIST)
        pathListField.isAccessible = true
        // 获取DexPathList对象
        val dexPathList: Any? = pathListField.get(classLoader)
        // 获取dexElements字段，并设置为可以访问
        val dexElementsField: Field? = dexPathList?.javaClass?.getDeclaredField(FIELD_DEX_ELEMENTS)
        dexElementsField?.isAccessible = true
        // 将合并后的Element数组赋值给dexElements变量
        dexElementsField?.set(dexPathList, value)
    }

    companion object {
        private const val NAME_BASE_DEX_CLASS_LOADER = "dalvik.system.BaseDexClassLoader"
        private const val FIELD_DEX_ELEMENTS = "dexElements"
        private const val FIELD_PATH_LIST = "pathList"
        private const val DEX_SUFFIX = ".dex"
        private const val APK_SUFFIX = ".apk"
        private const val JAR_SUFFIX = ".jar"
        private const val ZIP_SUFFIX = ".zip"
        const val DEX_DIR = "patch"
        private const val OPTIMIZE_DEX_DIR = "odex"
    }
}