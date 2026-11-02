package com.areyouasleep.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.areyouasleep.MainActivity
import com.areyouasleep.R
import com.areyouasleep.audio.AudioController
import com.areyouasleep.core.GuardState
import com.areyouasleep.core.formatClock
import com.areyouasleep.core.formatPosition
import com.areyouasleep.data.Bookmark
import com.areyouasleep.data.GuardSettings
import com.areyouasleep.data.GuardStore
import com.areyouasleep.data.Phase
import com.areyouasleep.key.AwakeSignalWatcher
import com.areyouasleep.tile.GuardTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SleepGuardService : Service() {

    companion object {
        const val ACTION_START = "com.areyouasleep.action.START"
        const val ACTION_STOP = "com.areyouasleep.action.STOP"
        const val ACTION_PAUSE_NOW = "com.areyouasleep.action.PAUSE_NOW"
        const val ACTION_RESUME_NOW = "com.areyouasleep.action.RESUME_NOW"
        const val ACTION_I_AM_AWAKE = "com.areyouasleep.action.I_AM_AWAKE"

        private const val CHANNEL_ID = "guard"
        private const val NOTIF_ID = 4101

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SleepGuardService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            send(context, ACTION_STOP)
        }

        fun pauseNow(context: Context) {
            send(context, ACTION_PAUSE_NOW)
        }

        fun resumeNow(context: Context) {
            send(context, ACTION_RESUME_NOW)
        }

        fun iAmAwake(context: Context) {
            send(context, ACTION_I_AM_AWAKE)
        }

        private fun send(context: Context, action: String) {
            runCatching {
                context.startService(
                    Intent(context, SleepGuardService::class.java).setAction(action)
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var audio: AudioController
    private lateinit var watcher: AwakeSignalWatcher

    private var settings = GuardSettings()
    private var report: AudioController.Report? = null
    private var timerJob: Job? = null
    private var windowJob: Job? = null
    private var snapshotJob: Job? = null
    private var tickerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var anchor: MediaSession? = null
    private var noisy: BroadcastReceiver? = null
    private var foreground = false
    private var running = false

    override fun onCreate() {
        super.onCreate()
        audio = AudioController(this)
        watcher = AwakeSignalWatcher(this) { signal -> onAwakeSignal(signal) }
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        // 服务没在跑的时候收到「暂停 / 续播 / 我还醒着」，说明是通知或快捷方式
        // 发来的野指令，直接收摊，别留下一个没有前台通知的空服务。
        if (action != ACTION_START && !running) {
            if (action == ACTION_STOP) guardStateReset()
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START -> {
                ensureForeground()
                if (GuardState.phase.value == Phase.DONE) {
                    // 从「已完成」再开一轮：只把焦点松开，别把上一轮的音频顶起来
                    runCatching { audio.releaseFocus() }
                    report = null
                    guardStateReset()
                    running = false
                }
                if (!running) begin()
            }

            ACTION_STOP -> {
                // 不主动续播：结束了就让它安静停着，用户自己去听书 App 点播放
                shutdown(resumeAudio = false)
                return START_NOT_STICKY
            }

            ACTION_PAUSE_NOW -> if (GuardState.phase.value == Phase.ARMED) triggerSleep()

            ACTION_RESUME_NOW -> if (GuardState.phase.value == Phase.PAUSED) resumeFromPause()

            ACTION_I_AM_AWAKE -> onUserAwakeSignal()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        timerJob?.cancel()
        windowJob?.cancel()
        snapshotJob?.cancel()
        tickerJob?.cancel()
        watcher.stop()
        unregisterNoisy()
        releaseWakeLock()
        anchor?.let {
            runCatching { it.isActive = false }
            runCatching { it.release() }
        }
        anchor = null
        runCatching { audio.releaseFocus() }
        // 服务没了就守不住任何东西了，状态一律回到待机
        guardStateReset()
        refreshTile()
        scope.cancel()
        super.onDestroy()
    }

    private fun guardStateReset() {
        GuardState.phase.value = Phase.IDLE
        GuardState.remainingMs.value = 0
        GuardState.totalMs.value = 0
        GuardState.pausedVia.value = null
        GuardState.bookmarkCaptured.value = true
    }

    // ---------- 启动 ----------

    private fun begin() {
        running = true
        scope.launch {
            settings = GuardStore.currentSettings(this@SleepGuardService)
            acquireWakeLock()
            watcher.stop()
            watcher.start()
            registerNoisy()
            anchorSession()
            arm()
            startSnapshotLoop()
            startTicker()

            // 设置页改完参数立刻生效；如果正在计时且时长变了，按新时长重来
            GuardStore.settings(this@SleepGuardService).collect { fresh ->
                val durationChanged = fresh.armedMinutes != settings.armedMinutes
                settings = fresh
                if (durationChanged && GuardState.phase.value == Phase.ARMED) arm()
            }
        }
    }

    private fun arm() {
        if (!running) return
        timerJob?.cancel()
        windowJob?.cancel()
        val total = settings.armedMinutes * 60_000L
        GuardState.phase.value = Phase.ARMED
        GuardState.pausedVia.value = null
        GuardState.totalMs.value = total
        GuardState.remainingMs.value = total
        updateNotification()
        refreshTile()

        timerJob = scope.launch {
            val deadline = SystemClock.elapsedRealtime() + total
            while (isActive) {
                val left = deadline - SystemClock.elapsedRealtime()
                GuardState.remainingMs.value = left.coerceAtLeast(0L)
                if (left <= 0L) break
                // 250ms 一次：Doze 里 delay 会漂，但用 elapsedRealtime 当基准，误差不会累积
                delay(250)
            }
            if (isActive) triggerSleep()
        }
    }

    // ---------- 睡着 ----------

    private fun triggerSleep() {
        if (GuardState.phase.value != Phase.ARMED) return
        timerJob?.cancel()
        watcher.suppress(1500)

        val r = audio.pauseAll()
        report = r
        GuardState.pausedVia.value = r.via.name
        GuardState.bookmarkCaptured.value = r.targets.isNotEmpty()

        r.targets.firstOrNull()?.let { t ->
            val bookmark = Bookmark(
                at = System.currentTimeMillis(),
                packageName = t.packageName,
                appLabel = t.appLabel,
                title = t.title,
                subtitle = t.subtitle,
                positionMs = t.positionMs,
                durationMs = t.durationMs,
                resumed = false,
            )
            GuardState.lastBookmark.value = bookmark
            scope.launch { GuardStore.addBookmark(this@SleepGuardService, bookmark) }
        }

        if (r.via == AudioController.Via.NONE) {
            // 没有活跃会话、也抢不到焦点：多半是此刻根本没在播。
            // 不退出，重新开一轮计时——宁可多守一会儿，也别在用户真在听的时候掉链子。
            report = null
            toast("没找到正在播放的音频，继续守着")
            arm()
            return
        }

        GuardState.phase.value = Phase.PAUSED
        val window = settings.resumeMinutes * 60_000L
        GuardState.totalMs.value = window
        GuardState.remainingMs.value = window
        startWindow(window)
        updateNotification()
        refreshTile()
        vibrate()

        val how = if (r.via == AudioController.Via.SESSION) "已暂停播放" else "已抢占音频焦点"
        toast("$how，${settings.resumeMinutes} 分钟内按音量键续播")
    }

    private fun startWindow(total: Long) {
        windowJob?.cancel()
        windowJob = scope.launch {
            val deadline = SystemClock.elapsedRealtime() + total
            while (isActive) {
                val left = deadline - SystemClock.elapsedRealtime()
                GuardState.remainingMs.value = left.coerceAtLeast(0L)
                if (left <= 0L) break
                delay(250)
            }
            if (isActive) expireWindow()
        }
    }

    /**
     * 恢复窗口过了。这里故意不 abandon 焦点、也不 stopSelf：
     * 进程一退，焦点就释放，对方的播放器会自己 resume —— 等于白暂停。
     * 保持前台服务（已释放 wakelock，耗电可忽略）让音频一直停着，等用户早上自己结束。
     */
    private fun expireWindow() {
        GuardState.phase.value = Phase.DONE
        GuardState.remainingMs.value = 0
        watcher.stop()
        releaseWakeLock()
        updateNotification()
        refreshTile()
    }

    private fun resumeFromPause() {
        if (GuardState.phase.value != Phase.PAUSED) return
        windowJob?.cancel()
        watcher.suppress(1500)
        report?.let { audio.resume(it) }
        report = null
        GuardState.phase.value = Phase.ARMED
        GuardState.lastBookmark.value?.let { bm ->
            scope.launch { GuardStore.markResumed(this@SleepGuardService, bm.at) }
        }
        arm()
        vibrate()
        toast("已续播")
    }

    /** 音量键、通知栏按钮、耳机断连都汇到这里 */
    private fun onUserAwakeSignal() {
        when (GuardState.phase.value) {
            Phase.ARMED -> arm()
            Phase.PAUSED -> resumeFromPause()
            else -> Unit
        }
    }

    /**
     * 音量键和电源键的区别在这里：
     * 计时中（ARMED）两者都算「还醒着」，重置计时；
     * 已暂停（PAUSED）只认音量键——半夜醒来按一下电源键看时间，不该把有声书放出来。
     */
    private fun onAwakeSignal(signal: AwakeSignalWatcher.Signal) {
        when (GuardState.phase.value) {
            Phase.ARMED -> arm()
            Phase.PAUSED -> if (signal == AwakeSignalWatcher.Signal.VOLUME) resumeFromPause()
            else -> Unit
        }
    }

    // ---------- 耳机断连（可选触发） ----------

    private fun registerNoisy() {
        if (noisy != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (GuardState.phase.value == Phase.ARMED && settings.earphoneTrigger) {
                    triggerSleep()
                }
            }
        }
        noisy = r
        runCatching {
            ContextCompat.registerReceiver(
                this,
                r,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun unregisterNoisy() {
        noisy?.let { runCatching { unregisterReceiver(it) } }
        noisy = null
    }

    // ---------- 空会话：把音量键钉在媒体流上 ----------

    /**
     * 对方被暂停后系统会认为「没有媒体在播」，此时音量键可能转去调铃声流，
     * 我们的 ContentObserver 就收不到媒体音量变化了。挂一个 STATE_PAUSED 的空
     * MediaSession，系统才会继续把音量键算在 STREAM_MUSIC 上。
     */
    private fun anchorSession() {
        if (anchor != null) return
        runCatching {
            val s = MediaSession(this, "SleepGuard")
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                    .build()
            )
            s.setSessionActivity(contentIntent())
            s.isActive = true
            anchor = s
        }
    }

    // ---------- 快照 ----------

    private fun startSnapshotLoop() {
        snapshotJob?.cancel()
        snapshotJob = scope.launch {
            while (isActive) {
                if (GuardState.phase.value == Phase.ARMED) {
                    audio.snapshot()?.let { np ->
                        GuardState.nowPlaying.value = np
                        runCatching { GuardStore.saveSnapshot(this@SleepGuardService, np) }
                    }
                }
                delay(30_000L)
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                if (GuardState.phase.value == Phase.ARMED || GuardState.phase.value == Phase.PAUSED) {
                    updateNotification()
                }
            }
        }
    }

    // ---------- 关机 ----------

    private fun shutdown(resumeAudio: Boolean) {
        running = false
        timerJob?.cancel()
        windowJob?.cancel()
        snapshotJob?.cancel()
        tickerJob?.cancel()
        watcher.stop()
        unregisterNoisy()
        if (resumeAudio) {
            report?.let { runCatching { audio.resume(it) } }
        }
        report = null
        releaseWakeLock()
        anchor?.let {
            runCatching { it.isActive = false }
            runCatching { it.release() }
        }
        anchor = null
        guardStateReset()
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        refreshTile()
        stopSelf()
    }

    // ---------- 通知 ----------

    private fun ensureForeground() {
        if (foreground) return
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        foreground = true
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "睡眠守护", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    private fun updateNotification() {
        if (!foreground) return
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val phase = GuardState.phase.value
        val bm = GuardState.lastBookmark.value
        val np = GuardState.nowPlaying.value

        val title = when (phase) {
            Phase.ARMED -> "守护中 · 还剩 ${formatClock(GuardState.remainingMs.value)}"
            Phase.PAUSED -> "已暂停 · ${formatClock(GuardState.remainingMs.value)} 内按音量键续播"
            Phase.DONE -> "已记录，音频保持在暂停"
            Phase.IDLE -> "未启动"
        }

        val body = when (phase) {
            Phase.ARMED -> np?.let { "${it.title} · ${formatPosition(it.positionMs)}" }
                ?: "按一下音量键表示你还醒着"
            Phase.PAUSED, Phase.DONE -> bm?.let {
                "《${it.title}》${formatPosition(it.positionMs)}" +
                    if (it.subtitle.isBlank()) "" else " · ${it.subtitle}"
            } ?: "没有记录到播放内容"
            Phase.IDLE -> "点按开始"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val total = GuardState.totalMs.value
        if ((phase == Phase.ARMED || phase == Phase.PAUSED) && total > 0L) {
            val pct = ((GuardState.remainingMs.value.toFloat() / total) * 100f)
                .toInt().coerceIn(0, 100)
            builder.setProgress(100, pct, false)
        }

        when (phase) {
            Phase.ARMED -> {
                builder.addAction(0, "我还醒着", serviceIntent(ACTION_I_AM_AWAKE, 10))
                builder.addAction(0, "立即暂停", serviceIntent(ACTION_PAUSE_NOW, 11))
                builder.addAction(0, "结束", serviceIntent(ACTION_STOP, 12))
            }

            Phase.PAUSED -> {
                builder.addAction(0, "续播", serviceIntent(ACTION_RESUME_NOW, 13))
                builder.addAction(0, "结束", serviceIntent(ACTION_STOP, 14))
            }

            Phase.DONE -> builder.addAction(0, "结束", serviceIntent(ACTION_STOP, 15))

            Phase.IDLE -> Unit
        }

        return builder.build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, SleepGuardService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ---------- 杂项 ----------

    private fun refreshTile() {
        runCatching {
            TileService.requestListeningState(
                this,
                ComponentName(this, GuardTileService::class.java),
            )
        }
    }

    private fun vibrate() {
        if (!settings.hapticFeedback) return
        runCatching {
            val effect = VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE)
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            v?.vibrate(effect)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepGuard:timer").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        }
    }
}
