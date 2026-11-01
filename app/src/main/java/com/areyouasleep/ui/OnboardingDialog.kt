package com.areyouasleep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun OnboardingDialog(
    nlsEnabled: Boolean,
    batteryIgnored: Boolean,
    onOpenNls: () -> Unit,
    onOpenBattery: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .padding(24.dp),
        ) {
            Text("睡了吗", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "三件事办完它才能真的管用。没办也能用，但会退化成“能暂停、记不住进度”。",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(18.dp))

            GuideStep(
                index = "1",
                title = "通知使用权",
                desc = "读取其他 App 的播放会话，暂停得准、也能记下书名和进度",
                done = nlsEnabled,
                actionLabel = "去开启",
                onAction = onOpenNls,
            )
            GuideStep(
                index = "2",
                title = "忽略电池优化",
                desc = "熄屏后不被系统冻结，十几分钟的计时才不会走丢",
                done = batteryIgnored,
                actionLabel = "去开启",
                onAction = onOpenBattery,
            )
            GuideStep(
                index = "3",
                title = "把媒体音量留在中间",
                desc = "音量已经在 0 或最大时，再按音量键不会产生变化，会被漏判",
                done = true,
                actionLabel = null,
                onAction = {},
            )

            Spacer(Modifier.height(18.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Accent)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("知道了", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(0xFF0B2409))
            }
        }
    }
}

@Composable
private fun GuideStep(
    index: String,
    title: String,
    desc: String,
    done: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (done) Accent.copy(alpha = 0.15f) else Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else index,
                fontSize = 11.sp,
                color = if (done) Accent else TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        if (!done && actionLabel != null) {
            Text(
                actionLabel,
                fontSize = 12.sp,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun StatsRow(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .padding(vertical = 14.dp, horizontal = 12.dp),
            ) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(label, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}
