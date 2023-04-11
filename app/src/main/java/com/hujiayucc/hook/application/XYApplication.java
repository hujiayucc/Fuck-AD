package com.hujiayucc.hook.application;

import android.annotation.SuppressLint;

import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication;


public class XYApplication extends ModuleApplication {
    static {
        System.loadLibrary("fuck_ad");
    }

    @SuppressLint("MissingSuperCall")
    public native void onCreate();
}