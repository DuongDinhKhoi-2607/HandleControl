package com.khoi.handlecontrol.core.gesture

import com.khoi.handlecontrol.core.gesture.SyntheticHand.Pose
import com.khoi.handlecontrol.core.vision.LandmarkPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureEngineTest {

    private val frameMs = 33L
    private var t = 1_000L
    private val engine = GestureEngine()
    private val fired = mutableListOf<Gesture>()

    private fun feed(landmarks: List<LandmarkPoint>, confidence: Float = 0.95f) {
        val snap = engine.process(HandFrame(t, landmarks, confidence, aspect = 1f))
        snap.event?.let { fired += it.gesture }
        t += frameMs
    }

    private fun hold(pose: Pose, ms: Long, rotationDeg: Float = 0f, cx: Float = 0.5f) {
        val end = t + ms
        while (t < end) feed(SyntheticHand.build(pose, cx = cx, rotationDeg = rotationDeg))
    }

    private fun noHand(ms: Long) {
        val end = t + ms
        while (t < end) feed(emptyList())
    }

    /** Moves a hand horizontally from [fromX] to [toX] over [ms] milliseconds. */
    private fun move(pose: Pose, fromX: Float, toX: Float, ms: Long) {
        val steps = (ms / frameMs).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val x = fromX + (toX - fromX) * i / steps
            feed(SyntheticHand.build(pose, cx = x))
        }
    }

    // --- Classifier ---------------------------------------------------------

    @Test
    fun classifier_recognises_poses() {
        assertEquals(Gesture.OPEN_PALM, StaticGestureClassifier.classify(SyntheticHand.build(Pose.OPEN_PALM), 1f))
        assertEquals(Gesture.POINT_UP, StaticGestureClassifier.classify(SyntheticHand.build(Pose.POINT), 1f))
        assertEquals(
            Gesture.POINT_DOWN,
            StaticGestureClassifier.classify(SyntheticHand.build(Pose.POINT, rotationDeg = 180f), 1f)
        )
        assertNull(StaticGestureClassifier.classify(SyntheticHand.build(Pose.FIST), 1f))
    }

    @Test
    fun classifier_ignores_sideways_point() {
        assertNull(StaticGestureClassifier.classify(SyntheticHand.build(Pose.POINT, rotationDeg = 90f), 1f))
    }

    // --- Static gestures ----------------------------------------------------

    @Test
    fun open_palm_held_fires_once() {
        hold(Pose.OPEN_PALM, 3_000)
        assertEquals(listOf(Gesture.OPEN_PALM), fired)
    }

    @Test
    fun open_palm_shorter_than_hold_time_does_not_fire() {
        hold(Pose.OPEN_PALM, 300)
        hold(Pose.FIST, 1_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun releasing_and_showing_again_fires_again() {
        hold(Pose.OPEN_PALM, 1_000)
        hold(Pose.FIST, 1_000)
        hold(Pose.OPEN_PALM, 1_000)
        assertEquals(listOf(Gesture.OPEN_PALM, Gesture.OPEN_PALM), fired)
    }

    @Test
    fun one_frame_glitch_while_holding_does_not_refire() {
        hold(Pose.OPEN_PALM, 1_500)
        feed(SyntheticHand.build(Pose.FIST))
        hold(Pose.OPEN_PALM, 1_500)
        assertEquals(listOf(Gesture.OPEN_PALM), fired)
    }

    @Test
    fun point_up_and_point_down() {
        hold(Pose.POINT, 800)
        noHand(600)
        hold(Pose.POINT, 800, rotationDeg = 180f)
        assertEquals(listOf(Gesture.POINT_UP, Gesture.POINT_DOWN), fired)
    }

    @Test
    fun low_confidence_is_ignored() {
        val end = t + 2_000
        while (t < end) feed(SyntheticHand.build(Pose.OPEN_PALM), confidence = 0.5f)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun disabled_gesture_never_fires() {
        engine.config = GestureConfig(enabledGestures = setOf(Gesture.SWIPE_LEFT, Gesture.SWIPE_RIGHT))
        hold(Pose.OPEN_PALM, 2_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    // --- Swipes -------------------------------------------------------------

    @Test
    fun fast_move_right_is_swipe_right_and_return_is_suppressed() {
        hold(Pose.FIST, 300, cx = 0.2f)
        move(Pose.FIST, 0.2f, 0.8f, 300)
        // Hand travels back right away: must not count as SWIPE_LEFT.
        move(Pose.FIST, 0.8f, 0.2f, 400)
        hold(Pose.FIST, 1_000, cx = 0.2f)
        assertEquals(listOf(Gesture.SWIPE_RIGHT), fired)
    }

    @Test
    fun fast_move_left_is_swipe_left() {
        hold(Pose.FIST, 300, cx = 0.8f)
        move(Pose.FIST, 0.8f, 0.2f, 300)
        assertEquals(listOf(Gesture.SWIPE_LEFT), fired)
    }

    @Test
    fun two_swipes_in_same_direction_after_cooldown() {
        hold(Pose.FIST, 300, cx = 0.2f)
        move(Pose.FIST, 0.2f, 0.8f, 300)
        noHand(800) // hand leaves frame and comes back on the left
        hold(Pose.FIST, 300, cx = 0.2f)
        move(Pose.FIST, 0.2f, 0.8f, 300)
        assertEquals(listOf(Gesture.SWIPE_RIGHT, Gesture.SWIPE_RIGHT), fired)
    }

    @Test
    fun slow_drift_is_not_a_swipe() {
        hold(Pose.FIST, 300, cx = 0.2f)
        move(Pose.FIST, 0.2f, 0.8f, 4_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun open_palm_swipe_does_not_trigger_play_pause() {
        hold(Pose.OPEN_PALM, 200, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.8f, 300)
        assertEquals(listOf(Gesture.SWIPE_RIGHT), fired)
    }

    @Test
    fun swipe_works_for_small_far_away_hand() {
        // Hand ~3x smaller (further away): distances are in palm units, so a shorter move still counts.
        val small = 0.07f
        var x = 0.4f
        val end = t + 300
        while (t < end) feed(SyntheticHand.build(Pose.FIST, cx = x, s = small))
        val steps = 9
        for (i in 0..steps) {
            x = 0.4f + 0.2f * i / steps
            feed(SyntheticHand.build(Pose.FIST, cx = x, s = small))
        }
        assertEquals(listOf(Gesture.SWIPE_RIGHT), fired)
    }
}
