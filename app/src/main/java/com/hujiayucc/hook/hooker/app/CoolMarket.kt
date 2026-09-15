package com.hujiayucc.hook.hooker.app

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hujiayucc.hook.ModuleMain
import com.hujiayucc.hook.annotation.RunJiaGu

import com.hujiayucc.hook.hooker.util.AdCloseController
import com.hujiayucc.hook.hooker.util.AdViewScanner
import com.hujiayucc.hook.hooker.util.DexKitMethodCache
import com.hujiayucc.hook.hooker.util.Hooker
import dalvik.system.BaseDexClassLoader
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

@RunJiaGu(
    appName = "酷安",
    packageName = "com.coolapk.market",
    action = "禁用SDK, 信息流广告",
    versions = ["16.6.1"]
)
object CoolMarket : Hooker() {
    private const val QUERY_REPLY_BIND = "coolapk_reply_bind"
    private const val COLLECTION_SELECT_ACTIVITY =
        "com.coolapk.market.view.collectionList.CollectionSelectActivity"
    override val jiaGuMarkerClasses = listOf(
        "com.coolapk.market.view.splash.SplashAdActivity",
        "com.coolapk.market.view.splash.SplashAdFragment",
        "com.coolapk.market.view.splash.SplashAdLoader",
        "com.coolapk.market.view.splash.CoolapkSplashView",
        "com.coolapk.market.view.splash.CountdownView",
        "hg5",
        "com.bytedance.sdk.openadsdk.TTAdSdk",
        "com.bytedance.sdk.openadsdk.TTInitializer",
        "com.bytedance.sdk.openadsdk.TTAdNative",
        "com.bytedance.sdk.openadsdk.TTAdManager",
        "com.bytedance.sdk.openadsdk.CSJSplashAd",
        "com.bytedance.sdk.openadsdk.c.a.a\$a",
        "com.bytedance.sdk.openadsdk.api.a\$c",
        "com.bytedance.sdk.openadsdk.c.a.a.b",
        "com.bytedance.sdk.openadsdk.core.AdSdkInitializerHolder",
        "com.kwad.components.ad.splashscreen.widget.SkipView",
        "com.kwad.components.ad.splashscreen.widget.CircleSkipView",
        "com.kwad.components.ad.splashscreen.widget.CloseCountDownView",
        "com.kwad.components.core.widget.KsAutoCloseView",
        "com.miui.zeus.mimo.sdk.ad.interstitial.view.InterstitialSkipCountDownView",
        "com.miui.zeus.mimo.sdk.ad.reward.view.RewardSkipCountDownView",
        "com.smartdigimkt.sdk.basead.ui.CloseImageView",
        "com.smartdigimkt.sdk.basead.ui.CloseFrameLayout",
        "com.smartdigimkt.sdk.basead.ui.CountDownCloseView",
        "com.smartdigimkt.sdk.basead.ui.CountDownView"
    )
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        if (!isMainProcess(this)) return

