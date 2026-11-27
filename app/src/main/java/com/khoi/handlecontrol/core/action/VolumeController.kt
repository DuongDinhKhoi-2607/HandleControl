package com.khoi.handlecontrol.core.action

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Media volume and media keys through AudioManager (Plan.md section 7).
 * One call = exactly one step, so a single gesture can never keep raising the volume.
 */
class VolumeController(context: Context) {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    fun volumeUp(): ActionResult = adjust(AudioManager.ADJUST_RAISE, "VOLUME +")

    fun volumeDown(): ActionResult = adjust(AudioManager.ADJUST_LOWER, "VOLUME -")

    fun mediaPlayPause(): ActionResult {
        val am = audioManager ?: return ActionResult(false, "AudioManager không khả dụng")
        return try {
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
            ActionResult(true, "MEDIA PLAY/PAUSE sent")
        } catch (e: Exception) {
            ActionResult(false, "MEDIA PLAY/PAUSE error: ${e.message}")
        }
    }

    private fun adjust(direction: Int, label: String): ActionResult {
        val am = audioManager ?: return ActionResult(false, "AudioManager không khả dụng")
        return try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            ActionResult(true, "$label → $current/$max")
        } catch (e: SecurityException) {
            // Thrown when Do Not Disturb blocks volume changes.
            ActionResult(false, "$label bị chặn (Không làm phiền?)")
        }
    }
}

data class ActionResult(val success: Boolean, val message: String)
