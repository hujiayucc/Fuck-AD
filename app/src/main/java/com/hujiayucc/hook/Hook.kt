package com.hujiayucc.hook

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.data.Hooker
import com.hujiayucc.hook.data.MethodType
import com.hujiayucc.hook.data.ResultType
import com.hujiayucc.hook.data.ResultType.*

/** 执行Hook */
class Hook(val hookers: List<Hooker>) : YukiBaseHooker() {
    override fun onHook() {
        for (hooker in hookers) {
            hooker.className.toClass().method {
                name = hooker.methodName
                if (hooker.methodParamCount > 0) paramCount = hooker.methodParamCount
            }.hook {
                if (hooker.resultType == void) return@hook
                when (hooker.methodType) {
                    MethodType.before -> {
                        before {
                            result = result(hooker.resultType, hooker.methodReturn)
                        }
                    }

                    MethodType.after -> {
                        after {
                            result = result(hooker.resultType, hooker.methodReturn)
                        }
                    }
                }
            }
        }
    }

    private fun result(type: ResultType, result: Any?): Any? {
        return when (type) {
            boolean -> (result as Boolean)
            byte -> (result as Byte)
            short -> (result as Short)
            char -> (result as Char)
            int -> (result as Int)
            long -> (result as Long)
            float -> (result as Float)
            double -> (result as Double)
            ResultType.String -> result.toString()
            Object -> result
            void -> null
        }
    }
}