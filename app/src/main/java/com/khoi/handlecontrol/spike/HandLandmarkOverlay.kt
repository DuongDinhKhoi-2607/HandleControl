package com.khoi.handlecontrol.spike

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.khoi.handlecontrol.core.vision.HandDetectionState
import com.khoi.handlecontrol.core.vision.LandmarkPoint
import kotlin.math.max

/**
 * Draws the 21 MediaPipe hand landmarks and bone connections aligned with PreviewView FILL_CENTER.
 */
@Composable
fun HandLandmarkOverlay(
    state: HandDetectionState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val landmarks = state.landmarks
        if (landmarks.isEmpty() || state.imageWidth <= 0 || state.imageHeight <= 0) {
            return@Canvas
        }

        // Match PreviewView.ScaleType.FILL_CENTER coordinate mapping.
        val scaleFactor = max(
            size.width / state.imageWidth.toFloat(),
            size.height / state.imageHeight.toFloat()
        )
        val scaledWidth = state.imageWidth * scaleFactor
        val scaledHeight = state.imageHeight * scaleFactor
        val offsetX = (size.width - scaledWidth) / 2f
        val offsetY = (size.height - scaledHeight) / 2f

        fun toOffset(point: LandmarkPoint): Offset {
            return Offset(
                x = point.x * scaledWidth + offsetX,
                y = point.y * scaledHeight + offsetY
            )
        }

        for ((startIdx, endIdx) in HAND_CONNECTIONS) {
            if (startIdx < landmarks.size && endIdx < landmarks.size) {
                drawLine(
                    color = Color(0xFF00E676),
                    start = toOffset(landmarks[startIdx]),
                    end = toOffset(landmarks[endIdx]),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        for ((index, point) in landmarks.withIndex()) {
            val isFingerTip = index in FINGER_TIP_INDICES
            drawCircle(
                color = if (isFingerTip) Color(0xFFFFEA00) else Color(0xFFFF5252),
                radius = if (isFingerTip) 12f else 8f,
                center = toOffset(point)
            )
        }
    }
}

private val FINGER_TIP_INDICES = setOf(4, 8, 12, 16, 20)

private val HAND_CONNECTIONS = listOf(
    // Thumb
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    // Index finger
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    // Middle finger
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    // Ring finger
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    // Pinky finger
    0 to 17, 17 to 18, 18 to 19, 19 to 20,
    // Palm
    5 to 9, 9 to 13, 13 to 17
)

