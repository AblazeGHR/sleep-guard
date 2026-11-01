package com.areyouasleep.data

/** 用户可配置项 */
data class GuardSettings(
    val armedMinutes: Int = 15,
    val resumeMinutes: Int = 5,
    val earphoneTrigger: Boolean = true,
    val hapticFeedback: Boolean = true,
)

enum class Phase {
    /** 没启动 */
    IDLE,

    /** 正在计时，等用户按音量键 */
    ARMED,

    /** 已暂停，等用户按音量键续播 */
    PAUSED,

    /** 恢复窗口已过，音频保持暂停 */
    DONE,
}

/** 当前前台播放器快照 */
data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** 一次“睡着”记录 */
data class Bookmark(
    val at: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val resumed: Boolean,
)
