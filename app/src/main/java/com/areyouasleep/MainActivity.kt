package com.areyouasleep

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areyouasleep.audio.AudioController
import com.areyouasleep.core.GuardState
import com.areyouasleep.data.GuardSettings
import com.areyouasleep.data.GuardStore
import com.areyouasleep.data.Phase
import com.areyouasleep.service.SleepGuardService
import com.areyouasleep.ui.Bg
import com.areyouasleep.ui.HistoryScreen
import com.areyouasleep.ui.HomeScreen
import com.areyouasleep.ui.OnboardingDialog
import com.areyouasleep.ui.SettingsScreen
import com.areyouasleep.ui.SleepTheme
import com.areyouasleep.ui.Surface1
import com.areyouasleep.util.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHORTCUT_START -> SleepGuardService.start(this)
            ACTION_SHORTCUT_PAUSE -> SleepGuardService.pauseNow(this)
        }
    }

    private companion object {
        const val ACTION_SHORTCUT_START = "com.areyouasleep.action.SHORTCUT_START"
        const val ACTION_SHORTCUT_PAUSE = "com.areyouasleep.action.SHORTCUT_PAUSE"
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val phase by GuardState.phase.collectAsStateWithLifecycle()
    val remaining by GuardState.remainingMs.collectAsStateWithLifecycle()
    val total by GuardState.totalMs.collectAsStateWithLifecycle()
    val nowPlaying by GuardState.nowPlaying.collectAsStateWithLifecycle()
    val lastBookmark by GuardState.lastBookmark.collectAsStateWithLifecycle()
    val pausedVia by GuardState.pausedVia.collectAsStateWithLifecycle()
    val bookmarkCaptured by GuardState.bookmarkCaptured.collectAsStateWithLifecycle()
    val nls by GuardState.nlsEnabled.collectAsStateWithLifecycle()
    val battery by GuardState.batteryIgnored.collectAsStateWithLifecycle()

    // initialValue = true：首帧别闪一下引导弹窗，读到 false 再弹
    val onboardedFlow = remember { GuardStore.onboarded(context) }
    val onboarded by onboardedFlow.collectAsStateWithLifecycle(initialValue = true)
    var showGuide by rememberSaveable { mutableStateOf(false) }

    // 冷启动时 GuardState.lastBookmark 是空的，用落盘的记录兜底
    val bookmarksFlow = remember { GuardStore.bookmarks(context) }
    val bookmarks by bookmarksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val latestBookmark = lastBookmark ?: bookmarks.firstOrNull()

    val settingsFlow = remember { GuardStore.settings(context) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = GuardSettings())

    LaunchedEffect(onboarded) {
        if (!onboarded) showGuide = true
    }

    // 没启动守护时也想知道"我是不是正在播"，这样主页能直接说清当前状态
    LaunchedEffect(phase, nls) {
        if (phase != Phase.IDLE || !nls) return@LaunchedEffect
        val probe = AudioController(context.applicationContext)
        while (true) {
            GuardState.nowPlaying.value = probe.snapshot()
            delay(3000L)
        }
    }

    if (showGuide) {
        OnboardingDialog(
            nlsEnabled = nls,
            batteryIgnored = battery,
            onOpenNls = { Permissions.openNlsSettings(context) },
            onOpenBattery = { Permissions.requestIgnoreBattery(context) },
            onDismiss = {
                showGuide = false
                scope.launch { GuardStore.setOnboarded(context) }
            },
        )
    }

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
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
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
                    armedMinutes = settings.armedMinutes,
                    remainingMs = remaining,
                    totalMs = total,
                    nowPlaying = nowPlaying,
                    lastBookmark = latestBookmark,
                    pausedVia = pausedVia,
                    bookmarkCaptured = bookmarkCaptured,
                    nlsEnabled = nls,
                    onStart = {
                        SleepGuardService.start(context)
                        if (!nls) {
                            Toast.makeText(
                                context,
                                "没开通知使用权，暂停可能不生效，也记不住进度",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onStop = { SleepGuardService.stop(context) },
                    onPauseNow = { SleepGuardService.pauseNow(context) },
                    onResumeNow = { SleepGuardService.resumeNow(context) },
                    onOpenNls = { Permissions.openNlsSettings(context) },
                    onOpenHistory = { tab = 1 },
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
