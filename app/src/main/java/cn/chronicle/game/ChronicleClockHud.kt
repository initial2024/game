package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.chronicle.core.ChronicleEngine
import cn.chronicle.core.World

/**
 * Calendar and progress come only from persisted World, never a visual-only timer.
 */
@Composable
internal fun ChronicleClockHud(world: World, ui: UiState, vm: GameViewModel, compact: Boolean = false) {
 val hasBulletin = ChronicleEngine.needsPause(world)
 val status = when {
  world.ended -> "本纪年已结束"
  hasBulletin -> "重大奏报待阅：剧情 → 目标与奏报"
  ui.busy -> "正在处理事务，时间暂缓"
  ui.paused -> "时间已暂停 · 点击继续"
  else -> "时间流动中 · 约每2秒推进${ui.speed}日"
 }
 val daysIntoQuarter = world.day % 90
 val nextQuarter = 90 - daysIntoQuarter
 val nextOrder = world.pending.minOfOrNull { it.remainingDays }
 Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
  Column(
   Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (compact) 3.dp else 6.dp),
   verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
   Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
     world.date,
     modifier = Modifier.weight(1f).testTag(if (compact) "map-clock-date" else "clock-date"),
     style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
     fontWeight = FontWeight.SemiBold
    )
    Text(
     "总第${world.day + 1}日",
     modifier = Modifier.testTag(if (compact) "map-clock-day" else "clock-day"),
     style = MaterialTheme.typography.labelSmall,
     color = MaterialTheme.colorScheme.secondary
    )
   }
   Row(verticalAlignment = Alignment.CenterVertically) {
    TextButton(
     onClick = { vm.toggleClock() },
     modifier = Modifier.testTag(if (compact) "map-clock" else "clock-toggle"),
     enabled = !world.ended
    ) { Text(if (ui.paused) "▶ 继续" else "Ⅱ 暂停") }
    TextButton(
     onClick = { vm.changeSpeed() },
     modifier = Modifier.testTag(if (compact) "map-speed" else "clock-speed")
    ) { Text("×${ui.speed}") }
    Text(
     status,
     modifier = Modifier.weight(1f).testTag(if (compact) "map-clock-status" else "clock-status"),
     style = MaterialTheme.typography.labelSmall,
     color = if (hasBulletin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
     maxLines = 2
    )
   }
   if (!compact) {
    LinearProgressIndicator(
     progress = { daysIntoQuarter / 90f },
     modifier = Modifier.fillMaxWidth().height(3.dp)
    )
    Text(
     "距下次季度结算 ${nextQuarter} 日" +
       (nextOrder?.let { " · 最近事务剩余 ${it} 日" } ?: " · 暂无在办事务"),
     style = MaterialTheme.typography.labelSmall,
     color = MaterialTheme.colorScheme.onSurfaceVariant
    )
   }
  }
 }
}
