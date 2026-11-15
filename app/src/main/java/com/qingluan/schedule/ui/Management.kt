package com.qingluan.schedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
    var deleting by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val term = s.terms.find { it.id == s.activeTermId }
    val courses =
        remember(s.courses, s.activeTermId, page) {
            if (page != "课程管理") emptyList()
            else s.courses.filter { it.termId == s.activeTermId }.sortedBy { it.name }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (page in listOf("课表列表", "课表管理")) {
            itemsIndexed(s.terms, key = { _, t -> "term-${t.id}" }) { index, t ->
                val active = t.id == s.activeTermId
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag("term-card-${t.id}")
                            .semantics { selected = active }
                            .clickable { vm.activate(t.id) },
                    border =
                        if (active)
                            BorderStroke(
                                .7.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = .45f),
                            )
                        else null,
                    shape = settingsSegmentShape(index, s.terms.size),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
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
                val active = t.id == term?.timetableId
                CompositionLocalProvider(LocalSettingsSelected provides active) {
                    Box(Modifier.testTag("timetable-${t.id}").semantics { selected = active }) {
                        SettingsSegment(index, s.timetables.size) {
                            InfoCard(
                                t.name,
                                (if (active) "当前使用 · " else "") +
                                    "${t.periods.size} 节 · ${clock(t.periods.first().start)}–${clock(t.periods.last().end)}",
                                { Icon(Icons.Outlined.Schedule, null) },
                                { onTimetable(t) },
                                trailing = {
                                    IconButton(
                                        onClick = {
                                            deleting =
                                                "删除「${t.name}」？" to { vm.deleteTimetable(t.id) }
                                        },
                                        enabled = s.terms.none { it.timetableId == t.id },
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, "删除作息 ${t.name}")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        if (page == "课程管理") {
            if (term == null) item { EmptyState("暂无课表") }
            else {
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
    var firstDay by rememberSaveable { mutableIntStateOf(term?.firstDayOfWeek ?: 1) }
    var weekPicker by rememberSaveable { mutableStateOf(false) }
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
                            firstDay,
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
        SettingsGroup {
            val current =
                (java.time.temporal.ChronoUnit.DAYS.between(
                        ScheduleEngine.weekStart(start, firstDay),
                        ScheduleEngine.weekStart(LocalDate.now(), firstDay),
                    ) / 7 + 1)
                    .toInt()
            ValueRow("当前周", if (current < 1) "未开学" else "第 $current 周", { weekPicker = true })
            Choice(
                "总周数",
                weeks.toIntOrNull() ?: 20,
                (1..60).map { it to "$it 周" },
                { weeks = it.toString() },
                Modifier.fillMaxWidth(),
            )
            Choice(
                "每周起始日",
                firstDay,
                weekdayNames.mapIndexed { i, n -> i + 1 to n },
                { firstDay = it },
                Modifier.fillMaxWidth(),
            )
        }
        Choice(
            "绑定作息",
            timetableId,
            s.timetables.map { it.id to it.name },
            { timetableId = it },
            Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
    if (weekPicker)
        SelectionDialog(
            "当前周",
            (java.time.temporal.ChronoUnit.DAYS.between(
                    ScheduleEngine.weekStart(start, firstDay),
                    ScheduleEngine.weekStart(LocalDate.now(), firstDay),
                ) / 7 + 1)
                .toInt()
                .coerceIn(1, weeks.toIntOrNull() ?: 20),
            (1..(weeks.toIntOrNull() ?: 20)).map { it to "第 $it 周" },
            { weekPicker = false },
        ) { chosen ->
            start =
                ScheduleEngine.weekStart(LocalDate.now(), firstDay)
                    .minusWeeks((chosen - 1).toLong())
            weekPicker = false
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
    var editingPeriod by rememberSaveable { mutableIntStateOf(-1) }
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("节次", Modifier.weight(1f))
            Text("开始", Modifier.width(76.dp))
            Text("结束", Modifier.width(76.dp))
            Spacer(Modifier.width(40.dp))
        }
        SettingsGroup {
            periods.forEachIndexed { i, period ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { editingPeriod = i }
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("第 ${i+1} 节", Modifier.weight(1f))
                    Text(clock(period.start), Modifier.width(76.dp))
                    Text(clock(period.end), Modifier.width(76.dp))
                    IconButton(
                        onClick = {
                            if (periods.size > 1)
                                periods = periods.filterIndexed { index, _ -> index != i }
                        },
                        enabled = periods.size > 1,
                    ) {
                        Icon(Icons.Outlined.RemoveCircleOutline, "删除第 ${i+1} 节")
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
    if (editingPeriod in periods.indices) {
        var draft by rememberJsonState { periods[editingPeriod] }
        var timeError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editingPeriod = -1 },
            title = { Text("第 ${editingPeriod+1} 节") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField(
                        "开始",
                        draft.start,
                        { draft = draft.copy(start = it) },
                        Modifier.fillMaxWidth(),
                    )
                    TimeField(
                        "结束",
                        draft.end,
                        { draft = draft.copy(end = it) },
                        Modifier.fillMaxWidth(),
                    )
                    timeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (
                            draft.end <= draft.start ||
                                (editingPeriod > 0 &&
                                    draft.start < periods[editingPeriod - 1].end) ||
                                (editingPeriod < periods.lastIndex &&
                                    draft.end > periods[editingPeriod + 1].start)
                        ) {
                            timeError = "请检查起止时间，节次之间不能重叠"
                        } else {
                            periods = periods.mapIndexed { i, old ->
                                if (i == editingPeriod) draft else old
                            }
                            editingPeriod = -1
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = { TextButton(onClick = { editingPeriod = -1 }) { Text("取消") } },
        )
    }
    if (batch)
        PeriodPlanDialog(periods, { batch = false }) { planned ->
            periods = planned
            error = null
            batch = false
        }
}
