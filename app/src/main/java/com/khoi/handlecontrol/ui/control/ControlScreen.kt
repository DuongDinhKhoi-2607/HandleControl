package com.khoi.handlecontrol.ui.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khoi.handlecontrol.core.action.ActionMapper
import com.khoi.handlecontrol.core.app.AppGate
import com.khoi.handlecontrol.service.HandControlAccessibilityService
import com.khoi.handlecontrol.service.HandControlForegroundService

/**
 * Phase 3 / 5 — Home: turn Hand Control on/off and watch it work (Plan.md section 19).
 * Interim Compose UI; the Flutter app (Phase 4) will replace it and talk to the same service.
 */
@Composable
fun ControlScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val status by HandControlForegroundService.status.collectAsState()
    val accessibilityOn by HandControlAccessibilityService.isConnected.collectAsState()

    var hasCamera by remember { mutableStateOf(isGranted(context, Manifest.permission.CAMERA)) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasCamera = isGranted(context, Manifest.permission.CAMERA)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCamera = result[Manifest.permission.CAMERA] ?: hasCamera
        if (hasCamera) {
            HandControlForegroundService.start(context)
            message = null
        } else {
            message = "Cần quyền Camera để nhận diện cử chỉ."
        }
    }

    fun turnOn() {
        val missing = buildList {
            if (!hasCamera) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
            ) {
                // Optional: without it the service still runs, the notification is just hidden.
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isEmpty()) {
            HandControlForegroundService.start(context)
            message = null
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("HAND CONTROL", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Text(
            text = if (status.running) "● ACTIVE" else "○ OFF",
            color = if (status.running) Color(0xFF2E7D32) else Color.Gray,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (status.running) {
            Button(
                onClick = { HandControlForegroundService.stop(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("TURN OFF") }
        } else {
            Button(onClick = ::turnOn, modifier = Modifier.fillMaxWidth()) { Text("TURN ON") }
        }

        (message ?: status.error)?.let { Text(it, color = Color(0xFFC62828), fontSize = 13.sp) }

        if (status.running && status.gate == AppGate.Decision.TEST) {
            TestModeCard(status.pose?.label, status.holdProgress, status.testLog)
        }

        StatusCard {
            StatusRow("Camera", status.cameraActive)
            StatusRow("AI (MediaPipe)", status.aiActive)
            StatusRow("Accessibility", accessibilityOn)
            if (!accessibilityOn) {
                Text(
                    "Accessibility OFF: chỉ Volume +/- hoạt động. Bật để vuốt/chạm được.",
                    fontSize = 12.sp,
                    color = Color(0xFFB71C1C)
                )
                OutlinedButton(onClick = { openAccessibilitySettings(context) }) { Text("Mở Cài đặt Accessibility") }
            }
        }

        StatusCard { AppFilterSection() }

        if (status.running) {
            StatusCard {
                Text("Live", fontWeight = FontWeight.SemiBold)
                Text("Hand: ${if (status.handDetected) "YES" else "NO"} | FPS: ${status.fps}", fontSize = 13.sp)
                Text("State: ${status.engineState} | Pose: ${status.pose?.label ?: "-"}", fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { status.holdProgress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                Text(
                    "Gần nhất: ${status.lastGesture?.label ?: "-"} → ${status.lastAction?.label ?: "-"}",
                    fontSize = 13.sp
                )
                status.lastResult?.let { Text(it, fontSize = 12.sp, color = Color.DarkGray) }
                Text("Số gesture đã thực hiện: ${status.gestureCount}", fontSize = 12.sp, color = Color.DarkGray)
            }
        }

        StatusCard {
            Text("Gesture Mapping", fontWeight = FontWeight.SemiBold)
            ActionMapper.DEFAULT_MAPPING.forEach { (gesture, action) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(gesture.label, fontSize = 13.sp)
                    Text("→ ${action.label}", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Xòe bàn tay rồi vuốt dứt khoát, đủ dài lên / xuống / trái / phải để Swipe (nắm tay di chuyển sẽ không tính). Để cả bàn tay nằm trong khung hình. Nắm 4 ngón, giơ ngón cái lên (Like) để chạm 2 lần, chỉ thẳng ngón cái sang trái giữ ~1 giây để Quay lại (Back). Nắm tay giữ ~0.8 giây để Play / Pause. Giữ yên ~0.5 giây để kích hoạt Point Up / Point Down / Like. Xòe tay không làm gì (chỉ dùng để vuốt). Mỗi hành động cách nhau ít nhất 1 giây.",
                fontSize = 12.sp,
                color = Color.DarkGray
            )
        }

        Text(
            "Camera chỉ xử lý trên thiết bị, không lưu, không gửi ảnh đi đâu.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

/** Shown while Hand Control itself is on screen: try gestures safely, nothing is executed. */
@Composable
private fun TestModeCard(pose: String?, holdProgress: Float, log: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE3F2FD), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("CHẾ ĐỘ TEST", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
        Text(
            "Đang ở trong Hand Control: cử chỉ được nhận diện và hiện ở đây nhưng KHÔNG thực hiện " +
                "(không vuốt, không Back, không đổi âm lượng). Mở app đã chọn để điều khiển thật.",
            fontSize = 12.sp,
            color = Color(0xFF0D47A1)
        )
        Text("Đang thấy: ${pose ?: "-"}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = { holdProgress }, modifier = Modifier.fillMaxWidth())
        if (log.isEmpty()) {
            Text("Chưa nhận cử chỉ nào. Thử nắm tay, Like, vuốt...", fontSize = 12.sp, color = Color.DarkGray)
        } else {
            Text("Đã nhận (mới nhất trước):", fontSize = 12.sp, color = Color.DarkGray)
            log.forEachIndexed { i, line ->
                Text(
                    "${i + 1}. $line",
                    fontSize = 14.sp,
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Composable
private fun StatusRow(label: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Text(
            if (on) "ON" else "OFF",
            color = if (on) Color(0xFF2E7D32) else Color(0xFFB71C1C),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
