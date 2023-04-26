package com.hujiayucc.hook.utils;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.StrictMode;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.highcapable.yukihookapi.hook.factory.YukiHookFactoryKt;
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge;
import com.hujiayucc.hook.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Check {
    public static void device(Activity activity) {
        Context context = activity.getApplication().getApplicationContext();
        YukiHookPrefsBridge prefs = YukiHookFactoryKt.prefs(context,"");
        AtomicLong qq = new AtomicLong(prefs.getLong("deviceQQ", 0));
        if (qq.get() == 0) {
            EditText editText = new EditText(context);
            editText.setInputType(EditorInfo.TYPE_CLASS_NUMBER);
            editText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            editText.setHint("请输入绑定QQ");
            editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
            AlertDialog alertDialog = new AlertDialog.Builder(activity).setCancelable(false).setTitle("绑定设备").setView(editText)
                    .setPositiveButton("确定",null)
                    .setNegativeButton("退出",null)
                    .setNeutralButton("加入群聊", null).create();
            alertDialog.show();

            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener((v -> {
                if (editText.getText().length() < 6) return;
                qq.set(Long.parseLong(editText.getText().toString()));
                new Thread(() -> b(activity,alertDialog,qq.get(),prefs)).start();
            }));

            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v -> {
                activity.finish();
                new Handler().postDelayed(() -> System.exit(0),500);
            });

            alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Data.QQ_GROUP));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, activity.getString(R.string.failed_to_open_qq), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            new Thread(() -> a(activity, qq.get(),prefs)).start();
        }
    }

    private static void a(Activity activity, long qq, YukiHookPrefsBridge prefs) {
        Context context = activity.getApplicationContext();
        String id = Data.INSTANCE.getDeviceId(context);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://fkad.hujiayucc.cn/info?id=" + id)
                .get()
                .build();
        try {
            Response response = client.newCall(request).execute();
            assert response.body() != null;
            JSONObject jsonObject = new JSONObject(new String(response.body().bytes()));
            if (jsonObject.getInt("code") == 200) {
                JSONObject message = jsonObject.getJSONObject("message");
                if (!message.getString("deviceId").equals(id) || message.getLong("qq") != qq) {
                    prefs.edit().putLong("deviceQQ",0);
                    prefs.edit().apply();
                    AtomicLong qql = new AtomicLong(0);
                    EditText editText = new EditText(context);
                    editText.setInputType(EditorInfo.TYPE_CLASS_NUMBER);
                    editText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    editText.setHint("请输入绑定QQ");
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
                    activity.runOnUiThread(() -> {
                        AlertDialog alertDialog = new AlertDialog.Builder(activity).setCancelable(false).setTitle("绑定设备").setView(editText)
                                .setPositiveButton("确定",null)
                                .setNegativeButton("退出",null)
                                .setNeutralButton("加入群聊", null).create();
                        alertDialog.show();

                        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener((v -> {
                            if (editText.getText().length() < 6) return;
                            qql.set(Long.parseLong(editText.getText().toString()));
                            Toast.makeText(context, "请等待服务器响应", Toast.LENGTH_SHORT).show();
                            new Thread(() -> b(activity,alertDialog,qql.get(),prefs)).start();
                        }));

                        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v -> {
                            activity.finish();
                            new Handler().postDelayed(() -> System.exit(0),500);
                        });

                        alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Data.QQ_GROUP));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                context.startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(context, activity.getString(R.string.failed_to_open_qq), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                }
            } else if (jsonObject.getInt("code") == 404) {
                prefs.edit().putLong("deviceQQ",0);
                prefs.edit().apply();
                AtomicLong qql = new AtomicLong(0);
                EditText editText = new EditText(context);
                editText.setInputType(EditorInfo.TYPE_CLASS_NUMBER);
                editText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                editText.setHint("请输入绑定QQ");
                editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
                activity.runOnUiThread(()-> {
                    AlertDialog alertDialog = new AlertDialog.Builder(activity).setCancelable(false).setTitle("绑定设备").setView(editText)
                            .setPositiveButton("确定",null)
                            .setNegativeButton("退出",null)
                            .setNeutralButton("加入群聊", null).create();
                    alertDialog.show();

                    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener((v -> {
                        if (editText.getText().length() < 6) return;
                        qql.set(Long.parseLong(editText.getText().toString()));
                        Toast.makeText(context, "请等待服务器响应", Toast.LENGTH_SHORT).show();
                        new Thread(() -> b(activity,alertDialog,qql.get(),prefs)).start();
                    }));

                    alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v -> {
                        activity.finish();
                        new Handler().postDelayed(() -> System.exit(0),500);
                    });

                    alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Data.QQ_GROUP));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            context.startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(context, activity.getString(R.string.failed_to_open_qq), Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
        } catch (IOException | JSONException e) {
            activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                    .setMessage(e.getMessage())
                    .setPositiveButton("关闭",((dialog1, which) -> dialog1.dismiss()))
                    .show());
        }
    }

    private static void b(Activity activity, AlertDialog dialog, long qq, YukiHookPrefsBridge prefs) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Context context = activity.getApplicationContext();
        MediaType type = MediaType.parse("application/json; charset=utf-8");
        String id = Data.INSTANCE.getDeviceId(context);
        Map<String, Object> map = new HashMap<>();
        map.put("qq", qq);
        map.put("id", id);
        JSONObject json = new JSONObject(map);
        //noinspection deprecation
        RequestBody body = RequestBody.create(type,json.toString());
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://fkad.hujiayucc.cn/info")
                .post(body)
                .build();
        try {
            Response response = client.newCall(request).execute();
            assert response.body() != null;
            JSONObject jsonObject = new JSONObject(new String(response.body().bytes()));
            int code = jsonObject.getInt("code");
            if (code == 200 && jsonObject.getString("message").equals("success") ||
                    code == 201 && jsonObject.getJSONObject("message").getString("deviceId").equals(id)) {
                prefs.edit().putLong("deviceQQ",qq);
                prefs.edit().apply();
                dialog.dismiss();
            } else if (code == 201 && !jsonObject.getJSONObject("message").getString("deviceId").equals(id)) {
                activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setMessage("该QQ已被其他设备绑定，如你是QQ主人或需要换绑请联系作者。")
                        .setPositiveButton("关闭",((dialog1, which) -> dialog1.dismiss()))
                        .show());
            } else {
                activity.runOnUiThread(() -> {
                    try {
                        new AlertDialog.Builder(activity)
                                .setMessage(jsonObject.getString("message"))
                                .setPositiveButton("关闭",((dialog1, which) -> dialog1.dismiss()))
                                .show();
                    } catch (JSONException e) {
                        new AlertDialog.Builder(activity)
                                .setMessage(e.getMessage())
                                .setPositiveButton("关闭",((dialog1, which) -> dialog1.dismiss()))
                                .show();
                    }
                });
            }
        } catch (IOException | JSONException e) {
            activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                    .setMessage(e.getMessage())
                    .setPositiveButton("关闭",((dialog1, which) -> dialog1.dismiss()))
                    .show());
        }
    }
}