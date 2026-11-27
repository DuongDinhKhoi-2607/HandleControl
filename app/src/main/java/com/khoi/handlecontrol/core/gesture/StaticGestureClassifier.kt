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

        if (extended.all { it }) return Gesture.OPEN_PALM

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
