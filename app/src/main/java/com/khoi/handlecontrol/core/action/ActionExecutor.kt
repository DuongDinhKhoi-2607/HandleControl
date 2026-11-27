package com.khoi.handlecontrol.core.action

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.khoi.handlecontrol.service.HandControlAccessibilityService

/**
 * Executes a [ControlAction] on the Android system (Plan.md section 15):
 * AccessibilityService for touch/global actions, AudioManager for volume and media keys.
 *
 * Safe to call from any thread; work is posted to the main thread and [onResult] is called there.
 */
class ActionExecutor(context: Context) {

    private val volume = VolumeController(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun execute(action: ControlAction, onResult: (ActionResult) -> Unit = {}) {
        mainHandler.post { executeOnMain(action, onResult) }
    }

    private fun executeOnMain(action: ControlAction, onResult: (ActionResult) -> Unit) {
        when (action) {
            ControlAction.NONE -> onResult(ActionResult(true, "Không có action"))
            ControlAction.VOLUME_UP -> onResult(volume.volumeUp())
            ControlAction.VOLUME_DOWN -> onResult(volume.volumeDown())
            ControlAction.MEDIA_PLAY_PAUSE -> onResult(volume.mediaPlayPause())
            else -> {
                val service = HandControlAccessibilityService.instance
                if (service == null) {
                    onResult(ActionResult(false, "${action.name}: Accessibility Service đang OFF"))
                    return
                }
                val callback: (Boolean, String) -> Unit = { ok, msg -> onResult(ActionResult(ok, msg)) }
                when (action) {
                    // Vertical feeds: next video is below, so the finger drags up.
                    ControlAction.NEXT, ControlAction.SWIPE_UP -> service.performSwipeUp(callback)
                    ControlAction.PREVIOUS, ControlAction.SWIPE_DOWN -> service.performSwipeDown(callback)
                    ControlAction.SWIPE_LEFT -> service.performSwipeLeft(callback)
                    ControlAction.SWIPE_RIGHT -> service.performSwipeRight(callback)
                    ControlAction.PLAY_PAUSE, ControlAction.CLICK -> service.performTap(callback)
                    ControlAction.BACK -> service.performBackAction(callback)
                    ControlAction.SCROLL_UP -> service.performScrollUp(callback)
                    ControlAction.SCROLL_DOWN -> service.performScrollDown(callback)
                    else -> onResult(ActionResult(false, "Action không hỗ trợ: ${action.name}"))
                }
            }
        }
    }
}
