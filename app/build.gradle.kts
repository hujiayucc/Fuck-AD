import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(17)
}

val xposedScopePackagesFile = layout.projectDirectory.file("src/main/xposed-scope/packages.txt")
val xposedHookerRegistryFile = layout.projectDirectory.file(
    "src/main/java/com/hujiayucc/hook/hooker/app/HookerRegistry.kt"
)
val xposedScopeListFile = layout.projectDirectory.file("src/main/resources/META-INF/xposed/scope.list")
val generateXposedScopeList = tasks.register("generateXposedScopeList") {
    inputs.file(xposedScopePackagesFile)
    inputs.file(xposedHookerRegistryFile)
    outputs.file(xposedScopeListFile)

    doLast {
        val packageNames = xposedScopePackagesFile.asFile.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        check(packageNames.isNotEmpty()) {
            "No Xposed scope packages found in ${xposedScopePackagesFile.asFile}"
        }

        val registryPackageNames = Regex("\"([^\"]+)\"\\s+to\\s+listOf")
            .findAll(xposedHookerRegistryFile.asFile.readText())
            .map { it.groupValues[1] }
            .distinct()
            .sorted()
            .toList()

        val missingInScope = registryPackageNames - packageNames.toSet()
        val unknownInScope = packageNames - registryPackageNames.toSet()
        check(missingInScope.isEmpty() && unknownInScope.isEmpty()) {
            buildString {
                if (missingInScope.isNotEmpty()) {
                    appendLine("Missing scope packages: ${missingInScope.joinToString()}")
                }
                if (unknownInScope.isNotEmpty()) {
                    appendLine("Unknown scope packages: ${unknownInScope.joinToString()}")
                }
            }
        }

        xposedScopeListFile.asFile.writeText(
            packageNames.joinToString(separator = "\n", postfix = "\n")
        )
    }
}

tasks.configureEach {
    if (name == "mergeDebugJavaResource" || name == "mergeReleaseJavaResource") {
        dependsOn(generateXposedScopeList)
    }
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

    packaging.resources.merges += "META-INF/xposed/*"

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