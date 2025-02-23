package com.hujiayucc.hook.data

import com.fasterxml.jackson.annotation.JsonProperty

/** 行为参数 */
data class Item(
    /** 行为名称 */
    @JsonProperty("name") val name: String,
    /** 行为解释 */
    @JsonProperty("desc") val desc: String,
    /** 应用版本名 */
    @JsonProperty("versionName") val versionName: String,
    /** 应用版本号 */
    @JsonProperty("versionCode") val versionCode: Int,
    /** 行为类型 [Type] */
    @JsonProperty("type") val type: Type,
    /** 行为脚本，需为 [Clicker] 和 [Hooker]，可以使用 [Action.toAction] */
    @JsonProperty("action") val action: Any
)
