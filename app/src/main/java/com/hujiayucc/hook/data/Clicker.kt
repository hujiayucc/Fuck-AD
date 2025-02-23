package com.hujiayucc.hook.data

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 点击器
 *
 * @author hujiayucc
 * @since 2025/2/5
 */
data class Clicker(
    /** 待点击的页面 */
    @JsonProperty("activity") val activity: String,
    /**
     * 待点击的控件包括 [android.widget.TextView] 和 [android.widget.Button]
     *
     * 示例：
     *
     * 可填入 组件id [android.view.View.getId] 和 组件text [android.widget.TextView.getText]
     *
     */
    @JsonProperty("view") val view: String,
    /** 延迟点击动作 */
    @JsonProperty("sleep") val sleep: Int = 0,
    /** 是否正则 */
    @JsonProperty("isRegex") val isRegex: Boolean = false
)
