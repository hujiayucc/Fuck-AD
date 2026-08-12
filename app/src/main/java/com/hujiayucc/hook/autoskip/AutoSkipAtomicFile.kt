package com.hujiayucc.hook.autoskip

import android.util.AtomicFile
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

internal object AutoSkipAtomicFile {
    private val writeLock = Any()

    fun exists(file: File): Boolean {
        return runCatching {
            AtomicFile(file).openRead().use { }
            true
        }.getOrDefault(false)
    }

    fun delete(file: File) {
        synchronized(writeLock) {
            AtomicFile(file).delete()
        }
    }

    fun readText(file: File): String {
        return AtomicFile(file).openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun writeText(file: File, text: String) {
        write(file) { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun writeFrom(file: File, input: InputStream) {
        write(file) { output -> input.copyTo(output) }
    }

    private inline fun write(file: File, writeContent: (java.io.FileOutputStream) -> Unit) {
        synchronized(writeLock) {
            file.parentFile?.mkdirs()
            val atomicFile = AtomicFile(file)
            val output = atomicFile.startWrite()
            try {
                writeContent(output)
                atomicFile.finishWrite(output)
            } catch (error: Throwable) {
                atomicFile.failWrite(output)
                throw error
            }
        }
    }
}