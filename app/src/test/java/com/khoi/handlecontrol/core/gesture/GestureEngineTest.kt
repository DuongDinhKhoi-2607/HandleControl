package com.khoi.handlecontrol.core.gesture

import com.khoi.handlecontrol.core.gesture.SyntheticHand.Pose
import com.khoi.handlecontrol.core.vision.LandmarkPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun holdAt(pose: Pose, ms: Long, cy: Float) {
        val end = t + ms
        while (t < end) feed(SyntheticHand.build(pose, cy = cy))
    }

    /** Moves a hand vertically from [fromY] to [toY] over [ms] milliseconds (image y grows downward). */
    private fun moveY(pose: Pose, fromY: Float, toY: Float, ms: Long) {
        val steps = (ms / frameMs).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val y = fromY + (toY - fromY) * i / steps
            feed(SyntheticHand.build(pose, cy = y))
        }
    }

    // --- Classifier ---------------------------------------------------------

    @Test
    fun classifier_recognises_poses() {
        assertEquals(Gesture.FIST, StaticGestureClassifier.classify(SyntheticHand.build(Pose.FIST), 1f))
        // An open palm is deliberately not a gesture.
        assertNull(StaticGestureClassifier.classify(SyntheticHand.build(Pose.OPEN_PALM), 1f))
        assertEquals(Gesture.POINT_UP, StaticGestureClassifier.classify(SyntheticHand.build(Pose.POINT), 1f))
        assertEquals(
            Gesture.POINT_DOWN,
            StaticGestureClassifier.classify(SyntheticHand.build(Pose.POINT, rotationDeg = 180f), 1f)
        )
    }

    @Test
    fun classifier_ignores_sideways_point() {
        assertNull(StaticGestureClassifier.classify(SyntheticHand.build(Pose.POINT, rotationDeg = 90f), 1f))
    }

    // --- Fist (Play / Pause) -------------------------------------------------

    @Test
    fun fist_held_fires_once() {
        hold(Pose.FIST, 3_000)
        assertEquals(listOf(Gesture.FIST), fired)
    }

    @Test
    fun fist_shorter_than_hold_time_does_not_fire() {
        hold(Pose.FIST, 600) // the fist needs 0.8 s
        hold(Pose.OPEN_PALM, 1_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun releasing_and_closing_fist_again_fires_again() {
        hold(Pose.FIST, 1_200)
        hold(Pose.OPEN_PALM, 1_000)
        hold(Pose.FIST, 1_200)
        assertEquals(listOf(Gesture.FIST, Gesture.FIST), fired)
    }

    @Test
    fun one_frame_glitch_while_holding_does_not_refire() {
        hold(Pose.FIST, 1_500)
        feed(SyntheticHand.build(Pose.OPEN_PALM))
        hold(Pose.FIST, 1_500)
        assertEquals(listOf(Gesture.FIST), fired)
    }

    @Test
    fun open_palm_held_does_nothing() {
        hold(Pose.OPEN_PALM, 3_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun point_up_and_point_down() {
        hold(Pose.POINT, 800)
        noHand(1_200) // wait out the 1.5 s static cooldown
        hold(Pose.POINT, 800, rotationDeg = 180f)
        assertEquals(listOf(Gesture.POINT_UP, Gesture.POINT_DOWN), fired)
    }

    @Test
    fun low_confidence_is_ignored() {
        val end = t + 2_000
        while (t < end) feed(SyntheticHand.build(Pose.FIST), confidence = 0.5f)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun disabled_gesture_never_fires() {
        engine.config = GestureConfig(enabledGestures = setOf(Gesture.SWIPE_LEFT, Gesture.SWIPE_RIGHT))
        hold(Pose.FIST, 2_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    // --- Swipes -------------------------------------------------------------

    @Test
    fun fast_move_right_is_swipe_right_and_return_is_suppressed() {
        hold(Pose.OPEN_PALM, 300, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.8f, 300)
        // Hand travels back right away: must not count as SWIPE_LEFT.
        move(Pose.OPEN_PALM, 0.8f, 0.2f, 400)
        hold(Pose.OPEN_PALM, 1_000, cx = 0.2f)
        assertEquals(listOf(Gesture.SWIPE_RIGHT), fired)
    }

    @Test
    fun fast_move_left_is_swipe_left() {
        hold(Pose.OPEN_PALM, 300, cx = 0.8f)
        move(Pose.OPEN_PALM, 0.8f, 0.2f, 300)
        assertEquals(listOf(Gesture.SWIPE_LEFT), fired)
    }

    @Test
    fun two_swipes_in_same_direction_after_cooldown() {
        hold(Pose.OPEN_PALM, 300, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.8f, 300)
        noHand(1_200) // hand leaves frame and comes back on the left
        hold(Pose.OPEN_PALM, 300, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.8f, 300)
        assertEquals(listOf(Gesture.SWIPE_RIGHT, Gesture.SWIPE_RIGHT), fired)
    }

    @Test
    fun slow_drift_is_not_a_swipe() {
        hold(Pose.OPEN_PALM, 300, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.8f, 4_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun swipe_right_after_play_pause_is_blocked_during_action_gap() {
        // Play/Pause fires at ~800 ms; a swipe 200 ms later is inside the 1 s rest time.
        hold(Pose.FIST, 1_000, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.8f, 300)
        assertEquals(listOf(Gesture.FIST), fired)
    }

    @Test
    fun swipe_right_after_play_pause_fires_once_rest_time_is_over() {
        hold(Pose.FIST, 2_000, cx = 0.2f)
        hold(Pose.OPEN_PALM, 300, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.8f, 300)
        assertEquals(listOf(Gesture.FIST, Gesture.SWIPE_RIGHT), fired)
    }

    @Test
    fun closing_fist_right_after_swipe_does_not_trigger_play_pause() {
        hold(Pose.OPEN_PALM, 200, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.8f, 300)
        hold(Pose.FIST, 2_000, cx = 0.8f)
        assertEquals(listOf(Gesture.SWIPE_RIGHT), fired)
    }

    @Test
    fun swipe_works_for_small_far_away_hand() {
        // Hand ~3x smaller (further away): distances are in palm units, so a shorter move still counts.
        val small = 0.07f
        var x = 0.4f
        val end = t + 300
        while (t < end) feed(SyntheticHand.build(Pose.OPEN_PALM, cx = x, s = small))
        val steps = 9
        for (i in 0..steps) {
            x = 0.4f + 0.2f * i / steps
            feed(SyntheticHand.build(Pose.OPEN_PALM, cx = x, s = small))
        }
        assertEquals(listOf(Gesture.SWIPE_RIGHT), fired)
    }

    // --- Vertical swipes ----------------------------------------------------

    @Test
    fun fast_move_up_is_swipe_up_and_return_is_suppressed() {
        holdAt(Pose.OPEN_PALM, 400, cy = 0.7f)
        moveY(Pose.OPEN_PALM, 0.7f, 0.3f, 300)
        holdAt(Pose.OPEN_PALM, 200, cy = 0.3f)
        // Hand travels back down right away: must not count as SWIPE_DOWN.
        moveY(Pose.OPEN_PALM, 0.3f, 0.7f, 400)
        holdAt(Pose.OPEN_PALM, 1_000, cy = 0.7f)
        assertEquals(listOf(Gesture.SWIPE_UP), fired)
    }

    @Test
    fun fast_move_down_is_swipe_down() {
        holdAt(Pose.OPEN_PALM, 400, cy = 0.3f)
        moveY(Pose.OPEN_PALM, 0.3f, 0.7f, 300)
        holdAt(Pose.OPEN_PALM, 500, cy = 0.7f)
        assertEquals(listOf(Gesture.SWIPE_DOWN), fired)
    }

    @Test
    fun raising_hand_into_frame_is_not_swipe_up() {
        // The hand appears at the bottom and is already moving up.
        moveY(Pose.OPEN_PALM, 0.9f, 0.4f, 300)
        holdAt(Pose.OPEN_PALM, 1_000, cy = 0.4f)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun lowering_hand_out_of_frame_is_not_swipe_down() {
        holdAt(Pose.OPEN_PALM, 400, cy = 0.4f)
        moveY(Pose.OPEN_PALM, 0.4f, 1.0f, 250)
        noHand(1_500)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun closing_fist_after_vertical_swipe_does_not_trigger_play_pause() {
        holdAt(Pose.OPEN_PALM, 400, cy = 0.7f)
        moveY(Pose.OPEN_PALM, 0.7f, 0.3f, 300)
        holdAt(Pose.FIST, 2_000, cy = 0.3f)
        assertEquals(listOf(Gesture.SWIPE_UP), fired)
    }

    // --- Thumb up ("like" -> double tap) ------------------------------------

    @Test
    fun classifier_recognises_thumb_up() {
        assertEquals(Gesture.THUMB_UP, StaticGestureClassifier.classify(SyntheticHand.build(Pose.THUMB_UP), 1f))
        // Thumb pointing down is nothing.
        assertNull(StaticGestureClassifier.classify(SyntheticHand.build(Pose.THUMB_UP, rotationDeg = 180f), 1f))
    }

    @Test
    fun thumb_up_held_fires_once() {
        hold(Pose.THUMB_UP, 3_000)
        assertEquals(listOf(Gesture.THUMB_UP), fired)
    }

    @Test
    fun thumb_up_shorter_than_hold_time_does_not_fire() {
        hold(Pose.THUMB_UP, 300)
        hold(Pose.OPEN_PALM, 1_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun thumb_up_again_after_release_fires_again() {
        hold(Pose.THUMB_UP, 1_000)
        hold(Pose.OPEN_PALM, 1_000)
        hold(Pose.THUMB_UP, 1_000)
        assertEquals(listOf(Gesture.THUMB_UP, Gesture.THUMB_UP), fired)
    }

    // --- Fist vs thumb gestures ----------------------------------------------

    @Test
    fun quick_fist_on_the_way_to_like_is_not_play_pause() {
        hold(Pose.OPEN_PALM, 300)
        hold(Pose.FIST, 500)
        hold(Pose.THUMB_UP, 1_000)
        assertEquals(listOf(Gesture.THUMB_UP), fired)
    }

    @Test
    fun fist_after_like_stays_locked() {
        // The thumb folds back into the fist after the like: must not become Play/Pause.
        hold(Pose.THUMB_UP, 1_000)
        hold(Pose.FIST, 3_000)
        assertEquals(listOf(Gesture.THUMB_UP), fired)
    }

    @Test
    fun fist_after_back_stays_locked() {
        hold(Pose.THUMB_UP, 1_300, rotationDeg = -90f)
        hold(Pose.FIST, 3_000)
        assertEquals(listOf(Gesture.THUMB_LEFT), fired)
    }

    @Test
    fun opening_the_hand_unlocks_the_fist() {
        hold(Pose.THUMB_UP, 1_000)
        hold(Pose.OPEN_PALM, 300)
        hold(Pose.FIST, 2_000)
        assertEquals(listOf(Gesture.THUMB_UP, Gesture.FIST), fired)
    }

    // --- Rest time between actions -----------------------------------------

    @Test
    fun static_poses_do_not_repeat_within_static_cooldown() {
        hold(Pose.FIST, 1_000) // fires at ~800 ms
        hold(Pose.OPEN_PALM, 300)
        hold(Pose.THUMB_UP, 400) // still inside the 1.5 s static cooldown
        assertEquals(listOf(Gesture.FIST), fired)
    }

    // --- Only the palm counts (not the arm) ---------------------------------

    @Test
    fun moving_arm_with_fist_is_not_a_swipe() {
        hold(Pose.FIST, 300, cx = 0.2f)
        move(Pose.FIST, 0.2f, 0.8f, 300)
        holdAt(Pose.FIST, 400, cy = 0.7f)
        moveY(Pose.FIST, 0.7f, 0.3f, 300)
        holdAt(Pose.FIST, 500, cy = 0.3f)
        assertTrue(fired.none { it.kind == GestureKind.SWIPE })
    }

    @Test
    fun hand_partly_out_of_frame_is_ignored() {
        // Wrist below the bottom edge: only part of the hand (or the forearm) is in view.
        holdAt(Pose.FIST, 2_000, cy = 0.97f)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun tiny_false_detection_is_ignored() {
        val end = t + 2_000
        while (t < end) feed(SyntheticHand.build(Pose.FIST, s = 0.03f))
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun whole_hand_in_frame_check() {
        val ok = SyntheticHand.build(Pose.OPEN_PALM)
        val cut = SyntheticHand.build(Pose.OPEN_PALM, cy = 0.97f)
        assertTrue(StaticGestureClassifier.isWholeHandInFrame(ok, 1f, 0.02f, 0.025f))
        assertFalse(StaticGestureClassifier.isWholeHandInFrame(cut, 1f, 0.02f, 0.025f))
        assertTrue(StaticGestureClassifier.isOpenHand(ok, 1f))
        assertFalse(StaticGestureClassifier.isOpenHand(SyntheticHand.build(Pose.FIST), 1f))
    }

    // --- Thumb left (back) ---------------------------------------------------

    @Test
    fun classifier_recognises_thumb_left() {
        // -90 deg: the upright "like" turned so the thumb points to the user's left (smaller x).
        assertEquals(
            Gesture.THUMB_LEFT,
            StaticGestureClassifier.classify(SyntheticHand.build(Pose.THUMB_UP, rotationDeg = -90f), 1f)
        )
        // Thumb to the right is not used.
        assertNull(StaticGestureClassifier.classify(SyntheticHand.build(Pose.THUMB_UP, rotationDeg = 90f), 1f))
        // A plain fist turned sideways is still just a fist.
        assertEquals(
            Gesture.FIST,
            StaticGestureClassifier.classify(SyntheticHand.build(Pose.FIST, rotationDeg = -90f), 1f)
        )
    }

    @Test
    fun thumb_left_held_fires_once() {
        hold(Pose.THUMB_UP, 3_000, rotationDeg = -90f)
        assertEquals(listOf(Gesture.THUMB_LEFT), fired)
    }

    @Test
    fun like_then_thumb_left_after_cooldown_fires_both() {
        hold(Pose.THUMB_UP, 1_000)
        hold(Pose.OPEN_PALM, 1_000)
        hold(Pose.THUMB_UP, 1_500, rotationDeg = -90f)
        assertEquals(listOf(Gesture.THUMB_UP, Gesture.THUMB_LEFT), fired)
    }

    @Test
    fun thumb_left_needs_a_full_second() {
        hold(Pose.THUMB_UP, 800, rotationDeg = -90f)
        hold(Pose.OPEN_PALM, 1_000)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun diagonal_thumb_is_not_back() {
        // Thumb pointing up-left (~45 deg): neither a clear like nor a clear back.
        assertNull(StaticGestureClassifier.classify(SyntheticHand.build(Pose.THUMB_UP, rotationDeg = -45f), 1f))
        hold(Pose.THUMB_UP, 2_000, rotationDeg = -45f)
        assertEquals(emptyList<Gesture>(), fired)
    }

    // --- Swipe sensitivity ---------------------------------------------------

    @Test
    fun short_hand_twitch_is_not_a_swipe() {
        // ~0.8 palm in 150 ms: fast, but too short to be a deliberate swipe (needs 0.95).
        hold(Pose.OPEN_PALM, 400, cx = 0.2f)
        move(Pose.OPEN_PALM, 0.2f, 0.28f, 150)
        hold(Pose.OPEN_PALM, 1_000, cx = 0.28f)
        assertEquals(emptyList<Gesture>(), fired)
    }

    @Test
    fun diagonal_move_is_not_a_swipe() {
        // dx about 1.25x dy (~40 deg): below the 1.5x horizontal and 1.8x vertical rules.
        assertTrue(swipeAlong(dx = 0.5f, dy = 0.4f).none { it.kind == GestureKind.SWIPE })
    }

    @Test
    fun slightly_curved_horizontal_swipe_still_counts() {
        // The arm swings in an arc: dx about 1.7x dy is still a clear sideways swipe.
        assertEquals(listOf(Gesture.SWIPE_RIGHT), swipeAlong(dx = 0.5f, dy = 0.3f))
    }

    @Test
    fun horizontal_swipe_with_palm_turning_sideways_still_counts() {
        // About half of the frames lose the "open palm" shape (palm turned edge-on).
        hold(Pose.OPEN_PALM, 400, cx = 0.2f)
        val steps = 9
        for (i in 0..steps) {
            val pose = if (i % 2 == 0) Pose.OPEN_PALM else Pose.FIST
            feed(SyntheticHand.build(pose, cx = 0.2f + 0.6f * i / steps))
        }
        hold(Pose.OPEN_PALM, 1_000, cx = 0.8f)
        assertEquals(listOf(Gesture.SWIPE_RIGHT), fired)
    }

    /** Holds an open palm, then moves it by (dx, dy) of the frame in ~300 ms, then holds again. */
    private fun swipeAlong(dx: Float, dy: Float): List<Gesture> {
        val x0 = 0.2f
        val y0 = 0.35f
        val startEnd = t + 400
        while (t < startEnd) feed(SyntheticHand.build(Pose.OPEN_PALM, cx = x0, cy = y0))
        val steps = 9
        for (i in 0..steps) {
            feed(SyntheticHand.build(Pose.OPEN_PALM, cx = x0 + dx * i / steps, cy = y0 + dy * i / steps))
        }
        val stopEnd = t + 1_000
        while (t < stopEnd) feed(SyntheticHand.build(Pose.OPEN_PALM, cx = x0 + dx, cy = y0 + dy))
        return fired
    }
}
