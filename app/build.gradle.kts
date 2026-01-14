import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    signingConfigs {
        all {
            print("Loading signingConfigs...")
            val properties = rootProject.file("local.properties").inputStream().use {
                Properties().apply { load(it) }
            }
            storeFile = file(properties.getProperty("storeFile"))
            storePassword = properties.getProperty("storePassword")
            keyAlias = properties.getProperty("keyAlias")
            keyPassword = properties.getProperty("keyPassword")
        }
    }

    namespace = "com.hujiayucc.hook"
    compileSdk = 36

    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id",
        "--package-id",
        "0x64"
    )

    defaultConfig {
        applicationId = "com.hujiayucc.hook"
        minSdk = 29
        targetSdk = 36
        versionCode = 8800
        versionName = "2.0.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            // 开启资源压缩
            isShrinkResources = true
            // 版本后缀
            // versionNameSuffix = "-release"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        getByName("debug") {
            isMinifyEnabled = true
            // 开启资源压缩
            isShrinkResources = true
            // 版本后缀
            // versionNameSuffix = "-debug"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    applicationVariants.all {
        outputs.all { output ->
            val baseName = "Fuck AD_$versionName"
            when (output) {
                is ApkVariantOutputImpl -> {
                    output.outputFileName = "$baseName.apk"
                    true
                }
                else -> {false}
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.dexkit)
    implementation(project(":FuckAD-Author"))

    // 基础依赖
    implementation(libs.yuki.api)
    // ❗作为 Xposed 模块使用务必添加，其它情况可选
    compileOnly(libs.xposed.api)
    // ❗作为 Xposed 模块使用务必添加，其它情况可选
    ksp(libs.ksp.xposed)
    // 使用 KavaRef 作为核心反射 API
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)

    compileOnly(libs.ads.sdk.pro)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}