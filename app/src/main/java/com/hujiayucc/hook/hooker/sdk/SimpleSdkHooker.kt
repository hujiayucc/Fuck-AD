package com.hujiayucc.hook.hooker.sdk

import com.hujiayucc.hook.hooker.util.Hooker
import java.lang.reflect.Method
import java.lang.reflect.Modifier

abstract class SimpleSdkHooker : Hooker() {
    private val booleanTypes = setOf(Boolean::class.javaPrimitiveType, Boolean::class.java)
    private val voidTypes = setOf(Void.TYPE, Void::class.java)

    protected fun Method.replaceWithDefault() {
        hook {
            replace {
                when (returnType) {
                    in booleanTypes -> false
                    in voidTypes -> null
                    String::class.java -> ""
                    Int::class.javaPrimitiveType, Int::class.java -> 0
                    Long::class.javaPrimitiveType, Long::class.java -> 0L
                    Float::class.javaPrimitiveType, Float::class.java -> 0F
                    Double::class.javaPrimitiveType, Double::class.java -> 0.0
                    Short::class.javaPrimitiveType, Short::class.java -> 0.toShort()
                    Byte::class.javaPrimitiveType, Byte::class.java -> 0.toByte()
                    Char::class.javaPrimitiveType, Char::class.java -> 0.toChar()
                    else -> null
                }
            }
        }
    }

    protected fun Class<*>.hookMethods(vararg names: String) {
        (cachedDeclaredMethods().asSequence() + cachedMethods().asSequence())
            .filter { method ->
                method.name in names &&
                    !Modifier.isAbstract(method.modifiers) &&
                    !Modifier.isNative(method.modifiers)
            }
            .distinctBy { method -> method.toGenericString() }
            .forEach { method -> method.replaceWithDefault() }
    }

    protected fun hookClassMethods(className: String, vararg methodNames: String) {
        className.toClassOrNull()?.hookMethods(*methodNames)
    }
}
