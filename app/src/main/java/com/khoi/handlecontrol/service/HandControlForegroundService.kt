package com.khoi.handlecontrol.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.khoi.handlecontrol.MainActivity
import com.khoi.handlecontrol.R
import com.khoi.handlecontrol.core.action.ActionExecutor
import com.khoi.handlecontrol.core.app.AppGate
import com.khoi.handlecontrol.core.app.AppGate.recognises
import com.khoi.handlecontrol.core.app.ControlSettings
import com.khoi.handlecontrol.core.app.OwnAppVisibility
import com.khoi.handlecontrol.core.action.ActionMapper
import com.khoi.handlecontrol.core.action.ControlAction
import com.khoi.handlecontrol.core.gesture.EngineState
import com.khoi.handlecontrol.core.gesture.Gesture
import com.khoi.handlecontrol.core.gesture.GestureEngine
import com.khoi.handlecontrol.core.gesture.HandFrame
import com.khoi.handlecontrol.core.vision.HandDetectionState
import com.khoi.handlecontrol.core.vision.HandLandmarkerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Everything the UI and the notification need to show (Plan.md section 18 / 19). */
data class ControlStatus(
    val running: Boolean = false,
    val cameraActive: Boolean = false,
    val aiActive: Boolean = false,
    val handDetected: Boolean = false,
    val fps: Int = 0,
    val engineState: EngineState = EngineState.IDLE,
    val pose: Gesture? = null,
    val holdProgress: Float = 0f,
    val lastGesture: Gesture? = null,
    val lastAction: ControlAction? = null,
    val lastResult: String? = null,
    val gestureCount: Int = 0,
    val error: String? = null,
    /** ACTIVE, TEST (inside Hand Control), or why control is paused. */
    val gate: AppGate.Decision = AppGate.Decision.ACTIVE,
    /** Gestures recognised in test mode, newest first (not executed). */
    val testLog: List<String> = emptyList()
)

/**
 * Keeps camera + MediaPipe + gesture engine alive while the user is in TikTok / YouTube / ...
 *
 * Start:  OFF → START → permission check → foreground (camera type) → camera + AI active.
 * Stop:   stop AI → stop camera → stop control → remove notification.
 *
 * Must be started while the app is visible: Android only lets a camera foreground service
 * use the camera if it was started from the foreground ("while-in-use" restriction).
 */
class HandControlForegroundService : LifecycleService() {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analysisExecutor: ExecutorService? = null
    private var landmarker: HandLandmarkerHelper? = null
    private var executor: ActionExecutor? = null
    private val engine = GestureEngine()
    private var mapper = ActionMapper()

    @Volatile
    private var stopping = false

    /** Last gate decision seen on the analysis thread, to react only when it changes. */
    @Volatile
    private var lastDecision: AppGate.Decision? = null
    /** Set when control pauses; the engine is reset on the result thread before the next frame. */
    @Volatile
    private var engineResetPending = false

    override fun onCreate() {
        super.onCreate()
        ControlSettings.init(this)
        createNotificationChannel()
    }

    private fun currentDecision(): AppGate.Decision = AppGate.decide(
        filterEnabled = ControlSettings.filterEnabled.value,
        foregroundPackage = HandControlAccessibilityService.foregroundPackage.value,
        allowedPackages = ControlSettings.allowedApps.value,
        ownAppVisible = OwnAppVisibility.visible.value
    )

