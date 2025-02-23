@file:Suppress("EnumEntryName")

package com.hujiayucc.hook.data

import java.lang.Boolean.TYPE as BooleanType
import java.lang.Byte.TYPE as ByteType
import java.lang.Character.TYPE as CharType
import java.lang.Double.TYPE as DoubleType
import java.lang.Float.TYPE as FloatType
import java.lang.Integer.TYPE as IntType
import java.lang.Long.TYPE as LongType
import java.lang.Short.TYPE as ShortType

/**
 * 方法类型
 * @param type 类型包含常见基本类型和引用类型
 * @author hujiayucc
 * @since 2025/2/5
 */
enum class ResultType(
    val type: String
) {
    boolean("boolean"),
    byte("byte"),
    short("short"),
    char("char"),
    int("int"),
    long("long"),
    float("float"),
    double("double"),
    String("String"),
    Object("object"),
    void("void");

    companion object {
        const val TYPE_NAME = "type"
        fun fromString(type: String): ResultType {
            return when (type) {
                "boolean" -> boolean
                "byte" -> byte
                "short" -> short
                "char" -> char
                "int" -> int
                "long" -> long
                "float" -> float
                "double" -> double
                "String" -> String
                "Object" -> Object
                "void" -> void
                else -> throw IllegalArgumentException("Unknown type: $type")
            }
        }

        /** 类型映射 */
        private val typeMap: Map<String, Class<*>> = mapOf(
            "boolean" to BooleanType,
            "byte" to ByteType,
            "short" to ShortType,
            "char" to CharType,
            "int" to IntType,
            "long" to LongType,
            "float" to FloatType,
            "double" to DoubleType,
            "String" to String::class.java,
            "Object" to Any::class.java,
            "void" to Void.TYPE
        )

        /** 将 MethodType 转换为对应的 Java Class 对象 */
        fun ResultType.toJavaClass(): Class<*> {
            return when (this) {
                boolean -> BooleanType
                byte -> ByteType
                short -> ShortType
                char -> CharType
                int -> IntType
                long -> LongType
                float -> FloatType
                double -> DoubleType
                String -> String::class.java
                Object -> Any::class.java
                void -> Void.TYPE
                else -> throw IllegalArgumentException("Unknown type: $name")
            }
        }
    }
}