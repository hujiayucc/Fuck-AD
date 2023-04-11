//
// Created by hujiayucc on 2023/4/6.
// 别被指针绕晕了
//
#include <string>
#include "Data.h"

#ifndef FUCK_AD_FUCK_AD_H
jobject MD5(JNIEnv*, const char *);
using namespace std;
using namespace Data;
#define FUCK_AD_FUCK_AD_H

void initHotFix(JNIEnv* env, jobject thiz) {
    jclass clazz = env->FindClass(File.data());
    jclass hoxFixClass = env->FindClass(HotFixUtils.data());
    jstring oldDexName = env->NewStringUTF(dexName1.data());
    jmethodID filejmethodId = env->GetMethodID(clazz,init.data(),filejmethodid.data());
    jobject dex_path = env->GetStaticObjectField(hoxFixClass,env->GetStaticFieldID(hoxFixClass,DEX_FILE.data(),File_Sig.data()));
    jobject file = env->NewObject(clazz, filejmethodId,dex_path,oldDexName);
    if (env->CallBooleanMethod(file, env->GetMethodID(clazz,exists.data(),Boolean_Sig.data()))) {
        jclass update = env->FindClass(Update.data());
        jmethodID deleteOldId = env->GetMethodID(update,deleteOld.data(),Delete_Sig.data());
        env->CallVoidMethod(env->NewObject(update,env->GetMethodID(update,init.data(),Void_Sig.data())),deleteOldId, dex_path);
        env->DeleteLocalRef(update);
    }

    if (!env->CallBooleanMethod(dex_path,env->GetMethodID(clazz,exists.data(),Boolean_Sig.data()))) {
        jmethodID create = env->GetMethodID(clazz,mkdirs.data(),Boolean_Sig.data());
        env->CallBooleanMethod(dex_path,create);
    }

    jmethodID getApplication = env->GetMethodID(env->FindClass(XYApplication.data()),
                                                getApplicationContextF.data(),Context_Sig.data());
    jobject appContext = env->CallObjectMethod(thiz,getApplication);
    jmethodID getClassLoaderj = env->GetMethodID(env->GetObjectClass(appContext),Data::getClassLoader.data(),
                                                ClassLoader.data());
    jobject classLoader = env->CallObjectMethod(appContext,getClassLoaderj);

    env->CallVoidMethod(env->NewObject(hoxFixClass,env->GetMethodID(hoxFixClass,init.data(),Void_Sig.data())),
                        env->GetMethodID(hoxFixClass,doHotFix.data(),ClassLoaderV.data()), classLoader);
    env->DeleteLocalRef(classLoader);
    env->DeleteLocalRef(appContext);
    env->DeleteLocalRef(file);
    env->DeleteLocalRef(dex_path);
    env->DeleteLocalRef(oldDexName);
    env->DeleteLocalRef(hoxFixClass);
    env->DeleteLocalRef(clazz);
}

jobject MD5(JNIEnv* env, const char * text) {
    jclass clazz = env->FindClass(Data::Data.data());
    jmethodID jmethodId = env->GetMethodID(clazz,md5.data(),md5_Sig.data());
    jobject data = env->NewObject(clazz,env->GetMethodID(clazz,init.data(),Void_Sig.data()));
    jstring jstring1 = env->NewStringUTF(text);
    jclass jclass1 = env->GetObjectClass(jstring1);
    jmethodID jmethodId1 = env->GetMethodID(jclass1,getBytes.data(), byte_Sig.data());
    return env->CallObjectMethod(data,jmethodId, env->CallObjectMethod(jstring1,jmethodId1));
}

#endif //FUCK_AD_FUCK_AD_H
