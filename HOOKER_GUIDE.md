# Hooker Development Guide

**English** | [简体中文](HOOKER_GUIDE-zh_CN.md)


This guide is for Fuck AD contributors. It explains how to write Hookers for new apps or advertising SDKs. The content matches the current project structure and mainly covers these paths:

- `app/src/main/java/com/hujiayucc/hook/hooker/app/`: app-specific Hookers.
- `app/src/main/java/com/hujiayucc/hook/hooker/sdk/`: reusable advertising SDK Hookers.
- `app/src/main/java/com/hujiayucc/hook/hooker/util/Hooker.kt`: the Hooker base class and Hook DSL.
- `app/src/main/java/com/hujiayucc/hook/ModuleMain.kt`: the Hooker dispatch entry point.

## Runtime Model

`ModuleMain.onPackageReady()` runs when a target app process is ready:

1. It looks up app-specific Hookers with `HookerRegistry.create(packageName)`.
2. It runs built-in Hookers and app-specific Hookers.
3. If the package has no app-specific Hooker, it uses `SdkHookerResolver` to detect advertising SDK marker classes, then runs the matching SDK Hookers according to `SdkHookerConfig`.

Choose the target type before adding a new Hooker:

- For one specific app: place it under `hooker/app/` and register it in `HookerRegistry`.
- For a reusable advertising SDK: place it under `hooker/sdk/` and register it in `SdkHookerRegistry`.
- If an app-specific Hooker also needs to disable SDK ads, call `loadSdk()` or `loadAllSDK()` inside that App Hooker.

## Writing An App Hooker

### 1. Create The Hooker Object

Create a Kotlin object under `hooker/app/`, extend `Hooker`, and implement `onPackageReady()`:

```kotlin
package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "Example App",
    packageName = "com.example.app",
    action = "Splash ad",
    versions = ["1.0.0"]
)
object ExampleApp : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.example.app.SplashActivity".toClassOrNull()
            ?.methodOrNull("showAd")
            ?.hook {
                replaceUnit()
            }
    }
}
```

Recommendations:

- Use the app name or a clear abbreviation as the `object` name.
- Use `@Run` to record the app name, package name, action, and verified versions.
- Prefer `toClassOrNull()` / `methodOrNull()` for unstable classes or methods, so missing targets do not crash the Hooker.
- Keep `onPackageReady()` focused on the current target only.

### 2. Register It In HookerRegistry

Add the package mapping to `appHookers` in `HookerRegistry.kt`:

```kotlin
"com.example.app" to listOf({ ExampleApp })
```

If one package needs multiple Hookers, put them in the same list:

```kotlin
"com.example.app" to listOf({ ExampleSplash }, { ExampleFeed })
```

After registration, `ModuleMain` automatically creates and runs these Hookers when that package loads.

## Writing A Packed App Hooker

Some apps use packers, protection, or multiple ClassLoaders. In that case, the default `param.classLoader` may not load the real business classes. Use `@RunJiaGu` for these apps:

```kotlin
import com.hujiayucc.hook.annotation.RunJiaGu

@RunJiaGu(
    appName = "Example Protected App",
    packageName = "com.example.protected",
    action = "Splash ad"
)
object ProtectedApp : Hooker() {
    override val jiaGuMarkerClasses = listOf(
        "com.example.protected.RealApplication",
        "com.bytedance.sdk.openadsdk.TTAdSdk"
    )

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        if (!isMainProcess(this)) return

        "com.example.protected.ad.SplashAd".toClassOrNull()
            ?.methodOrNull("show")
            ?.hook {
                replaceUnit()
            }
    }
}
```

Notes for packed Hookers:

- `jiaGuMarkerClasses` is required. It verifies whether the real ClassLoader is available.
- Choose stable marker classes from the target app or SDK that represent loaded business Dex files.
- The `Hooker` base class already handles lifecycle probes, ClassLoader probes, and delayed retries. Do not add heavy polling in an App Hooker.
- If only the main process needs the hook, start with `if (!isMainProcess(this)) return` to reduce side effects.

## Writing An SDK Hooker

SDK Hookers are used to disable common advertising SDK initialization, loading, and display methods. Most SDK Hookers can extend `SimpleSdkHooker`:

```kotlin
package com.hujiayucc.hook.hooker.sdk

import io.github.libxposed.api.XposedModuleInterface

object ExampleSdk : SimpleSdkHooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookClassMethods(
            "com.example.ads.ExampleAdSdk",
            "initialize",
            "loadAd",
            "show"
        )
    }
}
```

`SimpleSdkHooker` provides:

- `hookClassMethods(className, vararg methodNames)`: hooks named methods when the class exists.
- `Class<*>.hookMethods(vararg names)`: hooks methods with matching names on a class.
- `Method.replaceWithDefault()`: returns a safe default value based on the method return type, such as `false`, `0`, an empty string, or `null`.

### Register The SDK Hooker

After adding a new SDK Hooker, update `SdkHookerRegistry.kt` in three places:

1. Add the object to the `hookers` list.
2. Add a new `SdkHookerTarget` to `targets`.
3. Provide stable `markerClasses`.

```kotlin
SdkHookerTarget(
    id = SdkHookerConfig.EXAMPLE,
    name = "Example Ads",
    hooker = ExampleSdk,
    markerClasses = listOf(
        "com.example.ads.ExampleAdSdk",
        "com.example.ads.SplashAd",
        "com.example.ads.RewardAd"
    )
)
```

