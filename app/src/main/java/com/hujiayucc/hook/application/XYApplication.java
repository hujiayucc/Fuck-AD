package com.hujiayucc.hook.application;

import android.annotation.SuppressLint;

import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication;

@SuppressLint("MissingSuperCall")
public class XYApplication extends ModuleApplication {
    static {
        System.loadLibrary("fuck_ad");
    }

    public native void onCreate();
}