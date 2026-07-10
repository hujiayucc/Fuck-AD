# Hooker 编写指南

[English](HOOKER_GUIDE.md) | **简体中文**

本文档面向 Fuck AD 贡献者，说明如何为新应用或广告 SDK 编写 Hooker。内容以当前代码结构为准，主要涉及以下目录：

- `app/src/main/java/com/hujiayucc/hook/hooker/app/`：特定应用 Hooker。
- `app/src/main/java/com/hujiayucc/hook/hooker/sdk/`：通用广告 SDK Hooker。
- `app/src/main/java/com/hujiayucc/hook/hooker/util/Hooker.kt`：Hooker 基类和 Hook DSL。
- `app/src/main/java/com/hujiayucc/hook/ModuleMain.kt`：Hooker 调度入口。

## 运行模型

`ModuleMain.onPackageReady()` 会在目标应用进程就绪时执行：

1. 通过 `HookerRegistry.create(packageName)` 查找特定应用 Hooker。
2. 运行内置 Hooker 和应用 Hooker。
3. 如果当前包没有特定应用 Hooker，则通过 `SdkHookerResolver` 检测广告 SDK marker class，并按 `SdkHookerConfig` 设置运行对应 SDK Hooker。

因此新增 Hooker 时需要先判断目标：

- 只针对某个 App：放在 `hooker/app/`，并注册到 `HookerRegistry`。
- 针对通用广告 SDK：放在 `hooker/sdk/`，并注册到 `SdkHookerRegistry`。
- 某个 App 需要额外禁用 SDK：在 App Hooker 中调用 `loadSdk()` 或 `loadAllSDK()`。

## 编写 App Hooker

### 1. 创建 Hooker 对象

在 `hooker/app/` 下创建一个 Kotlin object，继承 `Hooker`，实现 `onPackageReady()`：

```kotlin
package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "示例应用",
    packageName = "com.example.app",
    action = "开屏广告",
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

建议：

- `object` 名称使用应用名或清晰缩写。
- `@Run` 用于记录应用名、包名、处理动作和已验证版本。
- 类名、方法名不确定时优先使用 `toClassOrNull()` / `methodOrNull()`，避免目标版本缺少类或方法时直接抛异常。
- `onPackageReady()` 中只写和当前目标相关的 Hook，不做无关逻辑。

### 2. 注册到 HookerRegistry

在 `HookerRegistry.kt` 的 `appHookers` 中追加包名映射：

```kotlin
"com.example.app" to listOf({ ExampleApp })
```

如果同一个包需要多个 Hooker，可以放入同一个 list：

```kotlin
"com.example.app" to listOf({ ExampleSplash }, { ExampleFeed })
```

注册后，`ModuleMain` 会在该包加载时自动创建并执行这些 Hooker。

## 编写加壳 App Hooker

部分应用经过加固或多 ClassLoader 处理，默认 `param.classLoader` 可能不能直接加载真实业务类。此时使用 `@RunJiaGu`：

```kotlin
import com.hujiayucc.hook.annotation.RunJiaGu

