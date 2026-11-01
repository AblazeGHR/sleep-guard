package com.areyouasleep.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areyouasleep.core.formatClock
import com.areyouasleep.core.formatPosition
import com.areyouasleep.core.formatStamp
import com.areyouasleep.data.Bookmark
import com.areyouasleep.data.NowPlaying
import com.areyouasleep.data.Phase

@Composable
fun HomeScreen(
    phase: Phase,
    remainingMs: Long,
    totalMs: Long,
    nowPlaying: NowPlaying?,
    lastBookmark: Bookmark?,
    pausedVia: String?,
    nlsEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPauseNow: () -> Unit,
    onResumeNow: () -> Unit,
    onOpenNls: () -> Unit,
) {
    val progress = when (phase) {
        Phase.IDLE -> 1f
        Phase.DONE -> 0f
        else -> if (totalMs <= 0L) 1f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    }

    val ringLabel = when (phase) {
        Phase.IDLE -> "待机"
        Phase.DONE -> "已完成"
        else -> formatClock(remainingMs)
    }

    val ringCaption = when (phase) {
        Phase.IDLE -> "开始后，长时间不动音量键就自动暂停"
        Phase.ARMED -> "这段时间内按任意音量键，计时重来"
        Phase.PAUSED -> "按音量键续播"
        Phase.DONE -> "音频保持在暂停，可以安心睡"
    }

    val accentActive = phase == Phase.ARMED || phase == Phase.PAUSED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))

        Column(Modifier.fillMaxWidth()) {
            Text("睡了吗", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("听书睡着后，自动帮你按下暂停", fontSize = 13.sp, color = TextSecondary)
        }

        Spacer(Modifier.height(26.dp))

        CountdownRing(
            progress = progress,
            label = ringLabel,
            caption = ringCaption,
            active = accentActive,
        )

        Spacer(Modifier.height(22.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                Phase.IDLE -> {
                    Button(
                        onClick = onStart,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color(0xFF0B2409),
                        ),
                    ) {
                        Text("开始守护", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Phase.ARMED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onPauseNow,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color(0xFF0B2409),
                            ),
                        ) { Text("现在暂停", fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = onStop) { Text("结束", color = TextSecondary) }
                    }
                }

                Phase.PAUSED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onResumeNow,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color(0xFF0B2409),
                            ),
                        ) { Text("立即续播", fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = onStop) { Text("结束", color = TextSecondary) }
                    }
                }

                Phase.DONE -> {
                    Button(
                        onClick = onStop,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Surface2,
                            contentColor = TextPrimary,
                        ),
                    ) { Text("结束并关闭守护") }
                }
            }
        }

        if (!nlsEnabled) {
            Spacer(Modifier.height(20.dp))
            PermissionBanner(onOpenNls = onOpenNls)
        }

        if (phase == Phase.ARMED && nowPlaying != null) {
            Spacer(Modifier.height(20.dp))
            PlayingCard(nowPlaying)
        }

        if (lastBookmark != null && (phase == Phase.PAUSED || phase == Phase.DONE)) {
            Spacer(Modifier.height(16.dp))
            BookmarkHint(lastBookmark, pausedVia)
        }

        if (phase == Phase.IDLE && lastBookmark != null) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth()) {
                Text("上次听到", fontSize = 12.sp, color = TextSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface1),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            lastBookmark.title,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatStamp(lastBookmark.at)} · ${formatPosition(lastBookmark.positionMs)}",
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun CountdownRing(
    progress: Float,
    label: String,
    caption: String,
    active: Boolean,
) {
    Box(Modifier.size(248.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = Surface2,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            val sweep = 360f * progress
            if (sweep > 1f) {
                drawArc(
                    color = if (active) Accent else TextSecondary,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = if (label.length > 6) 34.sp else 46.sp,
                fontWeight = FontWeight.Light,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = caption,
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}

@Composable
private fun PermissionBanner(onOpenNls: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarnBg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("缺一个权限", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFFC078))
            Spacer(Modifier.height(4.dp))
            Text(
                "没有通知使用权就拿不到其他 App 的播放会话，只能退化为抢占音频焦点——暂停成功率会下降，也记不住听到第几章。",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
            TextButton(onClick = onOpenNls, modifier = Modifier.padding(top = 2.dp)) {
                Text("去开启通知使用权", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PlayingCard(np: NowPlaying) {
    Column(Modifier.fillMaxWidth()) {
        Text("正在播放", fontSize = 12.sp, color = TextSecondary, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface1),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        np.title,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (np.subtitle.isNotBlank()) {
                        Text(
                            np.subtitle,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${np.appLabel} · ${formatPosition(np.positionMs)}",
                        fontSize = 12.sp,
                        color = AccentDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkHint(bm: Bookmark, pausedVia: String?) {
    Column(Modifier.fillMaxWidth()) {
        Text("已记录", fontSize = 12.sp, color = TextSecondary, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface1),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    bm.title,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bm.subtitle.isNotBlank()) {
                    Text(
                        bm.subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "听到 ${formatPosition(bm.positionMs)}",
                    fontSize = 13.sp,
                    color = Accent,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "由 ${if (pausedVia == "SESSION") "播放会话" else "音频焦点"} 暂停 · ${bm.appLabel}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}
