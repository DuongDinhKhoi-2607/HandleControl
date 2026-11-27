package com.khoi.handlecontrol.core.gesture

import com.khoi.handlecontrol.core.vision.LandmarkPoint
import kotlin.math.abs
import kotlin.math.hypot

/** One frame of input for the engine. [landmarks] is empty when no hand was found. */
data class HandFrame(
    val timestampMs: Long,
    val landmarks: List<LandmarkPoint>,
    val confidence: Float,
    /** imageWidth / imageHeight. */
    val aspect: Float
)

/**
 * Turns a stream of hand landmarks into discrete gesture events.
 *
 * State machine (Plan.md section 11):
 * IDLE → TRACKING → ARMED → CONFIRMING → EXECUTE → COOLDOWN → TRACKING/IDLE
 *
 * - Swipes (up / down / left / right) are detected from palm-centre motion
 *   (distance, velocity, duration, dominant axis).
 * - Vertical swipes need extra guards: raising the hand into the frame is an upward movement and
 *   lowering it out of the frame is a downward one, so they must start after the hand has been
 *   tracked for a while and the hand must still be visible shortly after the swipe.
 * - Static poses (Point Up/Down, Thumb Up/Left) must be held still for [GestureConfig.staticHoldMs],
 *   the fist for [GestureConfig.fistHoldMs]. An open palm is not a gesture (it is used to swipe).
 * - After Like / Back the hand usually closes into a fist: the fist stays locked until the palm
 *   opens or the hand leaves, so it does not become Play/Pause.
 * - After any gesture fires, every gesture is blocked for at least [GestureConfig.actionGapMs].
 * - A static pose fires once; it must be released (pose changes or hand leaves) before it fires again.
 * - After a swipe the opposite direction is suppressed, so bringing the hand back is not a swipe.
 *
 * Not thread-safe: call [process] from a single thread (the MediaPipe result thread).
 * Time comes only from [HandFrame.timestampMs], which keeps the engine deterministic in tests.
 */
class GestureEngine(config: GestureConfig = GestureConfig()) {

    @Volatile
    var config: GestureConfig = config

    /** [open] = palm shown with at least 3 fingers extended in this frame. */
    private data class Sample(val t: Long, val x: Float, val y: Float, val open: Boolean)

    private val history = ArrayDeque<Sample>()
    private var state = EngineState.IDLE
    private var lastSeenMs = Long.MIN_VALUE
    private var trackingSinceMs = 0L
    private var palmSize = 0f

    /** Blocks every gesture (set after any gesture fires). */
    private var cooldownUntilMs = Long.MIN_VALUE
    /** Blocks only static poses; longer than the global gap so poses do not repeat too fast. */
    private var staticCooldownUntilMs = Long.MIN_VALUE
    /** After a swipe, the pose the hand ends in is latched so stopping the hand does not fire it. */
    private var latchPoseAfterMotion = false
    private var suppressedSwipe: Gesture? = null
    private var suppressUntilMs = Long.MIN_VALUE

    /** Vertical swipe waiting for the hand to settle inside the frame before it fires. */
    private var pendingSwipe: Gesture? = null
    private var pendingSinceMs = 0L

    private var candidate: Gesture? = null
    private var candidateSinceMs = 0L
    private var candidateLastSeenMs = 0L
    /** Static gesture that already fired and has not been released yet. */
    private var latchedStatic: Gesture? = null
    private var latchedLastSeenMs = 0L
    /** Set after a thumb gesture fired; cleared when the palm opens or the hand leaves. */
    private var fistLocked = false

    fun reset() {
        fistLocked = false
        history.clear()
        state = EngineState.IDLE
        lastSeenMs = Long.MIN_VALUE
        palmSize = 0f
        cooldownUntilMs = Long.MIN_VALUE
        staticCooldownUntilMs = Long.MIN_VALUE
        latchPoseAfterMotion = false
        suppressedSwipe = null
        suppressUntilMs = Long.MIN_VALUE
        pendingSwipe = null
        clearCandidate()
        latchedStatic = null
    }

