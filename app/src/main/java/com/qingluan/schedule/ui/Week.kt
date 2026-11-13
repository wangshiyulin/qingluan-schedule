package com.qingluan.schedule.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingluan.schedule.data.AppSettings
import com.qingluan.schedule.model.*
import java.time.*

@Composable
fun WeekScreen(
    s: Snapshot,
    p: AppSettings,
    now: LocalDateTime,
    pager: PagerState,
    onOccurrence: (Occurrence) -> Unit,
    onAdd: (Int, Int) -> Unit,
    onEditCourse: (Course) -> Unit,
    onMerged: (List<Occurrence>) -> Unit = { onOccurrence(it.first()) },
) {
    val term = s.terms.find { it.id == s.activeTermId }
    if (term == null) {
        EmptyState("暂无课表")
        return
    }
    val prepared = remember(s, term.id) { ScheduleEngine.prepare(s, term.id)!! }
    val colors = remember(s.courses) { courseColorMap(s.courses) }
    HorizontalPager(
        state = pager,
        modifier =
            Modifier.fillMaxSize().testTag("week-pager").semantics {
                stateDescription = "第 ${pager.currentPage+1} 周"
            },
        beyondViewportPageCount = 0,
        key = { it },
    ) { index ->
        WeekGrid(
            s,
            p,
            now,
            term,
            index + 1,
            prepared,
            colors,
            onOccurrence,
            onAdd,
            onEditCourse,
            onMerged,
        )
    }
}

