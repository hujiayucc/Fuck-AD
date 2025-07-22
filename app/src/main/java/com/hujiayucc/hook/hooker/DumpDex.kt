package com.hujiayucc.hook.hooker

import android.content.Context
import android.widget.Toast
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.hujiayucc.hook.HookEntry.Companion.classLoader
import org.luckypray.dexkit.DexKitBridge
import java.io.File

class DumpDex(private val context: Context) : YukiBaseHooker() {

    override fun onHook() {
        val path = context.externalCacheDir?.absolutePath?.replace("/cache", "/Fuck AD") ?: ""
        val outPath = File(path)
        if (outPath.absolutePath.contains("com.hujiayucc.hook")) return
        if (!outPath.exists()) outPath.mkdirs()
        if (outPath.listFiles()?.isNotEmpty() == true) return
        Toast.makeText(context, "正在导出DEX", Toast.LENGTH_SHORT).show()
        DexKitBridge.create(classLoader, true).use { bridge ->
            bridge.exportDexFile(outPath.absolutePath)
            bridge.close()
            if (outPath.listFiles()?.isNotEmpty() == true) {
                Toast.makeText(
                    context,
                    "DEX文件已保存至： ${outPath.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}