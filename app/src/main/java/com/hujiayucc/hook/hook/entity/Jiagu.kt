package com.hujiayucc.hook.hook.entity

enum class Jiagu(val packageName: String, val type: String) {
    Jiagu360("com.stub.StubApp","360加固"),
    Tencent("com.wrapper.proxyapplication.WrapperProxyApplication","腾讯御安全"),
    NETEASE("com.netease.nis.wrapper.MyApplication","网易易盾"),;
}