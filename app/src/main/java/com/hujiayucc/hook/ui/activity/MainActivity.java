package com.hujiayucc.hook.ui.activity;

import android.os.Bundle;

import com.hujiayucc.hook.ui.base.BaseActivity;

public class MainActivity extends BaseActivity {
    public static String searchText = "";
    static {
        System.loadLibrary("fuck_ad");
    }

    @Override
    protected native void onCreate(Bundle savedInstanceState);
}
