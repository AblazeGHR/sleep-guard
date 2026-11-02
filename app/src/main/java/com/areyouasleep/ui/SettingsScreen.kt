package com.areyouasleep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areyouasleep.data.GuardSettings
import com.areyouasleep.data.GuardStore
import com.areyouasleep.util.Permissions
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    nlsEnabled: Boolean,
    batteryIgnored: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { GuardStore.settings(context) }
    val settings by flow.collectAsStateWithLifecycle(initialValue = GuardSettings())

    fun save(change: (GuardSettings) -> GuardSettings) {
        scope.launch { GuardStore.updateSettings(context, change) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("设置", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("改动立即生效，下一次计时按新参数走", fontSize = 12.sp, color = TextSecondary)

        SectionCard("计时") {
            SliderRow(
                title = "无操作多久算睡着",
                value = settings.armedMinutes,
                range = 1f..60f,
                steps = 58,
                onChange = { v -> save { it.copy(armedMinutes = v) } },
            )
            SliderRow(
                title = "暂停后多久内可续播",
                value = settings.resumeMinutes,
                range = 1f..15f,
                steps = 13,
                onChange = { v -> save { it.copy(resumeMinutes = v) } },
            )
            Text(
                "想快速验证？把上面调成 1 分钟，点开始，一分钟后什么都不按就行。",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
            )
        }

        SectionCard("触发与反馈") {
            SwitchRow(
                title = "耳机断开也算睡着",
                subtitle = "摘下耳机、蓝牙耳机没电断开，比音量键更早给出信号",
                checked = settings.earphoneTrigger,
                onCheckedChange = { v -> save { it.copy(earphoneTrigger = v) } },
            )
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                title = "暂停 / 续播时轻轻震一下",
                subtitle = "给个手感确认，不用睁眼看屏幕",
                checked = settings.hapticFeedback,
                onCheckedChange = { v -> save { it.copy(hapticFeedback = v) } },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "计时中：按音量键、按电源键灭屏、解锁屏幕都算「还醒着」，会把计时清零。" +
                    "暂停后只认音量键——半夜醒来按电源键看时间，不会把有声书放出来。",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
            )
        }

        SectionCard("权限") {
            PermissionRow(
                title = "通知使用权",
                subtitle = "读取其他 App 的播放会话，才能精准暂停并记住进度",
                granted = nlsEnabled,
                actionLabel = "去开启",
                onAction = { Permissions.openNlsSettings(context) },
            )
            PermissionRow(
                title = "忽略电池优化",
                subtitle = "熄屏后不被系统冻结，计时才不会走丢",
                granted = batteryIgnored,
                actionLabel = "去开启",
                onAction = { Permissions.requestIgnoreBattery(context) },
            )
        }

        SectionCard("快捷入口") {
            Text(
                "下拉通知栏 → 编辑快捷开关 → 把「睡了吗」拖到面板上。之后一下开、一下关。",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "长按桌面图标也能直接「开始守护」或「立即暂停」。",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
        }

        SectionCard("鸿蒙保活") {
            Text(
                "鸿蒙 4.x 对后台管得比较严，睡前建议把这三件事做了：",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            StepText("1", "设置 → 应用和服务 → 应用启动管理 → 睡了吗 → 关闭「自动管理」，把自启动、关联启动、后台活动都勾上")
            StepText("2", "最近任务卡片里把「睡了吗」下拉加锁")
            StepText("3", "设置 → 电池 → 更多电池设置 → 打开「休眠时始终保持网络连接」")
            TextButton(
                onClick = { Permissions.openAppDetail(context) },
                modifier = Modifier.padding(top = 2.dp),
            ) { Text("打开应用详情页", fontSize = 13.sp) }
        }

        SectionCard("音量键检测的盲区") {
            BulletText("普通应用拿不到全局按键事件，这里靠的是「音量值发生变化」这个间接信号。")
            BulletText("音量已经在 0 或最大时，再按不会产生变化，会被漏判。开始前把媒体音量留在 40%–70%。")
            BulletText("熄屏时如果音量键转去调铃声，也能识别——所有音量流都在监听范围内。")
        }

        Spacer(Modifier.height(24.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("睡了吗", fontSize = 13.sp, color = TextPrimary)
                Text("v0.1.2 · com.areyouasleep", fontSize = 11.sp, color = TextSecondary)
            }
            Text("本地运行", fontSize = 11.sp, color = AccentDim)
        }

        Spacer(Modifier.height(38.dp))
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 14.sp, color = TextPrimary)
            Text("${local.roundToInt()} 分钟", fontSize = 13.sp, color = Accent)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        Spacer(Modifier.size(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepText(index: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$index.", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(end = 6.dp))
        Text(text, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
}

@Composable
private fun BulletText(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("·", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(end = 8.dp))
        Text(text, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
}
