package com.areyouasleep.key

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 普通应用拿不到全局按键事件，所以这里绕一下：音量键按下去一定会改 System 里的 volume_* 值，
 * 观察这些 key 就等于观察到了按键。
 *
 * 已知盲区：音量已经在 0 或最大时再按不会产生变化（配合设置页“把音量调到中间”的提示规避）。
 * 未来可以加无障碍服务做 100% 捕获，见 README。
 */
class VolumeKeyWatcher(
    private val context: Context,
    private val onVolumeKey: () -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var lastFiredAt = 0L
    private var suppressUntil = 0L

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val key = uri?.lastPathSegment ?: return
            // 只看音量相关的 key，别把亮度、字体大小之类的变化也算进来
            if (key.startsWith("volume_")) fire()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            // 部分 ROM 不经过 Settings，直接发这个广播
            fire()
        }
    }

    fun start() {
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer,
            )
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_VOLUME_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        runCatching { context.unregisterReceiver(receiver) }
    }

    /**
     * 我们自己调 setStreamVolume（还原音量）时也会触发观察者，
     * 得在这段时间里闭嘴，否则会把「程序改音量」误判成「用户按键」，直接自己续播自己。
     */
    fun suppress(durationMs: Long = 1200L) {
        suppressUntil = SystemClock.elapsedRealtime() + durationMs
    }

    private fun fire() {
        val now = SystemClock.elapsedRealtime()
        if (now < suppressUntil) return
        // 一次按键可能同时触发 ContentObserver 和广播，去抖
        if (now - lastFiredAt < 150L) return
        lastFiredAt = now
        onVolumeKey()
    }

    private companion object {
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
