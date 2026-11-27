package com.khoi.handlecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.khoi.handlecontrol.core.app.AppGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dispatches system-wide swipe, scroll, tap, and back actions via Android Accessibility APIs.
 */
class HandControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        // The foreground app is learnt from the next window event (no window-content access needed).
    }

    /** Tracks which app is on screen, for "only work inside the selected apps". */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == _foregroundPackage.value) return
        if (AppGate.isTransientPackage(pkg, inputMethodPackages())) return
        if (!isAppSwitch(pkg, event.className?.toString())) return
        _foregroundPackage.value = pkg
    }

    /**
     * A real app switch shows an Activity. Dialogs, popups and menus also send window events
     * (with a View class name) but do not leave the current app.
     */
    private fun isAppSwitch(pkg: String, className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        return try {
            packageManager.getActivityInfo(ComponentName(pkg, className), 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            // Either not an Activity, or a package hidden by Android 11+ package visibility.
            // A hidden package cannot be checked: count it as a switch so we never stay "inside"
            // a selected app after the user left it.
            !isPackageVisible(pkg)
        }
    }

    private fun isPackageVisible(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun inputMethodPackages(): Set<String> {
        val imm = getSystemService(InputMethodManager::class.java) ?: return emptySet()
        return imm.enabledInputMethodList.mapTo(HashSet()) { it.packageName }
    }

    override fun onInterrupt() {
        // Required override; no-op.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
        _foregroundPackage.value = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
        _foregroundPackage.value = null
        super.onDestroy()
    }

    fun performSwipeUp(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = metrics.heightPixels * 0.75f
        val endY = metrics.heightPixels * 0.25f
        dispatchSwipe("SWIPE_UP", x, startY, x, endY, onResult)
    }

    fun performSwipeDown(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = metrics.heightPixels * 0.25f
        val endY = metrics.heightPixels * 0.75f
        dispatchSwipe("SWIPE_DOWN", x, startY, x, endY, onResult)
    }

    fun performSwipeLeft(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val y = metrics.heightPixels * 0.5f
        val startX = metrics.widthPixels * 0.8f
        val endX = metrics.widthPixels * 0.2f
        dispatchSwipe("SWIPE_LEFT", startX, y, endX, y, onResult)
    }

    fun performSwipeRight(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val y = metrics.heightPixels * 0.5f
        val startX = metrics.widthPixels * 0.2f
        val endX = metrics.widthPixels * 0.8f
        dispatchSwipe("SWIPE_RIGHT", startX, y, endX, y, onResult)
    }

    /** Short, slow drag upward: content scrolls down (reveals what is below). */
    fun performScrollDown(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = metrics.heightPixels * 0.65f
        val endY = metrics.heightPixels * 0.35f
        dispatchSwipe("SCROLL_DOWN", x, startY, x, endY, onResult, SCROLL_DURATION_MS)
    }

    /** Short, slow drag downward: content scrolls up. */
    fun performScrollUp(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = metrics.heightPixels * 0.35f
        val endY = metrics.heightPixels * 0.65f
        dispatchSwipe("SCROLL_UP", x, startY, x, endY, onResult, SCROLL_DURATION_MS)
    }

    fun performTap(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val y = metrics.heightPixels * 0.5f

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        dispatchGestureInternal("TAP", onResult, stroke)
    }

    /** Two quick taps at the centre of the screen (e.g. "like" on TikTok / Reels). */
    fun performDoubleTap(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val y = metrics.heightPixels * 0.5f

        val path = Path().apply { moveTo(x, y) }
        val first = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val second = GestureDescription.StrokeDescription(path, TAP_DURATION_MS + DOUBLE_TAP_GAP_MS, TAP_DURATION_MS)
        dispatchGestureInternal("DOUBLE_TAP", onResult, first, second)
    }

    fun performBackAction(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        try {
            val ok = performGlobalAction(GLOBAL_ACTION_BACK)
            onResult(ok, if (ok) "BACK executed" else "BACK rejected by system")
        } catch (e: Exception) {
            onResult(false, "BACK error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun dispatchSwipe(
        label: String,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        onResult: (Boolean, String) -> Unit,
        durationMs: Long = SWIPE_DURATION_MS
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        dispatchGestureInternal(label, onResult, stroke)
    }

    private fun dispatchGestureInternal(
        label: String,
        onResult: (Boolean, String) -> Unit,
        vararg strokes: GestureDescription.StrokeDescription
    ) {
        try {
            val builder = GestureDescription.Builder()
            strokes.forEach { builder.addStroke(it) }
            val gesture = builder.build()

            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onResult(true, "$label completed")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onResult(false, "$label cancelled")
                    }
                },
                null
            )

            if (!dispatched) {
                onResult(false, "$label failed to dispatch")
            }
        } catch (e: Exception) {
            onResult(false, "$label error: ${e.message ?: "Unknown error"}")
        }
    }

    companion object {
        private const val SWIPE_DURATION_MS = 280L
        private const val TAP_DURATION_MS = 80L
        /** Pause between the two taps; well under the ~300 ms Android double-tap timeout. */
        private const val DOUBLE_TAP_GAP_MS = 90L
        private const val SCROLL_DURATION_MS = 450L

        @Volatile
        var instance: HandControlAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _foregroundPackage = MutableStateFlow<String?>(null)
        /** Package of the app on screen, or null while Accessibility is off / nothing seen yet. */
        val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()
    }
}

