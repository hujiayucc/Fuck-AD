package com.hujiayucc.hook.update

import android.app.Activity
import android.os.StrictMode
import androidx.appcompat.app.AlertDialog
import com.hujiayucc.hook.hotfix.HotFixUtils.Companion.DEX_DIR
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.system.exitProcess


object Update {
    const val json = "https://fkad.hujiayucc.cn/version"
    fun checkUpdate(): JSONObject? {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val url = URL(json)
            val bufferedReader = BufferedReader(InputStreamReader(url.openStream()))
            val string = StringBuilder()
            var str: String?
            while (bufferedReader.readLine().also { str = it } != null) {
                string.append(str)
            }
            bufferedReader.close()
            return if (string.toString().isNotEmpty()) JSONObject(string.toString()) else null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun Activity.updateHotFix(downloadUrl: String, md5: String) {
        Thread {
            val patchDir = File(filesDir, DEX_DIR)
            val url = URL(downloadUrl)
            val dexFile = url.openStream()
            val oatDir = File(patchDir, "oat")
            val baseDex = File(patchDir, "base.dex")
            if (!baseDex.exists()) baseDex.createNewFile()
            val outputStream = FileOutputStream(baseDex)
            val byte = dexFile.readBytes()
            outputStream.write(byte)
            outputStream.flush()
            outputStream.close()
            val dexMd5 = md5(byte)
            if (oatDir.exists()) {
                val path = Paths.get(oatDir.path)
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
            }
            if (dexMd5.lowercase() == md5.lowercase()) {
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
                            updateHotFix(downloadUrl,md5)
                        }.setNegativeButton("取消") { dialog,_ ->
                            dialog?.dismiss()
                        }.show()
                }
            }
        }.start()
    }

    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
    private fun md5(byte: ByteArray): String {
        val md5: MessageDigest = MessageDigest.getInstance("MD5")
        val bytes: ByteArray = md5.digest(byte)
        val builder = java.lang.StringBuilder()
        for (aByte in bytes) {
            builder.append(Integer.toHexString(0x000000FF and aByte.toInt() or -0x100).substring(6))
        }
        return builder.toString()
    }
}
