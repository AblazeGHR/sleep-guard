package com.areyouasleep.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { GuardStore.bookmarks(context) }
    val list by flow.collectAsStateWithLifecycle(initialValue = emptyList<Bookmark>())

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
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

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "睡着一次之后\n这里会留下你听到哪儿了",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 80.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 14.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.at }) { bm -> BookmarkCard(bm) }
            }
        }
    }
}

@Composable
private fun BookmarkCard(bm: Bookmark) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatStamp(bm.at), fontSize = 11.sp, color = TextSecondary)
                Text(
                    if (bm.resumed) "已续播" else "未续播",
                    fontSize = 11.sp,
                    color = if (bm.resumed) AccentDim else TextSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                bm.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
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
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("听到 ${formatPosition(bm.positionMs)}", fontSize = 13.sp, color = Accent)
                if (bm.durationMs > 0L) {
                    Text(
                        " / ${formatPosition(bm.durationMs)}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(bm.appLabel, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}
