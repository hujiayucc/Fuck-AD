package com.hujiayucc.hook.ui.activity

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
    private var backGestureInProgress = false
    private var backActionRunning = false
    private var backSwipeDirection = 1f

    protected val predictiveBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            backGestureInProgress = true
            backActionRunning = false
            updateBackSwipeDirection(backEvent)
            applyBackAnimation(backEvent.progress)
            onBackStarted(backEvent)
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            applyBackAnimation(backEvent.progress)
            onBackProgressed(backEvent)
        }

        override fun handleOnBackCancelled() {
            backGestureInProgress = false
            backActionRunning = false
            resetBackAnimation(animated = true)
            onBackCancelled()
        }

        override fun handleOnBackPressed() {
            if (backActionRunning) return
            backActionRunning = true
            if (backGestureInProgress) {
                performBackAction()
            } else {
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
        override fun onLowMemory() {}

        override fun onTrimMemory(level: Int) {}
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
        return content.getChildAt(0) ?: content
    }

    private fun updateBackSwipeDirection(backEvent: BackEventCompat) {
        val width = backAnimationTarget()?.width ?: 0
        backSwipeDirection = if (width > 0 && backEvent.touchX > width / 2f) -1f else 1f
    }

    private fun applyBackAnimation(progress: Float) {
        val target = backAnimationTarget() ?: return
        val easedProgress = easeBackProgress(progress.coerceIn(0f, 1f))
        target.animate().cancel()
        target.pivotX = if (backSwipeDirection > 0f) 0f else target.width.toFloat()
        target.pivotY = target.height / 2f
        target.translationX = backSwipeDirection * target.width * BACK_TRANSLATION_RATIO * easedProgress
        target.scaleX = 1f - BACK_SCALE_DELTA * easedProgress
        target.scaleY = 1f - BACK_SCALE_DELTA * easedProgress
        target.alpha = 1f - BACK_ALPHA_DELTA * easedProgress
    }

    private fun runBackPressAnimation(onEnd: () -> Unit) {
        val target = backAnimationTarget()
        if (target == null || target.width == 0) {
            onEnd()
            return
        }

        backSwipeDirection = 1f
        target.animate().cancel()
        target.pivotX = 0f
        target.pivotY = target.height / 2f
        target.animate()
            .translationX(target.width * BACK_TRANSLATION_RATIO)
            .scaleX(1f - BACK_SCALE_DELTA)
            .scaleY(1f - BACK_SCALE_DELTA)
            .alpha(1f - BACK_ALPHA_DELTA)
            .setDuration(BACK_PRESS_ANIMATION_DURATION_MS)
            .setInterpolator(backAnimationInterpolator)
            .withEndAction { onEnd() }
            .start()
    }

    private fun resetBackAnimation(animated: Boolean) {
        val target = backAnimationTarget() ?: return
        target.animate().cancel()
        if (animated) {
            target.animate()
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(BACK_CANCEL_ANIMATION_DURATION_MS)
                .setInterpolator(backAnimationInterpolator)
                .start()
        } else {
            target.translationX = 0f
            target.scaleX = 1f
            target.scaleY = 1f
            target.alpha = 1f
        }
    }

    private fun easeBackProgress(progress: Float): Float {
        return 1f - (1f - progress) * (1f - progress)
    }

    private fun performBackAction() {
        backGestureInProgress = false
        onBackAction()
        if (!isFinishing && !isDestroyed) {
            resetBackAnimation(animated = true)
            backActionRunning = false
        }
    }

    protected open fun onBackStarted(backEvent: BackEventCompat) {}

    protected open fun onBackProgressed(backEvent: BackEventCompat) {}

    protected open fun onBackCancelled() {}

    protected open fun onBackAction() {
        predictiveBackCallback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        if (!isFinishing && !isDestroyed) {
            predictiveBackCallback.isEnabled = true
        }
    }

    protected fun setCustomBackEnabled(enabled: Boolean) {
        // Keep the callback active so every back path can drive the animation.
        predictiveBackCallback.isEnabled = predictiveBackCallback.isEnabled || enabled
    }

    private companion object {
        private const val BACK_TRANSLATION_RATIO = 0.08f
        private const val BACK_SCALE_DELTA = 0.04f
        private const val BACK_ALPHA_DELTA = 0.12f
        private const val BACK_PRESS_ANIMATION_DURATION_MS = 120L
        private const val BACK_CANCEL_ANIMATION_DURATION_MS = 160L
    }
}
