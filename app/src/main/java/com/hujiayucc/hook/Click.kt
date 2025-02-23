package com.hujiayucc.hook

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.android.TextViewClass
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.hujiayucc.hook.data.Clicker
import com.hujiayucc.hook.data.Data.ACTION
import com.hujiayucc.hook.service.IClickService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/** 执行点击 */
class Click(val clickers: List<Clicker>) : YukiBaseHooker() {
    private val ints = IntArray(2)

    override fun onHook() {
        ViewClass.method { name = "setOnClickListener" }.hook { after { hook() } }
        TextViewClass.method {
            name = "setText"
            paramCount = 1
        }.hook { after { hook() } }
    }

    /** 处理点击 */
    @SuppressLint("CheckResult")
    private fun HookParam.hook() {
        Observable.fromIterable(clickers)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { clicker ->
                val intValue = if (clicker.view.startsWith("0x")) clicker.view.substring(2)
                    .toIntOrNull(16) else clicker.view.toIntOrNull()
                val instance = instance<View>()
                val sleep = clicker.sleep
                Handler(Looper.getMainLooper()).postDelayed({
                    when {
                        instance is Button || instance is TextView -> {
                            if (intValue != null && instance.id == intValue) {
                                instance.isEnabled = true
                                instance.performClick()
                            } else {
                                if (!clicker.isRegex) {
                                    instance.run(clicker.activity, clicker.view)
                                } else {
                                    instance.run(clicker)
                                }
                            }
                        }

                        else -> {
                            if (intValue != null && instance.id == intValue) {
                                instance.isEnabled = true
                                instance.performClick()
                            }
                        }
                    }
                }, sleep.toLong())
            }
            .subscribe(
                { /* Toast.makeText(instance<View>().context, "Clicker处理成功", Toast.LENGTH_SHORT).show() */ },
                { error -> YLog.error("处理Clicker失败", error) }
            )
    }

    private fun TextView.run(activity: String, textValue: String) {
        getLocationOnScreen(ints)
        if (text.toString() == textValue) {
            isEnabled = true
            performClick(activity, ints[0], ints[1])
        }
    }

    private fun TextView.run(clicker: Clicker) {
        val text = text.toString()
        val patternForText = Pattern.compile(clicker.view)
        val matcherForText = patternForText.matcher(text)
        getLocationOnScreen(ints)
        if (matcherForText.find()) {
            if (text == matcherForText.group(1)) {
                isEnabled = true
                performClick(clicker.activity, ints[0], ints[1])
            } else if (text == matcherForText.group(2)) {
                isEnabled = true
                performClick(clicker.activity, ints[0], ints[1])
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun performClick(activity: String, x: Int, y: Int) {
        try {
            appContext?.sendBroadcast(Intent("com.hujiayucc.hook.action.BOOT"))
            GlobalScope.launch(Dispatchers.IO) {
                val intent = Intent(ACTION)
                intent.`package` = "com.hujiayucc.hook"
                appContext?.bindService(
                    intent, connection(
                        activity,
                        "input tap $x $y"
                    ), Context.BIND_AUTO_CREATE
                )
            }
        } catch (e: Exception) {
            YLog.error("模拟点击失败", e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun connection(activity: String, command: String) = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (command.isEmpty()) return
            GlobalScope.launch(Dispatchers.IO) {
                val clickService = IClickService.Stub.asInterface(service)
                try {
                    clickService.click(activity, command)
                } catch (e: Exception) {
                    YLog.error("模拟点击失败", e)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            YLog.debug("模拟点击服务断开")
        }

        override fun onNullBinding(name: ComponentName) {
            super.onNullBinding(name)
            YLog.error("当绑定服务时无法获得代理对象时调用")
        }

        override fun onBindingDied(name: ComponentName) {
            super.onBindingDied(name)
            YLog.error("模拟点击服务断开或销毁")
        }
    }
}