    fun process(frame: HandFrame): GestureEngineSnapshot {
        val cfg = config
        val t = frame.timestampMs
        if (state == EngineState.EXECUTE) state = EngineState.COOLDOWN

        val aspect = if (frame.aspect > 0f) frame.aspect else 1f
        // Only a whole palm inside the frame counts: a forearm, a hand half out of the frame
        // or a tiny far-away false detection is treated as "no hand".
        val valid = frame.landmarks.size >= 21 &&
            frame.confidence >= cfg.minConfidence &&
            StaticGestureClassifier.isWholeHandInFrame(frame.landmarks, aspect, cfg.frameMargin, cfg.minPalmSize)
        if (!valid) return handleNoHand(t, cfg)

        if (lastSeenMs == Long.MIN_VALUE) trackingSinceMs = t
        if (state == EngineState.IDLE) state = EngineState.TRACKING
        lastSeenMs = t

        val size = StaticGestureClassifier.palmSize(frame.landmarks, aspect)
        if (size > 0f) {
            palmSize = if (palmSize <= 0f) size else palmSize + cfg.palmSizeSmoothing * (size - palmSize)
        }
        val (cx, cy) = StaticGestureClassifier.palmCenter(frame.landmarks, aspect)
        val open = StaticGestureClassifier.isOpenHand(frame.landmarks, aspect)
        if (open) fistLocked = false
        history.addLast(Sample(t, cx, cy, open))
        while (history.isNotEmpty() && t - history.first().t > cfg.swipeMaxDurationMs) {
            history.removeFirst()
        }

        val pose = StaticGestureClassifier.classify(frame.landmarks, aspect)
            ?.takeIf { it in cfg.enabledGestures }
        val speed = currentSpeed(t)

        // The latched pose is released only after it has been gone for a while,
        // so a one-frame misclassification while holding it does not re-arm it.
        if (pose != null && pose == latchedStatic) {
            latchedLastSeenMs = t
        } else if (latchedStatic != null && t - latchedLastSeenMs > cfg.staticReleaseMs) {
            latchedStatic = null
        }

        if (latchPoseAfterMotion && pose != null && latchedStatic == null) {
            latchedStatic = pose
            latchedLastSeenMs = t
        }

        if (t < cooldownUntilMs) {
            state = EngineState.COOLDOWN
            // Motion during cooldown must not build up into a swipe afterwards.
            history.clear()
            history.addLast(Sample(t, cx, cy, open))
            clearCandidate()
            return GestureEngineSnapshot(state, pose, null, 0f, speed)
        }
        latchPoseAfterMotion = false

        pendingSwipe?.let { pending ->
            val settled = speed < cfg.swipeVerticalSettleSpeed
            if (!settled && t - pendingSinceMs < cfg.swipeVerticalMaxPendingMs) {
                state = EngineState.CONFIRMING
                return GestureEngineSnapshot(state, pose, pending, 0f, speed)
            }
            pendingSwipe = null
            if (cy < cfg.swipeVerticalEdgeMargin || cy > 1f - cfg.swipeVerticalEdgeMargin) {
                // The hand is at the edge of the frame: it is leaving, not swiping.
                history.clear()
                latchedStatic = pose
                latchedLastSeenMs = t
                state = EngineState.TRACKING
                return GestureEngineSnapshot(state, pose, null, 0f, speed)
            }
            return fire(pending, t, cfg, pose, speed)
        }

        detectSwipe(t, cfg)?.let { swipe ->
            if (isVertical(swipe)) {
                pendingSwipe = swipe
                pendingSinceMs = t
                state = EngineState.CONFIRMING
                clearCandidate()
                return GestureEngineSnapshot(state, pose, swipe, 0f, speed)
            }
            return fire(swipe, t, cfg, pose, speed)
        }

        if (speed > cfg.staticMaxSpeed) {
            // The hand is moving: maybe the start of a swipe, certainly not a held pose.
            state = EngineState.ARMED
            clearCandidate()
            return GestureEngineSnapshot(state, pose, null, 0f, speed)
        }

        if (t < staticCooldownUntilMs) {
            state = EngineState.COOLDOWN
            clearCandidate()
            return GestureEngineSnapshot(state, pose, null, 0f, speed)
        }

        return updateStatic(pose, t, cfg, speed)
    }

