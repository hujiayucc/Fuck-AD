package com.hujiayucc.hook.ui.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.hujiayucc.hook.ui.base.BaseActivity;

@SuppressLint("MissingSuperCall")
public class MainActivity extends BaseActivity {
    public static String searchText = "";
    static {
        System.loadLibrary("fuck_ad");
    }

    @Override
    protected native void onCreate(@Nullable Bundle savedInstanceState);
}