Also add the SDK id, display name, and component prefixes to `SdkHookerConfig.kt`:

```kotlin
const val EXAMPLE = "example"

val sdkNames = linkedMapOf(
    EXAMPLE to "Example Ads"
)

val sdkComponentPrefixes = linkedMapOf(
    EXAMPLE to listOf("com.example.ads.")
)
```

`SdkHookerResolver` checks `markerClasses.any { classLoader.loadClass(it) }` to decide whether the target app contains the SDK. Too few marker classes may miss SDKs; overly broad markers may cause false positives. Prefer official SDK entry classes, ad type classes, adapter classes, and ad Activities.

## Reusing SDK Hookers In An App Hooker

If a package already has an App Hooker, `ModuleMain` will not run the SDK fallback for that package. Load SDK Hookers explicitly inside the App Hooker:

```kotlin
override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
    loadSdk(this, pangle = true, gdt = true, kw = true)

    // App-specific ad logic
}
```

Available helpers:

- `loadSdk(this, pangle = true, gdt = true, kw = true)`: loads selected built-in SDK Hookers.
- `loadAllSDK(this)`: loads the GDT, KW, and Pangle SDK Hookers.

If another SDK Hooker must be reused, either extend `loadSdk()` with a minimal parameter change or call the target Hooker through `callWithClassLoader()`. Keep the change as small as possible.

## Common Hook DSL Patterns

The Hooker base class provides a `hook { ... }` DSL for `Method` and `Constructor`.

### Replace The Return Value

```kotlin
method.hook {
    replaceTo(false)
}
```

Use this for Boolean ad checks, switches, or display state methods.

### Replace Unit / void Methods

```kotlin
method.hook {
    replaceUnit()
}
```

Use this for `void` methods such as `show()`, `loadAd()`, and `init()`.

### Compute A Dynamic Return Value

```kotlin
method.hook {
    replace {
        val placementId = args.getOrNull(0) as? String
        placementId != "splash"
    }
}
```

Inside `replace {}`, you can read `args` and `thisObject`, then return the type expected by the target method.

### Run Logic Before The Original Method

```kotlin
method.hook {
    before {
        // Modify args or record state.
    }
}
```

### Run Logic After The Original Method

```kotlin
method.hook {
    after {
        result = false
    }
}
```

`after {}` runs after the original method. It can read or override `result`.

### Access The Instance

```kotlin
method.hook {
    after {
        val view = instance<android.view.View>()
        if (view.isClickable) view.performClick()
    }
}
```

`instance<T>()` casts `thisObject` to the target type. Make sure the target method is not static before using it.

## Common Helpers

`Hooker.kt` provides these helpers:

- `"class.name".toClass()`: loads a class with the current ClassLoader and throws on failure.
- `"class.name".toClassOrNull()`: returns `null` when the class does not exist.
- `Class<*>.method(name, vararg parameterTypes)`: finds a method.
- `Class<*>.methodOrNull(name, vararg parameterTypes)`: returns `null` if lookup fails.
- `Class<*>.methodExact(...)`: finds a method by exact parameter types.
- `Class<*>.methods(name)`: finds all declared methods with the given name.
- `Class<*>.methods`: returns declared methods of the class.
- `Array<Method>?.hook { ... }` / `List<Method>.hook { ... }`: batch hooks methods.
- `getField(obj, name)` / `setField(obj, name, value)`: accesses fields.
- `runMain { ... }` / `runMainDelayed(delay) { ... }`: runs UI work on the main thread.

## Guidelines

- Prefer precise hooks: use explicit class names, method names, parameters, and return types.
- Prefer null-safe lookup: use `toClassOrNull()` and `methodOrNull()` for unstable targets.
- Be careful with batch hooks: `methods.hook {}` affects every method in the class and should only be used when verified safe.
- Avoid duplicate installation: the base class already deduplicates with `installedHookKeys` and `completedHookerKeys`.
- Limit the process scope: use `isMainProcess(this)` when only the main process needs the hook.
- Do not break core app features: SDK methods may return defaults, but app business classes must be checked against login, payment, playback, downloads, and other normal flows.
- Keep verification metadata: fill `@Run` / `@RunJiaGu` with verified versions for maintainability.

## Debugging Tips

- `logHookDebug()` and `logHookError()` only output debug information when error logging is enabled in app settings.
- Use `logI()` for one-time success summaries. Avoid logging inside high-frequency methods.
- If a hook does not work, first check whether the package is in the LSPosed scope and whether the target app was restarted.
- If a class cannot be found, check the target app version, process name, ClassLoader, and whether the app is packed.
- If the SDK fallback does not run, check whether the package already has an App Hooker. Packages with App Hookers must call `loadSdk()` manually.
- If an SDK is detected incorrectly, tune `markerClasses` instead of relying on overly generic class names.

## Submission Checklist

Before submitting, check that:

- The new App Hooker is registered in `HookerRegistry.kt`.
- The new SDK Hooker is registered in `SdkHookerRegistry.kt`, and `SdkHookerConfig.kt` is updated.
- `@Run` / `@RunJiaGu` has the correct package name, app name, action, and version information.
- Packed Hookers define `jiaGuMarkerClasses`.
- Unstable classes and methods use `toClassOrNull()` / `methodOrNull()`.
- UI operations use `runMain {}` or are confirmed to already run on the main thread.
- Startup, ad skipping, and core app features have been verified on the actual target app version.
