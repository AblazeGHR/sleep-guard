package com.areyouasleep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areyouasleep.core.GuardState
import com.areyouasleep.service.SleepGuardService
import com.areyouasleep.ui.Bg
import com.areyouasleep.ui.HistoryScreen
import com.areyouasleep.ui.HomeScreen
import com.areyouasleep.ui.SettingsScreen
import com.areyouasleep.ui.SleepTheme
import com.areyouasleep.ui.Surface1
import com.areyouasleep.util.Permissions

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SleepTheme {
                val context = LocalContext.current
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    Permissions.refresh(context)
                }
                Root()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Root() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val phase by GuardState.phase.collectAsStateWithLifecycle()
    val remaining by GuardState.remainingMs.collectAsStateWithLifecycle()
    val total by GuardState.totalMs.collectAsStateWithLifecycle()
    val nowPlaying by GuardState.nowPlaying.collectAsStateWithLifecycle()
    val lastBookmark by GuardState.lastBookmark.collectAsStateWithLifecycle()
    val pausedVia by GuardState.pausedVia.collectAsStateWithLifecycle()
    val nls by GuardState.nlsEnabled.collectAsStateWithLifecycle()
    val battery by GuardState.batteryIgnored.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("守护") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("记录") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    phase = phase,
                    remainingMs = remaining,
                    totalMs = total,
                    nowPlaying = nowPlaying,
                    lastBookmark = lastBookmark,
                    pausedVia = pausedVia,
                    nlsEnabled = nls,
                    onStart = { SleepGuardService.start(context) },
                    onStop = { SleepGuardService.stop(context) },
                    onPauseNow = { SleepGuardService.pauseNow(context) },
                    onResumeNow = { SleepGuardService.resumeNow(context) },
                    onOpenNls = { Permissions.openNlsSettings(context) },
                )

                1 -> HistoryScreen()

                else -> SettingsScreen(
                    nlsEnabled = nls,
                    batteryIgnored = battery,
                )
            }
        }
    }
}
