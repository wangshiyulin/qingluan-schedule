package com.qingluan.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.data.AppSettings
import com.qingluan.schedule.data.resetScheduleAppearance
import com.qingluan.schedule.model.*
import com.qingluan.schedule.model.Period
import java.time.LocalDateTime

@Composable
fun ScheduleAppearance(p: AppSettings, vm: AppViewModel) {
    val sample = remember { appearanceSample() }
    val pager = rememberPagerState(pageCount = { 1 })
    var fraction by rememberSaveable { mutableFloatStateOf(.38f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val totalPixels = with(LocalDensity.current) { maxHeight.toPx() }
        val previewHeight = maxHeight * fraction
        val drag = rememberDraggableState { delta ->
            if (totalPixels > 0f) fraction = (fraction + delta / totalPixels).coerceIn(.18f, .72f)
        }
        Column(Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxWidth().height(previewHeight).testTag("appearance-preview")) {
                WeekScreen(
                    sample,
                    p.copy(showNonCurrentWeek = false),
                    LocalDateTime.of(2026, 11, 9, 9, 0),
                    pager,
                    {},
                    { _, _ -> },
                    {},
                    preview = true,
                )
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(24.dp)
                    .testTag("preview-divider")
                    .draggable(drag, Orientation.Vertical)
                    .semantics {
                        contentDescription = "调整课表预览高度"
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0.18f..0.72f)
                        setProgress { value ->
                            fraction = value.coerceIn(.18f, .72f)
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f)
                )
                Box(
                    Modifier.size(36.dp, 4.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(2.dp),
                        )
                )
            }
            LazyColumn(
                Modifier.weight(1f).testTag("schedule-appearance"),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item {
                    SettingsSegment(0, 10) {
                        Choice(
                            "节次高度",
                            p.rowHeight,
                            (listOf(48, 56, 64, 72, 80, 96, 110, 128, 144, 180) + p.rowHeight)
                                .distinct()
                                .sorted()
                                .map { it to "$it" },
                            { v -> vm.updateSettings { it.copy(rowHeight = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(1, 10) {
                        Choice(
                            "课程字号",
                            p.courseFontScale,
                            listOf(.75f, .85f, 1f, 1.1f, 1.25f, 1.5f).map {
                                it to "${(it*100).toInt()}%"
                            },
                            { v -> vm.updateSettings { it.copy(courseFontScale = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(2, 10) {
                        Choice(
                            "时间栏宽度",
                            p.timeColumnWidth,
                            (listOf(32, 36, 40, 44, 48, 56, 64, 72) + p.timeColumnWidth)
                                .distinct()
                                .sorted()
                                .map { it to "$it" },
                            { v -> vm.updateSettings { it.copy(timeColumnWidth = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(3, 10) {
                        Choice(
                            "日期栏高度",
                            p.dayHeaderHeight,
                            (listOf(36, 40, 44, 50, 56, 64, 72, 80) + p.dayHeaderHeight)
                                .distinct()
                                .sorted()
                                .map { it to "$it" },
                            { v -> vm.updateSettings { it.copy(dayHeaderHeight = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(4, 10) {
                        Choice(
                            "课程外间距",
                            p.courseOuterPadding,
                            (0..6).map { it to "$it" },
                            { v -> vm.updateSettings { it.copy(courseOuterPadding = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(5, 10) {
                        Choice(
                            "课程内间距",
                            p.courseInnerPadding,
                            (0..12).map { it to "$it" },
                            { v -> vm.updateSettings { it.copy(courseInnerPadding = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(6, 10) {
                        Choice(
                            "课程圆角",
                            p.courseCornerRadius,
                            listOf(0, 4, 8, 10, 12, 16, 20, 24).map { it to "$it" },
                            { v -> vm.updateSettings { it.copy(courseCornerRadius = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(7, 10) {
                        Choice(
                            "课程透明度",
                            p.courseOpacity,
                            listOf(.3f, .5f, .7f, .85f, 1f).map { it to "${(it*100).toInt()}%" },
                            { v -> vm.updateSettings { it.copy(courseOpacity = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(8, 10) {
                        Choice(
                            "横向对齐",
                            p.centerCourseText,
                            listOf(false to "左对齐", true to "居中"),
                            { v -> vm.updateSettings { it.copy(centerCourseText = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(9, 10) {
                        Choice(
                            "纵向对齐",
                            p.centerCourseVertically,
                            listOf(false to "顶部", true to "居中"),
                            { v -> vm.updateSettings { it.copy(centerCourseVertically = v) } },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    SettingsSegment(0, 8) {
                        SettingToggle("课程名称加粗", "", p.boldCourseNames) { v ->
                            vm.updateSettings { it.copy(boldCourseNames = v) }
                        }
                    }
                }
                item {
                    SettingsSegment(1, 8) {
                        SettingToggle("显示课程边框", "", p.showCourseBorders) { v ->
                            vm.updateSettings { it.copy(showCourseBorders = v) }
                        }
                    }
                }
                item {
                    SettingsSegment(2, 8) {
                        SettingToggle("显示课程上课时间", "", p.showCourseStartTime) { v ->
                            vm.updateSettings { it.copy(showCourseStartTime = v) }
                        }
                    }
                }
                item {
                    SettingsSegment(3, 8) {
                        SettingToggle("显示网格线", "", p.showGridLines) { v ->
                            vm.updateSettings { it.copy(showGridLines = v) }
                        }
                    }
                }
                item {
                    SettingsSegment(4, 8) {
                        SettingToggle("显示节次时间", "", p.showSectionTime) { v ->
                            vm.updateSettings { it.copy(showSectionTime = v) }
                        }
                    }
                }
                item {
                    SettingsSegment(5, 8) {
                        SettingToggle("显示日期", "", p.showDates) { v ->
                            vm.updateSettings { it.copy(showDates = v) }
                        }
                    }
                }
                item {
                    SettingsSegment(6, 8) {
                        SettingToggle("显示教师", "", p.showTeacher) { v ->
                            vm.updateSettings { it.copy(showTeacher = v) }
                        }
                    }
                }
                item {
                    SettingsSegment(7, 8) {
                        SettingToggle("显示教室", "", p.showLocation) { v ->
                            vm.updateSettings { it.copy(showLocation = v) }
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = {
                            vm.updateSettings { it.resetScheduleAppearance() }
                        }
                    ) {
                        Text("恢复课表默认样式")
                    }
                }
            }
        }
    }
}

private fun appearanceSample(): Snapshot {
    val table =
        Timetable(
            id = "preview-time",
            periods =
                listOf(Period(480, 525), Period(535, 580), Period(600, 645), Period(655, 700)),
        )
    val term = Term(id = "preview", startDate = "2026-11-09", weeks = 1, timetableId = table.id)
    val names = listOf("高等数学", "大学英语", "计算机基础", "体育", "电子技术")
    val courses = names.mapIndexed { i, name ->
        Course(
            id = "preview-$i",
            termId = term.id,
            name = name,
            teacher = listOf("李老师", "王老师")[i % 2],
            location = "教学楼 ${201+i}",
            meetings =
                listOf(
                    Meeting(
                        weekday = i + 1,
                        startPeriod = if (i % 2 == 0) 1 else 3,
                        endPeriod = if (i % 2 == 0) 2 else 4,
                        weeks = setOf(1),
                    )
                ),
        )
    }
    return Snapshot(
        activeTermId = term.id,
        terms = listOf(term),
        timetables = listOf(table),
        courses = courses,
    )
}
