package com.khoi.handlecontrol.spike

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * Phase 0 — CameraX + MediaPipe Hand Landmarks Spike.
 * Verifies camera permission, front camera preview (front camera only), 21 hand landmarks, and FPS on-device.
 */
@Composable
fun CameraPreviewScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(hasCameraPermission(context))
    }
    var statusText by remember { mutableStateOf("Initializing camera...") }
    var handState by remember { mutableStateOf(HandDetectionState()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        statusText = if (granted) "Permission granted" else "Camera permission denied"
    }

    // Re-check permission when returning from system Settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = hasCameraPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        PermissionRequestContent(
            modifier = modifier,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { openAppSettings(context) }
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraXPreviewWithMediaPipe(
            onStatusChanged = { statusText = it },
            onHandResult = { handState = it },
            modifier = Modifier.fillMaxSize()
        )

        HandLandmarkOverlay(
            state = handState,
            modifier = Modifier.fillMaxSize()
        )

        // Phase 0 debug HUD matching Plan.md 5.3.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .align(Alignment.TopCenter)
        ) {
            Text(
                text = "Phase 0 — CameraX + MediaPipe Spike",
                color = Color.White,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Camera: FRONT | $statusText",
                color = Color(0xFFB9F6CA),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hand detected: ${if (handState.handDetected) "YES" else "NO"} | Landmarks: ${handState.landmarkCount}",
                color = if (handState.handDetected) Color(0xFF00E676) else Color(0xFFFFAB91),
                fontSize = 13.sp
            )
            Text(
                text = "FPS: ${handState.fps} | Hand: ${handState.handedness} (${"%.2f".format(handState.confidence)}) | ${handState.inferenceTimeMs} ms",
                color = Color.White,
                fontSize = 12.sp
            )
            handState.errorMessage?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CameraXPreviewWithMediaPipe(
    onStatusChanged: (String) -> Unit,
    onHandResult: (HandDetectionState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val landmarkerHelper = HandLandmarkerHelper(
            context = context,
            onResult = onHandResult
        )
        var boundProvider: ProcessCameraProvider? = null
        // Both the listener and onDispose run on the main thread, so a plain flag is enough.
        // Prevents binding the camera after this effect was already disposed (fast tab switch).
        var disposed = false

        val listener = Runnable {
            if (disposed) return@Runnable
            try {
                val cameraProvider = cameraProviderFuture.get()
                boundProvider = cameraProvider

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                if (!cameraProvider.hasCamera(cameraSelector)) {
                    onStatusChanged("Thiết bị không có camera trước")
                    return@Runnable
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            landmarkerHelper.detectLiveStream(imageProxy)
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                onStatusChanged("Preview + MediaPipe active")
            } catch (e: Exception) {
                onStatusChanged("Error: ${e.message ?: "Unknown error"}")
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            disposed = true
            boundProvider?.unbindAll()
            analysisExecutor.shutdown()
            landmarkerHelper.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Phase 0 — CameraX + MediaPipe Spike",
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Hand Control cần quyền Camera để chạy on-device AI nhận diện cử chỉ tay. Không lưu và không gửi hình ảnh lên server.",
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRequestPermission) {
            Text("Cấp quyền Camera")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Text("Mở Cài đặt ứng dụng")
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
