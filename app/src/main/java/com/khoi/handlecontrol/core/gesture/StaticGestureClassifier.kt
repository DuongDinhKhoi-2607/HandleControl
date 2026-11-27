package com.khoi.handlecontrol.core.gesture

import com.khoi.handlecontrol.core.vision.LandmarkPoint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Classifies a single frame of 21 MediaPipe landmarks into a static pose.
 *
 * A finger counts as extended when its tip is clearly farther from the wrist than its PIP joint.
 * Comparing distances from the wrist keeps the test independent of hand rotation, which is what
 * lets POINT_DOWN (hand upside down) work with the same rule as POINT_UP.
 *
 * [aspect] = imageWidth / imageHeight. Landmark x/y are normalised separately to width and height,
 * so x is multiplied by the aspect to measure distances in one unit.
 */
object StaticGestureClassifier {

    private const val EXTENDED_RATIO = 1.1f
    private const val CURLED_RATIO = 1.05f
    /** Pointing must be within ~35° of vertical: |dy| >= 1.4 * |dx|. */
    private const val POINT_VERTICAL_RATIO = 1.4f
    /** Thumb tip must be at least this far (palm units) from the index MCP to count as sticking out. */
    private const val THUMB_OUT_DISTANCE = 0.7f
    /** Thumb direction must be within ~40° of the axis: main axis >= 1.2 * other axis. */
    private const val THUMB_AXIS_RATIO = 1.2f

    /** Thumb-left (Back) must be within ~27° of horizontal: |dx| >= 2 * |dy|. */
    private const val THUMB_LEFT_AXIS_RATIO = 2.0f
    /** Thumb-left: straight-line MCP → tip / bone path. 1 = perfectly straight. */
    private const val THUMB_STRAIGHT_RATIO = 0.9f
    /** Thumb-left: the tip must be this far (palm units) left of the leftmost knuckle. */
    private const val THUMB_LEFT_PAST_KNUCKLES = 0.25f

    private const val THUMB_MCP = 2
    private const val THUMB_IP = 3
    private const val THUMB_TIP = 4
    private const val INDEX_MCP = 5

    private const val WRIST = 0
    private val FINGERS = listOf(
        Finger(mcp = 5, pip = 6, tip = 8),   // index
        Finger(mcp = 9, pip = 10, tip = 12), // middle
        Finger(mcp = 13, pip = 14, tip = 16), // ring
        Finger(mcp = 17, pip = 18, tip = 20)  // pinky
    )

    private data class Finger(val mcp: Int, val pip: Int, val tip: Int)

    fun classify(landmarks: List<LandmarkPoint>, aspect: Float): Gesture? {
        if (landmarks.size < 21) return null

        val ratios = FINGERS.map { finger ->
            val toTip = distance(landmarks[WRIST], landmarks[finger.tip], aspect)
            val toPip = distance(landmarks[WRIST], landmarks[finger.pip], aspect)
            if (toPip <= 0f) 0f else toTip / toPip
        }
        val extended = ratios.map { it > EXTENDED_RATIO }
        val curled = ratios.map { it < CURLED_RATIO }

        // An open palm (all fingers extended) is deliberately not a gesture.
        if (extended.all { it }) return null

        if (curled.all { it }) return thumbGesture(landmarks, aspect)

        if (extended[0] && curled[1] && curled[2] && curled[3]) {
            val index = FINGERS[0]
            val dx = (landmarks[index.tip].x - landmarks[index.mcp].x) * aspect
            val dy = landmarks[index.tip].y - landmarks[index.mcp].y
            if (abs(dy) >= POINT_VERTICAL_RATIO * abs(dx)) {
                // Image y grows downward.
                return if (dy < 0f) Gesture.POINT_UP else Gesture.POINT_DOWN
            }
        }
        return null
    }