@Composable
private fun WeekGrid(
    s: Snapshot,
    p: AppSettings,
    now: LocalDateTime,
    term: Term,
    week: Int,
    prepared: ScheduleEngine.Prepared,
    courseColors: Map<String, Long>,
    onOccurrence: (Occurrence) -> Unit,
    onAdd: (Int, Int) -> Unit,
    onEditCourse: (Course) -> Unit,
    onMerged: (List<Occurrence>) -> Unit,
) {
    val dates =
        remember(term.startDate, term.firstDayOfWeek, week, p.showWeekend) {
            val start =
                ScheduleEngine.weekStart(LocalDate.parse(term.startDate), term.firstDayOfWeek)
                    .plusWeeks((week - 1).toLong())
            (0..6)
                .map { start.plusDays(it.toLong()) }
                .filter { p.showWeekend || it.dayOfWeek.value <= 5 }
        }
    val periods = s.timetables.first { it.id == term.timetableId }.periods
    val row = p.rowHeight.dp
    val dark = LocalDarkAppearance.current
    val fontScale = LocalDensity.current.fontScale
    val days = dates.size
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = ((maxWidth - 44.dp) / days).coerceAtLeast(1.dp)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
            Row {
                Box(
                    Modifier.width(44.dp).height(50.dp).drawBehind {
                        drawLine(
                            gridColor,
                            Offset(size.width, 0f),
                            Offset(size.width, size.height),
                            .65.dp.toPx(),
                        )
                        drawLine(
                            gridColor,
                            Offset(0f, size.height),
                            Offset(size.width, size.height),
                            .65.dp.toPx(),
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("节次", style = MaterialTheme.typography.labelSmall)
                }
                repeat(days) { i ->
                    val date = dates[i]
                    val today = date == now.toLocalDate()
                    Surface(
                        color =
                            if (today)
                                androidx.compose.ui.graphics.lerp(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.primary,
                                    .10f,
                                )
                            else Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.width(width).height(50.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                weekdayNames[date.dayOfWeek.value - 1],
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                "${date.monthValue}/${date.dayOfMonth}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Row {
                Column(Modifier.width(44.dp)) {
                    periods.forEachIndexed { i, period ->
                        Column(
                            Modifier.height(row)
                                .fillMaxWidth()
                                .testTag("period-time-$i")
                                .drawBehind {
                                    drawLine(
                                        gridColor,
                                        Offset(size.width, 0f),
                                        Offset(size.width, size.height),
                                        .65.dp.toPx(),
                                    )
                                    drawLine(
                                        gridColor,
                                        Offset(0f, size.height),
                                        Offset(size.width, size.height),
                                        .65.dp.toPx(),
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "${i+1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                clock(period.start),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                clock(period.end),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                repeat(days) { day ->
                    val date = dates[day]
                    val list =
                        remember(prepared, date, p.showNonCurrentWeek) {
                            val actual = prepared.onDate(date)
                            val ghosts =
                                if (p.showNonCurrentWeek)
                                    prepared.nonCurrentOnDate(date).filter { ghost ->
                                        actual.none {
                                            it.course.id == ghost.course.id &&
                                                it.meeting.id == ghost.meeting.id
                                        }
                                    }
                                else emptyList()
                            (lessonBlocks(actual).map { it to false } +
                                    lessonBlocks(ghosts).map { it to true })
                                .sortedWith(compareBy({ it.first.startPeriod }, { it.second }))
                        }
                    val (assigned, laneCount) =
                        remember(list) {
                            val lanes = mutableListOf<Int>()
                            val assigned = list.map { (o, inactive) ->
                                var lane = lanes.indexOfFirst { it < o.startPeriod }
                                if (lane < 0) {
                                    lane = lanes.size
                                    lanes += o.endPeriod
                                } else lanes[lane] = o.endPeriod
                                Triple(o, lane, inactive)
                            }
                            assigned to maxOf(1, lanes.size)
                        }
                    Box(Modifier.width(width).height(row * periods.size)) {
                        Column {
                            repeat(periods.size) { period ->
                                Box(
                                    Modifier.width(width)
                                        .height(row)
                                        .testTag("period-grid-$day-$period")
                                        .drawBehind {
                                            drawLine(
                                                gridColor,
                                                Offset(0f, size.height),
                                                Offset(size.width, size.height),
                                                .65.dp.toPx(),
                                            )
                                        }
                                        .clickable { onAdd(date.dayOfWeek.value, period + 1) }
                                        .semantics {
                                            contentDescription = "${date} 第 ${period+1} 节，添加课程"
                                        }
                                )
                            }
                        }
                        assigned.forEach { (block, lane, inactive) ->
                            val o = block.first
                            val color = Color(courseColors[o.course.id] ?: o.course.color)
                            val ink =
                                remember(color, dark, inactive) { courseInk(color, dark, inactive) }
                            val height = row * (block.endPeriod - block.startPeriod + 1)
                            val locationLines =
                                if (height.value >= 100 && fontScale <= 1.2f) 2 else 1
                            val detailHeight =
                                (if (block.location.isNotBlank()) 13f * locationLines + 3 else 0f) +
                                    (if (block.teacher.isNotBlank()) 16f else 0f)
                            val titleLines =
                                ((height.value - 16 - detailHeight * fontScale) /
                                        (15.6f * fontScale))
                                    .toInt()
                                    .coerceIn(1, 12)
                            val shape = RoundedCornerShape(10.dp)
                            Surface(
                                color = ink.fill,
                                contentColor = ink.text,
                                shape = shape,
                                border = BorderStroke(.65.dp, ink.border),
                                modifier =
                                    Modifier.offset(
                                            x = width / laneCount * lane,
                                            y = row * (block.startPeriod - 1),
                                        )
                                        .width(width / laneCount)
                                        .height(height)
                                        .padding(2.dp)
                                        .clip(shape)
                                        .testTag("course-block-${o.key}")
                                        .clickable {
                                            if (inactive) onEditCourse(o.course)
                                            else if (block.members.size > 1) onMerged(block.members)
                                            else onOccurrence(o)
                                        }
                                        .semantics {
                                            contentDescription =
                                                "${date} ${o.course.name} ${clock(block.startMinute)}至${clock(block.endMinute)} ${block.location}"
                                        },
                            ) {
                                Column(
                                    Modifier.fillMaxSize()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Top),
                                ) {
                                    Text(
                                        o.course.name,
                                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                                        fontSize = 13.sp,
                                        lineHeight = 15.6.sp,
                                        textAlign = TextAlign.Start,
                                        style =
                                            MaterialTheme.typography.bodySmall.copy(
                                                lineBreak = LineBreak.Paragraph
                                            ),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = titleLines,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (block.teacher.isNotBlank())
                                        Text(
                                            block.teacher,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start,
                                            color = ink.detail,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    if (block.location.isNotBlank())
                                        Text(
                                            block.location,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start,
                                            color = ink.detail,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = locationLines,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