    /** Called for every camera frame (analysis thread). */
    private fun onGate(decision: AppGate.Decision) {
        if (decision == lastDecision) return
        lastDecision = decision
        // Any switch (test ↔ app ↔ paused) starts clean: a half-held pose or half-done swipe
        // must not finish in the other place (e.g. a test fist becoming Pause in TikTok).
        engineResetPending = true
        _status.update {
            it.copy(gate = decision, handDetected = false, engineState = EngineState.IDLE, pose = null, holdProgress = 0f)
        }
        refreshNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopControl()
            return START_NOT_STICKY
        }
        startControl()
        // Not sticky: a restart from the background could not open the camera anyway.
        return START_NOT_STICKY
    }

    private fun startControl() {
        if (_status.value.running) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            _status.value = ControlStatus(error = "Chưa có quyền Camera")
            stopSelf()
            return
        }

        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(ControlStatus(running = true)), type)
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException when started from the background.
            Log.e(TAG, "startForeground failed", e)
            _status.value = ControlStatus(error = "Không thể chạy nền: ${e.message}")
            stopSelf()
            return
        }

        stopping = false
        lastDecision = null
        engineResetPending = false
        engine.reset()
        executor = ActionExecutor(this)
        _status.value = ControlStatus(running = true)
        startCamera()
    }

    private fun startCamera() {
        val helper = HandLandmarkerHelper(this, ::onHandResult)
        landmarker = helper
        val singleThread = Executors.newSingleThreadExecutor()
        analysisExecutor = singleThread

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (stopping) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                if (!provider.hasCamera(selector)) {
                    fail("Thiết bị không có camera trước")
                    return@addListener
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(singleThread) { proxy ->
                    val decision = currentDecision()
                    onGate(decision)
                    // Outside the selected apps the frame is dropped before MediaPipe: no AI work, less battery.
                    if (decision.recognises) helper.detectLiveStream(proxy) else proxy.close()
                }
                imageAnalysis = analysis
                // Analysis only, no Preview: nothing is drawn, frames stay in memory.
                provider.bindToLifecycle(this, selector, analysis)
                _status.update { it.copy(cameraActive = true, aiActive = it.error == null) }
                refreshNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                fail("Lỗi camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Called on the MediaPipe result thread. */
    private fun onHandResult(result: HandDetectionState) {
        if (stopping) return
        if (result.errorMessage != null) {
            _status.update { it.copy(aiActive = false, error = result.errorMessage) }
            return
        }
        // A frame already in MediaPipe when the user left the selected app.
        if (!currentDecision().recognises) return
        if (engineResetPending) {
            engineResetPending = false
            engine.reset()
        }

        val snapshot = engine.process(
            HandFrame(
                timestampMs = result.timestampMs,
                landmarks = result.landmarks,
                confidence = result.confidence,
                aspect = result.imageWidth.toFloat() / result.imageHeight.coerceAtLeast(1)
            )
        )

        _status.update {
            it.copy(
                aiActive = true,
                error = null,
                handDetected = result.handDetected,
                fps = result.fps,
                engineState = snapshot.state,
                pose = snapshot.pose ?: snapshot.candidate,
                holdProgress = snapshot.holdProgress
            )
        }

        val event = snapshot.event ?: return
        val decision = currentDecision()
        if (!decision.recognises) return
        val action = mapper.map(event.gesture)
        if (decision == AppGate.Decision.TEST) {
            // Test mode: show what was recognised, do not execute it.
            val line = "${event.gesture.label} → ${action.label}"
            _status.update {
                it.copy(
                    lastGesture = event.gesture,
                    lastAction = action,
                    lastResult = "Chế độ test: đã nhận, không thực hiện",
                    gestureCount = it.gestureCount + 1,
                    testLog = (listOf(line) + it.testLog).take(TEST_LOG_SIZE)
                )
            }
            refreshNotification()
            return
        }
        _status.update {
            it.copy(lastGesture = event.gesture, lastAction = action, lastResult = "…", gestureCount = it.gestureCount + 1)
        }
        executor?.execute(action) { res ->
            if (stopping) return@execute
            _status.update { it.copy(lastResult = res.message) }
            refreshNotification()
        }
    }

    private fun fail(message: String) {
        _status.update { it.copy(error = message, cameraActive = false, aiActive = false) }
        refreshNotification()
    }

    private fun stopControl() {
        stopping = true
        // Order matters: AI first so no frame is processed against a closed landmarker.
        landmarker?.close()
        landmarker = null
        imageAnalysis?.let { analysis ->
            analysis.clearAnalyzer()
            cameraProvider?.unbind(analysis)
        }
        imageAnalysis = null
        cameraProvider = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        executor = null
        engine.reset()
        _status.value = ControlStatus()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!stopping) stopControl()
        super.onDestroy()
    }

    // region Notification

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(status: ControlStatus) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_hand)
        .setContentTitle(getString(R.string.notification_title_active))
        .setContentText(notificationText(status))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .addAction(
            0,
            getString(R.string.notification_action_stop),
            PendingIntent.getService(
                this, 1,
                Intent(this, HandControlForegroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun notificationText(status: ControlStatus): String {
        status.error?.let { return "Lỗi: $it" }
        when (status.gate) {
            AppGate.Decision.TEST -> return "Chế độ test trong Hand Control: nhận cử chỉ nhưng không thực hiện."
            AppGate.Decision.NOT_SELECTED_APP -> return "Tạm dừng: chỉ hoạt động trong app đã chọn."
            AppGate.Decision.UNKNOWN_APP -> return "Tạm dừng: bật Accessibility để biết app đang mở."
            AppGate.Decision.ACTIVE -> Unit
        }
        val gesture = status.lastGesture ?: return "Camera + AI đang chạy. Đưa tay vào khung hình để điều khiển."
        val action = status.lastAction?.label ?: "-"
        return "Gần nhất: ${gesture.label} → $action"
    }

    private fun refreshNotification() {
        if (stopping) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(_status.value))
    }

    // endregion

    companion object {
        private const val TAG = "HandControlFgs"
        private const val CHANNEL_ID = "hand_control"
        private const val NOTIFICATION_ID = 1001
        private const val TEST_LOG_SIZE = 8
        private const val ACTION_STOP = "com.khoi.handlecontrol.action.STOP"

        private val _status = MutableStateFlow(ControlStatus())
        val status: StateFlow<ControlStatus> = _status.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HandControlForegroundService::class.java))
        }

        fun stop(context: Context) {
            // Delivered through onStartCommand so the service tears down in order.
            context.startService(Intent(context, HandControlForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
