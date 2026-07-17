import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(17)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

val useDebugSigningForRelease = !keystorePropertiesFile.exists()
val releaseSigningPropertyNames = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigningConfig = keystorePropertiesFile.isFile &&
    releaseSigningPropertyNames.all { !keystoreProperties.getProperty(it).isNullOrBlank() } &&
    rootProject.file(keystoreProperties.getProperty("storeFile")).isFile
val sourceDateEpoch = providers.environmentVariable("SOURCE_DATE_EPOCH").orNull
val buildTimeMillis = sourceDateEpoch?.let { value ->
    val epochSeconds = requireNotNull(value.toLongOrNull()) {
        "SOURCE_DATE_EPOCH must be an integer number of seconds"
    }
    Math.multiplyExact(epochSeconds, 1_000L)
} ?: System.currentTimeMillis()

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    doLast {
        check(useDebugSigningForRelease || hasReleaseSigningConfig) {
            "When keystore.properties exists, release signing requires storeFile, storePassword, keyAlias, " +
                "keyPassword, and an existing keystore file"
        }
    }
}

@CacheableTask
abstract class GenerateXposedScopeListTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scopesFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageNamePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        val configuredPackageNames = scopesFile.get().asFile.readLines(Charsets.UTF_8)
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        check(configuredPackageNames.isNotEmpty()) {
            "No Xposed scope packages found in ${scopesFile.get().asFile}"
        }
        check(configuredPackageNames.size == configuredPackageNames.distinct().size) {
            "Duplicate Xposed scope packages found in ${scopesFile.get().asFile}"
        }
        check(configuredPackageNames.all(packageNamePattern::matches)) {
            "Invalid Xposed scope package found in ${scopesFile.get().asFile}"
        }
        val packageNames = configuredPackageNames.sorted()
        check(configuredPackageNames == packageNames) {
            "Xposed scope packages must be sorted in ${scopesFile.get().asFile}"
        }

        val scopeList = outputDirectory.get().file("META-INF/xposed/scope.list").asFile
        scopeList.parentFile.mkdirs()
        scopeList.writeText(
            packageNames.joinToString(separator = "\n", postfix = "\n"),
            Charsets.UTF_8
        )
    }
}

abstract class VerifyXposedApkTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaInitFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val modulePropFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedScopeFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val builtArtifacts = checkNotNull(builtArtifactsLoader.get().load(apkDirectory.get())) {
            "Unable to load APK output metadata from ${apkDirectory.get().asFile}"
        }
        val apks = builtArtifacts.elements.map { it.path.toFile() }
        check(apks.isNotEmpty()) {
            "No APK outputs found for ${builtArtifacts.variantName} in ${apkDirectory.get().asFile}"
        }
        check(apks.all { it.isFile }) {
            "APK output metadata references a missing file: ${apks.filterNot { it.isFile }}"
        }

        val expectedJavaInit = javaInitFile.get().asFile.readText(Charsets.UTF_8)
        val moduleEntries = expectedJavaInit.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        check(moduleEntries == listOf("com.hujiayucc.hook.ModuleMain")) {
            "java_init.list must contain only com.hujiayucc.hook.ModuleMain"
        }

        val expectedEntries = mapOf(
            "META-INF/xposed/java_init.list" to expectedJavaInit,
            "META-INF/xposed/module.prop" to modulePropFile.get().asFile.readText(Charsets.UTF_8),
            "META-INF/xposed/scope.list" to generatedScopeFile.get().asFile.readText(Charsets.UTF_8)
        )
        val moduleProperties = Properties().apply {
            modulePropFile.get().asFile.reader(Charsets.UTF_8).use(::load)
        }
        val minApiVersion = checkNotNull(moduleProperties.getProperty("minApiVersion")?.toIntOrNull()) {
            "Xposed minApiVersion must be an integer"
        }
        val targetApiVersion = checkNotNull(moduleProperties.getProperty("targetApiVersion")?.toIntOrNull()) {
            "Xposed targetApiVersion must be an integer"
        }
        check(targetApiVersion == 102) {
            "Modern Xposed targetApiVersion must be 102"
        }
        check(minApiVersion > 0) {
            "Xposed minApiVersion must be positive"
        }
        check(minApiVersion <= targetApiVersion) {
            "Xposed minApiVersion must not exceed targetApiVersion"
        }
        check(moduleProperties.getProperty("staticScope") == "false") {
            "Xposed staticScope must be false"
        }

        apks.forEach { apk ->
            ZipFile(apk).use { zip ->
                expectedEntries.forEach { (entryName, expectedContent) ->
                    val entries = zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name == entryName }
                        .toList()
                    check(entries.size == 1) {
                        "$apk must contain exactly one $entryName, found ${entries.size}"
                    }
                    val actualContent = zip.getInputStream(entries.single())
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                    check(actualContent == expectedContent) {
                        "$entryName in $apk does not match its configured source"
                    }
                }
            }
        }
    }
}

val xposedMetadataSourceDir = layout.projectDirectory.dir("src/main/resources/META-INF/xposed")
val xposedScopesFile = layout.projectDirectory.file("src/main/xposed/scopes.txt")

android {
    namespace = "com.hujiayucc.hook"
    compileSdk = 37

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
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


    defaultConfig {
        applicationId = "com.hujiayucc.hook"
        minSdk = 30
        targetSdk = 37
        versionCode = 10500
        versionName = "3.0.5"
        buildConfigField("Long", "BUILD_TIME", "${buildTimeMillis}L")

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
            
            signingConfig = if (hasReleaseSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
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

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name
        val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }
        val generatedResourcesDir = layout.buildDirectory.dir("generated/xposedMetadata/$variantName")
        val generateScopeList = tasks.register<GenerateXposedScopeListTask>(
            "generate${capitalizedVariantName}XposedScopeList"
        ) {
            scopesFile.set(xposedScopesFile)
            outputDirectory.set(generatedResourcesDir)
        }
        variant.sources.resources?.addGeneratedSourceDirectory(
            generateScopeList,
            GenerateXposedScopeListTask::outputDirectory
        )

        val apkBuiltArtifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        val verifyApk = tasks.register<VerifyXposedApkTask>("verify${capitalizedVariantName}XposedApk") {
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            builtArtifactsLoader.set(apkBuiltArtifactsLoader)
            javaInitFile.set(xposedMetadataSourceDir.file("java_init.list"))
            modulePropFile.set(xposedMetadataSourceDir.file("module.prop"))
            generatedScopeFile.set(
                generatedResourcesDir.map { it.file("META-INF/xposed/scope.list") }
            )
        }
        tasks.configureEach {
            if (name == "assemble$capitalizedVariantName") {
                dependsOn(verifyApk)
            }
        }
    }
}

tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "packageRelease", "validateSigningRelease")) {
        dependsOn(verifyReleaseSigning)
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
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