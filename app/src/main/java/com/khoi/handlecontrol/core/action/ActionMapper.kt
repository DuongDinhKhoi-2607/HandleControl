package com.khoi.handlecontrol.core.action

import com.khoi.handlecontrol.core.gesture.Gesture

/**
 * Generic phone actions (Plan.md section 15). None of them is specific to one app:
 * NEXT / PREVIOUS are vertical feed swipes, which TikTok, YouTube Shorts, Reels and Instagram all use.
 */
enum class ControlAction(val label: String) {
    NONE("Không làm gì"),
    NEXT("Video tiếp theo"),
    PREVIOUS("Video trước"),
    PLAY_PAUSE("Play / Pause (chạm giữa màn hình)"),
    MEDIA_PLAY_PAUSE("Play / Pause (phím media)"),
    VOLUME_UP("Âm lượng +"),
    VOLUME_DOWN("Âm lượng -"),
    SWIPE_UP("Vuốt lên"),
    SWIPE_DOWN("Vuốt xuống"),
    SWIPE_LEFT("Vuốt sang trái"),
    SWIPE_RIGHT("Vuốt sang phải"),
    CLICK("Chạm giữa màn hình"),
    BACK("Quay lại"),
    SCROLL_UP("Cuộn lên"),
    SCROLL_DOWN("Cuộn xuống");

    /** Volume and media keys go through AudioManager and work without Accessibility. */
    val needsAccessibility: Boolean
        get() = this != NONE && this != VOLUME_UP && this != VOLUME_DOWN && this != MEDIA_PLAY_PAUSE
}

/** Gesture → Action lookup (Plan.md section 14). Immutable; build a new one to change mappings. */
class ActionMapper(mapping: Map<Gesture, ControlAction> = DEFAULT_MAPPING) {

    val mapping: Map<Gesture, ControlAction> =
        Gesture.entries.associateWith { mapping[it] ?: ControlAction.NONE }

    fun map(gesture: Gesture): ControlAction = mapping.getValue(gesture)

    fun with(gesture: Gesture, action: ControlAction): ActionMapper =
        ActionMapper(mapping + (gesture to action))

    companion object {
        val DEFAULT_MAPPING: Map<Gesture, ControlAction> = mapOf(
            Gesture.SWIPE_RIGHT to ControlAction.NEXT,
            Gesture.SWIPE_LEFT to ControlAction.PREVIOUS,
            Gesture.OPEN_PALM to ControlAction.PLAY_PAUSE,
            Gesture.POINT_UP to ControlAction.VOLUME_UP,
            Gesture.POINT_DOWN to ControlAction.VOLUME_DOWN
        )
    }
}