    /**
     * Four fingers curled. Thumb tucked (its tip rests on the curled index finger, close to the
     * index MCP) = FIST. Thumb sticking out: up = THUMB_UP ("like"), to the user's left = THUMB_LEFT
     * (back). Thumb down / right are not used and return null.
     */
    private fun thumbGesture(landmarks: List<LandmarkPoint>, aspect: Float): Gesture? {
        val palm = palmSize(landmarks, aspect)
        if (palm <= 0f) return null
        val tip = landmarks[THUMB_TIP]
        if (distance(tip, landmarks[INDEX_MCP], aspect) / palm < THUMB_OUT_DISTANCE) return Gesture.FIST
        val dx = (tip.x - landmarks[THUMB_MCP].x) * aspect
        val dy = tip.y - landmarks[THUMB_MCP].y
        return when {
            // Image y grows downward: pointing up means dy < 0.
            dy < 0f && abs(dy) >= THUMB_AXIS_RATIO * abs(dx) -> Gesture.THUMB_UP
            // The frame is mirrored like a selfie preview, so the user's left is smaller x.
            // Back is stricter than Like: clearly horizontal, thumb straight, tip well past the knuckles.
            dx < 0f && abs(dx) >= THUMB_LEFT_AXIS_RATIO * abs(dy) &&
                isThumbStraight(landmarks, aspect) &&
                isTipPastKnuckles(landmarks, aspect, palm) -> Gesture.THUMB_LEFT
            else -> null
        }
    }

    /** MCP → IP → tip almost in a line: the thumb is deliberately stretched, not half bent. */
    private fun isThumbStraight(landmarks: List<LandmarkPoint>, aspect: Float): Boolean {
        val mcp = landmarks[THUMB_MCP]
        val ip = landmarks[THUMB_IP]
        val tip = landmarks[THUMB_TIP]
        val path = distance(mcp, ip, aspect) + distance(ip, tip, aspect)
        return path > 0f && distance(mcp, tip, aspect) / path >= THUMB_STRAIGHT_RATIO
    }

    /** Thumb tip lies clearly to the user's left of every finger knuckle (MCP). */
    private fun isTipPastKnuckles(landmarks: List<LandmarkPoint>, aspect: Float, palm: Float): Boolean {
        val tipX = landmarks[THUMB_TIP].x * aspect
        val leftmostKnuckle = FINGERS.minOf { landmarks[it.mcp].x } * aspect
        return tipX < leftmostKnuckle - THUMB_LEFT_PAST_KNUCKLES * palm
    }

    /** At least 3 of the 4 fingers extended: the palm is shown open (used to accept swipes). */
    fun isOpenHand(landmarks: List<LandmarkPoint>, aspect: Float): Boolean {
        if (landmarks.size < 21) return false
        return FINGERS.count { finger ->
            val toTip = distance(landmarks[WRIST], landmarks[finger.tip], aspect)
            val toPip = distance(landmarks[WRIST], landmarks[finger.pip], aspect)
            toPip > 0f && toTip / toPip > EXTENDED_RATIO
        } >= 3
    }

    /**
     * The palm (wrist + thumb CMC + 4 finger MCPs) is fully inside the frame and not tiny.
     * Rejects a forearm or a hand only partly in view, where MediaPipe extrapolates the hand outside the image.
     */
    fun isWholeHandInFrame(
        landmarks: List<LandmarkPoint>,
        aspect: Float,
        margin: Float,
        minPalmSize: Float
    ): Boolean {
        if (landmarks.size < 21) return false
        for (i in PALM_POINTS) {
            val p = landmarks[i]
            if (p.x < -margin || p.x > 1f + margin || p.y < -margin || p.y > 1f + margin) return false
        }
        return palmSize(landmarks, aspect) >= minPalmSize
    }

    private val PALM_POINTS = intArrayOf(0, 1, 5, 9, 13, 17)

    /** Wrist → middle-finger MCP, the reference length for "palm units". */
    fun palmSize(landmarks: List<LandmarkPoint>, aspect: Float): Float =
        distance(landmarks[WRIST], landmarks[9], aspect)

    /** Centre of the palm (wrist + four MCPs), x already multiplied by [aspect]. */
    fun palmCenter(landmarks: List<LandmarkPoint>, aspect: Float): Pair<Float, Float> {
        val ids = intArrayOf(0, 5, 9, 13, 17)
        var x = 0f
        var y = 0f
        for (i in ids) {
            x += landmarks[i].x * aspect
            y += landmarks[i].y
        }
        return (x / ids.size) to (y / ids.size)
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint, aspect: Float): Float =
        hypot((a.x - b.x) * aspect, a.y - b.y)
}