    private fun handleNoHand(t: Long, cfg: GestureConfig): GestureEngineSnapshot {
        if (lastSeenMs != Long.MIN_VALUE && t - lastSeenMs <= cfg.lostToleranceMs) {
            // Short dropout: keep everything, just report the current state.
            return GestureEngineSnapshot(state, null, candidate ?: pendingSwipe, holdProgress(t, cfg), 0f)
        }
        history.clear()
        clearCandidate()
        latchedStatic = null
        palmSize = 0f
        lastSeenMs = Long.MIN_VALUE
        latchPoseAfterMotion = false
        fistLocked = false
        // A vertical swipe followed by the hand disappearing was the hand leaving the frame.
        pendingSwipe = null
        state = if (t < maxOf(cooldownUntilMs, staticCooldownUntilMs)) EngineState.COOLDOWN else EngineState.IDLE
        return GestureEngineSnapshot(state)
    }

    private fun updateStatic(pose: Gesture?, t: Long, cfg: GestureConfig, speed: Float): GestureEngineSnapshot {
        if (pose == null || pose == latchedStatic || (pose == Gesture.FIST && fistLocked)) {
            val current = candidate
            if (current != null && t - candidateLastSeenMs <= cfg.poseFlickerToleranceMs) {
                // Brief misclassification during a hold: keep the candidate.
                state = EngineState.CONFIRMING
                return GestureEngineSnapshot(state, pose, current, holdProgress(t, cfg), speed)
            }
            clearCandidate()
            state = EngineState.TRACKING
            return GestureEngineSnapshot(state, pose, null, 0f, speed)
        }

        if (pose != candidate) {
            candidate = pose
            candidateSinceMs = t
            candidateLastSeenMs = t
            state = EngineState.ARMED
            return GestureEngineSnapshot(state, pose, pose, 0f, speed)
        }

        candidateLastSeenMs = t
        if (t - candidateSinceMs >= holdTimeFor(pose, cfg)) {
            latchedStatic = pose
            latchedLastSeenMs = t
            return fire(pose, t, cfg, pose, speed)
        }
        state = EngineState.CONFIRMING
        return GestureEngineSnapshot(state, pose, pose, holdProgress(t, cfg), speed)
    }

    private fun fire(
        gesture: Gesture,
        t: Long,
        cfg: GestureConfig,
        pose: Gesture?,
        speed: Float
    ): GestureEngineSnapshot {
        state = EngineState.EXECUTE
        when (gesture.kind) {
            GestureKind.STATIC -> {
                cooldownUntilMs = t + cfg.actionGapMs
                staticCooldownUntilMs = t + maxOf(cfg.staticCooldownMs, cfg.actionGapMs)
                if (gesture == Gesture.THUMB_UP || gesture == Gesture.THUMB_LEFT) fistLocked = true
            }
            GestureKind.SWIPE -> {
                cooldownUntilMs = t + maxOf(cfg.swipeCooldownMs, cfg.actionGapMs)
                suppressedSwipe = gesture.opposite
                suppressUntilMs = t + cfg.swipeReturnSuppressMs
                latchMotionEndPose(pose, t)
            }
        }
        history.clear()
        clearCandidate()
        return GestureEngineSnapshot(state, pose, null, 1f, speed, GestureEvent(gesture, t))
    }

    /**
     * Swipes are usually done with an open palm:
     * the pose the hand ends in must not count as a new Play/Pause.
     */
    private fun latchMotionEndPose(pose: Gesture?, t: Long) {
        latchedStatic = pose
        latchedLastSeenMs = t
        latchPoseAfterMotion = true
    }

