import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(17)
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
    compileSdk = 37

    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id",
        "--package-id",
        "0x64"
    )

    defaultConfig {
        applicationId = "com.hujiayucc.hook"
        minSdk = 30
        targetSdk = 37
        versionCode = 10001
        versionName = "3.0.0.1"
        buildConfigField("Long", "BUILD_TIME", "${System.currentTimeMillis()}L")

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

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    compileOnly(libs.ads.sdk.pro)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}