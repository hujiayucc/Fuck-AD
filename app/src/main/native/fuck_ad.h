//
// Created by hujiayucc on 2023/4/6.
// 别被指针绕晕了
//
#include <jni.h>
jobject MD5(JNIEnv*, const char *);
#ifndef FUCK_AD_FUCK_AD_H
#define FUCK_AD_FUCK_AD_H

void initHotFix(JNIEnv* env, jobject thiz) {
    jclass clazz = env->FindClass("java/io/File");

    jclass hoxFixClass = env->FindClass("com/hujiayucc/hook/utils/HotFixUtils");
    jstring oldDexName = env->NewStringUTF("2023.dex");
    jmethodID filejmethodId = env->GetMethodID(clazz,"<init>", "(Ljava/io/File;Ljava/lang/String;)V");
    jobject dex_path = env->GetStaticObjectField(hoxFixClass,env->GetStaticFieldID(hoxFixClass,"DEX_FILE", "Ljava/io/File;"));
    jobject file = env->NewObject(clazz, filejmethodId,dex_path,oldDexName);
    if (env->CallBooleanMethod(file, env->GetMethodID(clazz,"exists", "()Z"))) {
        jclass update = env->FindClass("com/hujiayucc/hook/utils/Update");
        jmethodID deleteOld = env->GetMethodID(update,"deleteOld","(Ljava/io/File;)V");
        env->CallVoidMethod(env->NewObject(update,env->GetMethodID(update,"<init>", "()V")),deleteOld, dex_path);
        env->DeleteLocalRef(update);
    }

    if (!env->CallBooleanMethod(dex_path,env->GetMethodID(clazz,"exists", "()Z"))) {
        jmethodID create = env->GetMethodID(clazz,"mkdirs", "()Z");
        env->CallBooleanMethod(dex_path,create);
    }

    jmethodID getApplication = env->GetMethodID(env->FindClass("com/hujiayucc/hook/application/XYApplication"),
                                                "getApplicationContext","()Landroid/content/Context;");
    jobject appContext = env->CallObjectMethod(thiz,getApplication);
    jmethodID getClassLoader = env->GetMethodID(env->GetObjectClass(appContext),"getClassLoader",
                                                "()Ljava/lang/ClassLoader;");
    jobject classLoader = env->CallObjectMethod(appContext,getClassLoader);

    env->CallVoidMethod(env->NewObject(hoxFixClass,env->GetMethodID(hoxFixClass,"<init>", "()V")),
                        env->GetMethodID(hoxFixClass,"doHotFix","(Ljava/lang/ClassLoader;)V"), classLoader);
    env->DeleteLocalRef(classLoader);
    env->DeleteLocalRef(appContext);
    env->DeleteLocalRef(file);
    env->DeleteLocalRef(dex_path);
    env->DeleteLocalRef(oldDexName);
    env->DeleteLocalRef(hoxFixClass);
    env->DeleteLocalRef(clazz);
}

jobject DEC(JNIEnv* env, jbyteArray datasource) {
    return MD5(env, "hjy20010615.");
}

jobject MD5(JNIEnv* env, const char * text) {
    jclass clazz = env->FindClass("com/hujiayucc/hook/utils/Data");
    jmethodID jmethodId = env->GetMethodID(clazz,"md5", "([B)Ljava/lang/String;");
    jobject data = env->NewObject(clazz,env->GetMethodID(clazz,"<init>", "()V"));
    jstring jstring1 = env->NewStringUTF(text);
    jclass jclass1 = env->GetObjectClass(jstring1);
    jmethodID jmethodId1 = env->GetMethodID(jclass1,"getBytes", "()[B");
    return env->CallObjectMethod(data,jmethodId, env->CallObjectMethod(jstring1,jmethodId1));
}

#endif //FUCK_AD_FUCK_AD_H