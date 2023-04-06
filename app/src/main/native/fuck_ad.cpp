//
// Created by hujiayucc on 2023/4/6.
// 别被指针绕晕了
//

#include <jni.h>
#include "fuck_ad.h"

extern "C"
JNIEXPORT void JNICALL
Java_com_hujiayucc_hook_application_XYApplication_onCreate(JNIEnv* env,jobject thiz) {
    jclass superClass = env->GetSuperclass(env->GetObjectClass(thiz));
    jmethodID onCreate = env->GetMethodID(superClass,"onCreate","()V");
    env->CallNonvirtualVoidMethod(thiz,superClass,onCreate);
    jmethodID registerReceiver = env->GetMethodID(superClass,
              "registerReceiver","(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;");
    jmethodID sendBroadcast = env->GetMethodID(superClass,"sendBroadcast","(Landroid/content/Intent;)V");
    jclass clazz = env->FindClass("com/hujiayucc/hook/service/BootReceiver");
    jclass clazz1 = env->FindClass("android/content/IntentFilter");
    jclass clazz2 = env->FindClass("android/content/Intent");
    jstring action = env->NewStringUTF("com.hujiayucc.hook.service.StartService");
    jobject filter = env->NewObject(clazz1,env->GetMethodID(clazz1,"<init>",
                   "(Ljava/lang/String;)V"), action);
    jobject intent = env->NewObject(clazz2,env->GetMethodID(clazz2,"<init>", "(Ljava/lang/String;)V"),action);
    jobject bootReceiver = env->NewObject(clazz,env->GetMethodID(clazz,"<init>", "()V"));
    env->CallObjectMethod(thiz,registerReceiver, bootReceiver, filter);
    env->CallVoidMethod(thiz,sendBroadcast,intent);
    env->DeleteLocalRef(filter);
    env->DeleteLocalRef(intent);
    env->DeleteLocalRef(bootReceiver);
    // 加载热更新
    initHotFix(env,thiz);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hujiayucc_hook_service_SkipService_onStartCommand(
        JNIEnv *env, jobject thiz, jobject intent, jint flags, jint start_id
) {
    jclass clazz = env->GetObjectClass(thiz);
    jclass clazz1 = env->FindClass("com/hujiayucc/hook/service/SkipServiceImpl");
    jfieldID jfieldId = env->GetFieldID(clazz,"serviceImpl", "Lcom/hujiayucc/hook/service/SkipServiceImpl;");
    jmethodID jmethodId = env->GetMethodID(clazz1, "<init>", "(Lcom/hujiayucc/hook/service/SkipService;)V");
    jobject serviceImpl = env->NewObject(clazz1, jmethodId,thiz);
    jmethodID refresh = env->GetMethodID(clazz1,"refresh", "()V");
    env->SetObjectField(thiz,jfieldId,serviceImpl);
    env->CallVoidMethod(serviceImpl,refresh);
    env->DeleteLocalRef(serviceImpl);
    env->DeleteLocalRef(clazz1);
    env->DeleteLocalRef(clazz);
    return 1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hujiayucc_hook_service_SkipService_onAccessibilityEvent(JNIEnv *env, jobject thiz, jobject event) {
    jclass clazz = env->GetObjectClass(event);
    jclass clazz1 = env->FindClass("com/hujiayucc/hook/utils/Data");
    jmethodID getApplicationContext = env->GetMethodID(env->GetObjectClass(thiz),"getApplicationContext",
                                                       "()Landroid/content/Context;");
    jobject applicationContext = env->CallObjectMethod(thiz,getApplicationContext);
    jmethodID data = env->GetMethodID(clazz1,"<init>", "()V");
    jstring SERVICE_NAME = env->NewStringUTF("com.hujiayucc.hook.service.SkipService");
    jboolean jboolean1 = env->CallBooleanMethod(env->NewObject(clazz1,data),env->GetMethodID(clazz1,"isAccessibilitySettingsOn",
                        "(Landroid/content/Context;Ljava/lang/String;)Z"),applicationContext,SERVICE_NAME);
    if (jboolean1) {
        jmethodID jmethodId = env->GetMethodID(clazz, "getEventType", "()I");
        jint type = env->CallIntMethod(event, jmethodId);
        switch (type) {
            case 32:
            case 2048:
            case 4194304: {
                jfieldID jfieldId = env->GetFieldID(env->GetObjectClass(thiz),"serviceImpl", "Lcom/hujiayucc/hook/service/SkipServiceImpl;");
                jobject serviceImpl = env->GetObjectField(thiz,jfieldId);
                jmethodID onAccessibilityEvent = env->GetMethodID(env->GetObjectClass(serviceImpl),"onAccessibilityEvent",
                                                                  "(Landroid/view/accessibility/AccessibilityEvent;)V");
                env->CallVoidMethod(serviceImpl,onAccessibilityEvent,event);
                env->DeleteLocalRef(serviceImpl);
            }
            default:
                break;
        }
    }
    env->DeleteLocalRef(SERVICE_NAME);
    env->DeleteLocalRef(applicationContext);
    env->DeleteLocalRef(clazz1);
    env->DeleteLocalRef(clazz);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hujiayucc_hook_service_SkipService_onInterrupt(JNIEnv *env, jobject thiz) {
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID jfieldId = env->GetFieldID(clazz,"serviceImpl", "Lcom/hujiayucc/hook/service/SkipServiceImpl;");
    jobject serviceImpl = env->GetObjectField(thiz,jfieldId);
    jclass clazz1 = env->GetObjectClass(serviceImpl);
    jmethodID jmethodId = env->GetMethodID(clazz1,"onInterrupt", "()V");
    env->CallVoidMethod(serviceImpl,jmethodId);
    env->DeleteLocalRef(clazz1);
    env->DeleteLocalRef(serviceImpl);
    env->DeleteLocalRef(clazz);
}