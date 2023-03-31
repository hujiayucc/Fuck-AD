package com.hujiayucc.hook.utils

import com.hujiayucc.hook.utils.HotFixUtils.Companion.DEX_SUFFIX
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object FileHelper {
    private const val BUFFER_SIZE = 8192

    fun unzip(sourceFile: String, destinationFolder: String): Boolean {
        var zis: ZipInputStream? = null
        try {
            zis = ZipInputStream(BufferedInputStream(FileInputStream(sourceFile)))
            var ze: ZipEntry
            var count: Int
            val buffer = ByteArray(BUFFER_SIZE)
            while (zis.nextEntry.also { ze = it } != null) {
                var fileName: String = ze.name
                if (!fileName.endsWith(DEX_SUFFIX)) continue
                fileName = fileName.substring(fileName.indexOf("/") + 1)
                val file = File(destinationFolder, fileName)
                val dir: File? = if (ze.isDirectory) file else file.parentFile
                if (dir?.isDirectory == false && !dir.mkdirs()) throw FileNotFoundException("Invalid path: " + dir.absolutePath)
                if (ze.isDirectory) continue
                val out = FileOutputStream(file)
                out.use {
                    while (zis.read(buffer).also { it1 -> count = it1 } != -1) it.write(buffer, 0, count)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            if (zis != null) try {
                zis.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return true
    }
}