@RunJiaGu(
    appName = "示例加壳应用",
    packageName = "com.example.protected",
    action = "开屏广告"
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

加壳 Hooker 注意事项：

- 必须提供 `jiaGuMarkerClasses`，用于验证真实 ClassLoader 是否已经可用。
- marker class 应选择目标应用或 SDK 中稳定、能代表真实业务 Dex 已加载的类。
- `Hooker` 基类会进行生命周期探针、ClassLoader 探针和延迟重试；不要在 App Hooker 中手动做大量重复轮询。
- 仅主进程需要处理时，先用 `if (!isMainProcess(this)) return` 降低副作用。

## 编写 SDK Hooker

SDK Hooker 用于禁用通用广告 SDK 的加载、展示、初始化等方法。大多数 SDK Hooker 可以继承 `SimpleSdkHooker`：

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

`SimpleSdkHooker` 提供：

- `hookClassMethods(className, vararg methodNames)`：类存在时 Hook 指定方法名。
- `Class<*>.hookMethods(vararg names)`：对一个类的同名方法批量 Hook。
- `Method.replaceWithDefault()`：根据返回值类型返回安全默认值，例如 `false`、`0`、空字符串或 `null`。

### 注册 SDK Hooker

新增 SDK Hooker 后，需要在 `SdkHookerRegistry.kt` 做三件事：

1. 将对象加入 `hookers` 列表。
2. 在 `targets` 中新增 `SdkHookerTarget`。
3. 提供足够稳定的 `markerClasses`。

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

还需要在 `SdkHookerConfig.kt` 中增加 SDK id、展示名称和组件前缀：

```kotlin
const val EXAMPLE = "example"

val sdkNames = linkedMapOf(
    EXAMPLE to "Example Ads"
)

val sdkComponentPrefixes = linkedMapOf(
    EXAMPLE to listOf("com.example.ads.")
)
```

`SdkHookerResolver` 会通过 `markerClasses.any { classLoader.loadClass(it) }` 判断目标应用是否包含该 SDK。marker class 太少会漏判，太宽泛会误判；优先选择 SDK 官方入口类、广告类型类、适配器类和广告 Activity。

## 在 App Hooker 中复用 SDK Hooker

如果某个应用已经有 App Hooker，`ModuleMain` 不会再走 SDK fallback。需要在 App Hooker 里显式加载 SDK Hooker：

```kotlin
override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
    loadSdk(this, pangle = true, gdt = true, kw = true)

    // App 自身广告逻辑
}
```

可用工具：

- `loadSdk(this, pangle = true, gdt = true, kw = true)`：加载指定内置 SDK Hooker。
- `loadAllSDK(this)`：加载 GDT、KW、Pangle 三个常用 SDK Hooker。

如需复用其他 SDK Hooker，可参考 `loadSdk()` 的实现扩展参数或直接调用对应 Hooker 的 `callWithClassLoader()`，但应保持最小改动。

## Hook DSL 常用写法

Hooker 基类为 `Executable` 提供了 `hook { ... }` DSL，可用于 `Method` 和 `Constructor`。

### 完全替换返回值

```kotlin
method.hook {
    replaceTo(false)
}
```

适合禁用返回 `Boolean` 的广告判断、开关或展示状态方法。

### 替换为 Unit / void

```kotlin
method.hook {
    replaceUnit()
}
```

适合拦截 `void` 方法，例如 `show()`、`loadAd()`、`init()`。

### 动态计算返回值

```kotlin
method.hook {
    replace {
        val placementId = args.getOrNull(0) as? String
        placementId != "splash"
    }
}
```

`replace {}` 中可以读取 `args`、`thisObject`，并返回目标方法需要的类型。

### 调用前处理参数

```kotlin
method.hook {
    before {
        // 修改 args 或记录状态
    }
}
```

### 调用后处理结果

```kotlin
method.hook {
    after {
        result = false
    }
}
```

`after {}` 会在原方法执行后运行，可以读取或覆盖 `result`。

### 访问实例

```kotlin
method.hook {
    after {
        val view = instance<android.view.View>()
        if (view.isClickable) view.performClick()
    }
}
```

`instance<T>()` 等价于将 `thisObject` 转成目标类型。使用前确认目标方法不是静态方法。

## 常用辅助方法

`Hooker.kt` 中提供了以下常用工具：

- `"class.name".toClass()`：用当前 ClassLoader 加载类，失败会抛异常。
- `"class.name".toClassOrNull()`：类不存在时返回 `null`。
- `Class<*>.method(name, vararg parameterTypes)`：查找方法。
- `Class<*>.methodOrNull(name, vararg parameterTypes)`：查找失败返回 `null`。
- `Class<*>.methodExact(...)`：按参数精确查找。
- `Class<*>.methods(name)`：查找指定名称的所有声明方法。
- `Class<*>.methods`：获取类的声明方法集合。
- `Array<Method>?.hook { ... }` / `List<Method>.hook { ... }`：批量 Hook。
- `getField(obj, name)` / `setField(obj, name, value)`：访问字段。
- `runMain { ... }` / `runMainDelayed(delay) { ... }`：切到主线程执行 UI 操作。

## 编写原则

- 优先精确 Hook：明确类名、方法名、参数和返回类型，避免过宽匹配。
- 优先空安全：对非稳定类使用 `toClassOrNull()`、`methodOrNull()`。
- 谨慎批量 Hook：`methods.hook {}` 会影响类内所有方法，只有确认安全时使用。
- 避免重复安装：基类已通过 `installedHookKeys` 和 `completedHookerKeys` 做去重，不要自行重复调用同一 Hooker。
- 控制进程范围：只需要主进程时使用 `isMainProcess(this)`。
- 不要吞掉核心功能：广告 SDK 可以返回默认值，但 App 业务类要确认不会影响登录、支付、播放、下载等正常功能。
- 保留可验证信息：`@Run` / `@RunJiaGu` 中填写已验证版本，便于后续维护。

## 调试建议

- 在应用设置中打开错误日志后，`logHookDebug()` 和 `logHookError()` 才会输出调试信息。
- 使用 `logI()` 输出一次性成功摘要，避免在高频方法里刷屏。
- 如果 Hook 没生效，先确认包名是否已加入 LSPosed 作用域，以及目标应用是否重启。
- 如果类找不到，优先检查目标应用版本、进程名、ClassLoader 和是否加壳。
- 如果 SDK fallback 没运行，确认该包是否已经存在 App Hooker；存在 App Hooker 时需要手动 `loadSdk()`。
- 如果 SDK 被误判，调整 `markerClasses`，不要只依赖过于通用的类名。

## 提交流程检查清单

提交前建议检查：

- 新 App Hooker 已注册到 `HookerRegistry.kt`。
- 新 SDK Hooker 已注册到 `SdkHookerRegistry.kt`，并补充 `SdkHookerConfig.kt`。
- `@Run` / `@RunJiaGu` 的包名、应用名、动作、版本信息准确。
- 加壳 Hooker 已设置 `jiaGuMarkerClasses`。
- 不稳定类和方法使用了 `toClassOrNull()` / `methodOrNull()`。
- UI 操作用 `runMain {}` 或确认已在主线程。
- 已在目标应用实际版本验证启动、跳过广告和核心功能。