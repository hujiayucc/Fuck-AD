package com.hujiayucc.hook.hooker.util

import java.lang.reflect.Executable
import java.util.Collections

internal class HookHandleRegistry {
    private val handles = Collections.synchronizedList(mutableListOf<RegisteredHook>())

    fun register(owner: String, executable: Executable, handle: Any) {
        handles += RegisteredHook(owner, executable.toGenericString(), handle)
    }

    fun count(owner: String): Int {
        return synchronized(handles) { handles.count { it.owner == owner } }
    }

    fun executables(owner: String): List<String> {
        return synchronized(handles) {
            handles.filter { it.owner == owner }.map { it.executable }
        }
    }

    fun clear(owner: String): Int {
        var removedCount = 0
        synchronized(handles) {
            val iterator = handles.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().owner == owner) {
                    iterator.remove()
                    removedCount++
                }
            }
        }
        return removedCount
    }

    private data class RegisteredHook(
        val owner: String,
        val executable: String,
        val handle: Any
    )
}