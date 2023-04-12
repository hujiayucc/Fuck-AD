package com.hujiayucc.hook.utils

import android.app.Activity
import android.os.StrictMode
import androidx.appcompat.app.AlertDialog
import com.hujiayucc.hook.utils.Data.md5
import com.hujiayucc.hook.utils.HotFixUtils.Companion.DEX_FILE
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.system.exitProcess


object Update {
    private const val json = "https://fkad.hujiayucc.cn/version"
    fun checkUpdate(): JSONObject? {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        return try {
            val url = URL(json)
            val bufferedReader = BufferedReader(InputStreamReader(url.openStream()))
            val string = StringBuilder()
            var str: String?
            while (bufferedReader.readLine().also { str = it } != null) {
                string.append(str)
            }
            bufferedReader.close()
            if (string.toString().isNotEmpty()) JSONObject(string.toString()) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun Activity.updateHotFix(info: JSONObject) {
        Thread {
            deleteOld(DEX_FILE)
            val downloadUrl = info.getString("dexUrl")
            val url = URL(downloadUrl)
            val dexFile = url.openStream()
            val fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1, downloadUrl.length)
            val baseDex = File(DEX_FILE, fileName)
            if (!baseDex.exists()) baseDex.createNewFile()
            val outputStream = FileOutputStream(baseDex)
            val byte = dexFile.readBytes()
            outputStream.write(byte)
            outputStream.flush()
            outputStream.close()
            val dexMd5 = md5(byte)
            if (dexMd5.lowercase() == info.getString("dexMd5").lowercase()) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setMessage("下载完成，重启应用生效")
                        .setPositiveButton("重启") { dialog,_ ->
                            dialog?.dismiss()
                            exitProcess(0)
                        }.setNegativeButton("稍后重启") { dialog,_ ->
                            dialog?.dismiss()
                        }.show()
                }
            } else {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setMessage("文件校验失败，是否重新下载？")
                        .setPositiveButton("确定") { dialog,_ ->
                            dialog?.dismiss()
                            updateHotFix(info)
                        }.setNegativeButton("取消") { dialog,_ ->
                            dialog?.dismiss()
                        }.show()
                }
            }
        }.start()
    }

    private fun deleteOld(patchDir: File) {
        val path = Paths.get(patchDir.path)
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
        patchDir.mkdir()
    }
}
