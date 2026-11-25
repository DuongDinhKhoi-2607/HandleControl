package com.khoi.handlecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 0 — Feature 3: AccessibilityService Spike.
 * Dispatches system-wide swipe, tap, and back actions via Android Accessibility APIs.
 */
class HandControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for gesture dispatching in Phase 0 spike.
    }

    override fun onInterrupt() {
        // Required override; no-op.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
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

    fun performTap(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val y = metrics.heightPixels * 0.5f

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        dispatchGestureInternal("TAP", stroke, onResult)
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
        onResult: (Boolean, String) -> Unit
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS)
        dispatchGestureInternal(label, stroke, onResult)
    }

    private fun dispatchGestureInternal(
        label: String,
        stroke: GestureDescription.StrokeDescription,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

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

        @Volatile
        var instance: HandControlAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}

