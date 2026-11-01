package com.areyouasleep.core

import com.areyouasleep.data.Bookmark
import com.areyouasleep.data.NowPlaying
import com.areyouasleep.data.Phase
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 进程内唯一状态源。UI 与服务共用一个进程，Activity 重建也不影响。
 */
object GuardState {
    val phase = MutableStateFlow(Phase.IDLE)
    val remainingMs = MutableStateFlow(0L)
    val totalMs = MutableStateFlow(0L)
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val lastBookmark = MutableStateFlow<Bookmark?>(null)
    /** SESSION / FOCUS / NONE，用于 UI 上说明这次是靠哪条路暂停的 */
    val pausedVia = MutableStateFlow<String?>(null)
    /** 本次暂停有没有真的拿到播放进度（纯焦点模式下拿不到） */
    val bookmarkCaptured = MutableStateFlow(true)
    val nlsEnabled = MutableStateFlow(false)
    val batteryIgnored = MutableStateFlow(false)
}
