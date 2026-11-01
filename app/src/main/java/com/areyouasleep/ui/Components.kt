package com.areyouasleep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(top = 18.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = TextSecondary,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface1),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(8.dp).clip(CircleShape).background(if (active) Accent else TextSecondary)
    )
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(active = granted)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
        }
        if (granted) {
            Text("已开启", fontSize = 12.sp, color = AccentDim)
        } else {
            TextButton(onClick = onAction) { Text(actionLabel, fontSize = 13.sp) }
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.horizontalGradient(listOf(Accent, AccentDim)))
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF0B2409), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
fun PhasePill(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, fontSize = 11.sp, color = color)
    }
}
