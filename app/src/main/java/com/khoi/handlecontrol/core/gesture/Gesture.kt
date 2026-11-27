package com.khoi.handlecontrol.core.gesture

/** Gestures recognised by the engine (Plan.md section 10). */
enum class Gesture(val label: String, val isStatic: Boolean) {
    OPEN_PALM("Open Palm", true),
    POINT_UP("Point Up", true),
    POINT_DOWN("Point Down", true),
    SWIPE_LEFT("Swipe Left", false),
    SWIPE_RIGHT("Swipe Right", false);
}

/** States of the gesture state machine (Plan.md section 11). */
enum class EngineState {
    IDLE,
    TRACKING,
    ARMED,
    CONFIRMING,
    EXECUTE,
    COOLDOWN
}

/**
 * Tunable parameters. Distances are measured in "palm units" (wrist → middle-finger MCP),
 * so the same values work whether the hand is 0.5 m or 1.5 m away from the camera.
 */
data class GestureConfig(
    /** Frames whose handedness score is below this are ignored (Plan.md section 13). */
    val minConfidence: Float = 0.60f,
    /** A hand missing for less than this is treated as a detection glitch, not a release. */
    val lostToleranceMs: Long = 300L,

    // Static gestures
    /** How long a static pose must be held before it fires. */
    val staticHoldMs: Long = 500L,
    /** A pose may disappear for this long during the hold without restarting it. */
    val poseFlickerToleranceMs: Long = 150L,
    /** Above this hand speed (palms/s) a pose is not considered "held". */
    val staticMaxSpeed: Float = 1.2f,
    val staticCooldownMs: Long = 800L,
    /** A pose that already fired must be absent this long before it can fire again. */
    val staticReleaseMs: Long = 300L,

    // Swipes
    val swipeMinDistance: Float = 1.2f,
    val swipeMinVelocity: Float = 2.0f,
    val swipeMaxDurationMs: Long = 800L,
    val swipeMinDurationMs: Long = 60L,
    /** |dx| must be at least this many times |dy|. */
    val swipeHorizontalRatio: Float = 1.5f,
    val swipeCooldownMs: Long = 700L,
    /** After a swipe, the opposite direction is blocked this long (the hand travelling back). */
    val swipeReturnSuppressMs: Long = 1200L,
    /** The hand must have been tracked this long before a swipe may start (hand entering the frame). */
    val swipeMinTrackingMs: Long = 150L,

    /** Smoothing for the palm-size estimate. */
    val palmSizeSmoothing: Float = 0.3f,
    val enabledGestures: Set<Gesture> = Gesture.entries.toSet()
)

/** A gesture that passed the state machine and should be executed. */
data class GestureEvent(val gesture: Gesture, val timestampMs: Long)

/** Snapshot of the engine after a frame, for debug HUDs and the notification. */
data class GestureEngineSnapshot(
    val state: EngineState = EngineState.IDLE,
    val pose: Gesture? = null,
    val candidate: Gesture? = null,
    /** 0..1 progress of the static hold. */
    val holdProgress: Float = 0f,
    /** Hand speed in palms per second. */
    val speed: Float = 0f,
    val event: GestureEvent? = null
)
