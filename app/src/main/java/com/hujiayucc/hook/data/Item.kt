package com.hujiayucc.hook.data

import android.graphics.drawable.Drawable
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Item @JsonCreator constructor(
    @JsonProperty("appName") val appName: String,
    @JsonProperty("packageName") val packageName: String,
    @JsonProperty("versionList") val versionList: List<String>,
    @JsonProperty("action") val action: String,
    @JsonProperty("appIcon") var appIcon: Drawable? = null
)

