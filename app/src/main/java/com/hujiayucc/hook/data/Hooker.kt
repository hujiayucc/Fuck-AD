package com.hujiayucc.hook.data

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Hook钩子
 *
 * @author hujiayucc
 * @since 2025/2/5
 */
data class Hooker(
    /** 类名 */
    @JsonProperty("class_name") val className: String,
    /** 方法名 */
    @JsonProperty("method_name") val methodName: String,
    /** 方法Hook类型 */
    @JsonProperty("result_type") val methodType: MethodType,
    /** 方法类型 */
    @JsonProperty("method_type") val resultType: ResultType,
    /** 方法参数 */
    @JsonProperty("method_return") val methodReturn: Any? = null,
    /** 方法参数数量，0 表示不限制 */
    @JsonProperty("method_param_count") val methodParamCount: Int = 0
)