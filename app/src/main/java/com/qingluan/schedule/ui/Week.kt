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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
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
    preview: Boolean = false,
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
            preview,
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
    preview: Boolean,
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
    val dark = LocalDarkAppearance.current
    val days = dates.size
    val gridColor =
        if (p.showGridLines) MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
        else Color.Transparent
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = ((maxWidth - p.timeColumnWidth.dp) / days).coerceAtLeast(1.dp)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val titleStyle =
            TextStyle(
                fontSize = (13 * p.courseFontScale).sp,
                lineHeight = (15.6f * p.courseFontScale).sp,
                fontWeight = if (p.boldCourseNames) FontWeight.Bold else FontWeight.Normal,
                lineBreak = LineBreak.Paragraph,
            )
        val detailStyle =
            TextStyle(
                fontSize = (10 * p.courseFontScale).sp,
                lineHeight = (13 * p.courseFontScale).sp,
            )
        val columns =
            remember(prepared, dates, p.showNonCurrentWeek) {
                dates.map { date ->
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
                    val list =
                        (lessonBlocks(actual).map { it to false } +
                                lessonBlocks(ghosts).map { it to true })
                            .sortedWith(compareBy({ it.first.startPeriod }, { it.second }))
                    val lanes = mutableListOf<Int>()
                    val assigned = list.map { (block, inactive) ->
                        var lane = lanes.indexOfFirst { it < block.startPeriod }
                        if (lane < 0) {
                            lane = lanes.size
                            lanes += block.endPeriod
                        } else lanes[lane] = block.endPeriod
                        Triple(block, lane, inactive)
                    }
                    assigned to maxOf(1, lanes.size)
                }
            }
        val row =
            remember(
                columns,
                width,
                p.rowHeight,
                p.courseFontScale,
                p.boldCourseNames,
                p.courseOuterPadding,
                p.courseInnerPadding,
                p.showTeacher,
                p.showLocation,
                p.showCourseStartTime,
                density.density,
                density.fontScale,
            ) {
                var needed = p.rowHeight.toFloat()
                for ((blocks, lanes) in columns) for ((block, _, _) in blocks) {
                    val spacing =
                        courseSpacing(
                            (width / lanes).value,
                            p.courseOuterPadding,
                            p.courseInnerPadding,
                            13 * p.courseFontScale * density.fontScale + 2,
                        )
                    val textWidth =
                        with(density) { spacing.textWidth((width / lanes).value).dp.roundToPx() }
                            .coerceAtLeast(1)
                    fun height(text: String, style: TextStyle) =
                        measurer
                            .measure(text, style, constraints = Constraints(maxWidth = textWidth))
                            .size
                            .height / density.density
                    var total =
                        height(block.first.course.name, titleStyle) +
                            2 * spacing.outer +
                            2 * spacing.inner +
                            6f
                    if (p.showCourseStartTime)
                        total += height(clock(block.startMinute), detailStyle) + 3f
                    if (p.showTeacher && block.teacher.isNotBlank())
                        total += height(block.teacher, detailStyle) + 3f
                    if (p.showLocation && block.location.isNotBlank())
                        total +=
                            height(
                                block.location,
                                detailStyle.copy(fontWeight = FontWeight.SemiBold),
                            ) + 3f
                    needed =
                        maxOf(
                            needed,
                            kotlin.math.ceil(total / (block.endPeriod - block.startPeriod + 1)),
                        )
                }
                needed.dp
            }
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(bottom = if (preview) 0.dp else 100.dp)
        ) {
            Row {
                Box(
                    Modifier.width(p.timeColumnWidth.dp)
                        .height(p.dayHeaderHeight.dp)
                        .testTag("time-header")
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
                        modifier = Modifier.width(width).height(p.dayHeaderHeight.dp),
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
                            if (p.showDates)
                                Text(
                                    "${date.monthValue}/${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                        }
                    }
                }
            }
            Row {
                Column(Modifier.width(p.timeColumnWidth.dp)) {
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
                            if (p.showSectionTime)
                                Text(
                                    clock(period.start),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            if (p.showSectionTime)
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
                    val (assigned, laneCount) = columns[day]
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
                            val shape = RoundedCornerShape(p.courseCornerRadius.dp)
                            val spacing =
                                courseSpacing(
                                    (width / laneCount).value,
                                    p.courseOuterPadding,
                                    p.courseInnerPadding,
                                    13 * p.courseFontScale * density.fontScale + 2,
                                )
                            Surface(
                                color = ink.fill.copy(alpha = ink.fill.alpha * p.courseOpacity),
                                contentColor = ink.text,
                                shape = shape,
                                border =
                                    if (p.showCourseBorders) BorderStroke(.65.dp, ink.border)
                                    else null,
                                modifier =
                                    Modifier.offset(
                                            x = width / laneCount * lane,
                                            y = row * (block.startPeriod - 1),
                                        )
                                        .width(width / laneCount)
                                        .height(height)
                                        .padding(spacing.outer.dp)
                                        .clip(shape)
                                        .testTag("course-block-${o.key}")
                                        .clickable {
                                            if (inactive) onEditCourse(o.course)
                                            else if (block.members.size > 1) onMerged(block.members)
                                            else onOccurrence(o)
                                        }
                                        .semantics {
                                            contentDescription =
                                                listOf(
                                                        date.toString(),
                                                        o.course.name,
                                                        "${clock(block.startMinute)}至${clock(block.endMinute)}",
                                                        if (p.showTeacher) block.teacher else "",
                                                        if (p.showLocation) block.location else "",
                                                    )
                                                    .filter(String::isNotBlank)
                                                    .joinToString(" ")
                                        },
                            ) {
                                Column(
                                    Modifier.fillMaxSize()
                                        .padding(
                                            horizontal = spacing.inner.dp,
                                            vertical = (spacing.inner + 2).dp,
                                        ),
                                    horizontalAlignment =
                                        if (p.centerCourseText) Alignment.CenterHorizontally
                                        else Alignment.Start,
                                    verticalArrangement =
                                        Arrangement.spacedBy(
                                            3.dp,
                                            if (p.centerCourseVertically) Alignment.CenterVertically
                                            else Alignment.Top,
                                        ),
                                ) {
                                    Text(
                                        o.course.name,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontSize = (13 * p.courseFontScale).sp,
                                        lineHeight = (15.6f * p.courseFontScale).sp,
                                        textAlign =
                                            if (p.centerCourseText) TextAlign.Center
                                            else TextAlign.Start,
                                        style = titleStyle,
                                        fontWeight =
                                            if (p.boldCourseNames) FontWeight.Bold
                                            else FontWeight.Normal,
                                    )
                                    if (p.showCourseStartTime)
                                        Text(
                                            clock(block.startMinute),
                                            modifier = Modifier.fillMaxWidth(),
                                            style = detailStyle,
                                            color = ink.detail,
                                            textAlign =
                                                if (p.centerCourseText) TextAlign.Center
                                                else TextAlign.Start,
                                        )
                                    if (p.showTeacher && block.teacher.isNotBlank())
                                        Text(
                                            block.teacher,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign =
                                                if (p.centerCourseText) TextAlign.Center
                                                else TextAlign.Start,
                                            color = ink.detail,
                                            style = detailStyle,
                                            fontSize = (10 * p.courseFontScale).sp,
                                            lineHeight = (13 * p.courseFontScale).sp,
                                        )
                                    if (p.showLocation && block.location.isNotBlank())
                                        Text(
                                            block.location,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign =
                                                if (p.centerCourseText) TextAlign.Center
                                                else TextAlign.Start,
                                            color = ink.detail,
                                            style = detailStyle,
                                            fontSize = (10 * p.courseFontScale).sp,
                                            lineHeight = (13 * p.courseFontScale).sp,
                                            fontWeight = FontWeight.SemiBold,
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
