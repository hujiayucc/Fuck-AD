package com.hujiayucc.hook.application;

import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication;

public class XYApplication extends ModuleApplication {
    static {
        System.loadLibrary("fuck_ad");
    }

    public native void onCreate();
}