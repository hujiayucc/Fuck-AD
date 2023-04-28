package com.hujiayucc.hook.utils

import org.json.JSONObject

object FormatJson {
    fun JSONObject.formatJson(): ByteArray {
        // 计数tab的个数
        var tabNum = 0
        val jsonFormat = StringBuilder()
        var last = 0.toChar()
        for (i in this.toString().indices) {
            val c = toString()[i]
            if (c == '{') {
                tabNum++
                jsonFormat.append(c).append("\n")
                jsonFormat.append(getSpaceOrTab(tabNum))
            } else if (c == '}') {
                tabNum--
                jsonFormat.append("\n")
                jsonFormat.append(getSpaceOrTab(tabNum))
                jsonFormat.append(c)
            } else if (c == ',') {
                jsonFormat.append(c).append("\n")
                jsonFormat.append(getSpaceOrTab(tabNum))
            } else if (c == ':') {
                jsonFormat.append(c).append(" ")
            } else if (c == '[') {
                tabNum++
                val next = toString()[i + 1]
                if (next == ']') {
                    jsonFormat.append(c)
                } else {
                    jsonFormat.append(c).append("\n")
                    jsonFormat.append(getSpaceOrTab(tabNum))
                }
            } else if (c == ']') {
                tabNum--
                if (last == '[') {
                    jsonFormat.append(c)
                } else {
                    jsonFormat.append("\n").append(getSpaceOrTab(tabNum)).append(c)
                }
            } else {
                jsonFormat.append(c)
            }
            last = c
        }
        return jsonFormat.toString().toByteArray(Charsets.UTF_8)
    }

    private fun getSpaceOrTab(tabNum: Int): String {
        val sbTab = StringBuilder()
        for (i in 0 until tabNum) {
            sbTab.append('\t')
        }
        return sbTab.toString()
    }
}