package com.qingluan.schedule.ui

import android.app.Activity
import android.content.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.model.*
import com.qingluan.schedule.transfer.*
import java.time.LocalDate

private data class SaveFile(val name: String, val mime: String)

private class CreateExchangeFile : ActivityResultContract<SaveFile, Uri?>() {
    override fun createIntent(context: Context, input: SaveFile) =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mime)
            .putExtra(Intent.EXTRA_TITLE, input.name)

    override fun parseResult(resultCode: Int, intent: Intent?) =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
fun TransferScreen(s: Snapshot, vm: AppViewModel) {
    val context = LocalContext.current
    var tableId by rememberSaveable { mutableStateOf(s.activeTermId) }
    if (s.terms.none { it.id == tableId }) tableId = s.activeTermId
    val document =
        remember(s, tableId) {
            s.terms.find { it.id == tableId }?.let { TimetableDocument.from(s, it.id) }
        }
    var week by
        rememberSaveable(tableId) {
            mutableIntStateOf(
                document?.term?.let {
                    ScheduleEngine.week(it, LocalDate.now()).coerceIn(1, it.weeks)
                } ?: 1
            )
        }
    var format by rememberSaveable { mutableStateOf(ExchangeFormat.JSON.name) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var fullBackup by rememberSaveable { mutableStateOf(false) }
    var pendingKind by rememberSaveable { mutableStateOf("table") }
    var pendingFormat by rememberSaveable { mutableStateOf(ExchangeFormat.JSON.name) }
    val save =
        rememberLauncherForActivityResult(CreateExchangeFile()) { uri ->
            if (uri != null) {
                if (pendingKind == "table" && document != null)
                    vm.writeDocument(uri, document, ExchangeFormat.valueOf(pendingFormat), week)
                else vm.writeFullBackup(uri, pendingKind == "zip")
            }
        }
    fun finishExport(selected: ExchangeFormat, share: Boolean) {
        val doc = document ?: return
        if (share)
            vm.shareDocument(doc, selected, week) { uri ->
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType(selected.mime)
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "分享课表",
                    )
                )
            }
        else {
            pendingKind = "table"
            pendingFormat = selected.name
            save.launch(
                SaveFile("${doc.term.name.replace('/','_')}.${selected.extension}", selected.mime)
            )
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("transfer-page"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (document != null)
            item {
                SettingsGroup {
                    Choice(
                        "选择课表",
                        tableId,
                        s.terms.map { it.id to it.name },
                        { tableId = it },
                        Modifier.fillMaxWidth(),
                    )
                    Choice(
                        "图片 / PDF 周次",
                        week,
                        (1..document.term.weeks).map { it to "第 $it 周" },
                        { week = it },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        if (document != null) {
            item { SettingsHeading("导出课程表") }
            item {
                SettingsGroup {
                    InfoCard(
                        "导出单张课表",
                        "",
                        { Icon(Icons.Outlined.SaveAlt, null) },
                        { exporting = true },
                    )
                    InfoCard("分享课表", "", { Icon(Icons.Outlined.Code, null) }, { sharing = true })
                }
            }
            item {
                Text(
                    "CSV / Excel / HTML 保留周期课程；完整临时调整请用 JSON / ICS。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else item { EmptyState("暂无课表") }
        item { SettingsHeading("全部数据") }
        item {
            SettingsGroup {
                InfoCard(
                    "导出完整备份",
                    "全部课表和设置",
                    { Icon(Icons.Outlined.SaveAlt, null) },
                    { fullBackup = true },
                )
            }
        }
    }
    if (exporting || sharing)
        SelectionDialog(
            if (sharing) "分享格式" else "导出格式",
            format,
            ExchangeFormat.entries.map { it.name to it.title },
            {
                exporting = false
                sharing = false
            },
        ) { v ->
            val share = sharing
            format = v
            exporting = false
            sharing = false
            finishExport(ExchangeFormat.valueOf(v), share)
        }
    if (fullBackup)
        SelectionDialog(
            "完整备份格式",
            "json",
            listOf("json" to "JSON", "zip" to "ZIP 压缩备份"),
            { fullBackup = false },
        ) { kind ->
            fullBackup = false
            pendingKind = kind
            save.launch(
                SaveFile(
                    "青鸾课程表-完整备份-${LocalDate.now()}.$kind",
                    if (kind == "zip") "application/zip" else "application/json",
                )
            )
        }
}

@Composable
fun ImportPreview(
    result: ImportResult,
    s: Snapshot,
    vm: AppViewModel,
    busy: Boolean,
    onComplete: () -> Unit,
) {
    key(result.previewKey) { ImportPreviewContent(result, s, vm, busy, onComplete) }
}

@Composable
private fun ImportPreviewContent(
    result: ImportResult,
    s: Snapshot,
    vm: AppViewModel,
    busy: Boolean,
    onComplete: () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var document by rememberJsonState { result.documents.first() }
    var included by rememberJsonState { document.courses.map { it.id }.toSet() }
    var mode by rememberSaveable { mutableStateOf("new") }
    var target by rememberSaveable { mutableStateOf(s.activeTermId) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var time by rememberSaveable { mutableStateOf(false) }
    var editing by rememberJsonState<Course?> { null }
    var fieldDialog by rememberSaveable { mutableStateOf(false) }
    var dateConfirmed by rememberSaveable { mutableStateOf(result.assessment?.dateUnknown != true) }
    var timeConfirmed by rememberSaveable { mutableStateOf(result.assessment?.timeUnknown != true) }
    var timeChoice by rememberSaveable { mutableStateOf("") }
    var timePrompt by rememberSaveable { mutableStateOf(false) }
    val maxPeriod = document.courses.maxOf { c -> c.meetings.maxOf { it.endPeriod } }
    val compatibleTimes = s.timetables.filter { it.periods.size >= maxPeriod }
    fun useTime(id: String) {
        if (id == "manual") {
            time = true
            return
        }
        val chosen = compatibleTimes.firstOrNull { it.id == id } ?: return
        document = document.copy(timetable = chosen.copy(id = document.timetable.id))
        timeChoice = id
        timeConfirmed = true
    }
    var datePrompt by rememberSaveable { mutableStateOf(false) }
    var qualityPrompt by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val week = ScheduleEngine.week(document.term, today)
    val missingTeacher =
        document.courses
            .filter { it.id in included }
            .count { c ->
                c.meetings.any { it.teacher.ifBlank { c.teacher }.isBlank() }
            }
    val missingRoom =
        document.courses
            .filter { it.id in included }
            .count { c ->
                c.meetings.any { it.location.ifBlank { c.location }.isBlank() }
            }
    val qualityIssues =
        result.assessment != null &&
            (missingTeacher > 0 ||
                missingRoom > 0 ||
                result.assessment.rejected.isNotEmpty() ||
                result.assessment.incomplete)
    fun calibrate(value: Int) {
        if (value > 0) {
            val start =
                ScheduleEngine.weekStart(today, document.term.firstDayOfWeek)
                    .minusWeeks((value - 1).toLong())
            document = document.copy(term = document.term.copy(startDate = start.toString()))
            dateConfirmed = true
        }
    }
    fun commit() {
        if (!busy) {
            val selected =
                document.copy(
                    courses = document.courses.filter { it.id in included },
                    exceptions = document.exceptions.filter { it.courseId in included },
                )
            vm.importDocument(selected, if (mode == "merge") target else null, onComplete)
        }
    }
    FormScreen(
        "导入预览",
        { if (!busy) vm.pendingImport.value = null },
        {
            if (!busy)
                when {
                    !dateConfirmed -> datePrompt = true
                    !timeConfirmed -> timePrompt = true
                    qualityIssues -> qualityPrompt = true
                    else -> commit()
                }
        },
        if (busy) "导入中…" else "导入 ${included.size} 门",
    ) {
        if (result.sources.size > 1)
            Choice(
                "识别来源",
                result.selectedSource,
                result.sources.mapIndexed { i, source ->
                    i to "${source.label} · ${source.result.documents.sumOf {it.courses.size}}门"
                },
                vm::chooseImportSource,
                Modifier.fillMaxWidth(),
            )
        if (result.mapping != null) ValueRow("核对字段", "课程 · 教师 · 地点", { fieldDialog = true })
        Text(
            "共 ${document.courses.size} 门课程 · ${document.courses.sumOf {it.meetings.size}} 个时段",
            style = MaterialTheme.typography.titleSmall,
        )
        if (dateConfirmed && week in 1..document.term.weeks)
            Text(
                "第 $week 周显示 ${document.courses.count {c->c.meetings.any {week in it.weeks}}} 门",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        if (result.assessment != null) {
            if (result.assessment.rejected.isNotEmpty())
                Text(
                    "${result.assessment.rejected.size} 条记录未识别",
                    color = MaterialTheme.colorScheme.error,
                )
            if (missingTeacher > 0 || missingRoom > 0)
                Text(
                    "缺少教师 $missingTeacher 门 · 缺少地点 $missingRoom 门",
                    color = MaterialTheme.colorScheme.error,
                )
            if (result.assessment.dateUnknown)
                Choice(
                    "当前教学周",
                    if (dateConfirmed && week in 1..document.term.weeks) week else 0,
                    listOf(0 to "请选择") + (1..document.term.weeks).map { it to "第 $it 周" },
                    ::calibrate,
                    Modifier.fillMaxWidth(),
                )
        }
        if (result.documents.size > 1)
            Choice(
                "文件中的课表",
                index,
                result.documents.mapIndexed { i, d -> i to d.term.name },
                {
                    index = it
                    document = result.documents[it]
                    dateConfirmed = result.assessment?.dateUnknown != true
                    timeConfirmed = result.assessment?.timeUnknown != true
                    included = document.courses.map { c -> c.id }.toSet()
                },
                Modifier.fillMaxWidth(),
            )
        ValueRow("课表", document.term.name, { setup = true })
        ValueRow(
            "开学日期 / 周数",
            if (dateConfirmed) "${document.term.startDate} · ${document.term.weeks} 周"
            else "待确认 · ${document.term.weeks} 周",
            { setup = true },
        )
        ValueRow(
            "核对作息",
            if (timeConfirmed) "${document.timetable.periods.size} 节"
            else "待确认 · ${document.timetable.periods.size} 节",
            { timePrompt = true },
        )
        Choice(
            "导入方式",
            mode,
            listOf("new" to "新建课表") +
                if (s.terms.isNotEmpty()) listOf("merge" to "合并到已有课表") else emptyList(),
            { mode = it },
            Modifier.fillMaxWidth(),
        )
        if (mode == "merge")
            Choice(
                "目标课表",
                target,
                s.terms.map { it.id to it.name },
                { target = it },
                Modifier.fillMaxWidth(),
            )
        val unresolvedWarnings =
            result.warnings
                .filterNot {
                    (dateConfirmed && it.contains("开学日期")) || (timeConfirmed && it.contains("作息"))
                }
                .distinct()
        unresolvedWarnings.take(20).forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (unresolvedWarnings.size > 20)
            Text("另有 ${unresolvedWarnings.size-20} 项未识别内容", color = MaterialTheme.colorScheme.error)
        SectionTitle("课程 · ${included.size}/${document.courses.size}")
        document.courses.take(200).forEach { course ->
            InfoCard(
                course.name,
                listOf(
                        course.teacher.takeIf { it.isNotBlank() }?.let { "教师：$it" }.orEmpty(),
                        course.location.takeIf { it.isNotBlank() }?.let { "地点：$it" }.orEmpty(),
                    )
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                { Icon(Icons.Outlined.MenuBook, null) },
                { editing = course },
                trailing = {
                    IconButton(
                        onClick = {
                            included =
                                if (course.id in included) included - course.id
                                else included + course.id
                        }
                    ) {
                        Icon(
                            if (course.id in included) Icons.Outlined.CheckCircle
                            else Icons.Outlined.RadioButtonUnchecked,
                            "选择 ${course.name}",
                        )
                    }
                },
            )
        }
        if (document.courses.size > 200) Text("其余 ${document.courses.size-200} 门课程将一并导入")
    }
    if (fieldDialog && result.mapping != null)
        ImportFieldDialog(result.mapping, { fieldDialog = false }) {
            fieldDialog = false
            vm.remapImport(it)
        }
    if (datePrompt)
        SelectionDialog(
            "当前教学周",
            if (week in 1..document.term.weeks) week else 1,
            (1..document.term.weeks).map { it to "第 $it 周" },
            { datePrompt = false },
        ) { selected ->
            calibrate(selected)
            datePrompt = false
        }
    if (timePrompt)
        SelectionDialog(
            "选择作息",
            timeChoice,
            compatibleTimes.map { it.id to it.name } + listOf("manual" to "手动设置"),
            { timePrompt = false },
        ) { selected ->
            timePrompt = false
            useTime(selected)
        }
    if (qualityPrompt)
        AlertDialog(
            onDismissRequest = { qualityPrompt = false },
            title = { Text("仍有信息需要核对") },
            text = {
                Text(
                    "缺少教师 $missingTeacher 门，缺少地点 $missingRoom 门，未识别 ${result.assessment?.rejected?.size?:0} 条。请确认课程总数与周次。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        qualityPrompt = false
                        commit()
                    }
                ) {
                    Text("仍然导入")
                }
            },
            dismissButton = { TextButton(onClick = { qualityPrompt = false }) { Text("继续核对") } },
        )
    if (setup)
        TermEditor(document.snapshot(), document.term, false, { setup = false }) { term ->
            document = document.copy(term = term).validate()
            setup = false
            dateConfirmed = true
        }
    if (time)
        TimetableEditor(document.snapshot(), document.timetable, false, { time = false }) { table ->
            document = document.copy(timetable = table).validate()
            time = false
            timeConfirmed = true
        }
    editing?.let { course ->
        CourseEditor(
            document.snapshot(),
            document.term,
            course,
            1,
            1,
            false,
            { editing = null },
            { changed ->
                document =
                    TimetableDocument.from(
                        document.snapshot().withCourse(changed),
                        document.term.id,
                    )
                editing = null
            },
        )
    }
}
