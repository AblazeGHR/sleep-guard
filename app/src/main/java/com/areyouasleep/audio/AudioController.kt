package com.areyouasleep.audio

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.areyouasleep.data.NowPlaying
import com.areyouasleep.nls.MediaListenerService

/**
 * 两级降级：
 *  1. SESSION —— 拿通知使用权后查所有活跃 MediaSession，直接 transportControls.pause()/play()
 *  2. FOCUS   —— 抢 AudioFocus（AUDIOFOCUS_GAIN），守规矩的播放器自己暂停
 *
 * 降级顺序有讲究：SESSION 能拿到进度，FOCUS 拿不到但兼容性更广。
 */
class AudioController(private val context: Context) {

    enum class Via { SESSION, FOCUS, NONE }

    data class Target(
        val packageName: String,
        val appLabel: String,
        val title: String,
        val subtitle: String,
        val positionMs: Long,
        val durationMs: Long,
    )

    data class Report(
        val via: Via,
        val targets: List<Target>,
        val volumeAtPause: Int,
    )

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val sessionManager: MediaSessionManager? =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private var focusRequest: AudioFocusRequest? = null
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null

    // ---------- 查询 ----------

    private fun activeControllers(): List<MediaController> = runCatching {
        sessionManager?.getActiveSessions(
            ComponentName(context, MediaListenerService::class.java)
        ) ?: emptyList()
    }.getOrDefault(emptyList())

    fun playingControllers(): List<MediaController> =
        activeControllers().filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }

    /** 当前正在播的东西，用于 UI 展示和 30 秒一次的快照 */
    fun snapshot(): NowPlaying? {
        val c = playingControllers().firstOrNull() ?: return null
        return runCatching {
            val ps = c.playbackState
            val md = c.metadata
            val pkg = c.packageName ?: return null
            val label = labelOf(pkg)
            val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            NowPlaying(
                packageName = pkg,
                appLabel = label,
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                    ?: album.takeIf { it.isNotBlank() }
                    ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
                    ?: label,
                subtitle = listOf(artist, album)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · "),
                positionMs = ps?.let { estimatePosition(it) } ?: 0L,
                durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                updatedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    /**
     * PlaybackState.position 是「上报那一刻」的位置，得按播放速度把已经过去的时间补上，
     * 否则暂停瞬间记下的位置会比实际听到的早几秒。
     */
    private fun estimatePosition(ps: PlaybackState): Long {
        var pos = ps.position
        if (ps.state == PlaybackState.STATE_PLAYING && ps.playbackSpeed > 0f) {
            val delta = SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime
            if (delta in 1..60_000L) pos += (delta * ps.playbackSpeed).toLong()
        }
        return pos.coerceAtLeast(0L)
    }

    private fun labelOf(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    // ---------- 暂停 ----------

    fun pauseAll(): Report {
        val volume = runCatching {
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        }.getOrDefault(0)

        val playing = playingControllers()
        if (playing.isNotEmpty()) {
            val targets = ArrayList<Target>(playing.size)
            for (c in playing) {
                val pkg = c.packageName ?: continue
                val md = c.metadata
                val ps = c.playbackState
                val label = labelOf(pkg)
                val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
                val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
                targets += Target(
                    packageName = pkg,
                    appLabel = label,
                    title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                        ?: album.takeIf { it.isNotBlank() }
                        ?: label,
                    subtitle = listOf(artist, album).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                    positionMs = ps?.let { estimatePosition(it) } ?: 0L,
                    durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                )
                runCatching { c.transportControls?.pause() }
            }
            if (targets.isNotEmpty()) return Report(Via.SESSION, targets, volume)
        }

        val granted = requestFocus()
        return Report(if (granted) Via.FOCUS else Via.NONE, emptyList(), volume)
    }

    // ---------- 恢复 ----------

    fun resume(report: Report) {
        when (report.via) {
            Via.SESSION -> {
                val packages = report.targets.map { it.packageName }.toSet()
                var any = false
                activeControllers().forEach { c ->
                    if (c.packageName in packages) {
                        runCatching { c.transportControls?.play() }
                        any = true
                    }
                }
                if (!any) fallbackMediaPlay()
                restoreVolume(report.volumeAtPause)
            }

            Via.FOCUS -> {
                abandonFocus()
                restoreVolume(report.volumeAtPause)
            }

            Via.NONE -> {
                fallbackMediaPlay()
                restoreVolume(report.volumeAtPause)
            }
        }
    }

    /** 会话对象可能已经失效，退一步发媒体键给系统分发 */
    private fun fallbackMediaPlay() {
        runCatching {
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
    }

    /** 用户是按音量键把音频叫回来的，音量本身也被他改动了，这里还原成暂停前的值 */
    private fun restoreVolume(volume: Int) {
        runCatching {
            val am = audioManager ?: return
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) != volume) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            }
        }
    }

    // ---------- 焦点 ----------

    private fun requestFocus(): Boolean {
        val am = audioManager ?: return false
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val listener = AudioManager.OnAudioFocusChangeListener { }
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            // 关键：别让对方只是压低音量（duck），要它老老实实 pause
            .setWillPauseWhenDucked(false)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(listener)
            .build()
        focusListener = listener
        focusRequest = req
        val result = runCatching { am.requestAudioFocus(req) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        runCatching {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
        focusListener = null
    }
}
