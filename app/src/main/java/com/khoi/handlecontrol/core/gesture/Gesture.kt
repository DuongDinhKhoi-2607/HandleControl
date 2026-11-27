package com.khoi.handlecontrol.core.gesture

/** How a gesture is recognised. */
enum class GestureKind {
    /** A pose held still for [GestureConfig.staticHoldMs]. */
    STATIC,
    /** A fast movement of the palm. */
    SWIPE
}

/** Gestures recognised by the engine (Plan.md section 10). */
enum class Gesture(val label: String, val kind: GestureKind) {
    /** Closed fist, thumb tucked. (An open palm is not a gesture: it is only used to swipe.) */
    FIST("Nắm tay", GestureKind.STATIC),
    POINT_UP("Point Up", GestureKind.STATIC),
    POINT_DOWN("Point Down", GestureKind.STATIC),
    /** "Like": four fingers curled into a fist, thumb pointing up. */
    THUMB_UP("Like (ngón cái)", GestureKind.STATIC),
    /** Four fingers curled, thumb pointing to the user's left. */
    THUMB_LEFT("Ngón cái sang trái", GestureKind.STATIC),
    SWIPE_UP("Vuốt lên", GestureKind.SWIPE),
    SWIPE_DOWN("Vuốt xuống", GestureKind.SWIPE),
    SWIPE_LEFT("Vuốt trái", GestureKind.SWIPE),
    SWIPE_RIGHT("Vuốt phải", GestureKind.SWIPE);

    val isStatic: Boolean get() = kind == GestureKind.STATIC

    /** The swipe in the opposite direction (the hand travelling back), or null for non-swipes. */
    val opposite: Gesture?
        get() = when (this) {
            SWIPE_UP -> SWIPE_DOWN
            SWIPE_DOWN -> SWIPE_UP
            SWIPE_LEFT -> SWIPE_RIGHT
            SWIPE_RIGHT -> SWIPE_LEFT
            else -> null
        }
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
    val minConfidence: Float = 0.70f,
    /**
     * Wrist and the 5 palm joints must lie inside the frame, with this tolerance (0..1 of the frame).
     * A forearm entering the frame or a half-visible hand gets extrapolated points outside it.
     */
    val frameMargin: Float = 0.02f,
    /** Palm (wrist → middle MCP) smaller than this (fraction of frame height) is a false detection. */
    val minPalmSize: Float = 0.025f,
    /** A hand missing for less than this is treated as a detection glitch, not a release. */
    val lostToleranceMs: Long = 300L,

    // Static gestures
    /** How long a static pose must be held before it fires. */
    val staticHoldMs: Long = 500L,
    /**
     * The fist is held longer: the hand passes through a fist on its way to Like / Back,
     * and a quick fist on the way must not count as Play/Pause.
     */
    val fistHoldMs: Long = 800L,
    /** Back is disruptive (leaves the screen), so the thumb-left pose must be held longer. */
    val thumbLeftHoldMs: Long = 1000L,
    /** A pose may disappear for this long during the hold without restarting it. */
    val poseFlickerToleranceMs: Long = 150L,
    /** Above this hand speed (palms/s) a pose is not considered "held". */
    val staticMaxSpeed: Float = 1.2f,
    /** After a static pose fires, no static pose can fire for this long. */
    val staticCooldownMs: Long = 1500L,
    /**
     * After ANY gesture fires, every gesture is blocked this long, so one hand movement
     * (e.g. raising the palm, then moving it) does not trigger two actions in a row.
     */
    val actionGapMs: Long = 1000L,
    /** A pose that already fired must be absent this long before it can fire again. */
    val staticReleaseMs: Long = 300L,

    // Swipes
    /** Minimum travel (palm units) for Swipe Left / Right. */
    // Horizontal swipes get looser limits than vertical ones: the portrait frame is narrow, the arm
    // moves sideways in an arc, and the palm tends to turn edge-on while moving sideways.
    val swipeMinDistance: Float = 0.95f,
    /** Minimum travel for Swipe Up / Down. */
    val swipeMinDistanceVertical: Float = 1.35f,
    /** Average speed (palms/s) over a horizontal swipe. */
    val swipeMinVelocityHorizontal: Float = 1.7f,
    /** Average speed (palms/s) over a vertical swipe. */
    val swipeMinVelocity: Float = 2.2f,
    val swipeMaxDurationMs: Long = 800L,
    /** Shorter "swipes" are usually a landmark jump between two frames, not a real movement. */
    val swipeMinDurationMs: Long = 80L,
    /** Swipe Left / Right: |dx| must be at least this many times |dy|. */
    val swipeAxisRatioHorizontal: Float = 1.35f,
    /** Swipe Up / Down: |dy| must be at least this many times |dx|. */
    val swipeAxisRatioVertical: Float = 1.65f,
    /** Share of frames during a horizontal swipe that must show an open palm (0 = any hand shape). */
    val swipeMinOpenRatioHorizontal: Float = 0.35f,
    /** Same for vertical swipes. */
    val swipeMinOpenRatioVertical: Float = 0.55f,
    /**
     * Vertical swipes may only start this long after the hand appeared: raising the hand into
     * the frame is itself an upward movement and must not count as Swipe Up.
     */
    val swipeVerticalMinTrackingMs: Long = 350L,
    /**
     * A vertical swipe fires only once the hand slows below this speed (palms/s) while still in
     * the frame. Lowering the hand out of the frame (end of use) keeps moving until it is gone,
     * so it never counts as Swipe Down.
     */
    val swipeVerticalSettleSpeed: Float = 1.5f,
    /** ...or after this long, if the hand is still visible but has not fully stopped. */
    val swipeVerticalMaxPendingMs: Long = 500L,
    /** A vertical swipe ending this close (0..1 of frame height) to the top/bottom edge is the hand leaving. */
    val swipeVerticalEdgeMargin: Float = 0.08f,
    val swipeCooldownMs: Long = 1200L,
    /** After a swipe, the opposite direction is blocked this long (the hand travelling back). */
    val swipeReturnSuppressMs: Long = 1800L,
    /** The hand must have been tracked this long before a swipe may start (hand entering the frame). */
    val swipeMinTrackingMs: Long = 200L,

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
