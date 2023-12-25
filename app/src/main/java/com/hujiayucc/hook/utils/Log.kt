package com.hujiayucc.hook.utils

import com.highcapable.yukihookapi.hook.log.*
import com.hujiayucc.hook.BuildConfig.TAG

object Log {
    fun d(msg: String) {
        YLog.debug(msg = "------$msg------", tag = TAG)
    }

    fun e(msg: String) {
        YLog.error(msg = "------$msg------", tag = TAG)
    }

    fun i(msg: String) {
        YLog.info(msg = "------$msg------", tag = TAG)
    }

    fun w(msg: String) {
        YLog.warn(msg = "------$msg------", tag = TAG)
    }

    fun d(TAG: String, msg: String) {
        YLog.debug(msg = "------$msg------", tag = TAG)
    }

    fun e(TAG: String, msg: String) {
        YLog.error(msg = "------$msg------", tag = TAG)
    }

    fun i(TAG: String, msg: String) {
        YLog.info(msg = "------$msg------", tag = TAG)
    }

    fun w(TAG: String, msg: String) {
        YLog.warn(msg = "------$msg------", tag = TAG)
    }
}