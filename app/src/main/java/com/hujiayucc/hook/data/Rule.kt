package com.hujiayucc.hook.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.SerializationFeature
import com.hujiayucc.hook.data.Data.mapper

/** 规则 */
data class Rule(
    /** 应用名称 */
    @JsonProperty("name") val name: String,
    /** 应用包名 */
    @JsonProperty("packageName") val packageName: String,
    /** 行为参数列表 [Item]中的 [Item.action]传入[com.hujiayucc.hook.Click] 或 [com.hujiayucc.hook.Hook] */
    @JsonProperty("items") val items: ArrayList<Item>
) {
    override fun toString(): String {
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        return mapper.writeValueAsString(this)
    }
}
