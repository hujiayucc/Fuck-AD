//
// Created by hujiayucc on 2023/4/6.
// 别被指针绕晕了
//

#include <jni.h>
#include "fuck_ad.h"
#include <string>
#include "Data.h"

extern "C"
JNIEXPORT void JNICALL
Java_com_hujiayucc_hook_application_XYApplication_onCreate(JNIEnv* env,jobject thiz) {
    jclass superClass = env->GetSuperclass(env->GetObjectClass(thiz));
    jmethodID onCreate = env->GetMethodID(superClass,onCreateF.data(),Void_Sig.data());
    env->CallNonvirtualVoidMethod(thiz,superClass,onCreate);
    jmethodID registerReceiver = env->GetMethodID(superClass,registerReceiverF.data(),registerReceiver_Sig.data());
    jmethodID sendBroadcast = env->GetMethodID(superClass,sendBroadcastF.data(),sendBroadcast_Sig.data());
    jclass clazz = env->FindClass(BootReceiver.data());
    jclass clazz1 = env->FindClass(IntentFilter.data());
    jclass clazz2 = env->FindClass(Intent.data());
    jstring action = env->NewStringUTF(StartService.data());
    jobject filter = env->NewObject(clazz1,env->GetMethodID(clazz1,init.data(),Intent_Sig.data()), action);
    jobject intent = env->NewObject(clazz2,env->GetMethodID(clazz2,init.data(), Intent_Sig.data()),action);
    jobject bootReceiver = env->NewObject(clazz,env->GetMethodID(clazz,init.data(), Void_Sig.data()));
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
    jclass clazz1 = env->FindClass(SkipServiceImpl.data());
    jfieldID jfieldId = env->GetFieldID(clazz,serviceImplC.data(),serviceImpl_Sig.data());
    jmethodID jmethodId = env->GetMethodID(clazz1, init.data(),SkipServiceImpl_init.data());
    jobject serviceImpl = env->NewObject(clazz1, jmethodId,thiz);
    jmethodID refresh = env->GetMethodID(clazz1,refreshF.data(), Void_Sig.data());
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
    jclass clazz1 = env->FindClass(Data::Data.data());
    jmethodID getApplicationContext = env->GetMethodID(env->GetObjectClass(thiz),getApplicationContextF.data(),
                                                       Context_Sig.data());
    jobject applicationContext = env->CallObjectMethod(thiz,getApplicationContext);
    jmethodID data = env->GetMethodID(clazz1,init.data(), Void_Sig.data());
    jstring SERVICE_NAME = env->NewStringUTF(SkipServiceC.data());
    jboolean jboolean1 = env->CallBooleanMethod(env->NewObject(clazz1,data),env->GetMethodID(clazz1,"isAccessibilitySettingsOn",
                        "(Landroid/content/Context;Ljava/lang/String;)Z"),applicationContext,SERVICE_NAME);
    if (jboolean1) {
        jmethodID jmethodId = env->GetMethodID(clazz,getEventType.data(),Int_Sig.data());
        jint type = env->CallIntMethod(event, jmethodId);
        switch (type) {
            case 32:
            case 2048:
            case 4194304: {
                jfieldID jfieldId = env->GetFieldID(env->GetObjectClass(thiz),serviceImplC.data(),serviceImpl_Sig.data());
                jobject serviceImpl = env->GetObjectField(thiz,jfieldId);
                jmethodID onAccessibilityEvent = env->GetMethodID(env->GetObjectClass(serviceImpl),onAccessibilityEventF.data(),
                                                                  onAccessibilityEvent_Sig.data());
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
    jfieldID jfieldId = env->GetFieldID(clazz,serviceImplC.data(),serviceImpl_Sig.data());
    jobject serviceImpl = env->GetObjectField(thiz,jfieldId);
    jclass clazz1 = env->GetObjectClass(serviceImpl);
    jmethodID jmethodId = env->GetMethodID(clazz1,onInterrupt.data(),Void_Sig.data());
    env->CallVoidMethod(serviceImpl,jmethodId);
    env->DeleteLocalRef(clazz1);
    env->DeleteLocalRef(serviceImpl);
    env->DeleteLocalRef(clazz);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_hujiayucc_hook_ui_activity_MainActivity_onCreate(JNIEnv *env, jobject thiz,jobject saved_instance_state) {
    jclass clazz = env->GetSuperclass(env->GetObjectClass(thiz));
    jmethodID jmethodId = env->GetMethodID(clazz,onCreateF.data(),onCreate_Sig.data());

    jclass check_class = env->FindClass("com/hujiayucc/hook/utils/Check");
    jmethodID checkId = env->GetStaticMethodID(check_class,"device", "(Landroid/app/Activity;)V");
    env->CallStaticVoidMethod(check_class,checkId, thiz);
    env->CallNonvirtualVoidMethod(thiz,clazz,jmethodId,saved_instance_state);
    env->CallVoidMethod(thiz,env->GetMethodID(clazz,initView.data(),Void_Sig.data()));
    env->DeleteLocalRef(clazz);
}