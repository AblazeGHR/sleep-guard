package com.areyouasleep.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import com.areyouasleep.util.AppLauncher

private val Warn = Color(0xFFFFC078)

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
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val active = phase == Phase.ARMED || phase == Phase.PAUSED

    val target = when (phase) {
        Phase.IDLE -> 1f
        Phase.DONE -> 0f
        else -> if (totalMs <= 0L) 1f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "progress",
    )

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    val ringColor = when (phase) {
        Phase.ARMED -> Accent
        Phase.PAUSED -> Warn
        else -> TextSecondary
    }

    val ringLabel = when (phase) {
        Phase.IDLE -> "待机"
        Phase.DONE -> "已记录"
        else -> formatClock(remainingMs)
    }

    val caption = when (phase) {
        Phase.IDLE -> "开始后，长时间不动音量键就自动暂停"
        Phase.ARMED -> "这段时间里按一下音量键，计时就重来"
        Phase.PAUSED -> "按音量键，把刚才的声音叫回来"
        Phase.DONE -> "音频一直停着，明早再看记录"
    }

    val pillText = when (phase) {
        Phase.IDLE -> "待机"
        Phase.ARMED -> "守护中"
        Phase.PAUSED -> "已暂停"
        Phase.DONE -> "已完成"
    }
    val pillColor = when (phase) {
        Phase.IDLE -> TextSecondary
        Phase.ARMED -> Accent
        Phase.PAUSED -> Warn
        Phase.DONE -> AccentDim
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.width * 0.8f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = 0.11f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.1f),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(size.width / 2f, size.height * 0.1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("睡了吗", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("听书睡着后，替你按下暂停", fontSize = 12.sp, color = TextSecondary)
                }
                PhasePill(text = pillText, color = pillColor)
            }

            Spacer(Modifier.height(28.dp))

            Box(Modifier.size(268.dp), contentAlignment = Alignment.Center) {
                if (active) {
                    Box(
                        Modifier
                            .size(244.dp)
                            .graphicsLayer {
                                scaleX = pulse
                                scaleY = pulse
                                alpha = 0.9f
                            }
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = 0.055f))
                    )
                }

                Canvas(Modifier.size(240.dp)) {
                    val stroke = 11.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)

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
                    if (sweep > 0.5f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    ringColor.copy(alpha = 0.35f),
                                    ringColor,
                                    ringColor.copy(alpha = 0.35f),
                                ),
                            ),
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
                        text = ringLabel,
                        fontSize = if (phase == Phase.IDLE || phase == Phase.DONE) 36.sp else 48.sp,
                        fontWeight = FontWeight.Light,
                        color = TextPrimary,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = caption,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 44.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            when (phase) {
                Phase.IDLE -> GradientButton(text = "开始守护", onClick = onStart)

                Phase.ARMED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton(text = "现在暂停", onClick = onPauseNow)
                    GhostButton(text = "结束", onClick = onStop)
                }

                Phase.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton(text = "立即续播", onClick = onResumeNow)
                    GhostButton(text = "结束", onClick = onStop)
                }

                Phase.DONE -> GhostButton(text = "结束并关闭守护", onClick = onStop)
            }

            if (!nlsEnabled) {
                Spacer(Modifier.height(20.dp))
                PermissionBanner(onOpenNls = onOpenNls)
            }

            if (phase == Phase.ARMED && nowPlaying != null) {
                Spacer(Modifier.height(22.dp))
                PlayingCard(nowPlaying)
            }

            if (lastBookmark != null && (phase == Phase.PAUSED || phase == Phase.DONE)) {
                Spacer(Modifier.height(18.dp))
                BookmarkCard(lastBookmark, pausedVia) {
                    AppLauncher.open(context, lastBookmark.packageName)
                }
            }

            if (phase == Phase.IDLE && lastBookmark != null) {
                Spacer(Modifier.height(22.dp))
                Column(Modifier.fillMaxWidth()) {
                    SectionTitle("上次听到")
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Surface1)
                            .clickable {
                                if (!AppLauncher.open(context, lastBookmark.packageName)) onOpenHistory()
                            }
                            .padding(16.dp),
                    ) {
                        Text(
                            lastBookmark.title,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${formatStamp(lastBookmark.at)} · 听到 ${formatPosition(lastBookmark.positionMs)}",
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "音量键就是你的「我还醒着」按钮",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 11.sp, color = TextSecondary, letterSpacing = 1.2.sp)
}

@Composable
private fun PermissionBanner(onOpenNls: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WarnBg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(6.dp).clip(CircleShape).background(Warn)
            )
            Spacer(Modifier.size(8.dp))
            Text("还缺通知使用权", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Warn)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "没有它就拿不到其他 App 的播放会话，只能退化为抢占音频焦点——暂停成功率下降，也记不住听到第几章。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "去开启 →",
            fontSize = 12.sp,
            color = Accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenNls)
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun PlayingCard(np: NowPlaying) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("正在播放")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Surface1)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(3.dp, 34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Accent)
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    np.title,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (np.subtitle.isNotBlank()) {
                    Text(
                        np.subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(
                formatPosition(np.positionMs),
                fontSize = 12.sp,
                color = AccentDim,
            )
        }
    }
}

@Composable
private fun BookmarkCard(bm: Bookmark, pausedVia: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("已记录")
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Surface1)
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
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
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("听到 ${formatPosition(bm.positionMs)}", fontSize = 13.sp, color = Accent)
                Spacer(Modifier.weight(1f))
                Text(
                    "${if (pausedVia == "SESSION") "会话暂停" else "焦点抢占"} · ${bm.appLabel}",
                    fontSize = 10.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}
