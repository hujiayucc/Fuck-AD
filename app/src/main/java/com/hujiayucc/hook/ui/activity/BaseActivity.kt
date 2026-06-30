package com.hujiayucc.hook.ui.activity

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.hujiayucc.hook.application.XYApplication
import io.github.libxposed.service.XposedService
import java.util.*

abstract class BaseActivity<T : Any> : AppCompatActivity(), XYApplication.ServiceStateListener {
    protected var service: XposedService? = null

    private val backAnimationInterpolator = DecelerateInterpolator()
    private var previousPagePreview: ImageView? = null
    private var backTargetOriginalClipToOutline: Boolean? = null
    private var backTargetOriginalOutlineProvider: ViewOutlineProvider? = null
    private val topRoundedCornerProvider by lazy {
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = TOP_CORNER_RADIUS_DP * resources.displayMetrics.density
                val path = Path().apply {
                    addRoundRect(
                        0f,
                        0f,
                        view.width.toFloat(),
                        view.height.toFloat() + radius,
                        floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
                        Path.Direction.CW
                    )
                }
                outline.setPath(path)
            }
        }
    }
    private var backGestureInProgress = false
    private var backActionFromGesture = false
    private var backActionRunning = false
    private var backSwipeDirection = 1f
    private var backGestureProgress = 0f

    protected val predictiveBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            backGestureInProgress = true
            backActionFromGesture = false
            backActionRunning = false
            backGestureProgress = backEvent.progress.coerceIn(0f, 1f)
            updateBackSwipeDirection(backEvent)
            ensurePreviousPagePreview()
            applyBackAnimation(backEvent.progress)
            onBackStarted(backEvent)
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            backGestureProgress = backEvent.progress.coerceIn(0f, 1f)
            applyBackAnimation(backEvent.progress)
            onBackProgressed(backEvent)
        }

        override fun handleOnBackCancelled() {
            backGestureInProgress = false
            backActionFromGesture = false
            backGestureProgress = 0f
            backActionRunning = false
            resetBackAnimation(animated = true)
            onBackCancelled()
        }

        override fun handleOnBackPressed() {
            if (backActionRunning) return
            backActionRunning = true
            if (backGestureInProgress) {
                backActionFromGesture = true
                performBackAction()
            } else {
                backActionFromGesture = false
                runBackPressAnimation { performBackAction() }
            }
        }
    }

    private val configChangeListener = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (newConfig.locales[0] != Locale.getDefault()) {
                handleLanguageChange()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {
            clearPreviousPageBitmaps()
        }

        override fun onTrimMemory(level: Int) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            ) {
                clearPreviousPageBitmaps()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        application.registerComponentCallbacks(configChangeListener)

        onBackPressedDispatcher.addCallback(this, predictiveBackCallback)
    }

    override fun onStart() {
        super.onStart()
        XYApplication.addServiceStateListener(this, true)
    }

    override fun onStop() {
        XYApplication.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        application.unregisterComponentCallbacks(configChangeListener)
    }

    private fun handleLanguageChange() {
        val currentLocale = AppCompatDelegate.getApplicationLocales()[0]
        val systemLocale = Locale.getDefault()
        if (currentLocale != systemLocale) recreate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        delegate.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onServiceStateChanged(service: XposedService?) {
        this.service = service
    }

    private fun backAnimationTarget(): View? {
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return null
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child !== previousPagePreview) return child
        }
        return content
    }

    private fun backAnimationContainer(): ViewGroup? {
        return findViewById(android.R.id.content)
    }

    private fun ensurePreviousPagePreview() {
        if (this is MainActivity || previousPagePreview != null) return
        val preview = previousPageBitmaps[javaClass.name] ?: return
        val container = backAnimationContainer() ?: return
        val imageView = ImageView(this).apply {
            setImageBitmap(preview)
            scaleType = ImageView.ScaleType.FIT_XY
            alpha = 1f
            translationX = -backSwipeDirection * container.width * PREVIOUS_PAGE_OFFSET_RATIO
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(imageView, 0)
        previousPagePreview = imageView
    }

    private fun applyPreviousPagePreview(progress: Float, targetWidth: Int) {
        val preview = previousPagePreview ?: return
        preview.translationX = previousPagePreviewTranslationX(targetWidth, progress)
        preview.alpha = previousPagePreviewAlpha(progress)
    }

    private fun previousPagePreviewTranslationX(targetWidth: Int, progress: Float): Float {
        return -backSwipeDirection * targetWidth * PREVIOUS_PAGE_OFFSET_RATIO * (1f - progress)
    }

    private fun previousPagePreviewAlpha(progress: Float): Float {
        return PREVIOUS_PAGE_MIN_ALPHA + (1f - PREVIOUS_PAGE_MIN_ALPHA) * progress
    }

    private fun resetPreviousPagePreview(animated: Boolean) {
        val preview = previousPagePreview ?: return
        preview.animate().cancel()
        if (animated) {
            val width = backAnimationTarget()?.width ?: preview.width
            preview.animate()
                .translationX(-backSwipeDirection * width * PREVIOUS_PAGE_OFFSET_RATIO)
                .alpha(PREVIOUS_PAGE_MIN_ALPHA)
                .setDuration(BACK_CANCEL_ANIMATION_DURATION_MS)
                .setInterpolator(backAnimationInterpolator)
                .start()
        } else {
            val width = backAnimationTarget()?.width ?: preview.width
            preview.translationX = -backSwipeDirection * width * PREVIOUS_PAGE_OFFSET_RATIO
            preview.alpha = PREVIOUS_PAGE_MIN_ALPHA
        }
    }

    private fun updateBackSwipeDirection(backEvent: BackEventCompat) {
        val width = backAnimationTarget()?.width ?: 0
        backSwipeDirection = if (width > 0 && backEvent.touchX > width / 2f) -1f else 1f
    }

    private fun applyBackAnimation(progress: Float) {
        val target = backAnimationTarget() ?: return
        if (this is MainActivity) applyTopRoundedCorners(target)
        val easedProgress = easeBackProgress(progress.coerceIn(0f, 1f))
        target.animate().cancel()
        applyBackAnimationValues(target, backAnimationValues(target, easedProgress))
        applyPreviousPagePreview(easedProgress, target.width)
    }

    private fun runBackPressAnimation(onEnd: () -> Unit) {
        val target = backAnimationTarget()
        if (target == null || target.width == 0 || target.height == 0) {
            onEnd()
            return
        }

        backSwipeDirection = 1f
        animateBackAnimation(target, BACK_BUTTON_PROGRESS, BACK_PRESS_ANIMATION_DURATION_MS, onEnd)
    }

    private fun runBackCommitAnimation(onEnd: () -> Unit) {
        val target = backAnimationTarget()
        if (target == null || target.width == 0 || target.height == 0) {
            onEnd()
            return
        }

        val duration = backCommitAnimationDuration()
        val awaitEnd = awaitAnimationEnds(1 + if (previousPagePreview != null) 1 else 0, onEnd)
        animateBackAnimation(target, 1f, duration) { awaitEnd() }
        if (previousPagePreview != null) {
            animatePreviousPagePreview(duration) { awaitEnd() }
        }
    }

    private fun awaitAnimationEnds(count: Int, onEnd: () -> Unit): () -> Unit {
        var remainingAnimations = count
        return {
            remainingAnimations -= 1
            if (remainingAnimations <= 0) {
                onEnd()
            }
        }
    }

    private fun animatePreviousPagePreview(duration: Long, onEnd: (() -> Unit)? = null) {
        val preview = previousPagePreview ?: run {
            onEnd?.invoke()
            return
        }
        preview.animate().cancel()
        preview.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(backAnimationInterpolator)
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    private fun animateBackAnimation(target: View, progress: Float, duration: Long, onEnd: () -> Unit) {
        if (this is MainActivity) applyTopRoundedCorners(target)
        val easedProgress = easeBackProgress(progress.coerceIn(0f, 1f))
        val values = backAnimationValues(target, easedProgress)
        target.animate().cancel()
        target.pivotX = values.pivotX
        target.pivotY = values.pivotY
        target.animate()
            .translationX(values.translationX)
            .translationY(values.translationY)
            .scaleX(values.scaleX)
            .scaleY(values.scaleY)
            .alpha(values.alpha)
            .setDuration(duration)
            .setInterpolator(backAnimationInterpolator)
            .withEndAction { onEnd() }
            .start()
    }

    private fun applyBackAnimationValues(target: View, values: BackAnimationValues) {
        target.pivotX = values.pivotX
        target.pivotY = values.pivotY
        target.translationX = values.translationX
        target.translationY = values.translationY
        target.scaleX = values.scaleX
        target.scaleY = values.scaleY
        target.alpha = values.alpha
    }

    private fun applyTopRoundedCorners(target: View) {
        if (backTargetOriginalClipToOutline == null) {
            backTargetOriginalClipToOutline = target.clipToOutline
            backTargetOriginalOutlineProvider = target.outlineProvider
        }
        target.outlineProvider = topRoundedCornerProvider
        target.clipToOutline = true
        target.invalidateOutline()
    }

    private fun restoreTopRoundedCorners(target: View) {
        val clipToOutline = backTargetOriginalClipToOutline ?: return
        target.clipToOutline = clipToOutline
        target.outlineProvider = backTargetOriginalOutlineProvider
        target.invalidateOutline()
        backTargetOriginalClipToOutline = null
        backTargetOriginalOutlineProvider = null
    }

    private fun backAnimationValues(target: View, progress: Float): BackAnimationValues {
        return if (this is MainActivity) {
            homeBackAnimationValues(target, progress)
        } else {
            pageBackAnimationValues(target, progress)
        }
    }

    private fun homeBackAnimationValues(target: View, progress: Float): BackAnimationValues {
        return BackAnimationValues(
            pivotX = target.width / 2f,
            pivotY = target.height * CARD_PIVOT_Y_RATIO,
            translationX = 0f,
            translationY = target.height * cardTranslationProgress(progress),
            scaleX = 1f - CARD_SCALE_DELTA * cardHorizontalScaleProgress(progress),
            scaleY = 1f - CARD_SCALE_DELTA * cardScaleProgress(progress),
            alpha = 1f - CARD_ALPHA_DELTA * progress
        )
    }

    private fun pageBackAnimationValues(target: View, progress: Float): BackAnimationValues {
        return BackAnimationValues(
            pivotX = target.width / 2f,
            pivotY = target.height / 2f,
            translationX = backSwipeDirection * target.width * PAGE_TRANSLATION_RATIO * progress,
            translationY = 0f,
            scaleX = 1f,
            scaleY = 1f,
            alpha = 1f - PAGE_ALPHA_DELTA * progress
        )
    }

    private fun resetBackAnimation(animated: Boolean, onEnd: (() -> Unit)? = null) {
        resetPreviousPagePreview(animated)
        val target = backAnimationTarget()
        if (target == null) {
            onEnd?.invoke()
            return
        }

        target.animate().cancel()
        if (animated) {
            target.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(BACK_CANCEL_ANIMATION_DURATION_MS)
                .setInterpolator(backAnimationInterpolator)
                .withEndAction {
                    restoreTopRoundedCorners(target)
                    onEnd?.invoke()
                }
                .start()
        } else {
            target.translationX = 0f
            target.translationY = 0f
            target.scaleX = 1f
            target.scaleY = 1f
            target.alpha = 1f
            restoreTopRoundedCorners(target)
            onEnd?.invoke()
        }
    }

    private fun easeBackProgress(progress: Float): Float {
        val remaining = 1f - progress
        return 1f - remaining * remaining * remaining
    }

    private fun cardScaleProgress(progress: Float): Float {
        return (progress / CARD_SCALE_PROGRESS_END).coerceIn(0f, 1f)
    }

    private fun cardHorizontalScaleProgress(progress: Float): Float {
        val baseProgress = cardScaleProgress(progress)
        val squeezeProgress = ((progress - CARD_SCALE_PROGRESS_END) / (1f - CARD_SCALE_PROGRESS_END)).coerceIn(0f, 1f)
        return baseProgress + (CARD_HORIZONTAL_SCALE_MULTIPLIER - 1f) * squeezeProgress
    }

    private fun cardTranslationProgress(progress: Float): Float {
        val scaleProgress = cardScaleProgress(progress)
        val dropProgress = ((progress - CARD_SCALE_PROGRESS_END) / (1f - CARD_SCALE_PROGRESS_END)).coerceIn(0f, 1f)
        return CARD_TRANSLATION_Y_RATIO * scaleProgress +
            (CARD_DROP_TRANSLATION_Y_RATIO - CARD_TRANSLATION_Y_RATIO) * dropProgress
    }

    private fun backCommitAnimationDuration(): Long {
        if (!backActionFromGesture) return BACK_COMMIT_ANIMATION_DURATION_MS
        val remainingProgress = (1f - backGestureProgress).coerceIn(0f, 1f)
        val easedRemaining = remainingProgress * remainingProgress
        return (BACK_COMMIT_ANIMATION_MIN_DURATION_MS +
            (BACK_COMMIT_ANIMATION_MAX_DURATION_MS - BACK_COMMIT_ANIMATION_MIN_DURATION_MS) * easedRemaining).toLong()
    }

    private fun performBackAction() {
        backGestureInProgress = false
        if (!onBackAction()) {
            resetBackAnimation(animated = true) {
                backActionRunning = false
            }
            return
        }

        runBackCommitAnimation {
            finishBackNavigation()
        }
    }

    private fun finishBackNavigation() {
        predictiveBackCallback.isEnabled = false
        finish()
        clearPreviousPagePreview()
        backActionFromGesture = false
        backGestureProgress = 0f
        backActionRunning = false
    }

    protected open fun onBackStarted(backEvent: BackEventCompat) {}

    protected open fun onBackProgressed(backEvent: BackEventCompat) {}

    protected open fun onBackCancelled() {}

    protected open fun onBackAction(): Boolean = true

    protected fun setCustomBackEnabled(enabled: Boolean) {
        predictiveBackCallback.isEnabled = predictiveBackCallback.isEnabled || enabled
    }

    fun preparePreviousPagePreview(targetClass: Class<out BaseActivity<*>>) {
        val targetName = targetClass.name
        previousPageBitmaps.remove(targetName)?.recycle()
        createBackPreviewBitmap()?.let { preview ->
            previousPageBitmaps[targetName] = preview
        }
    }

    private fun clearPreviousPageBitmaps() {
        previousPageBitmaps.clear()
    }

    private fun clearPreviousPagePreview() {
        previousPagePreview?.let { preview ->
            (preview.parent as? ViewGroup)?.removeView(preview)
        }
        previousPagePreview = null
        previousPageBitmaps.remove(javaClass.name)?.recycle()
    }

    private fun createBackPreviewBitmap(): Bitmap? {
        val target = backAnimationTarget() ?: return null
        if (target.width == 0 || target.height == 0) return null
        return runCatching {
            Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                target.draw(Canvas(bitmap))
            }
        }.getOrNull()
    }

    private data class BackAnimationValues(
        val pivotX: Float,
        val pivotY: Float,
        val translationX: Float,
        val translationY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val alpha: Float
    )

    companion object {
        private val previousPageBitmaps = mutableMapOf<String, Bitmap>()

        private const val CARD_PIVOT_Y_RATIO = 0.72f
        private const val CARD_TRANSLATION_Y_RATIO = 0.24f
        private const val CARD_DROP_TRANSLATION_Y_RATIO = 0.82f
        private const val CARD_SCALE_PROGRESS_END = 0.62f
        private const val CARD_HORIZONTAL_SCALE_MULTIPLIER = 2.85f
        private const val CARD_SCALE_DELTA = 0.32f
        private const val CARD_ALPHA_DELTA = 0.18f
        private const val TOP_CORNER_RADIUS_DP = 24f
        private const val PAGE_TRANSLATION_RATIO = 1.0f
        private const val PAGE_ALPHA_DELTA = 0f
        private const val PREVIOUS_PAGE_OFFSET_RATIO = 0.36f
        private const val PREVIOUS_PAGE_MIN_ALPHA = 0.92f
        private const val BACK_BUTTON_PROGRESS = 0.42f
        private const val BACK_PRESS_ANIMATION_DURATION_MS = 150L
        private const val BACK_COMMIT_ANIMATION_DURATION_MS = 130L
        private const val BACK_COMMIT_ANIMATION_MIN_DURATION_MS = 120L
        private const val BACK_COMMIT_ANIMATION_MAX_DURATION_MS = 300L
        private const val BACK_CANCEL_ANIMATION_DURATION_MS = 220L
    }
}
