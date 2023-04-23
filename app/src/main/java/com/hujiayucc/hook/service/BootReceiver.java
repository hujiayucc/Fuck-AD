package com.hujiayucc.hook.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.highcapable.yukihookapi.hook.factory.YukiHookFactoryKt;
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge;
import com.hujiayucc.hook.utils.Data;

public class BootReceiver extends BroadcastReceiver {
    static {
        System.loadLibrary("fuck_ad");
    }

    private static final String ACTION = "com.hujiayucc.hook.service.StartService";

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION:
            case Intent.ACTION_BOOT_COMPLETED: {
                YukiHookPrefsBridge prefs = YukiHookFactoryKt.prefs(context,"");
                if (prefs.getLong("qq",0) != 0) Data.INSTANCE.runService(context);
            }
        }
    }
}
