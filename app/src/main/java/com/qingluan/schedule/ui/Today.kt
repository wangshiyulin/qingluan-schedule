package com.qingluan.schedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.model.*
import java.time.*

@Composable
fun TodayScreen(
    s: Snapshot,
    now: LocalDateTime,
    onOccurrence: (Occurrence) -> Unit,
    onMerged: (List<Occurrence>) -> Unit = { onOccurrence(it.first()) },
) {
    val courses =
        remember(s, now.toLocalDate()) {
            lessonBlocks(ScheduleEngine.onDate(s, s.activeTermId, now.toLocalDate()))
        }
    val colors = remember(s.courses) { courseColorMap(s.courses) }
    val term = s.terms.find { it.id == s.activeTermId }
    val minute = now.hour * 60 + now.minute
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkAppearance.current
    LazyColumn(
        Modifier.fillMaxSize().testTag("today-page"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${now.monthValue} 月 ${now.dayOfMonth} 日 · ${weekdayNames[now.dayOfWeek.value-1]}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (term != null) {
                    val week = ScheduleEngine.week(term, now.toLocalDate())
                    Text(
                        when {
                            week == 0 -> "未开学"
                            week > term.weeks -> "学期已结束"
                            else -> "第 $week 周"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (courses.isEmpty()) item { EmptyState("今日无课") }
        items(courses, key = { it.first.key }) { block ->
            val o = block.first
            val ended = minute >= block.endMinute
            val ongoing = !ended && minute >= block.startMinute
            val muted = lerp(scheme.onSurfaceVariant, scheme.surface, if (dark) .20f else .08f)
            val foreground = if (ended) muted else scheme.onSurface
            val secondary = if (ended) muted else scheme.onSurfaceVariant
            val ink = courseInk(Color(colors[o.course.id] ?: o.course.color), dark)
            val shape = RoundedCornerShape(20.dp)
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(shape)
                        .clickable {
                            if (block.members.size > 1) onMerged(block.members) else onOccurrence(o)
                        }
                        .testTag("today-lesson-${o.course.id}"),
                color = if (ongoing) lerp(scheme.surface, ink.fill, .35f) else scheme.surface,
                contentColor = foreground,
                shape = shape,
                border =
                    BorderStroke(
                        .65.dp,
                        if (ongoing) ink.border else scheme.outlineVariant.copy(alpha = .55f),
                    ),
            ) {
                Row(
                    Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            clock(block.startMinute),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (ended) FontWeight.Normal else FontWeight.SemiBold,
                        )
                        Text(
                            clock(block.endMinute),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondary,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            o.course.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (ended) FontWeight.Normal else FontWeight.Bold,
                            textDecoration =
                                if (ended) TextDecoration.LineThrough else TextDecoration.None,
                        )
                        Text(
                            listOf(block.location, block.teacher)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                                .ifBlank { "第 ${block.startPeriod}–${block.endPeriod} 节" },
                            style = MaterialTheme.typography.bodySmall,
                            color = secondary,
                        )
                        if (block.adjusted)
                            Text(
                                "临时调整",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondary,
                            )
                    }
                }
            }
        }
    }
}