        // 酷安自身开屏由 Activity/布局直接收敛；CountdownView 不触发广告动作入口。
        runPhase { loadSdk(this, pangle = true, kw = true, mimo = true, smartDigiMkt = true) }
        runPhase { hookActivityLifecycle() }
        runPhase { hookAdLayoutChanges() }
        runPhase { hookCoolapkSplash() }
        runPhase { hookFeedAdBinding() }
        runPhase { hookGenericEntityBinding() }
        runPhase { hookReplyAdBinding() }
        runPhase { hookReplyBindingWithDexKit(applicationInfo.sourceDir) }
        runPhase { hookReplyCloseView() }
    }

    private fun runPhase(block: () -> Unit) {
        runCatching(block)
    }


    private fun hookFeedAdBinding() {
        val cardClass = "com.coolapk.market.design.CoolapkCardView".toClassOrNull()
        if (cardClass == null) return
        val bindMethod = cardClass.cachedDeclaredMethods().firstOrNull { method ->
            method.name == "Ԩ" && method.parameterTypes.size == 2 &&
                RecyclerView.ViewHolder::class.java.isAssignableFrom(method.parameterTypes[0])
        } ?: cardClass.cachedDeclaredMethods().firstOrNull { method ->
            method.name == "Ԩ" && method.parameterTypes.size == 2 &&
                method.parameterTypes[1] == Any::class.java
        }
        if (bindMethod == null) return
        bindMethod.hook {
            after {
                val entity = args.getOrNull(1)
                val type = entity?.let { readStringProperty(it, "getEntityType") }
                val template = entity?.let { readStringProperty(it, "getEntityTemplate") }
                val hit = isSponsorEntity(type, template)
                val holderItem = readHolderItemView(args.getOrNull(0))
                val target = resolveRecyclerItem(holderItem ?: (thisObject as? View)) ?: return@after
                if (hit) {
                    collapseFeedItem(target)
                } else {
                    restoreFeedItem(target)
                }
            }
        }
    }

    private fun hookGenericEntityBinding() {
        val genericClass = "hg5".toClassOrNull()
        if (genericClass == null) return
        val bindMethod = genericClass.cachedDeclaredMethods().firstOrNull { method ->
            method.name == "ވ" && method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Any::class.java &&
                method.returnType == Void.TYPE
        }
        if (bindMethod == null) return
        bindMethod.hook {
            after {
                val entity = args.firstOrNull()
                val type = entity?.let { readStringProperty(it, "getEntityType") }
                val template = entity?.let { readStringProperty(it, "getEntityTemplate") }
                val hit = isSponsorEntity(type, template)
                val holderItem = readHolderItemView(thisObject)
                val itemView = resolveRecyclerItem(holderItem)
                if (itemView != null) {
                    if (hit) {
                        collapseFeedItem(itemView)
                    } else {
                        restoreFeedItem(itemView)
                    }
                }
            }
        }
    }

    private val replyBindingMethods = Collections.synchronizedSet(mutableSetOf<String>())
    private val replyAdItems = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    private fun hookReplyAdBinding() {
        val replyHolderClass = "du9".toClassOrNull()
        if (replyHolderClass == null) return
        val bindMethod = replyHolderClass.cachedDeclaredMethods().firstOrNull { method ->
            method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == "com.coolapk.market.model.FeedReply" &&
                method.returnType == Void.TYPE
        }
        if (bindMethod == null) return
        installReplyBindingHook(bindMethod)
    }

    private fun installReplyBindingHook(method: Method) {
        if (!replyBindingMethods.add(method.toGenericString())) return
        method.hook {
            after {
                handleReplyBinding(
                    reply = args.firstOrNull(),
                    holder = thisObject
                )
            }
        }
    }

    private fun handleReplyBinding(reply: Any?, holder: Any?) {
        if (reply == null) return
        val type = readStringProperty(reply, "getEntityType")
        val flag = readStringProperty(reply, "getExtraFlag")
        val id = readStringProperty(reply, "getId")
        val extra = readObjectProperty(reply, "getExtraData")
        val sponsorType = extra?.let { readStringProperty(it, "getSponsorType") }
        val hit = isReplyAd(type, flag, id, sponsorType, extra)
        val itemView = resolveRecyclerItem(readHolderItemView(holder))
        if (itemView != null) {
            if (hit) {
                synchronized(replyAdItems) { replyAdItems.add(itemView) }
                collapseFeedItem(itemView)
            } else {
                synchronized(replyAdItems) { replyAdItems.remove(itemView) }
                restoreFeedItem(itemView)
            }
        }
    }

    private fun hookReplyBindingWithDexKit(apkPath: String) {
        val targetLoader = classLoader ?: return
        val cached = DexKitMethodCache.get(
            ModuleMain.prefs,
            packageName = "com.coolapk.market",
            apkPath = apkPath,
            queryId = QUERY_REPLY_BIND,
            classLoader = targetLoader
        )
        if (cached != null) {
            installReplyBindingHook(cached)
            return
        }
        if (!ModuleMain.ensureDexKitLoaded()) return
        runCatching {
            DexKitBridge.create(targetLoader, true).use { bridge ->
                val candidates = bridge.findMethod {
                    matcher {
                        paramCount(1)
                        addParamType("com.coolapk.market.model.FeedReply", StringMatchType.Equals)
                    }
                }
                val candidateData = candidates.asSequence()
                    .filter { data -> data.returnTypeName == "void" || data.returnTypeName == "V" }
                    .firstOrNull(::isViewHolderMethodData)
                    ?: candidates.firstOrNull { data ->
                        data.declaredClassName == "du9" && data.methodName == "ޝ"
                    }
                val candidate = candidateData?.let { data ->
                    runCatching { data.getMethodInstance(targetLoader) }.getOrNull()
                }
                if (candidate != null) {
                    runCatching {
                        DexKitMethodCache.put(
                            ModuleMain.prefs,
                            "com.coolapk.market",
                            apkPath,
                            QUERY_REPLY_BIND,
                            candidate
                        )
                    }
                    runCatching { installReplyBindingHook(candidate) }
                }
            }
        }
    }

    private fun isViewHolderMethodData(data: org.luckypray.dexkit.result.MethodData): Boolean {
        return runCatching {
            var current = data.declaredClass?.getInstance(classLoader ?: return@runCatching false)
            repeat(8) {
                if (current == RecyclerView.ViewHolder::class.java) return@runCatching true
                current = current?.superclass
            }
            false
        }.getOrDefault(false)
    }
    private fun hookReplyCloseView() {
        View::class.java.method("onAttachedToWindow").hook {
            after {
                val view = instance<View>()
                if (!isFeedDetailView(view)) return@after
                scheduleReplyCloseIfMatch(view)
                listOf(180L, 500L, 1_000L).forEach { delay ->
                    runMainDelayed(delay) {
                        scheduleReplyCloseIfMatch(view)
                    }
                }
            }
        }
        View::class.java.method("setVisibility", Int::class.javaPrimitiveType!!).hook {
            after {
                val view = instance<View>()
                if (view.visibility == View.VISIBLE && isFeedDetailView(view)) {
                    scheduleReplyCloseIfMatch(view)
                }
            }
        }
        View::class.java.method("setOnClickListener", View.OnClickListener::class.java).hook {
            after {
                val view = instance<View>()
                if (isFeedDetailView(view)) scheduleReplyCloseIfMatch(view)
            }
        }
        // 详情页根扫描已合并到 hookActivityLifecycle()，此处只保留 View 时序入口。
    }

    private fun scheduleReplyCloseIfMatch(view: View) {
        if (!isFeedDetailView(view)) return
        val adCard = findReplyNativeAdCard(view) ?: return
        collapseFeedItem(adCard)
    }

    private fun findOwningActivity(view: View): Activity? {
        var current: View? = view
        repeat(16) {
            var context: android.content.Context? = current?.context
            while (context != null) {
                if (context is Activity) return context
                val wrapper = context as? android.content.ContextWrapper ?: break
                val base = wrapper.baseContext
                if (base === context) break
                context = base
            }
            current = current?.parent as? View
        }
        return null
    }

    private fun isCollectionSelectView(view: View): Boolean {
        return findOwningActivity(view)?.javaClass?.name == COLLECTION_SELECT_ACTIVITY
    }

    private fun isFeedDetailView(view: View): Boolean {
        return findOwningActivity(view)?.javaClass?.name?.contains("FeedDetailActivity") == true
    }

    private fun scanReplyCloseViews(root: View) {
        var visited = 0
        fun visit(node: View) {
            if (visited++ >= 2_000) return
            if (viewResourceName(node) == "close_view") {
                findReplyNativeAdCard(node)?.let { adCard ->
                    collapseFeedItem(adCard)
                }
            }
            (node as? ViewGroup)?.let { group ->
                for (index in 0 until group.childCount) {
                    if (visited >= 2_000) break
                    visit(group.getChildAt(index))
                }
            }
        }
        visit(root)
    }

    /**
     * 这个 close_view 的宿主动作会打开反馈面板；只把它作为广告卡结构的锚点。
     */
    private fun findReplyNativeAdCard(view: View): View? {
        if (viewResourceName(view) != "close_view") return null
        val className = view.javaClass.name
        if (className != "androidx.appcompat.widget.AppCompatImageView" &&
            className != "android.widget.ImageView"
        ) return null
        var current: View? = view
        repeat(12) {
            val node = current ?: return null
            if (viewResourceName(node) == "coolapk_card_view" && isReplyAdCard(node)) return node
            current = node.parent as? View
        }
        return null
    }

    /**
     * 评论区原生广告不进入 FeedReply，直接作为 coolapk_card_view 插入 GridView。
     * 资源链和广告子节点同时命中，避免把其它 close_view 当作广告关闭控件。
     */
    private fun isReplyAdCard(root: View): Boolean {
        var hasContentContainer = false
        var hasDescription = false
        var hasRelative = false
        var hasAdLabel = false
        var visited = 0

        fun visit(node: View) {
            if (visited++ >= 180) return
            when (viewResourceName(node)) {
                "content_container" -> hasContentContainer = true
                "description_view" -> hasDescription = true
                "relative_view" -> hasRelative = true
            }
            val text = (node as? android.widget.TextView)?.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            if (text == "广告" || text == "今日推荐" ||
                description.contains("NativeVideoAdView", ignoreCase = true)
            ) {
                hasAdLabel = true
            }
            if (hasContentContainer && hasDescription && hasRelative && hasAdLabel) return
            (node as? ViewGroup)?.let { group ->
                for (index in 0 until group.childCount) {
                    if (hasContentContainer && hasDescription && hasRelative && hasAdLabel) break
                    visit(group.getChildAt(index))
                }
            }
        }

        visit(root)
        return hasContentContainer && hasDescription && hasRelative && hasAdLabel
    }

    private fun viewResourceName(view: View): String {
        return runCatching {
            if (view.id == View.NO_ID || view.id == 0) ""
            else view.resources.getResourceEntryName(view.id)
        }.getOrDefault("")
    }


    private fun isReplyAd(
        type: String?,
        flag: String?,
        id: String?,
        sponsorType: String?,
        extra: Any?
    ): Boolean {
        val markers = listOf(type, flag, id, sponsorType, extra?.toString())
            .filterNotNull()
            .joinToString(" ")
            .lowercase()
        return isSponsorEntity(type, null) ||
            markers.contains("sponsor") ||
            markers.contains("advert") ||
            markers.contains("ad_native") ||
            markers.contains("adnative")
    }

    private fun readHolderItemView(value: Any?): View? {
        if (value == null) return null
        if (value is RecyclerView.ViewHolder) return value.itemView
        return runCatching {
            var type: Class<*>? = value.javaClass
            while (type != null) {
                val field = type.declaredFields.firstOrNull { it.name == "itemView" }
                if (field != null) {
                    field.isAccessible = true
                    return@runCatching field.get(value) as? View
                }
                type = type.superclass
            }
            null
        }.getOrNull()
    }

    private fun resolveRecyclerItem(view: View?): View? {
        var current = view ?: return null
        repeat(16) {
            val parent = current.parent as? View ?: return current
            if (parent is RecyclerView) return current
            current = parent
        }
        return current
    }

    private data class FeedLayoutState(
        val height: Int,
        val width: Int,
        val minHeight: Int,
        val minWidth: Int,
        val leftMargin: Int,
        val topMargin: Int,
        val rightMargin: Int,
        val bottomMargin: Int
    )

    private val feedLayoutStates = Collections.synchronizedMap(WeakHashMap<View, FeedLayoutState>())

    private fun collapseFeedItem(itemView: View) {
        runCatching {
            val params = itemView.layoutParams ?: return@runCatching
            val marginParams = params as? ViewGroup.MarginLayoutParams
            feedLayoutStates.putIfAbsent(
                itemView,
                FeedLayoutState(
                    height = params.height,
                    width = params.width,
                    minHeight = itemView.minimumHeight,
                    minWidth = itemView.minimumWidth,
                    leftMargin = marginParams?.leftMargin ?: 0,
                    topMargin = marginParams?.topMargin ?: 0,
                    rightMargin = marginParams?.rightMargin ?: 0,
                    bottomMargin = marginParams?.bottomMargin ?: 0
                )
            )
            params.height = 0
            itemView.minimumHeight = 0
            if (marginParams != null) {
                marginParams.topMargin = 0
                marginParams.bottomMargin = 0
            }
            itemView.visibility = View.GONE
            itemView.layoutParams = params
            itemView.requestLayout()
            (itemView.parent as? ViewGroup)?.requestLayout()
        }
    }

    private fun restoreFeedItem(itemView: View) {
        runCatching {
            val params = itemView.layoutParams ?: return@runCatching
            val saved = feedLayoutStates.remove(itemView)
            if (saved != null) {
                params.height = saved.height
                params.width = saved.width
                itemView.minimumHeight = saved.minHeight
                itemView.minimumWidth = saved.minWidth
                (params as? ViewGroup.MarginLayoutParams)?.let { marginParams ->
                    marginParams.leftMargin = saved.leftMargin
                    marginParams.topMargin = saved.topMargin
                    marginParams.rightMargin = saved.rightMargin
                    marginParams.bottomMargin = saved.bottomMargin
                }
                itemView.layoutParams = params
                itemView.visibility = View.VISIBLE
                itemView.requestLayout()
                (itemView.parent as? ViewGroup)?.requestLayout()
            } else if (itemView.visibility != View.VISIBLE) {
                itemView.visibility = View.VISIBLE
            }
        }
    }

    private fun isSponsorEntity(type: String?, template: String?): Boolean {
        val sponsorValues = setOf("sponsorCard", "sponsorForSearch", "sponsorMiniCard")
        return type in sponsorValues || template in sponsorValues
    }

    private fun readStringProperty(value: Any, methodName: String): String? {
        return readObjectProperty(value, methodName) as? String
    }

    private fun readObjectProperty(value: Any, methodName: String): Any? {
        return runCatching {
            value.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
                ?.invoke(value)
        }.getOrNull()
    }

    private fun isAdRelatedView(view: View): Boolean {
        val value = buildString {
            append(view.javaClass.name)
            append(' ')
            append(view.contentDescription?.toString().orEmpty())
            if (view is android.widget.TextView) append(' ').append(view.text?.toString().orEmpty())
            runCatching {
                if (view.id != View.NO_ID && view.id != 0) {
                    append(' ').append(view.resources.getResourceEntryName(view.id))
                }
            }
        }.lowercase()
        return value.contains("ad") || value.contains("splash") ||
            value.contains("skip") || value.contains("close") || value.contains("关闭") ||
            value.contains("跳过") || view.javaClass.name.startsWith("com.bytedance.sdk.openadsdk") ||
            view.javaClass.name.startsWith("com.kwad.") ||
            view.javaClass.name.startsWith("com.miui.zeus.mimo.") ||
            view.javaClass.name.startsWith("com.smartdigimkt.sdk")
    }

    private fun hookActivityLifecycle() {
        // 目标 Activity 会覆写 onResume；Instrumentation 入口可覆盖所有恢复路径。
        Instrumentation::class.java.method("callActivityOnResume", Activity::class.java).hook {
            after {
                (args.firstOrNull() as? Activity)?.let { activity ->
                    handleActivityResumed(activity, "instrumentation-resume")
                }
            }
        }
        Activity::class.java.method("onResume").hook {
            after {
                handleActivityResumed(instance<Activity>(), "activity-resume")
            }
        }
        Activity::class.java.method("onWindowFocusChanged", Boolean::class.javaPrimitiveType!!).hook {
            after {
                if (args.firstOrNull() == true) {
                    scheduleReplyCloseScan(instance<Activity>())
                }
            }
        }
    }

    private fun handleActivityResumed(activity: Activity, source: String) {
        if (activity.javaClass.name.contains("SplashAdActivity")) {
            AdViewScanner.scheduleActivity(activity, source)
            runMainDelayed(260L) {
                AdViewScanner.scheduleActivity(activity, "splash-observe")
            }
        }
        scheduleReplyCloseScan(activity)
    }

    private fun scheduleReplyCloseScan(activity: Activity) {
        if (!activity.javaClass.name.contains("FeedDetailActivity")) return
        val root = runCatching { activity.window?.decorView }.getOrNull() ?: return
        listOf(120L, 400L, 900L, 1_800L, 3_000L).forEach { delay ->
            runMainDelayed(delay) {
                scanReplyCloseViews(root)
            }
        }
    }

    private fun hookAdLayoutChanges() {
        ViewGroup::class.java.methods("addView").hook {
            after {
                val added = args.firstOrNull() as? View ?: return@after
                if (isCollectionSelectView(added)) return@after
                if (isAdRelatedView(added)) {
                    AdViewScanner.scheduleView(added, "add-view")
                }
            }
        }
        View::class.java.method("setVisibility", Int::class.javaPrimitiveType!!).hook {
            after {
                val view = instance<View>()
                if (isCollectionSelectView(view)) return@after
                if (view.visibility == View.VISIBLE && isAdRelatedView(view)) {
                    AdViewScanner.scheduleView(view, "visibility")
                }
            }
        }
        View::class.java.method("setOnClickListener", View.OnClickListener::class.java).hook {
            after {
                val view = instance<View>()
                if (isCollectionSelectView(view)) return@after
                if (AdViewScanner.isKnownCloseCandidate(view)) {
                    AdCloseController.schedule(view, "coolapk-close-listener", 80L)
                }
            }
        }
    }

    private fun hookCoolapkSplash() {
        "com.coolapk.market.view.splash.SplashAdFragment".toClassOrNull()
            ?.cachedDeclaredMethods()
            ?.filter { it.name in setOf("onCreateView", "onViewCreated", "onResume") }
            ?.forEach { method ->
                method.hook {
                    after {
                        (result as? View)?.let { root ->
                            AdViewScanner.schedule(root, "coolapk-splash-fragment")
                        }
                    }
                }
            }
        "com.coolapk.market.view.splash.CoolapkSplashView".toClassOrNull()
            ?.methodOrNull("setupSkipView")
            ?.hook {
                after {
                    (instance as? View)?.visibility = View.GONE
                }
            }
    }
}