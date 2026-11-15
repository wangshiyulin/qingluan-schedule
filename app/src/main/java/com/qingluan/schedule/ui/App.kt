@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qingluan.schedule.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.model.*
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QingluanApp(initialTab: Int = -1, vm: AppViewModel) {
    val data by vm.state.collectAsStateWithLifecycle()
    val p by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    val incoming by vm.pendingImport.collectAsStateWithLifecycle()
    val backup by vm.pendingBackup.collectAsStateWithLifecycle()
    val loadError by vm.repo.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var startupApplied by rememberSaveable { mutableStateOf(initialTab >= 0) }
    LaunchedEffect(Unit) {
        if (!startupApplied) {
            tab = vm.repo.currentSettings().startupPage
            startupApplied = true
        }
    }
    var page by rememberSaveable { mutableStateOf("") }
    var educationUrl by rememberSaveable { mutableStateOf("") }
    var switchOpen by rememberSaveable { mutableStateOf(false) }
    var weekPickerOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var history by rememberSaveable { mutableStateOf(listOf<Int>()) }
    if (tab !in 0..2) tab = tab.coerceIn(0, 2)
    when (page) {
        "课表设置",
        "课表管理",
        "通用设置" -> page = "课表列表"
        "外观设置" -> page = "个性化"
        "备份与恢复",
        "备份",
        "导入课表" -> page = "导入与备份"
        "单次调整" -> page = "临时调整"
        "桌面小组件",
        "切换课表" -> page = ""
    }
    var focusWeek by rememberSaveable { mutableIntStateOf(0) }
    val screenStates = rememberSaveableStateHolder()
    val educationStates = rememberSaveableStateHolder()
    val backMotion = remember { Animatable(0f) }
    var backDirection by remember { mutableFloatStateOf(1f) }
    fun goBack() {
        if (page == "教务处导入" && educationUrl.isNotBlank()) {
            educationUrl = ""
            return
        }
        if (page.isNotBlank())
            page =
                when (page) {
                    "教务处导入",
                    "本地自动备份" -> "导入与备份"
                    "个性化课表" -> "个性化"
                    else -> ""
                }
        else if (history.isNotEmpty()) {
            tab = history.last()
            history = history.dropLast(1)
        }
    }
    val navigationBackdrop = rememberLayerBackdrop()
    val hardware = androidx.compose.ui.platform.LocalView.current.isHardwareAccelerated
    var editCourse by rememberJsonState<Course?> { null }
    var courseOpen by rememberSaveable { mutableStateOf(false) }
    var firstDay by rememberSaveable { mutableIntStateOf(1) }
    var firstPeriod by rememberSaveable { mutableIntStateOf(1) }
    var termEdit by rememberJsonState<Term?> { null }
    var termOpen by rememberSaveable { mutableStateOf(false) }
    var timeEdit by rememberJsonState<Timetable?> { null }
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    var detail by rememberJsonState<Occurrence?> { null }
    var detailMembers by rememberJsonState<List<Occurrence>> { emptyList() }
    var temporaryCourseId by rememberSaveable { mutableStateOf("") }
    var temporaryKey by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val snackbar = remember { SnackbarHostState() }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(vm::previewRestore)
        }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000)
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }
    val canGoBack =
        (page.isNotBlank() || history.isNotEmpty()) &&
            !courseOpen &&
            !termOpen &&
            !timeOpen &&
            detail == null &&
            backup == null &&
            incoming == null &&
            failure == null &&
            !switchOpen &&
            !weekPickerOpen
    PredictiveBackHandler(enabled = canGoBack) { events ->
        try {
            events.collect { event ->
                backDirection = if (event.swipeEdge == 0) 1f else -1f
                backMotion.snapTo(event.progress)
            }
            goBack()
            backMotion.snapTo(0f)
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                backMotion.animateTo(0f, spring(dampingRatio = .88f, stiffness = 600f))
            }
        }
    }
    QingluanTheme(p) {
        GlassPage(Modifier.fillMaxSize()) {
            val s = if (startupApplied) data else null
            if (s == null) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (loadError == null) {
                            CircularProgressIndicator()
                            Text("正在打开课表", Modifier.padding(top = 16.dp))
                        } else {
                            Text(loadError!!)
                            Button(
                                onClick = { restore.launch(arrayOf("application/json", "*/*")) }
                            ) {
                                Text("导入备份")
                            }
                        }
                    }
                }
            } else {
                val term = s.terms.find { it.id == s.activeTermId }
                val weekPager =
                    key(term?.id, term?.startDate, term?.firstDayOfWeek, term?.weeks) {
                        rememberPagerState(
                            initialPage =
                                term?.let {
                                    ScheduleEngine.week(it, now.toLocalDate())
                                        .coerceIn(1, it.weeks) - 1
                                } ?: 0,
                            pageCount = { term?.weeks ?: 1 },
                        )
                    }
                LaunchedEffect(term?.id, focusWeek) {
                    if (focusWeek > 0 && term != null) {
                        weekPager.scrollToPage((focusWeek - 1).coerceIn(0, term.weeks - 1))
                        focusWeek = 0
                    }
                }
                fun createTerm() {
                    termEdit = null
                    termOpen = true
                }
                fun add(day: Int = 1, period: Int = 1) {
                    if (term == null) createTerm()
                    else {
                        editCourse = null
                        firstDay = day
                        firstPeriod = period
                        courseOpen = true
                    }
                }
                Surface(
                    Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    if (backMotion.value > 0f) {
                        val targetTab = if (page.isNotBlank()) tab else history.lastOrNull() ?: tab
                        val targetPage =
                            when (page) {
                                "教务处导入" -> if (educationUrl.isNotBlank()) "教务处导入" else "导入与备份"
                                "本地自动备份" -> "导入与备份"
                                "个性化课表" -> "个性化"
                                else -> ""
                            }
                        PredictiveDestination(
                            s,
                            p,
                            vm,
                            targetTab,
                            targetPage,
                            now,
                            weekPager,
                            navigationBackdrop,
                        )
                    }
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = 1f - backMotion.value * .08f
                                scaleY = scaleX
                                translationX = size.width * backMotion.value * .22f * backDirection
                                shadowElevation = 8.dp.toPx() * backMotion.value
                                shape = RoundedCornerShape((24 * backMotion.value).dp)
                                clip = backMotion.value > 0f
                            },
                        snackbarHost = {
                            SnackbarHost(snackbar) { message -> GlassSnackbar(message) }
                        },
                        topBar = {
                            ScreenAppBar(
                                centered = page.isBlank(),
                                title = {
                                    if (page.isBlank() && tab == 1) {
                                        TextButton(
                                            onClick = { weekPickerOpen = true },
                                            enabled = term != null,
                                            modifier = Modifier.testTag("week-selector"),
                                        ) {
                                            Text(
                                                if (term == null) "暂无课表"
                                                else "第 ${weekPager.currentPage+1} 周",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (term != null)
                                                Icon(
                                                    Icons.Outlined.ExpandMore,
                                                    null,
                                                    Modifier.size(18.dp),
                                                )
                                        }
                                    } else
                                        Text(
                                            page.ifBlank { listOf("今日", "课表", "设置")[tab] },
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                },
                                navigationIcon = {
                                    if (page.isNotBlank())
                                        IconButton(onClick = { goBack() }) {
                                            Icon(Icons.Outlined.ArrowBack, "返回")
                                        }
                                },
                                actions = {
                                    if (page.isBlank() && tab == 1)
                                        IconButton(onClick = { switchOpen = true }) {
                                            Icon(Icons.Outlined.SwapHoriz, "切换课表")
                                        }
                                    when (page) {
                                        "编辑课表" -> {
                                            IconButton(
                                                onClick = {
                                                    termEdit = term
                                                    termOpen = true
                                                }
                                            ) {
                                                Icon(Icons.Outlined.Schedule, "基本设置")
                                            }
                                            TextButton(onClick = { page = "" }) { Text("完成") }
                                        }

                                        "课表管理",
                                        "课表列表" ->
                                            IconButton(onClick = { createTerm() }) {
                                                Icon(Icons.Outlined.Add, "新建课表")
                                            }
                                        "课程管理" ->
                                            IconButton(onClick = { add() }) {
                                                Icon(Icons.Outlined.Add, "添加课程")
                                            }
                                        "作息管理" ->
                                            IconButton(
                                                onClick = {
                                                    timeEdit = null
                                                    timeOpen = true
                                                }
                                            ) {
                                                Icon(Icons.Outlined.Add, "新建作息表")
                                            }
                                    }
                                },
                            )
                        },
                        bottomBar = {
                            if (page.isBlank())
                                LiquidNavigation(tab, navigationBackdrop) { i ->
                                    if (tab != i) {
                                        history = (history + tab).takeLast(10)
                                        tab = i
                                    }
                                }
                        },
                    ) { padding ->
                        Box(
                            Modifier.padding(top = padding.calculateTopPadding())
                                .then(
                                    if (hardware && page != "教务处导入")
                                        Modifier.layerBackdrop(navigationBackdrop)
                                    else Modifier
                                )
                        ) {
                            AmbientBackdrop(Modifier.matchParentSize())
                            key(tab, page) {
                                val screen = tab
                                val child = page
                                val entry = remember {
                                    Animatable(if (p.animationsEnabled) 0f else 1f)
                                }
                                LaunchedEffect(Unit) {
                                    if (p.animationsEnabled)
                                        entry.animateTo(
                                            1f,
                                            tween(150, easing = FastOutSlowInEasing),
                                        )
                                }
                                Box(
                                    Modifier.fillMaxSize().testTag("screen-content").graphicsLayer {
                                        alpha =
                                            if (child == "教务处导入") 1f else .7f + .3f * entry.value
                                    }
                                ) {
                                    screenStates.SaveableStateProvider("$screen/$child") {
                                        when {
                                            child == "导入与备份" ->
                                                TransferScreen(
                                                    s,
                                                    vm,
                                                    onLocalBackups = { page = "本地自动备份" },
                                                ) {
                                                    educationUrl = ""
                                                    page = "教务处导入"
                                                }
                                            child == "本地自动备份" -> LocalBackupsScreen(p, vm)
                                            child == "节假日与调休" -> CalendarRulesScreen(s, vm)
                                            child == "课程冲突" ->
                                                ConflictListScreen(s) { week ->
                                                    focusWeek = week
                                                    page = ""
                                                    tab = 1
                                                }
                                            child == "个性化课表" -> ScheduleAppearance(p, vm)
                                            child == "教务处导入" ->
                                                if (educationUrl.isBlank())
                                                    educationStates.SaveableStateProvider(
                                                        "selector"
                                                    ) {
                                                        SchoolImportScreen { educationUrl = it }
                                                    }
                                                else
                                                    key(educationUrl) {
                                                        EducationImportScreen(vm, educationUrl)
                                                    }
                                            child == "临时调整" ->
                                                TemporaryAdjustmentScreen(
                                                    s,
                                                    vm,
                                                    temporaryCourseId,
                                                    temporaryKey,
                                                    {
                                                        page = ""
                                                        tab = 1
                                                    },
                                                )
                                            child == "编辑课表" ->
                                                WeekScreen(
                                                    s,
                                                    p,
                                                    now,
                                                    weekPager,
                                                    {
                                                        editCourse = it.course
                                                        courseOpen = true
                                                    },
                                                    { day, period -> add(day, period) },
                                                    {
                                                        editCourse = it
                                                        courseOpen = true
                                                    },
                                                )
                                            screen == 0 ->
                                                TodayScreen(
                                                    s,
                                                    now,
                                                    {
                                                        detailMembers = emptyList()
                                                        detail = it
                                                    },
                                                    {
                                                        detailMembers = it
                                                        detail = it.first()
                                                    },
                                                )
                                            screen == 1 ->
                                                WeekScreen(
                                                    s,
                                                    p,
                                                    now,
                                                    weekPager,
                                                    {
                                                        detailMembers = emptyList()
                                                        detail = it
                                                    },
                                                    { day, period -> add(day, period) },
                                                    {
                                                        editCourse = it
                                                        courseOpen = true
                                                    },
                                                    onMerged = {
                                                        detailMembers = it
                                                        detail = it.first()
                                                    },
                                                )
                                            screen == 2 ->
                                                if (child in listOf("课表列表", "课程管理", "作息管理")) {
                                                    Management(
                                                        s,
                                                        vm,
                                                        {
                                                            editCourse = it
                                                            courseOpen = true
                                                        },
                                                        {
                                                            termEdit = it
                                                            termOpen = true
                                                        },
                                                        {
                                                            timeEdit = it
                                                            timeOpen = true
                                                        },
                                                        child,
                                                    )
                                                } else
                                                    SettingsScreen(
                                                        s,
                                                        p,
                                                        vm,
                                                        child,
                                                        {
                                                            if (it == "临时调整") {
                                                                temporaryCourseId = ""
                                                                temporaryKey = ""
                                                            }
                                                            page = it
                                                        },
                                                    )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (weekPickerOpen && term != null)
                    SelectionDialog(
                        "选择周次",
                        weekPager.currentPage + 1,
                        (1..term.weeks).map { it to "第 $it 周" },
                        { weekPickerOpen = false },
                    ) { week ->
                        weekPickerOpen = false
                        scope.launch {
                            if (p.animationsEnabled) weekPager.animateScrollToPage(week - 1)
                            else weekPager.scrollToPage(week - 1)
                        }
                    }
                if (switchOpen)
                    AlertDialog(
                        onDismissRequest = { switchOpen = false },
                        title = { Text("切换课表") },
                        text = {
                            LazyColumn(
                                Modifier.fillMaxWidth().heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (s.terms.isEmpty()) item { Text("暂无课表") }
                                items(s.terms, key = { it.id }) { table ->
                                    Surface(
                                        onClick = {
                                            vm.activate(table.id)
                                            switchOpen = false
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        color =
                                            if (table.id == s.activeTermId)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else androidx.compose.ui.graphics.Color.Transparent,
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(table.name, Modifier.weight(1f))
                                            if (table.id == s.activeTermId)
                                                Icon(Icons.Outlined.Check, null)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    switchOpen = false
                                    createTerm()
                                }
                            ) {
                                Text("新建课表")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { switchOpen = false }) { Text("取消") }
                        },
                    )
                if (incoming != null)
                    ImportPreview(incoming!!, s, vm, busy) {
                        courseOpen = false
                        termOpen = false
                        timeOpen = false
                        detail = null
                        tab = 1
                        page = "编辑课表"
                    }
                if (courseOpen && term != null)
                    CourseEditor(
                        s,
                        term,
                        editCourse,
                        firstDay,
                        firstPeriod,
                        busy,
                        { if (!busy) courseOpen = false },
                        { c -> vm.saveCourse(c) { courseOpen = false } },
                        onDelete =
                            editCourse?.let { course ->
                                {
                                    vm.deleteCourse(course.id)
                                    courseOpen = false
                                }
                            },
                    )
                if (termOpen)
                    TermEditor(
                        s,
                        termEdit,
                        busy,
                        {
                            if (!busy) {
                                termOpen = false
                            }
                        },
                        { t ->
                            vm.saveTerm(t, activate = true) {
                                termOpen = false
                                tab = 1
                                page = "编辑课表"
                            }
                        },
                    )
                if (timeOpen)
                    TimetableEditor(
                        s,
                        timeEdit,
                        busy,
                        { if (!busy) timeOpen = false },
                        { t -> vm.saveTimetable(t) { timeOpen = false } },
                    )
                detail?.let { o ->
                    CourseDetails(
                        o,
                        { detail = null },
                        {
                            detail = null
                            editCourse = o.course
                            courseOpen = true
                        },
                        {
                            detail = null
                            temporaryCourseId = o.course.id
                            temporaryKey = o.key
                            page = "临时调整"
                        },
                        {
                            detail = null
                            editCourse =
                                o.course.copy(
                                    id = newId(),
                                    name = o.course.name + "（副本）",
                                    meetings = o.course.meetings.map { it.copy(id = newId()) },
                                )
                            courseOpen = true
                        },
                        alternatives = detailMembers,
                        onSelect = { detail = it },
                    )
                }
            }
            failure?.let { error ->
                AlertDialog(
                    onDismissRequest = { vm.failure.value = null },
                    title = { Text("未能完成操作") },
                    text = { Text(error) },
                    confirmButton = {
                        TextButton(onClick = { vm.failure.value = null }) { Text("返回") }
                    },
                )
            }
            backup?.let { b ->
                AlertDialog(
                    onDismissRequest = { if (!busy) vm.pendingBackup.value = null },
                    title = { Text("导入预览") },
                    text = {
                        Text(
                            "备份中包含：\n${b.data.terms.size} 个学期\n${b.data.courses.size} 门课程\n${b.data.timetables.size} 套作息\n${b.data.exceptions.size} 次临时调整\n\n确认后替换本机全部课表和设置。请先导出当前备份。"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { if (!busy) vm.confirmRestore() }, enabled = !busy) {
                            Text(if (busy) "导入中…" else "确认替换")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.pendingBackup.value = null }, enabled = !busy) {
                            Text("取消")
                        }
                    },
                )
            }
        }
    }
}
