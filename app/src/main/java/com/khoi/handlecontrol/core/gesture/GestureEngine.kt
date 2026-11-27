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
 * - Swipes are detected from palm-centre motion (distance, velocity, duration, direction).
 * - Static poses must be held still for [GestureConfig.staticHoldMs].
 * - A static pose fires once; it must be released (pose changes or hand leaves) before it fires again.
 * - After a swipe the opposite direction is suppressed, so bringing the hand back is not a swipe.
 *
 * Not thread-safe: call [process] from a single thread (the MediaPipe result thread).
 * Time comes only from [HandFrame.timestampMs], which keeps the engine deterministic in tests.
 */
class GestureEngine(config: GestureConfig = GestureConfig()) {

    @Volatile
    var config: GestureConfig = config

    private data class Sample(val t: Long, val x: Float, val y: Float)

    private val history = ArrayDeque<Sample>()
    private var state = EngineState.IDLE
    private var lastSeenMs = Long.MIN_VALUE
    private var trackingSinceMs = 0L
    private var palmSize = 0f

    private var cooldownUntilMs = Long.MIN_VALUE
    private var suppressedSwipe: Gesture? = null
    private var suppressUntilMs = Long.MIN_VALUE

    private var candidate: Gesture? = null
    private var candidateSinceMs = 0L
    private var candidateLastSeenMs = 0L
    /** Static gesture that already fired and has not been released yet. */
    private var latchedStatic: Gesture? = null
    private var latchedLastSeenMs = 0L

    fun reset() {
        history.clear()
        state = EngineState.IDLE
        lastSeenMs = Long.MIN_VALUE
        palmSize = 0f
        cooldownUntilMs = Long.MIN_VALUE
        suppressedSwipe = null
        suppressUntilMs = Long.MIN_VALUE
        clearCandidate()
        latchedStatic = null
    }

    fun process(frame: HandFrame): GestureEngineSnapshot {
        val cfg = config
        val t = frame.timestampMs
        if (state == EngineState.EXECUTE) state = EngineState.COOLDOWN

        val valid = frame.landmarks.size >= 21 && frame.confidence >= cfg.minConfidence
        if (!valid) return handleNoHand(t, cfg)

        if (lastSeenMs == Long.MIN_VALUE) trackingSinceMs = t
        if (state == EngineState.IDLE) state = EngineState.TRACKING
        lastSeenMs = t

        val aspect = if (frame.aspect > 0f) frame.aspect else 1f
        val size = StaticGestureClassifier.palmSize(frame.landmarks, aspect)
        if (size > 0f) {
            palmSize = if (palmSize <= 0f) size else palmSize + cfg.palmSizeSmoothing * (size - palmSize)
        }
        val (cx, cy) = StaticGestureClassifier.palmCenter(frame.landmarks, aspect)
        history.addLast(Sample(t, cx, cy))
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

        if (t < cooldownUntilMs) {
            state = EngineState.COOLDOWN
            // Motion during cooldown must not build up into a swipe afterwards.
            history.clear()
            history.addLast(Sample(t, cx, cy))
            clearCandidate()
            return GestureEngineSnapshot(state, pose, null, 0f, speed)
        }

        detectSwipe(t, cfg)?.let { swipe ->
            return fire(swipe, t, cfg, pose, speed)
        }

        if (speed > cfg.staticMaxSpeed) {
            // The hand is moving: maybe the start of a swipe, certainly not a held pose.
            state = EngineState.ARMED
            clearCandidate()
            return GestureEngineSnapshot(state, pose, null, 0f, speed)
        }

        return updateStatic(pose, t, cfg, speed)
    }

    private fun handleNoHand(t: Long, cfg: GestureConfig): GestureEngineSnapshot {
        if (lastSeenMs != Long.MIN_VALUE && t - lastSeenMs <= cfg.lostToleranceMs) {
            // Short dropout: keep everything, just report the current state.
            return GestureEngineSnapshot(state, null, candidate, holdProgress(t, cfg), 0f)
        }
        history.clear()
        clearCandidate()
        latchedStatic = null
        palmSize = 0f
        lastSeenMs = Long.MIN_VALUE
        state = if (t < cooldownUntilMs) EngineState.COOLDOWN else EngineState.IDLE
        return GestureEngineSnapshot(state)
    }

    private fun updateStatic(pose: Gesture?, t: Long, cfg: GestureConfig, speed: Float): GestureEngineSnapshot {
        if (pose == null || pose == latchedStatic) {
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
        if (t - candidateSinceMs >= cfg.staticHoldMs) {
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
        if (gesture.isStatic) {
            cooldownUntilMs = t + cfg.staticCooldownMs
        } else {
            cooldownUntilMs = t + cfg.swipeCooldownMs
            suppressedSwipe = if (gesture == Gesture.SWIPE_LEFT) Gesture.SWIPE_RIGHT else Gesture.SWIPE_LEFT
            suppressUntilMs = t + cfg.swipeReturnSuppressMs
        }
        history.clear()
        clearCandidate()
        return GestureEngineSnapshot(state, pose, null, 1f, speed, GestureEvent(gesture, t))
    }

    private fun detectSwipe(t: Long, cfg: GestureConfig): Gesture? {
        if (palmSize <= 0f || history.size < 2) return null
        val newest = history.last()
        for (start in history) {
            val dt = newest.t - start.t
            if (dt < cfg.swipeMinDurationMs) break // history is ordered, later samples are even closer
            if (start.t - trackingSinceMs < cfg.swipeMinTrackingMs) continue

            val dx = (newest.x - start.x) / palmSize
            val dy = (newest.y - start.y) / palmSize
            if (abs(dx) < cfg.swipeMinDistance) continue
            if (abs(dx) < cfg.swipeHorizontalRatio * abs(dy)) continue
            if (abs(dx) / (dt / 1000f) < cfg.swipeMinVelocity) continue

            // Mirrored front camera: moving the hand to the user's right increases x.
            val swipe = if (dx > 0f) Gesture.SWIPE_RIGHT else Gesture.SWIPE_LEFT
            if (swipe !in cfg.enabledGestures) return null
            if (swipe == suppressedSwipe && t < suppressUntilMs) return null
            return swipe
        }
        return null
    }

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
        if (candidate == null || cfg.staticHoldMs <= 0) return 0f
        return ((t - candidateSinceMs).toFloat() / cfg.staticHoldMs).coerceIn(0f, 1f)
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
