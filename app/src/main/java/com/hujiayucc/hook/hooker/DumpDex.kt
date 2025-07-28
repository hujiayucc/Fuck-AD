package com.hujiayucc.hook.hooker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import org.luckypray.dexkit.DexKitBridge
import java.io.File

class DumpDex(private val context: Context) : YukiBaseHooker() {

    override fun onHook() {
        val path = context.externalCacheDir?.absolutePath?.replace("/cache", "/Fuck AD") ?: ""
        val outPath = File(path)
        if (outPath.absolutePath.contains("com.hujiayucc.hook")) return
        if (!outPath.exists()) outPath.mkdirs()
        if (outPath.listFiles()?.isNotEmpty() == true) return
        showToast("正在保存DEX文件...")
        DexKitBridge.create(appClassLoader!!, true).use { bridge ->
            bridge.exportDexFile(outPath.absolutePath)
            bridge.close()
            if (outPath.listFiles()?.isNotEmpty() == true) {
                showToast("DEX文件已保存至： ${outPath.absolutePath}")
            }
        }
    }

    private fun showToast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}