package com.khoi.handlecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.khoi.handlecontrol.spike.AccessibilitySpikeScreen
import com.khoi.handlecontrol.spike.CameraPreviewScreen
import com.khoi.handlecontrol.ui.control.ControlScreen
import com.khoi.handlecontrol.ui.theme.HandleControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HandleControlTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("Điều khiển", "Camera + AI", "Accessibility")

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        PrimaryTabRow(selectedTabIndex = selectedTab) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title) }
                                )
                            }
                        }

                        when (selectedTab) {
                            0 -> ControlScreen(modifier = Modifier.fillMaxSize())
                            1 -> CameraPreviewScreen(modifier = Modifier.fillMaxSize())
                            2 -> AccessibilitySpikeScreen(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
