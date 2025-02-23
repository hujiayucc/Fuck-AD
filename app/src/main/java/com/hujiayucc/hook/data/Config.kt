package com.hujiayucc.hook.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.hujiayucc.hook.data.Data.mapper

data class Config(
    /** 配置版本 */
    @JsonProperty("version") val version: Int,
    /** 规则集 */
    @JsonProperty("rules") val rules: ArrayList<Rule>
) {
    /** 转为JSON字符串 */
    override fun toString(): String {
        return mapper.writeValueAsString(this)
    }
}
