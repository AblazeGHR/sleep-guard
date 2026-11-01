package com.areyouasleep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areyouasleep.core.formatPosition
import com.areyouasleep.core.formatStamp
import com.areyouasleep.data.Bookmark
import com.areyouasleep.data.GuardStore
import com.areyouasleep.util.AppLauncher
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { GuardStore.bookmarks(context) }
    val list by flow.collectAsStateWithLifecycle(initialValue = emptyList<Bookmark>())

    val weekCount = remember(list) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        list.count { it.at >= weekAgo }
    }

    val avgTime = remember(list) {
        if (list.isEmpty()) return@remember "--:--"
        val minutes = list.take(30).map { bm ->
            val c = Calendar.getInstance().apply { timeInMillis = bm.at }
            c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        }.average()
        "%02d:%02d".format((minutes / 60).toInt(), (minutes % 60).toInt())
    }

    val resumedCount = remember(list) { list.count { it.resumed } }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("记录", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (list.isEmpty()) "还没有记录" else "共 ${list.size} 次",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
            if (list.isNotEmpty()) {
                TextButton(onClick = { scope.launch { GuardStore.clearBookmarks(context) } }) {
                    Text("清空", fontSize = 13.sp, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.padding(horizontal = 22.dp)) {
            StatsRow(
                listOf(
                    "近 7 天" to "${weekCount}次",
                    "平均入睡" to avgTime,
                    "续播回来" to "${resumedCount}次",
                )
            )
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "睡着一次之后\n这里会留下你听到哪儿了",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 90.dp),
                )
            }
        } else {
            val grouped = remember(list) { list.groupBy { dayLabel(it.at) } }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 18.dp),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (day, dayItems) ->
                    item(key = "header_$day") {
                        Text(
                            day,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(dayItems, key = { it.at }) { bm ->
                        BookmarkCard(bm) { AppLauncher.open(context, bm.packageName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(bm: Bookmark, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(3.dp, 52.dp)
                .clip(RoundedCornerShape(50))
                .background(if (bm.resumed) AccentDim else Accent.copy(alpha = 0.4f))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatStamp(bm.at), fontSize = 11.sp, color = TextSecondary)
                Text(
                    if (bm.resumed) "续播回来" else "没续播",
                    fontSize = 11.sp,
                    color = if (bm.resumed) AccentDim else TextSecondary,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                bm.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
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
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("听到 ${formatPosition(bm.positionMs)}", fontSize = 13.sp, color = Accent)
                if (bm.durationMs > bm.positionMs && bm.durationMs > 0L) {
                    Text(
                        " / ${formatPosition(bm.durationMs)}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(bm.appLabel, fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
}

private fun dayLabel(at: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    if (sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) return "今天"
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameYear && yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) return "昨天"
    return SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(at))
}
