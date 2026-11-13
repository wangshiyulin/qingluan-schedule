package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.time.LocalDate

@Composable
fun Management(
    s: Snapshot,
    vm: AppViewModel,
    onCourse: (Course) -> Unit,
    onTerm: (Term?) -> Unit,
    onTimetable: (Timetable?) -> Unit,
    page: String,
) {
    var query by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val term = s.terms.find { it.id == s.activeTermId }
    val courses =
        remember(s.courses, s.activeTermId, query, page) {
            if (page != "课程管理") emptyList()
            else
                s.courses
                    .filter {
                        it.termId == s.activeTermId &&
                            (query.isBlank() ||
                                "${it.name} ${it.teacher} ${it.location}".contains(query, true))
                    }
                    .sortedBy { it.name }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (page in listOf("课表列表", "课表管理")) {
            if (s.terms.isNotEmpty())
                item {
                    SettingsGroup {
                        Choice(
                            "当前课表",
                            s.activeTermId,
                            s.terms.map { it.id to it.name },
                            { vm.activate(it) },
                            Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            itemsIndexed(s.terms, key = { _, t -> "term-${t.id}" }) { index, t ->
                Card(
                    shape = settingsSegmentShape(index, s.terms.size),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${t.startDate} 开学 · ${t.weeks} 周",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onTerm(t) }) {
                            Icon(Icons.Outlined.Edit, "编辑课表 ${t.name}")
                        }
                        IconButton(
                            onClick = {
                                deleting = "删除「${t.name}」及其全部课程和调整？" to { vm.deleteTerm(t.id) }
                            }
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, "删除课表 ${t.name}")
                        }
                    }
                }
            }
        }
        if (page == "作息管理")
            itemsIndexed(s.timetables, key = { _, t -> "time-${t.id}" }) { index, t ->
                SettingsSegment(index, s.timetables.size) {
                    InfoCard(
                        t.name,
                        "${t.periods.size} 节 · ${clock(t.periods.first().start)}–${clock(t.periods.last().end)}",
                        { Icon(Icons.Outlined.Schedule, null) },
                        { onTimetable(t) },
                        trailing = {
                            IconButton(
                                onClick = {
                                    deleting = "删除「${t.name}」？" to { vm.deleteTimetable(t.id) }
                                }
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, "删除作息 ${t.name}")
                            }
                        },
                    )
                }
            }
        if (page == "课程管理") {
            if (term == null) item { EmptyState("暂无课表") }
            else {
                item {
                    OutlinedTextField(
                        query,
                        { query = it },
                        label = { Text("搜索课程、教师、地点") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                itemsIndexed(courses, key = { _, c -> "course-${c.id}" }) { index, c ->
                    SettingsSegment(index, courses.size) {
                        InfoCard(
                            c.name,
                            "${c.teacher} · ${c.location.ifBlank{"未设置地点"}}",
                            { Icon(Icons.Outlined.MenuBook, null) },
                            { onCourse(c) },
                            trailing = {
                                IconButton(
                                    onClick = {
                                        deleting =
                                            "删除「${c.name}」及其所有临时调整？" to { vm.deleteCourse(c.id) }
                                    }
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, "删除课程 ${c.name}")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    deleting?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("请确认") },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        action()
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
fun TermEditor(
    s: Snapshot,
    term: Term?,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (Term) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(term?.name ?: "${LocalDate.now().year} 年新学期") }
    var start by rememberDate { term?.startDate?.let(LocalDate::parse) ?: LocalDate.now() }
    var weeks by rememberSaveable { mutableStateOf((term?.weeks ?: 20).toString()) }
    var timetableId by rememberSaveable {
        mutableStateOf(term?.timetableId ?: s.timetables.first().id)
    }
    val id = rememberSaveable { term?.id ?: newId() }
    var error by remember { mutableStateOf<String?>(null) }
    FormScreen(
        if (term == null) "新建课表" else "编辑课表",
        onClose,
        {
            if (!busy)
                try {
                    val t =
                        Term(
                            id,
                            name.trim(),
                            start.toString(),
                            weeks.toIntOrNull() ?: 0,
                            timetableId,
                            term?.firstDayOfWeek ?: 1,
                        )
                    val next =
                        s.copy(
                            terms = s.terms.filterNot { it.id == id } + t,
                            activeTermId = s.activeTermId.ifBlank { id },
                        )
                    BackupCodec.validate(Backup(data = next))
                    onSave(t)
                } catch (e: Exception) {
                    error = e.message
                }
        },
        if (busy) "保存中…" else "保存",
    ) {
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("课表名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DateField("开学日期", start, { start = it }, Modifier.fillMaxWidth())
        OutlinedTextField(
            weeks,
            { weeks = it },
            label = { Text("总周数（1–60）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Choice(
            "绑定作息",
            timetableId,
            s.timetables.map { it.id to it.name },
            { timetableId = it },
            Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun TimetableEditor(
    s: Snapshot,
    timetable: Timetable?,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (Timetable) -> Unit,
) {
    val id = rememberSaveable { timetable?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(timetable?.name ?: "新作息表") }
    var periods by rememberJsonState { timetable?.periods ?: defaultPeriods() }
    var batch by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FormScreen(
        if (timetable == null) "新建作息表" else "编辑作息表",
        onClose,
        {
            if (!busy)
                try {
                    val t = Timetable(id, name.trim(), periods)
                    BackupCodec.validate(
                        Backup(
                            data = s.copy(timetables = s.timetables.filterNot { it.id == id } + t)
                        )
                    )
                    onSave(t)
                } catch (e: Exception) {
                    error = e.message
                }
        },
        if (busy) "保存中…" else "保存",
    ) {
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("作息名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ValueRow("批量设置时间", "${periods.size} 节", { batch = true })
        periods.forEachIndexed { i, p ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${i+1} 节", Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (periods.size > 1)
                                    periods = periods.filterIndexed { index, _ -> index != i }
                            }
                        ) {
                            Icon(Icons.Outlined.RemoveCircleOutline, "删除第 ${i+1} 节")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(
                            "开始",
                            p.start,
                            { v ->
                                periods = periods.mapIndexed { index, item ->
                                    if (index == i) item.copy(start = v) else item
                                }
                            },
                            Modifier.weight(1f),
                        )
                        TimeField(
                            "结束",
                            p.end,
                            { v ->
                                periods = periods.mapIndexed { index, item ->
                                    if (index == i) item.copy(end = v) else item
                                }
                            },
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                if (periods.size < 24) {
                    val start = periods.last().end + 10
                    if (start + 45 <= 1439) periods = periods + Period(start, start + 45)
                    else error = "当天已无足够时间，请先调整前面的节次"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, null)
            Text("添加节次")
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
    if (batch)
        PeriodPlanDialog(periods, { batch = false }) { planned ->
            periods = planned
            error = null
            batch = false
        }
}
