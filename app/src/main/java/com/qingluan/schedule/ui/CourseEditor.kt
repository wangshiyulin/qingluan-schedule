@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qingluan.schedule.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*

@Composable
fun CourseEditor(
    s: Snapshot,
    term: Term,
    course: Course?,
    initialDay: Int = 1,
    initialPeriod: Int = 1,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(course?.name.orEmpty()) }
    var teacher by rememberSaveable { mutableStateOf(course?.teacher?.trim().orEmpty()) }
    var location by rememberSaveable { mutableStateOf(course?.location?.trim().orEmpty()) }
    var note by rememberSaveable { mutableStateOf(course?.note.orEmpty()) }
    var automaticColor by rememberSaveable {
        mutableStateOf(
            course?.let { it.automaticColor && it.color == DEFAULT_COURSE_COLOR } ?: true
        )
    }
    var color by rememberSaveable { mutableLongStateOf(course?.color ?: DEFAULT_COURSE_COLOR) }
    var meetings by rememberJsonState {
        course
            ?.coalesced(
                s.exceptions.filter { it.courseId == course.id }.map { it.meetingId }.toSet()
            )
            ?.meetings
            ?: listOf(
                Meeting(
                    weekday = initialDay,
                    startPeriod = initialPeriod,
                    endPeriod = initialPeriod,
                    weeks = (1..term.weeks).toSet(),
                )
            )
    }
    val id = rememberSaveable { course?.id ?: newId() }
    val periods = s.timetables.first { it.id == term.timetableId }.periods
    var error by remember { mutableStateOf<String?>(null) }
    var conflicts by remember { mutableStateOf<Pair<Course, String>?>(null) }
    fun save() {
        if (busy) return
        try {
            val c =
                Course(
                    id,
                    term.id,
                    name.trim(),
                    teacher.trim(),
                    location.trim(),
                    note.trim(),
                    color,
                    meetings.map { meeting ->
                        meeting.copy(
                            teacher =
                                if (teacher.trim() != course?.teacher?.trim().orEmpty()) ""
                                else meeting.teacher,
                            location =
                                if (location.trim() != course?.location?.trim().orEmpty()) ""
                                else meeting.location,
                        )
                    },
                    automaticColor,
                )
            val next = s.withCourse(c)
            BackupCodec.validate(Backup(data = next))
            val clash =
                ScheduleEngine.conflicts(next, term.id, setOf(id))
            if (clash.isNotEmpty()) {
                val first = clash.first()
                conflicts =
                    c to
                        "发现 ${clash.size} 处时间重叠，最早在 ${first.date}：${first.first.course.name} 与 ${first.second.course.name}。保存后将并排显示，请确认安排。"
            } else onSave(c)
        } catch (e: Exception) {
            error = e.message
        }
    }
    FormScreen(
        if (course == null) "添加课程" else "编辑课程",
        onClose,
        ::save,
        if (busy) "保存中…" else "保存",
    ) {
        Text(
            term.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("课程名称 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                teacher,
                { teacher = it },
                label = { Text("教师") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                location,
                { location = it },
                label = { Text("地点") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        SectionTitle("课程颜色")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                onClick = {
                    automaticColor = true
                    color = DEFAULT_COURSE_COLOR
                },
                modifier = Modifier.semantics { selected = automaticColor },
            ) {
                Text(if (automaticColor && color == DEFAULT_COURSE_COLOR) "自动 ✓" else "自动")
            }
            courseColors.forEachIndexed { i, c ->
                Box(
                    Modifier.size(42.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .clickable {
                            color = c
                            automaticColor = false
                        }
                        .semantics {
                            contentDescription = "课程颜色 ${i+1}"
                            selected = !automaticColor && color == c
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!automaticColor && color == c)
                        Icon(Icons.Outlined.Check, null, tint = readableText(Color(c)))
                }
            }
        }
        SectionTitle("上课安排")
        meetings.forEachIndexed { index, m ->
            key(m.id) {
                var weeksText by
                    rememberSaveable(m.id) { mutableStateOf(m.weeks.sorted().joinToString(",")) }
                fun update(n: Meeting) {
                    meetings = meetings.map { if (it.id == m.id) n else it }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "时段 ${index+1}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (meetings.size > 1)
                                IconButton(
                                    onClick = { meetings = meetings.filterNot { it.id == m.id } }
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, "删除此时段")
                                }
                        }
                        Choice(
                            "星期",
                            m.weekday,
                            (1..7).map { it to weekdayNames[it - 1] },
                            { update(m.copy(weekday = it)) },
                            Modifier.fillMaxWidth(),
                        )
                        PeriodRangeFields(m.startPeriod, m.endPeriod, periods.size) { start, end ->
                            update(m.copy(startPeriod = start, endPeriod = end))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("全部", "单周", "双周").forEachIndexed { i, label ->
                                OutlinedButton(
                                    onClick = {
                                        val w =
                                            (1..term.weeks)
                                                .filter { i == 0 || it % 2 == if (i == 1) 1 else 0 }
                                                .toSet()
                                        weeksText = w.joinToString(",")
                                        update(m.copy(weeks = w))
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(4.dp),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                        OutlinedTextField(
                            weeksText,
                            { text ->
                                weeksText = text
                                runCatching { parseWeeks(text, term.weeks) }
                                    .onSuccess { update(m.copy(weeks = it)) }
                                    .onFailure { update(m.copy(weeks = emptySet())) }
                            },
                            label = { Text("上课周次 *") },
                            placeholder = { Text("1-8,10,12-16") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                if (meetings.size < 30)
                    meetings = meetings + Meeting(weeks = (1..term.weeks).toSet())
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, null)
            Text("添加上课时段")
        }
        OutlinedTextField(
            note,
            { note = it },
            label = { Text("备注") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onDelete != null)
            OutlinedButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) {
                Text("删除课程")
            }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除这门课程？") },
            confirmButton = {
                Button(
                    onClick = {
                        deleting = false
                        onDelete?.invoke()
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
        )
    conflicts?.let { (c, text) ->
        AlertDialog(
            onDismissRequest = { conflicts = null },
            title = { Text("课程时间重叠") },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        conflicts = null
                        onSave(c)
                    }
                ) {
                    Text("仍然保存")
                }
            },
            dismissButton = { TextButton(onClick = { conflicts = null }) { Text("返回修改") } },
        )
    }
}
