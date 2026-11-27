package com.khoi.handlecontrol.ui.control

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khoi.handlecontrol.core.app.AppGate
import com.khoi.handlecontrol.core.app.ControlSettings
import com.khoi.handlecontrol.service.HandControlAccessibilityService
import com.khoi.handlecontrol.service.HandControlForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app the user can open from the launcher. */
private data class InstalledApp(val packageName: String, val label: String)

/**
 * "Only work inside the selected apps": switch, the app currently on screen, and the app picker.
 */
@Composable
fun AppFilterSection() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ControlSettings.init(context) }

    val filterEnabled by ControlSettings.filterEnabled.collectAsState()
    val allowed by ControlSettings.allowedApps.collectAsState()
    val foreground by HandControlAccessibilityService.foregroundPackage.collectAsState()
    val accessibilityOn by HandControlAccessibilityService.isConnected.collectAsState()
    val status by HandControlForegroundService.status.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    val apps by produceState<List<InstalledApp>?>(initialValue = null) { value = loadLaunchableApps(context) }
    val homePackages = remember { homePackages(context) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Chỉ hoạt động trong app đã chọn", fontWeight = FontWeight.SemiBold)
            Text(
                if (filterEnabled) "Ở màn hình Home hoặc app khác: không nhận cử chỉ."
                else "Đang tắt: cử chỉ hoạt động ở mọi nơi, kể cả màn hình Home.",
                fontSize = 12.sp,
                color = Color.DarkGray
            )
        }
        Switch(checked = filterEnabled, onCheckedChange = ControlSettings::setFilterEnabled)
    }

    if (!filterEnabled) return

    if (!accessibilityOn) {
        Text(
            "Cần bật Accessibility để biết bạn đang mở app nào. Khi chưa bật, cử chỉ sẽ bị tạm dừng.",
            fontSize = 12.sp,
            color = Color(0xFFB71C1C)
        )
    }

    val foregroundLabel = when {
        foreground == null -> "chưa biết (hãy mở một app)"
        foreground in homePackages -> "Màn hình Home"
        foreground == context.packageName -> "Hand Control (app này, chế độ test)"
        else -> appLabel(context, foreground!!)
    }
    Text("App đang mở: $foregroundLabel", fontSize = 13.sp)
    if (status.running) {
        val (text, color) = when (status.gate) {
            AppGate.Decision.ACTIVE -> "● Đang điều khiển" to Color(0xFF2E7D32)
            AppGate.Decision.TEST -> "◐ Chế độ test (nhận cử chỉ, không thực hiện)" to Color(0xFF1565C0)
            else -> "○ Tạm dừng" to Color(0xFFE65100)
        }
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

    val selectedLabels = apps?.filter { it.packageName in allowed }?.map { it.label }
    Text(
        when {
            selectedLabels == null -> "Đang tải danh sách app…"
            selectedLabels.isEmpty() -> "Chưa chọn app nào: cử chỉ sẽ không hoạt động ở đâu cả."
            else -> "Đã chọn: " + selectedLabels.joinToString(", ")
        },
        fontSize = 12.sp,
        color = if (selectedLabels?.isEmpty() == true) Color(0xFFB71C1C) else Color.DarkGray
    )
    OutlinedButton(onClick = { showPicker = true }) { Text("Chọn app") }

    if (showPicker) {
        AppPickerDialog(apps = apps, allowed = allowed, onDismiss = { showPicker = false })
    }
}

@Composable
private fun AppPickerDialog(apps: List<InstalledApp>?, allowed: Set<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } },
        title = { Text("Chọn app dùng cử chỉ") },
        text = {
            if (apps == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Selected apps first, so they are easy to find and untick.
                val sorted = remember(apps) { apps.sortedByDescending { it.packageName in allowed } }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(sorted, key = { it.packageName }) { app ->
                        val checked = app.packageName in allowed
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { ControlSettings.setAppAllowed(app.packageName, !checked) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { ControlSettings.setAppAllowed(app.packageName, it) }
                            )
                            Column {
                                Text(app.label, fontSize = 14.sp)
                                Text(app.packageName, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    )
}

/** Apps with a launcher icon, without this app and without home-screen launchers. */
private suspend fun loadLaunchableApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val home = homePackages(context)
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcherIntent, 0)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName && it.packageName !in home }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun homePackages(context: Context): Set<String> {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.queryIntentActivities(homeIntent, 0)
        .mapTo(HashSet()) { it.activityInfo.packageName }
}

private fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    packageName
}
