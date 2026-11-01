package com.areyouasleep.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 倒计时 / 恢复窗口用 mm:ss */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999) / 1000
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

/** 播放位置用 h:mm:ss */
fun formatPosition(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatStamp(epochMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(epochMs))
