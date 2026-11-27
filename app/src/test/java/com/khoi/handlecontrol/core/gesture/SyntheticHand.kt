package com.khoi.handlecontrol.core.gesture

import com.khoi.handlecontrol.core.vision.LandmarkPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds fake but anatomically ordered 21-point hands for tests.
 * Upright hand: wrist at the bottom, fingers pointing to smaller y (up in image coordinates).
 */
object SyntheticHand {

    /** THUMB_UP = "like": four fingers curled, thumb sticking up out of the fist. */
    enum class Pose { OPEN_PALM, POINT, FIST, THUMB_UP }

    /**
     * @param s hand scale (wrist → fingertip is roughly 1.2 * s)
     * @param rotationDeg rotation around the palm centre; 180 turns a POINT into a point down.
     */
    fun build(
        pose: Pose,
        cx: Float = 0.5f,
        cy: Float = 0.5f,
        s: Float = 0.2f,
        rotationDeg: Float = 0f
    ): List<LandmarkPoint> {
        val pts = MutableList(21) { 0f to 0f }
        pts[0] = 0f to 0.5f // wrist

        pts[1] = -0.3f to 0.35f
        if (pose == Pose.THUMB_UP) {
            // Thumb sticking up, away from the fist.
            pts[2] = -0.4f to 0.2f
            pts[3] = -0.45f to -0.15f
            pts[4] = -0.5f to -0.45f
        } else {
            // Thumb tucked against the index finger.
            pts[2] = -0.4f to 0.2f
            pts[3] = -0.35f to 0.1f
            pts[4] = -0.25f to 0.05f
        }

        val baseX = floatArrayOf(-0.3f, -0.1f, 0.1f, 0.3f)
        val extended = when (pose) {
            Pose.OPEN_PALM -> booleanArrayOf(true, true, true, true)
            Pose.POINT -> booleanArrayOf(true, false, false, false)
            Pose.FIST -> booleanArrayOf(false, false, false, false)
            Pose.THUMB_UP -> booleanArrayOf(false, false, false, false)
        }
        for (f in 0 until 4) {
            val mcp = 5 + f * 4
            val x = baseX[f]
            pts[mcp] = x to 0f
            if (extended[f]) {
                pts[mcp + 1] = x to -0.3f
                pts[mcp + 2] = x to -0.5f
                pts[mcp + 3] = x to -0.7f
            } else {
                pts[mcp + 1] = x to -0.25f
                pts[mcp + 2] = x to -0.15f
                pts[mcp + 3] = x to -0.05f
            }
        }

        val rad = Math.toRadians(rotationDeg.toDouble())
        val c = cos(rad).toFloat()
        val sn = sin(rad).toFloat()
        return pts.map { (x, y) ->
            val rx = x * c - y * sn
            val ry = x * sn + y * c
            LandmarkPoint(cx + rx * s, cy + ry * s)
        }
    }
}
