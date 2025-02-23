package com.hujiayucc.hook.data

/**
 * 动作
 *
 * @author hujiayucc
 *
 * @since 2025/2/5
 */
class Action {
    companion object {
        /** 点击 */
        fun LinkedHashMap<*, *>.toClickerAction(): Clicker {
            return Clicker(
                activity = this["activity"] as String,
                view = this["view"] as String,
                sleep = this["sleep"] as Int,
                isRegex = this["isRegex"] as Boolean? == true,
            )
        }

        /** Hook钩子 */
        fun LinkedHashMap<*, *>.toHookerAction(): Hooker {
            return Hooker(
                className = this["className"] as String,
                methodName = this["methodName"] as String,
                resultType = ResultType.fromString(this["resultType"] as String),
                methodType = this["methodType"] as? MethodType ?: MethodType.before,
                methodReturn = this["methodReturn"],
                methodParamCount = this["methodParam"] as? Int ?: 0
            )
        }

        /** 转换为Action [Clicker], [Hooker]，[String] 为返回 activity，[Int] 为 sleep */
        inline fun <reified T : Any> Any.toAction(): T {
            val action = this as LinkedHashMap<*, *>
            return when (T::class) {
                Clicker::class -> toClickerAction() as T
                Hooker::class -> toHookerAction() as T
                String::class -> action["activity"] as T
                Int::class -> action["sleep"] as T
                else -> action as T
            }
        }
    }
}