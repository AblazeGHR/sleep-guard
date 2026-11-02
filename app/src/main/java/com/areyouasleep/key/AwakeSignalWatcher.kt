package com.areyouasleep.key

import android.app.KeyguardManager
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
 * 「用户还醒着」的信号采集器，两条来源：
 *
 * 1. VOLUME —— 音量键。普通应用拿不到全局按键事件，所以绕个弯：音量键按下去一定会改
 *    System 里的 volume_* 值，观察这些 key 等价于观察到了按键。
 *    盲区：音量已经在 0 或最大时再按不产生变化。
 *
 * 2. SCREEN —— 电源键。系统同样不把 KEYCODE_POWER 交给第三方，但它带来的状态变化能收到：
 *    - ACTION_SCREEN_OFF：按电源键灭屏，或屏幕超时自动熄灭（两种都说明人还醒着）
 *    - ACTION_USER_PRESENT：亮屏后解锁，误判率极低
 *    - ACTION_SCREEN_ON 且未锁屏：抬起唤醒之类的小动作
 *    刻意不听「亮屏 + 锁屏界面」——那多半是来通知时屏幕闪一下，人可能睡得很沉。
 */
class AwakeSignalWatcher(
    private val context: Context,
    private val onAwake: (Signal) -> Unit,
) {

    enum class Signal { VOLUME, SCREEN }

    private val handler = Handler(Looper.getMainLooper())
    private var lastFiredAt = 0L
    private var suppressUntil = 0L

    private val keyguard: KeyguardManager? =
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val key = uri?.lastPathSegment ?: return
            if (key.startsWith("volume_")) fire(Signal.VOLUME)
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            // 部分 ROM 不走 Settings，直接发这个广播
            fire(Signal.VOLUME)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> fire(Signal.SCREEN)
                Intent.ACTION_USER_PRESENT -> fire(Signal.SCREEN)
                Intent.ACTION_SCREEN_ON -> {
                    if (keyguard?.isKeyguardLocked == false) fire(Signal.SCREEN)
                }
            }
        }
    }

    fun start() {
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver,
            )
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                volumeReceiver,
                IntentFilter(ACTION_VOLUME_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(
                context,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(volumeObserver) }
        runCatching { context.unregisterReceiver(volumeReceiver) }
        runCatching { context.unregisterReceiver(screenReceiver) }
    }

    /**
     * 我们自己调 setStreamVolume（还原音量）时也会触发观察者，
     * 得在这段时间里闭嘴，否则会把「程序改音量」误判成「用户按键」，自己续播自己。
     */
    fun suppress(durationMs: Long = 1200L) {
        suppressUntil = SystemClock.elapsedRealtime() + durationMs
    }

    private fun fire(signal: Signal) {
        val now = SystemClock.elapsedRealtime()
        if (now < suppressUntil) return
        // 一次按键可能同时触发 ContentObserver 和广播，去抖
        if (now - lastFiredAt < 150L) return
        lastFiredAt = now
        onAwake(signal)
    }

    private companion object {
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
