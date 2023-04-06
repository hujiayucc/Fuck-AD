package com.hujiayucc.hook.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class SkipService extends AccessibilityService {
    static {
        System.loadLibrary("fuck_ad");
    }
    public SkipServiceImpl serviceImpl;
    @Override
    public native int onStartCommand(Intent intent, int flags, int startId);
    @Override
    public native void onAccessibilityEvent(AccessibilityEvent event);
    @Override
    public native void onInterrupt();
}
