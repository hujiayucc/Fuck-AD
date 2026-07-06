import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(17)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val xposedMetadataSourceDir = layout.projectDirectory.dir("src/main/resources/META-INF/xposed")
val xposedHookerRegistryFile = layout.projectDirectory.file(
    "src/main/java/com/hujiayucc/hook/hooker/app/HookerRegistry.kt"
)
val generatedXposedResourcesDir = layout.buildDirectory.dir("xposedResources")
val generateXposedScopeList = tasks.register("generateXposedScopeList") {
    inputs.file(xposedHookerRegistryFile)
    inputs.file(xposedMetadataSourceDir.file("java_init.list"))
    inputs.file(xposedMetadataSourceDir.file("module.prop"))
    outputs.dir(generatedXposedResourcesDir)

    doLast {
        val packageNames = Regex("\"([^\"]+)\"\\s+to\\s+listOf")
            .findAll(xposedHookerRegistryFile.asFile.readText())
            .map { it.groupValues[1] }
            .distinct()
            .sorted()
            .toList()

        check(packageNames.isNotEmpty()) {
            "No Xposed scope packages found in ${xposedHookerRegistryFile.asFile}"
        }

        val xposedOutputDir = generatedXposedResourcesDir.get().dir("META-INF/xposed").asFile
        xposedOutputDir.mkdirs()
        xposedMetadataSourceDir.file("java_init.list").asFile.copyTo(
            xposedOutputDir.resolve("java_init.list"),
            overwrite = true
        )
        xposedMetadataSourceDir.file("module.prop").asFile.copyTo(
            xposedOutputDir.resolve("module.prop"),
            overwrite = true
        )
        xposedOutputDir.resolve("scope.list").writeText(
            packageNames.joinToString(separator = "\n", postfix = "\n")
        )
    }
}

tasks.configureEach {
    if (name in setOf(
            "mergeDebugJavaResource",
            "mergeReleaseJavaResource",
            "processDebugJavaRes",
            "processReleaseJavaRes"
        )
    ) {
        dependsOn(generateXposedScopeList)
    }
}

android {
    namespace = "com.hujiayucc.hook"
    compileSdk = 37

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
            enableV1Signing = false
            enableV2Signing = false
            enableV3Signing = true
            enableV4Signing = true
        }
        
        getByName("debug") {
            enableV1Signing = false
            enableV2Signing = false
            enableV3Signing = true  
            enableV4Signing = true
        }
    }

    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id",
        "--package-id",
        "0x64"
    )

    packaging.resources.merges += "META-INF/xposed/*"
    sourceSets {
        getByName("main") {
            resources.setSrcDirs(listOf(generatedXposedResourcesDir.get().asFile.path))
        }
    }

    defaultConfig {
        applicationId = "com.hujiayucc.hook"
        minSdk = 30
        targetSdk = 37
        versionCode = 10130
        versionName = "3.0.1.3"
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
            
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // 如果没有检测到配置文件，临时降级为 debug 签名以保证能成功编译
                signingConfig = signingConfigs.getByName("debug")
            }
        }

        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            // 版本后缀
            // versionNameSuffix = "-debug"
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
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.dexkit)
    implementation(project(":FuckAD-Author"))
    implementation(project(":gkd-selector"))

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    compileOnly(libs.ads.sdk.pro)
    compileOnly(libs.gdt.union)
    compileOnly(libs.google.mobile.ads)
    compileOnly(libs.applovin.sdk)
    compileOnly(libs.unity.ads)
    compileOnly(libs.vungle.ads)
    compileOnly(libs.levelplay)
    compileOnly(fileTree("libs") { include("ks_adsdk*.aar") })

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}