    private fun detectSwipe(t: Long, cfg: GestureConfig): Gesture? {
        if (palmSize <= 0f || history.size < 2) return null
        val newest = history.last()
        for (start in history) {
            val dt = newest.t - start.t
            if (dt < cfg.swipeMinDurationMs) break // history is ordered, later samples are even closer
            val trackedMs = start.t - trackingSinceMs
            if (trackedMs < cfg.swipeMinTrackingMs) continue

            val dx = (newest.x - start.x) / palmSize
            val dy = (newest.y - start.y) / palmSize
            val horizontal = abs(dx) >= cfg.swipeMinDistance && abs(dx) >= cfg.swipeAxisRatioHorizontal * abs(dy)
            val vertical = abs(dy) >= cfg.swipeMinDistanceVertical &&
                abs(dy) >= cfg.swipeAxisRatioVertical * abs(dx) &&
                trackedMs >= cfg.swipeVerticalMinTrackingMs
            if (!horizontal && !vertical) continue

            val distance = if (horizontal) abs(dx) else abs(dy)
            val minVelocity = if (horizontal) cfg.swipeMinVelocityHorizontal else cfg.swipeMinVelocity
            if (distance / (dt / 1000f) < minVelocity) continue

            // A swipe is made with an open palm. Moving the arm with a fist / relaxed hand
            // (bringing the hand in, scratching, reaching for the phone) is not a command.
            val openRatio = if (horizontal) cfg.swipeMinOpenRatioHorizontal else cfg.swipeMinOpenRatioVertical
            if (!isOpenDuring(start, openRatio)) continue

            // Mirrored front camera: moving the hand to the user's right increases x.
            // Image y grows downward: moving the hand up decreases y.
            val swipe = when {
                horizontal && dx > 0f -> Gesture.SWIPE_RIGHT
                horizontal -> Gesture.SWIPE_LEFT
                dy < 0f -> Gesture.SWIPE_UP
                else -> Gesture.SWIPE_DOWN
            }
            if (swipe !in cfg.enabledGestures) continue
            if (swipe == suppressedSwipe && t < suppressUntilMs) return null
            return swipe
        }
        return null
    }

    /** True when enough frames from [start] to now show an open palm (motion blur may spoil a few). */
    private fun isOpenDuring(start: Sample, minRatio: Float): Boolean {
        if (minRatio <= 0f) return true
        var total = 0
        var open = 0
        for (sample in history) {
            if (sample.t < start.t) continue
            total++
            if (sample.open) open++
        }
        return total > 0 && open >= minRatio * total
    }

    private fun isVertical(g: Gesture) = g == Gesture.SWIPE_UP || g == Gesture.SWIPE_DOWN

    /** Speed in palms/s over roughly the last 100 ms. */
    private fun currentSpeed(t: Long): Float {
        if (palmSize <= 0f || history.size < 2) return 0f
        val newest = history.last()
        var reference = history.first()
        for (sample in history) {
            if (t - sample.t >= SPEED_WINDOW_MS) reference = sample else break
        }
        val dt = newest.t - reference.t
        if (dt <= 0) return 0f
        val dist = hypot(newest.x - reference.x, newest.y - reference.y) / palmSize
        return dist / (dt / 1000f)
    }

    private fun holdProgress(t: Long, cfg: GestureConfig): Float {
        val current = candidate ?: return 0f
        val need = holdTimeFor(current, cfg)
        if (need <= 0) return 0f
        return ((t - candidateSinceMs).toFloat() / need).coerceIn(0f, 1f)
    }

    private fun holdTimeFor(pose: Gesture, cfg: GestureConfig): Long = when (pose) {
        Gesture.FIST -> cfg.fistHoldMs
        Gesture.THUMB_LEFT -> cfg.thumbLeftHoldMs
        else -> cfg.staticHoldMs
    }

    private fun clearCandidate() {
        candidate = null
        candidateSinceMs = 0L
        candidateLastSeenMs = 0L
    }

    private companion object {
        const val SPEED_WINDOW_MS = 100L
    }
}
