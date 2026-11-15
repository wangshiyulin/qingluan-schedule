package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.time.*

@Composable
fun TemporaryAdjustmentScreen(
    s: Snapshot,
    vm: AppViewModel,
    initialCourseId: String,
    initialKey: String,
    onOpenTable: () -> Unit,
) {
    val term = s.terms.firstOrNull { it.id == s.activeTermId }
    val courses = s.courses.filter { it.termId == s.activeTermId }.sortedBy { it.name }
    var courseId by rememberSaveable {
        mutableStateOf(
            courses.firstOrNull { it.id == initialCourseId }?.id
                ?: courses.firstOrNull()?.id.orEmpty()
        )
    }
    val course = courses.firstOrNull { it.id == courseId }
    val occurrences =
        remember(s, courseId) {
            if (term == null || course == null) emptyList()
            else {
                val only =
                    s.copy(
                        courses = listOf(course),
                        exceptions = s.exceptions.filter { it.courseId == courseId },
                    )
                generateSequence(LocalDate.parse(term.startDate)) { it.plusDays(1) }
                    .takeWhile { it <= ScheduleEngine.endDate(term) }
                    .flatMap { date ->
                        val actual = ScheduleEngine.onDate(only, term.id, date)
                        val canceled =
                            ScheduleEngine.onDate(
                                    only.copy(exceptions = emptyList()),
                                    term.id,
                                    date,
                                )
                                .mapNotNull { base ->
                                    only.exceptions
                                        .firstOrNull {
                                            it.originalDate == date.toString() &&
                                                it.targetDate.isBlank() &&
                                                it.meetingId == base.meeting.id
                                        }
                                        ?.let { base.copy(exception = it) }
                                }
                        actual + canceled
                    }
                    .toList()
            }
        }
    val fallback =
        occurrences.firstOrNull { it.date >= LocalDate.now() } ?: occurrences.lastOrNull()
    var originalKey by
        rememberSaveable(courseId) {
            mutableStateOf(
                occurrences.firstOrNull { it.key == initialKey }?.key ?: fallback?.key.orEmpty()
            )
        }
    val occurrence = occurrences.firstOrNull { it.key == originalKey } ?: fallback
    var operation by rememberSaveable { mutableStateOf("move") }
    var target by rememberDate { occurrence?.date ?: LocalDate.now() }
    var start by rememberSaveable { mutableIntStateOf(1) }
    var end by rememberSaveable { mutableIntStateOf(2) }
    var room by rememberSaveable { mutableStateOf("") }
    var teacher by rememberSaveable { mutableStateOf("") }
    var candidate by rememberJsonState<CourseException?> { null }
    var error by remember { mutableStateOf<String?>(null) }
    val count = term?.let { t -> s.timetables.first { it.id == t.timetableId }.periods.size } ?: 1
    LaunchedEffect(occurrence?.key) {
        occurrence?.let {
            target = it.date
            start = it.startPeriod
            end = it.endPeriod
            room = it.location
            teacher = it.teacher
        }
    }
    fun applyChange(confirmOverlap: Boolean = false) {
        if (vm.busy.value) return
        val o = occurrence ?: return
        if (operation == "cancel" && o.exception != null && o.exception.originalDate.isBlank()) {
            vm.removeException(o.exception.id)
            return
        }
        val originalDate =
            if (operation == "add") "" else o.exception?.originalDate ?: o.date.toString()
        val teachingDate =
            if (originalDate.isBlank()) ""
            else
                o.exception?.originalTeachingDate?.takeIf(String::isNotBlank)
                    ?: if (o.exception != null) originalDate
                    else ScheduleEngine.teachingDate(s, term!!.id, o.date)?.toString().orEmpty()
        val x =
            CourseException(
                id = if (operation == "add") newId() else o.exception?.id ?: newId(),
                courseId = o.course.id,
                meetingId = o.meeting.id,
                originalDate = originalDate,
                targetDate = if (operation == "cancel") "" else target.toString(),
                startPeriod = start,
                endPeriod = end,
                location = room.trim(),
                teacher = teacher.trim(),
                originalTeachingDate = teachingDate,
            )
        try {
            val next = s.withException(x)
            BackupCodec.validate(Backup(data = next))
            val overlap =
                operation != "cancel" &&
                    ScheduleEngine.onDate(next, s.activeTermId, target).any {
                        it.exception?.id != x.id && it.startPeriod <= end && it.endPeriod >= start
                    }
            if (overlap && !confirmOverlap) candidate = x
            else vm.saveException(x) { candidate = null }
        } catch (e: Exception) {
            error = e.message
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("temporary-adjustment"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (course == null || term == null) item { EmptyState("暂无课程") }
        else {
            item {
                SettingsGroup {
                    Choice(
                        "课程",
                        courseId,
                        courses.map { it.id to it.name },
                        { courseId = it },
                        Modifier.fillMaxWidth(),
                    )
                    if (occurrence != null) {
                        Choice(
                            "原课次",
                            occurrence.key,
                            occurrences.map {
                                it.key to
                                    "${it.date} · ${periodLabel(it.startPeriod, it.endPeriod)} · ${clock(it.startMinute)}"
                            },
                            { originalKey = it },
                            Modifier.fillMaxWidth(),
                        )
                        Choice(
                            "调整方式",
                            operation,
                            listOf("move" to "调课", "cancel" to "停课", "add" to "补课"),
                            { operation = it },
                            Modifier.fillMaxWidth(),
                        )
                        if (operation != "cancel")
                            DateField("目标日期", target, { target = it }, Modifier.fillMaxWidth())
                    }
                }
            }
            if (occurrence != null) {
                if (operation != "cancel") {
                    item {
                        SettingsGroup {
                            PeriodRangeFields(start, end, count) { first, last ->
                                start = first
                                end = last
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            room,
                            { room = it },
                            label = { Text("地点") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            teacher,
                            { teacher = it },
                            label = { Text("教师") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Button(onClick = { applyChange() }, modifier = Modifier.fillMaxWidth()) {
                        Text("应用到课表")
                    }
                }
            } else item { Text("当前课程没有有效课次") }
        }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        val changes =
            s.exceptions
                .filter { x -> courses.any { it.id == x.courseId } }
                .sortedBy { it.targetDate.ifBlank { it.originalDate } }
        if (changes.isNotEmpty()) item { SettingsHeading("已生效") }
        items(changes.size) { i ->
            val x = changes[i]
            val name = courses.first { it.id == x.courseId }.name
            SettingsSegment(i, changes.size) {
                InfoCard(
                    name,
                    if (x.targetDate.isBlank()) "停课 · ${x.originalDate}"
                    else "${x.originalDate.ifBlank{"补课"}} → ${x.targetDate}",
                    { Icon(Icons.Outlined.EventRepeat, null) },
                    onOpenTable,
                    trailing = {
                        IconButton(onClick = { vm.removeException(x.id) }) {
                            Icon(Icons.Outlined.Undo, "撤销 $name 的临时调整")
                        }
                    },
                )
            }
        }
    }
    candidate?.let { x ->
        AlertDialog(
            onDismissRequest = { candidate = null },
            title = { Text("课程时间重叠") },
            text = { Text("是否仍然应用这次调整？") },
            confirmButton = {
                Button(onClick = { vm.saveException(x) { candidate = null } }) { Text("应用") }
            },
            dismissButton = { TextButton(onClick = { candidate = null }) { Text("取消") } },
        )
    }
}
