package com.hujiayucc.hook.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    static {
        System.loadLibrary("fuck_ad");
    }
    @Override
    public native void onReceive(Context context, Intent intent);
}
