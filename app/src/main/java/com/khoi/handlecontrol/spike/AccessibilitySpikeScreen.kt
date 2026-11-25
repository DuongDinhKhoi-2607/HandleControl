package com.khoi.handlecontrol.spike

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khoi.handlecontrol.service.HandControlAccessibilityService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phase 0 — Feature 3: AccessibilityService Spike UI.
 * Provides test buttons for Swipe Up/Down/Left/Right, Tap, and Back.
 */
@Composable
fun AccessibilitySpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isServiceConnected by HandControlAccessibilityService.isConnected.collectAsState()

    var delay3Seconds by remember { mutableStateOf(true) }
    var statusLog by remember { mutableStateOf("Chưa thực hiện thao tác nào") }
    var pendingJob by remember { mutableStateOf<Job?>(null) }

    fun triggerAction(actionName: String, action: (HandControlAccessibilityService) -> Unit) {
        val service = HandControlAccessibilityService.instance
        if (!isServiceConnected || service == null) {
            statusLog = "AccessibilityService đang OFF. Hãy bật trong Cài đặt trước."
            return
        }

        pendingJob?.cancel()
        pendingJob = scope.launch {
            if (delay3Seconds) {
                for (sec in 3 downTo 1) {
                    statusLog = "Chuẩn bị $actionName sau ${sec}s... (Hãy chuyển sang TikTok / YouTube)"
                    delay(1000L)
                }
            }
            val activeService = HandControlAccessibilityService.instance
            if (activeService == null) {
                statusLog = "Service đã bị ngắt kết nối."
            } else {
                action(activeService)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Phase 0 — AccessibilityService Spike",
            style = MaterialTheme.typography.titleLarge
        )

        // Connection status card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isServiceConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = "Accessibility Service: ${if (isServiceConnected) "ON (Connected)" else "OFF"}",
                color = if (isServiceConnected) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Bật dịch vụ \"Hand Control Gesture Service\" trong Accessibility Settings để test vuốt/chạm.",
                fontSize = 13.sp,
                color = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { openAccessibilitySettings(context) }) {
                Text("Mở Cài đặt Accessibility")
            }
        }

        // Delay toggle for testing on external apps (TikTok / YouTube Shorts / Facebook Reels)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Delay 3s trước khi thực hiện",
                    fontSize = 14.sp
                )
                Text(
                    text = "Bật để có 3 giây chuyển sang TikTok / YouTube Shorts",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Switch(
                checked = delay3Seconds,
                onCheckedChange = { delay3Seconds = it }
            )
        }

        // Action test buttons from Plan.md section 6
        Text(
            text = "Dispatch Gesture Tests",
            style = MaterialTheme.typography.titleMedium
        )

        val onGestureResult: (Boolean, String) -> Unit = { _, message ->
            statusLog = message
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { triggerAction("SWIPE UP") { it.performSwipeUp(onGestureResult) } },
                modifier = Modifier.weight(1f)
            ) {
                Text("TEST SWIPE UP")
            }
            Button(
                onClick = { triggerAction("SWIPE DOWN") { it.performSwipeDown(onGestureResult) } },
                modifier = Modifier.weight(1f)
            ) {
                Text("TEST SWIPE DOWN")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { triggerAction("SWIPE LEFT") { it.performSwipeLeft(onGestureResult) } },
                modifier = Modifier.weight(1f)
            ) {
                Text("TEST SWIPE LEFT")
            }
            Button(
                onClick = { triggerAction("SWIPE RIGHT") { it.performSwipeRight(onGestureResult) } },
                modifier = Modifier.weight(1f)
            ) {
                Text("TEST SWIPE RIGHT")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { triggerAction("TAP") { it.performTap(onGestureResult) } },
                modifier = Modifier.weight(1f)
            ) {
                Text("TEST TAP")
            }
            Button(
                onClick = { triggerAction("BACK") { it.performBackAction(onGestureResult) } },
                modifier = Modifier.weight(1f)
            ) {
                Text("TEST BACK")
            }
        }

        // Result status box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF212121), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "Status Log",
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusLog,
                color = Color(0xFF80CBC4),
                fontSize = 14.sp
            )
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

