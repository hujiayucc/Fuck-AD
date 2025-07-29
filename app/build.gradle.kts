import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hujiayucc.hook"
    compileSdk = 35

    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id",
        "--package-id",
        "0x64"
    )

    defaultConfig {
        applicationId = "com.hujiayucc.hook"
        minSdk = 29
        targetSdk = 35
        versionCode = 8200
        versionName = "2.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            // 开启资源压缩
            isShrinkResources = true
            // 版本后缀
            versionNameSuffix = "-release"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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

    compileOnly(libs.ads.sdk.pro)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}