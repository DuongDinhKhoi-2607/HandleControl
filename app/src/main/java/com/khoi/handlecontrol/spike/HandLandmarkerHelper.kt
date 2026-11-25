package com.khoi.handlecontrol.spike

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.nio.ByteBuffer
import kotlin.math.roundToInt

data class LandmarkPoint(val x: Float, val y: Float)

data class HandDetectionState(
    val handDetected: Boolean = false,
    val landmarkCount: Int = 0,
    val fps: Int = 0,
    val inferenceTimeMs: Long = 0L,
    val handedness: String = "-",
    val confidence: Float = 0f,
    val landmarks: List<LandmarkPoint> = emptyList(),
    val imageWidth: Int = 1,
    val imageHeight: Int = 1,
    val errorMessage: String? = null
)

/**
 * Wraps MediaPipe HandLandmarker in LIVE_STREAM mode for Phase 0 spike verification.
 * Front camera only: every frame is rotated upright and mirrored to match the selfie PreviewView.
 *
 * Threading: [detectLiveStream] runs on the CameraX analysis thread, [close] on the main thread.
 * Both take [lock], so the landmarker and the reused bitmaps are never touched after close.
 */
class HandLandmarkerHelper(
    context: Context,
    private val onResult: (HandDetectionState) -> Unit
) {
    private val lock = Any()

    @Volatile
    private var closed = false
    private var handLandmarker: HandLandmarker? = null

    // Reused across frames to avoid allocating two bitmaps per frame (~30 fps).
    // Safe because MediaPipe copies the pixels into its own packet inside detectAsync().
    private var sourceBitmap: Bitmap? = null
    private var uprightBitmap: Bitmap? = null
    private var packedBuffer: ByteBuffer? = null
    private val transformMatrix = Matrix()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var frameCounter = 0
    private var fpsWindowStartMs = SystemClock.uptimeMillis()
    private var currentFps = 0

    @Volatile
    private var lastImageWidth = 1

    @Volatile
    private var lastImageHeight = 1

    init {
        setupHandLandmarker(context)
    }

    private fun setupHandLandmarker(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    if (!closed) handleResult(result)
                }
                .setErrorListener { error ->
                    if (!closed) {
                        onResult(HandDetectionState(errorMessage = error.message ?: "MediaPipe error"))
                    }
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (t: Throwable) {
            onResult(
                HandDetectionState(
                    errorMessage = "MediaPipe init failed (${t.javaClass.simpleName}): ${t.message ?: "Unsupported ABI"}"
                )
            )
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy) {
        try {
            synchronized(lock) {
                val landmarker = handLandmarker
                if (closed || landmarker == null) return

                val frameTimeMs = SystemClock.uptimeMillis()
                val source = copyToSourceBitmap(imageProxy)
                val upright = drawUprightMirrored(source, imageProxy.imageInfo.rotationDegrees)

                lastImageWidth = upright.width
                lastImageHeight = upright.height

                landmarker.detectAsync(BitmapImageBuilder(upright).build(), frameTimeMs)
            }
        } catch (t: Throwable) {
            if (!closed) {
                onResult(HandDetectionState(errorMessage = "Frame error: ${t.message ?: "Unknown error"}"))
            }
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Copies the RGBA_8888 plane into a reused bitmap, honoring rowStride.
     * Many devices pad each row, so a plain copyPixelsFromBuffer() would skew or throw.
     */
    private fun copyToSourceBitmap(imageProxy: ImageProxy): Bitmap {
        val width = imageProxy.width
        val height = imageProxy.height
        val plane = imageProxy.planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowBytes = width * BYTES_PER_PIXEL

        val bitmap = sourceBitmap?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                sourceBitmap?.recycle()
                sourceBitmap = it
            }

        val src = plane.buffer
        src.rewind()

        if (rowStride == rowBytes && pixelStride == BYTES_PER_PIXEL) {
            bitmap.copyPixelsFromBuffer(src)
            return bitmap
        }

        val packed = packedBuffer?.takeIf { it.capacity() == rowBytes * height }
            ?: ByteBuffer.allocateDirect(rowBytes * height).also { packedBuffer = it }
        packed.clear()

        if (pixelStride == BYTES_PER_PIXEL) {
            // Only row padding: copy each row without the trailing padding bytes.
            val srcLimit = src.limit()
            for (row in 0 until height) {
                val rowStart = row * rowStride
                src.limit(rowStart + rowBytes)
                src.position(rowStart)
                packed.put(src)
                src.limit(srcLimit)
            }
        } else {
            // Rare: pixels are not tightly packed within a row.
            for (row in 0 until height) {
                val rowStart = row * rowStride
                for (col in 0 until width) {
                    val p = rowStart + col * pixelStride
                    for (c in 0 until BYTES_PER_PIXEL) packed.put(src.get(p + c))
                }
            }
        }

        packed.flip()
        bitmap.copyPixelsFromBuffer(packed)
        return bitmap
    }

    /** Rotates the frame upright and mirrors it horizontally into a reused bitmap. */
    private fun drawUprightMirrored(source: Bitmap, rotationDegrees: Int): Bitmap {
        val swapSides = rotationDegrees == 90 || rotationDegrees == 270
        val outWidth = if (swapSides) source.height else source.width
        val outHeight = if (swapSides) source.width else source.height

        val output = uprightBitmap?.takeIf { it.width == outWidth && it.height == outHeight }
            ?: Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).also {
                uprightBitmap?.recycle()
                uprightBitmap = it
            }

        transformMatrix.reset()
        transformMatrix.postTranslate(-source.width / 2f, -source.height / 2f)
        transformMatrix.postRotate(rotationDegrees.toFloat())
        // Mirror so landmarks line up with the mirrored front-camera preview.
        transformMatrix.postScale(-1f, 1f)
        transformMatrix.postTranslate(outWidth / 2f, outHeight / 2f)

        Canvas(output).drawBitmap(source, transformMatrix, bitmapPaint)
        return output
    }

    private fun handleResult(result: HandLandmarkerResult) {
        val nowMs = SystemClock.uptimeMillis()
        frameCounter++
        val elapsedMs = nowMs - fpsWindowStartMs
        if (elapsedMs >= 1000L) {
            currentFps = ((frameCounter * 1000f) / elapsedMs).roundToInt()
            frameCounter = 0
            fpsWindowStartMs = nowMs
        }

        val firstHandLandmarks = result.landmarks().firstOrNull()
        val firstHandedness = result.handednesses().firstOrNull()?.firstOrNull()

        val points = firstHandLandmarks?.map { landmark ->
            LandmarkPoint(x = landmark.x(), y = landmark.y())
        }.orEmpty()

        onResult(
            HandDetectionState(
                handDetected = points.isNotEmpty(),
                landmarkCount = points.size,
                fps = currentFps,
                inferenceTimeMs = nowMs - result.timestampMs(),
                handedness = firstHandedness?.categoryName() ?: "-",
                confidence = firstHandedness?.score() ?: 0f,
                landmarks = points,
                imageWidth = lastImageWidth,
                imageHeight = lastImageHeight
            )
        )
    }

    fun close() {
        closed = true
        synchronized(lock) {
            handLandmarker?.close()
            handLandmarker = null
            sourceBitmap?.recycle()
            sourceBitmap = null
            uprightBitmap?.recycle()
            uprightBitmap = null
            packedBuffer = null
        }
    }

    companion object {
        private const val MODEL_ASSET_PATH = "hand_landmarker.task"
        private const val BYTES_PER_PIXEL = 4
    }
}
