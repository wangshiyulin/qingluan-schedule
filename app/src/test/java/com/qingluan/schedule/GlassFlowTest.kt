package com.qingluan.schedule

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import com.qingluan.schedule.transfer.*
import com.qingluan.schedule.ui.EDGE_SHADER
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (ui.activity.application as ScheduleApplication).repository

    @Before
    fun reset() {
        val today = LocalDate.now()
        runBlocking {
            repo.awaitReady()
            repo.restore(
                Backup(
                    data =
                        Snapshot(
                            activeTermId = "t",
                            terms =
                                listOf(
                                    Term(
                                        id = "t",
                                        name = "测试课表",
                                        startDate = ScheduleEngine.monday(today).toString(),
                                        timetableId = "default",
                                    )
                                ),
                            courses =
                                listOf(
                                    Course(
                                        id = "c",
                                        termId = "t",
                                        name = "测试课程",
                                        location = "A301",
                                        meetings =
                                            listOf(
                                                Meeting(id = "m", weekday = today.dayOfWeek.value)
                                            ),
                                    )
                                ),
                        )
                )
            )
        }
        ui.waitForIdle()
    }

    private fun settingsItem(title: String) {
        val list = ui.onNodeWithTag("my-settings")
        list.performScrollToNode(hasText(title))
        val y = ui.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.center.y
        val center = list.fetchSemanticsNode().boundsInRoot.center.y
        if (y > center)
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, y - center) }
        ui.onNodeWithText(title).performClick()
    }

    private fun wheel(title: String, index: Int) {
        ui.onNodeWithText(title).performClick()
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(index.toFloat())
        }
        ui.onNodeWithText("确定").performClick()
    }

    private fun picture(name: String) {
        ui.waitForIdle()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        val file = File(System.getProperty("qingluan.screenshots"), name)
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun importAndExportAreSeparatedAndAboutLinksPrecedeUpdates() {
        ui.onNodeWithText("设置").performClick()
        settingsItem("导入课表")
        ui.onNodeWithText("导入课表").performClick()
        ui.onNodeWithTag("import-page").assertIsDisplayed()
        ui.onNodeWithText("教务处导入").assertIsDisplayed()
        ui.onNodeWithText("导入完整备份").assertExists()
        ui.onNodeWithText("导出单张课表").assertDoesNotExist()
        picture("liquid-import.png")
        ui.onNodeWithContentDescription("返回").performClick()
        settingsItem("备份")
        ui.onNodeWithText("备份").performClick()
        ui.onNodeWithTag("transfer-page").assertIsDisplayed()
        ui.onNodeWithText("导出单张课表").assertExists()
        ui.onNodeWithText("导入完整备份").assertDoesNotExist()
        ui.onNodeWithText("教务处导入").assertDoesNotExist()
        picture("liquid-export.png")
        ui.onNodeWithContentDescription("返回").performClick()
        settingsItem("关于")
        ui.onNodeWithText("关于").performClick()
        val links = ui.onNodeWithText("官网").fetchSemanticsNode().boundsInRoot
        val update = ui.onNodeWithText("检查更新").fetchSemanticsNode().boundsInRoot
        assertTrue(links.bottom < update.top)
        picture("liquid-about.png")
    }

    @Test
    fun bottomLensDragChoosesDestinationOnlyWhenReleased() {
        ui.onNodeWithTag("transparent-navigation").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width / 6f, height / 2f))
            moveTo(androidx.compose.ui.geometry.Offset(width * 5f / 6f, height / 2f), 220)
        }
        ui.onNodeWithTag("my-settings").assertDoesNotExist()
        ui.onNodeWithTag("transparent-navigation").performTouchInput { up() }
        ui.onNodeWithTag("my-settings").assertIsDisplayed()
    }

    @Test
    fun timeColumnGridLinesAndLargerNavigationAlign() {
        ui.onNodeWithText("课表").performClick()
        ui.onNodeWithTag("transparent-navigation").assertHeightIsEqualTo(60.dp)
        (0..3).forEach { index ->
            val left = ui.onNodeWithTag("period-time-$index").fetchSemanticsNode().boundsInRoot
            val right = ui.onNodeWithTag("period-grid-0-$index").fetchSemanticsNode().boundsInRoot
            assertEquals(left.top, right.top, .1f)
            assertEquals(left.bottom, right.bottom, .1f)
        }
        picture("aligned-grid-larger-nav.png")
    }

    @Test
    fun schoolSelectionOpensUrlAndLoadingKeepsNativeControlsVisible() {
        runBlocking { repo.updateSettings { it.copy(theme = "dark") } }
        ui.onNodeWithText("设置").performClick()
        settingsItem("导入课表")
        ui.onNodeWithText("从学校导入").performClick()
        ui.onNodeWithText("搜索学校").performTextInput("南京中医药大学")
        ui.onAllNodesWithText("南京中医药大学").onLast().performClick()
        ui.onNodeWithText("教务网址").assertTextContains("https://jwgl.njucm.edu.cn/student/home")
        ui.onNodeWithTag("education-loading").assertIsDisplayed()
        ui.onNodeWithText("教务导入").assertIsDisplayed()
        ui.onNodeWithText("提取课表").assertIsDisplayed()
        picture("school-web-loading-dark.png")
        ui.onNodeWithText("提取课表").assertIsNotEnabled()
        ui.runOnIdle {
            fun find(view: android.view.View): android.webkit.WebView? {
                if (view is android.webkit.WebView) return view
                if (view is android.view.ViewGroup)
                    for (i in 0 until view.childCount) find(view.getChildAt(i))?.let {
                        return it
                    }
                return null
            }
            val web = find(ui.activity.window.decorView)!!
            web.webViewClient.onPageCommitVisible(web, "https://jwgl.njucm.edu.cn/student/home")
        }
        ui.onNodeWithTag("education-loading").assertDoesNotExist()
        ui.onNodeWithText("提取课表").assertIsEnabled()
        ui.onNodeWithText("教务导入").assertIsDisplayed()
        ui.onNodeWithText("自动识别").performClick()
        ui.onNodeWithText("提取方式").assertIsDisplayed()
        ui.onNodeWithText("取消").performClick()
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.onNodeWithTag("school-import-page").assertIsDisplayed()
        ui.onNodeWithText("搜索学校").assertTextContains("南京中医药大学")
        picture("school-search-dark.png")
    }

    @Test
    fun academicBrowserFitsViewportAndKeepsSafeWebSettings() {
        ui.onNodeWithText("设置").performClick()
        settingsItem("导入课表")
        ui.onNodeWithText("教务处导入").performClick()
        ui.onNodeWithText("教务网址").performTextInput("https://example.edu/schedule")
        ui.onNodeWithText("打开").performClick()
        ui.waitForIdle()
        fun find(view: android.view.View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup)
                for (i in 0 until view.childCount) {
                    find(view.getChildAt(i))?.let {
                        return it
                    }
                }
            return null
        }
        ui.runOnIdle {
            val web = find(ui.activity.window.decorView)!!
            assertTrue(
                "web ${web.width}x${web.height}, measured ${web.measuredWidth}x${web.measuredHeight}",
                web.measuredWidth > 0 && web.measuredHeight > 0,
            )
            assertTrue(web.settings.useWideViewPort && web.settings.loadWithOverviewMode)
            assertTrue(web.settings.builtInZoomControls)
            assertFalse(web.settings.userAgentString.contains(" Mobile"))
            assertFalse(web.settings.allowFileAccess)
            assertFalse(web.settings.allowContentAccess)
            web.webViewClient.onPageFinished(web, "https://example.edu/schedule")
        }
    }

    @Test
    fun defaultMotionAndPredictiveDestinationWorkWithoutAppearanceToggles() {
        runBlocking {
            repo.updateSettings {
                it.copy(animationsEnabled = false, predictiveBackEnabled = false)
            }
        }
        assertTrue(runBlocking { repo.currentSettings() }.animationsEnabled)
        assertTrue(runBlocking { repo.currentSettings() }.predictiveBackEnabled)
        ui.onNodeWithText("设置").performClick()
        settingsItem("个性化")
        ui.onNodeWithText("界面动效").assertDoesNotExist()
        ui.onNodeWithText("预测返回").assertDoesNotExist()
        val label =
            ui.onNodeWithText("配色", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value =
            ui.onNodeWithText(
                    com.qingluan.schedule.ui.AppPalette.find("jade").title,
                    useUnmergedTree = true,
                )
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(value.left > label.right)
        val dispatcher = ui.activity.onBackPressedDispatcher
        fun start() {
            ui.runOnIdle {
                dispatcher.dispatchOnBackStarted(
                    androidx.activity.BackEventCompat(
                        0f,
                        400f,
                        0f,
                        androidx.activity.BackEventCompat.EDGE_LEFT,
                    )
                )
            }
            ui.runOnIdle {
                dispatcher.dispatchOnBackProgressed(
                    androidx.activity.BackEventCompat(
                        50f,
                        400f,
                        .65f,
                        androidx.activity.BackEventCompat.EDGE_LEFT,
                    )
                )
            }
            ui.waitForIdle()
        }
        start()
        ui.onNodeWithTag("predictive-destination").assertIsDisplayed()
        picture("predictive-return-preview.png")
        ui.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        ui.waitForIdle()
        ui.onNodeWithTag("predictive-destination").assertDoesNotExist()
        ui.onNodeWithText("配色").assertIsDisplayed()
        start()
        ui.runOnIdle { dispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.onNodeWithTag("my-settings").assertIsDisplayed()
    }

    @Test
    fun courseTitlesUseStartAlignment() {
        ui.onNodeWithText("课表").performClick()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        ui.onNodeWithText("测试课程", useUnmergedTree = true).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(layouts)
        }
        assertEquals(
            androidx.compose.ui.text.style.TextAlign.Start,
            layouts.single().layoutInput.style.textAlign,
        )
    }

    @Test
    fun academicMetadataSurvivesPreviewSaveAndWeekRendering() {
        val vm = ViewModelProvider(ui.activity)[AppViewModel::class.java]
        val capture =
            """{"format":"QingluanWebCapture","records":[{"courseName":"导入英语","day":${LocalDate.now().dayOfWeek.value},"startNode":1,"step":2,"startWeek":8,"endWeek":17,"teacherName":"王老师","classroomName":"400446—004"}]}"""
        ui.runOnIdle { vm.previewWeb(capture) }
        ui.waitUntil(10000) { ui.onAllNodesWithText("导入 1 门").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("待确认 · 20 周").assertExists()
        ui.onNodeWithText("当前教学周").performScrollTo()
        wheel("当前教学周", 11)
        ui.onNodeWithText("第 11 周显示 1 门").assertExists()
        ui.onNodeWithText("核对作息").performScrollTo()
        wheel("核对作息", 0)
        ui.onNodeWithText("教师：王老师 · 地点：400446—004").performScrollTo().assertExists()
        picture("import-preview-metadata-week.png")
        ui.onNodeWithText("导入 1 门").performClick()
        ui.waitUntil(10000) {
            runBlocking { repo.state.value?.courses?.any { it.name == "导入英语" } == true }
        }
        val imported = repo.state.value!!.courses.single { it.name == "导入英语" }
        val term = repo.state.value!!.terms.single { it.id == imported.termId }
        assertEquals(11, ScheduleEngine.week(term, LocalDate.now()))
        assertEquals("王老师", imported.teacher)
        assertEquals("400446—004", imported.location)
        ui.onNodeWithText("王老师", useUnmergedTree = true).assertExists()
        ui.onNodeWithText("400446—004", useUnmergedTree = true).assertExists()
        picture("imported-teacher-room-grid.png")
    }

    @Test
    fun contiguousLessonsStayUnifiedInGridDetailsAndEditor() {
        val day = LocalDate.now().dayOfWeek.value
        runBlocking {
            val old = repo.state.value!!
            val course =
                old.courses
                    .single()
                    .copy(
                        teacher = "王老师",
                        meetings =
                            listOf(
                                Meeting(
                                    id = "first",
                                    weekday = day,
                                    startPeriod = 1,
                                    endPeriod = 2,
                                ),
                                Meeting(
                                    id = "third",
                                    weekday = day,
                                    startPeriod = 3,
                                    endPeriod = 3,
                                ),
                            ),
                    )
            repo.restore(Backup(data = old.copy(courses = listOf(course))))
        }
        assertEquals(1, repo.state.value!!.courses.single().meetings.size)
        ui.onNodeWithText("课表").performClick()
        ui.onNodeWithText("测试课程").performTouchInput { click() }
        ui.onNodeWithText("上课时段").assertDoesNotExist()
        ui.onAllNodesWithText("第 1–3 节", substring = true).assertCountEquals(2)
        picture("unified-course-details.png")
        ui.onNodeWithText("编辑整门课").performTouchInput { click() }
        ui.onNodeWithText("时段 2").assertDoesNotExist()
        ui.onNodeWithText("时段教师").assertDoesNotExist()
        ui.onNodeWithText("时段地点").assertDoesNotExist()
        ui.onNodeWithText("开始").performScrollTo()
        ui.onNodeWithText("开始").performClick()
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(1f)
        }
        ui.onNodeWithText("确定").performClick()
        picture("unified-course-editor.png")
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10000) {
            repo.state.value!!.courses.single().meetings.single().startPeriod == 2
        }
        val saved = repo.state.value!!.courses.single()
        assertEquals(3, saved.meetings.single().endPeriod)
        assertEquals("王老师", saved.teacher)
    }

    @Test
    fun physicalCourseTapDisplaysUsableDetails() {
        ui.onNodeWithText("课表").performClick()
        ui.waitForIdle()
        val visible =
            SemanticsMatcher("visible course") {
                it.boundsInRoot.center.x in 1f..410f && it.boundsInRoot.center.y in 90f..800f
            }
        ui.onAllNodesWithText("测试课程").filterToOne(visible).performTouchInput { click() }
        ui.onNodeWithText("编辑整门课").assertIsDisplayed().performTouchInput { click() }
        ui.onNodeWithText("课程名称 *").assertIsDisplayed()
    }

    @Test
    fun mergedCourseDetailTimeSelectionRespondsToTouch() {
        runBlocking {
            repo.change { snapshot ->
                snapshot.copy(
                    courses =
                        snapshot.courses.map { c ->
                            c.copy(
                                meetings =
                                    c.meetings +
                                        Meeting(
                                            id = "later",
                                            weekday = LocalDate.now().dayOfWeek.value,
                                            startPeriod = 3,
                                            endPeriod = 3,
                                            teacher = "教师B",
                                            location = "B302",
                                        )
                            )
                        }
                )
            }
        }
        ui.waitForIdle()
        ui.onNodeWithText("课表").performClick()
        ui.waitForIdle()
        val visible =
            SemanticsMatcher("visible") {
                it.boundsInRoot.center.x in 1f..410f && it.boundsInRoot.center.y in 90f..800f
            }
        ui.onAllNodesWithText("测试课程").filterToOne(visible).performTouchInput { click() }
        ui.onNodeWithText("上课时段").assertIsDisplayed().performTouchInput { click() }
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(1f)
        }
        ui.onNodeWithText("确定").performTouchInput { click() }
        ui.onAllNodesWithText("教师B", substring = true)
            .filterToOne(hasAnyAncestor(hasTestTag("course-detail-sheet")))
            .assertIsDisplayed()
        ui.onNodeWithText("编辑整门课").performTouchInput { click() }
        ui.onNodeWithText("课程名称 *").assertIsDisplayed()
    }

    @Test
    fun glassButtonsInstantSettingsAndAllCourseRunsRender() {
        val shader = RuntimeShader(EDGE_SHADER)
        shader.setFloatUniform("resolution", 200f, 80f)
        shader.setFloatUniform("radius", 30f)
        shader.setFloatUniform("strength", 4f)
        assertNotNull(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"))
        val names =
            listOf(
                "大学英语发展课程",
                "离散数学",
                "Python高级应用",
                "计算机系统基础",
                "Linux网络操作系统",
                "体育III",
                "电子技术",
                "马克思主义基本原理",
            )
        runBlocking {
            val courses =
                names.mapIndexed { i, name ->
                    val day = listOf(1, 2, 4, 5, 6, 1, 5, 1)[i]
                    val meetings =
                        when (i) {
                            1 ->
                                listOf(
                                    Meeting(
                                        id = "math-early",
                                        weekday = day,
                                        startPeriod = 1,
                                        endPeriod = 2,
                                    ),
                                    Meeting(
                                        id = "math-later",
                                        weekday = day,
                                        startPeriod = 3,
                                        endPeriod = 3,
                                    ),
                                )
                            7 ->
                                listOf(
                                    Meeting(
                                        id = "marx-early",
                                        weekday = day,
                                        startPeriod = 3,
                                        endPeriod = 3,
                                        location = "自定义教室",
                                    ),
                                    Meeting(
                                        id = "marx-later",
                                        weekday = day,
                                        startPeriod = 4,
                                        endPeriod = 5,
                                        location = "110048—003",
                                        teacher = "教师B",
                                    ),
                                )
                            else ->
                                listOf(
                                    Meeting(
                                        weekday = day,
                                        startPeriod = if (i in 5..6) 6 else if (i == 4) 3 else 1,
                                        endPeriod = if (i in 5..6) 7 else if (i == 4) 5 else 2,
                                    )
                                )
                        }
                    Course(
                        id = "return-$i",
                        termId = "t",
                        name = name,
                        location = if (i == 7) "" else "A30${i+1}",
                        meetings = meetings,
                    )
                } +
                    Course(
                        id = "ghost",
                        termId = "t",
                        name = "数据结构课程设计",
                        location = "实验楼B205",
                        meetings =
                            listOf(
                                Meeting(
                                    weekday = 3,
                                    startPeriod = 1,
                                    endPeriod = 3,
                                    weeks = setOf(2),
                                )
                            ),
                    )
            repo.restore(
                Backup(
                    data =
                        repo.state.value!!.copy(
                            timetables =
                                listOf(
                                    Timetable(
                                        id = "default",
                                        periods =
                                            (0..9).map { Period(360 + it * 40, 395 + it * 40) },
                                    )
                                ),
                            courses = courses,
                        ),
                    settings = AppSettings(theme = "dark"),
                )
            )
        }
        ui.waitForIdle()
        ui.onNodeWithText("设置").performClick()
        ui.waitForIdle()
        ui.onNodeWithText("个性化").assertExists()
        ui.mainClock.autoAdvance = false
        ui.onNodeWithText("个性化").performSemanticsAction(SemanticsActions.OnClick) { it() }
        ui.mainClock.advanceTimeByFrame()
        assertEquals(
            0f,
            ui.onNodeWithTag("screen-content").fetchSemanticsNode().boundsInRoot.left,
            .1f,
        )
        ui.mainClock.advanceTimeBy(240)
        assertEquals(
            0f,
            ui.onNodeWithTag("screen-content").fetchSemanticsNode().boundsInRoot.left,
            .1f,
        )
        ui.mainClock.autoAdvance = true
        ui.onNodeWithTag("toggle-触觉反馈").performClick()
        ui.waitUntil(10000) {
            ui.waitForIdle()
            runBlocking { repo.currentSettings() }.hapticsEnabled
        }
        assertFalse(ViewModelProvider(ui.activity)[AppViewModel::class.java].busy.value)
        ui.onAllNodes(
                SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
                )
            )
            .assertCountEquals(0)
        ui.onAllNodesWithTag("settings-group").assertCountEquals(2)
        picture("kazumi-controls-dark.png")
        ui.onNodeWithContentDescription("返回").performClick()
        picture("kazumi-settings-dark.png")
        ui.onNodeWithText("课表").performClick()
        val date = ScheduleEngine.monday(LocalDate.now())
        val original =
            ScheduleEngine.onDate(repo.state.value!!, "t", date).first {
                it.course.id == "return-7"
            }
        val block = ui.onNodeWithTag("course-block-${original.key}")
        block.assertHeightIsEqualTo((64 * 3 - 4).dp)
        val visible =
            SemanticsMatcher("visible") {
                it.boundsInRoot.center.x in 1f..410f && it.boundsInRoot.center.y in 80f..800f
            }
        val title =
            ui.onAllNodesWithText("马克思主义基本原理", useUnmergedTree = true)
                .filterToOne(visible)
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            "course title must be top aligned",
            title.top - block.fetchSemanticsNode().boundsInRoot.top < 9f,
        )
        ui.onNodeWithText("110048—003", useUnmergedTree = true).assertExists()
        picture("kazumi-week-dark.png")
        runBlocking { repo.updateSettings { it.copy(showNonCurrentWeek = true) } }
        ui.waitForIdle()
        ui.onNodeWithText("非本周").assertDoesNotExist()
        ui.onNodeWithTag("transparent-navigation").assertHeightIsEqualTo(60.dp)
        ui.onNodeWithText("教师B", useUnmergedTree = true).assertExists()
        picture("kazumi-noncurrent-dark.png")
        runBlocking { repo.updateSettings { it.copy(dynamicColor = true) } }
        ui.waitForIdle()
        picture("kazumi-week-oled.png")
        runBlocking { repo.updateSettings { it.copy(theme = "light") } }
        ui.waitForIdle()
        picture("kazumi-week-light.png")
    }

    @Test
    fun quietSettingsWeekdayAndMergedColoredGridRender() {
        val names =
            listOf("大学英语发展课程", "离散数学", "Python高级应用", "计算机系统基础", "Linux网络操作系统", "体育III", "电子技术")
        runBlocking {
            val current = repo.state.value!!
            val periods = (0..9).map { Period(360 + it * 40, 395 + it * 40) }
            repo.restore(
                Backup(
                    data =
                        current.copy(
                            timetables = listOf(Timetable(id = "default", periods = periods)),
                            courses =
                                names.mapIndexed { i, name ->
                                    val day = listOf(1, 2, 4, 5, 6, 1, 5)[i]
                                    val meetings =
                                        if (i == 1)
                                            listOf(
                                                Meeting(
                                                    id = "early",
                                                    weekday = day,
                                                    startPeriod = 1,
                                                    endPeriod = 2,
                                                ),
                                                Meeting(
                                                    id = "later",
                                                    weekday = day,
                                                    startPeriod = 3,
                                                    endPeriod = 3,
                                                ),
                                            )
                                        else
                                            listOf(
                                                Meeting(
                                                    weekday = day,
                                                    startPeriod = if (i > 4) 6 else 1,
                                                    endPeriod = if (i > 4) 7 else 2,
                                                )
                                            )
                                    Course(
                                        id = "quiet-$i",
                                        termId = "t",
                                        name = name,
                                        location = "教学楼 A30${i+1}",
                                        meetings = meetings,
                                    )
                                },
                        ),
                    settings = AppSettings(theme = "dark"),
                )
            )
        }
        ui.waitForIdle()
        ui.onNodeWithText("设置").performClick()
        picture("quiet-settings-dark.png")
        settingsItem("课表管理")
        val list = ui.onNodeWithTag("settings-课表管理")
        list.performScrollToNode(hasText("每周起始日"))
        ui.onNodeWithText("每周起始日").performClick()
        ui.onNodeWithTag("wheel-picker")
            .assert(
                SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                    androidx.compose.ui.semantics.ProgressBarRangeInfo(0f, 0f..6f, 5),
                )
            )
        ui.onNode(isDialog()).captureToImage().asAndroidBitmap().let { image ->
            File(System.getProperty("qingluan.screenshots"), "quiet-weekday-dialog.png")
                .outputStream()
                .use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(2f)
        }
        ui.onNodeWithText("确定").performClick()
        ui.waitUntil(10000) {
            ui.waitForIdle()
            repo.state.value!!.terms.single().firstDayOfWeek == 3
        }
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("课表").performClick()
        // Return to Monday for the supplied screenshot's geometry.
        runBlocking {
            repo.change { it.copy(terms = it.terms.map { t -> t.copy(firstDayOfWeek = 1) }) }
        }
        ui.waitForIdle()
        val course = repo.state.value!!.courses.first { it.id == "quiet-1" }
        val date = ScheduleEngine.monday(LocalDate.now()).plusDays(1)
        val raw = ScheduleEngine.onDate(repo.state.value!!, "t", date)
        val key = raw.first { it.course.id == course.id }.key
        ui.onAllNodesWithTag("course-block-$key").onFirst().assertHeightIsEqualTo((64 * 3 - 4).dp)
        picture("quiet-week-dark.png")
        val visible =
            SemanticsMatcher("visible") {
                it.boundsInRoot.center.x in 1f..410f && it.boundsInRoot.center.y in 80f..800f
            }
        ui.onAllNodesWithText("离散数学").filterToOne(visible).performClick()
        ui.onNodeWithText("上课时段").assertExists()
        ui.onNodeWithText("上课时段").performSemanticsAction(SemanticsActions.OnClick) { it() }
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(1f)
        }
        ui.onNodeWithText("确定").performClick()
        ui.onAllNodesWithText("第 3 节", substring = true).onFirst().assertExists()
    }

    @Test
    fun compactWheelAndSettingsNavigationWork() {
        ui.onNodeWithText("设置").performClick()
        settingsItem("课表管理")
        ui.onNodeWithText("总周数").performClick()
        val bounds = ui.onNodeWithTag("wheel-picker").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height <= 150f)
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(21f)
        }
        ui.onNodeWithText("确定").performClick()
        ui.waitUntil(10000) { repo.state.value!!.terms.single().weeks == 22 }
        picture("liquid-settings.png")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("课表").performClick()
        ui.onNodeWithTag("week-selector").performClick()
        ui.onNodeWithTag("wheel-picker").assertExists()
        ui.onNodeWithText("取消").performClick()
        picture("liquid-week.png")
    }

    @Test
    fun darkPaletteAndCourseRenderingRemainStableAcrossTabs() {
        val names = listOf("高等数学（微积分）", "大学英语", "数据结构与算法", "计算机组成原理", "线性代数", "物理实验")
        runBlocking {
            repo.change { snapshot ->
                snapshot.copy(
                    courses =
                        names.mapIndexed { i, name ->
                            Course(
                                id = "polish-$i",
                                termId = "t",
                                name = name,
                                location = "理科楼 A30${i+1}",
                                color = com.qingluan.schedule.model.courseColors[i],
                                meetings =
                                    listOf(
                                        Meeting(
                                            id = "m-$i",
                                            weekday = i + 1,
                                            startPeriod = 1 + i % 3,
                                            endPeriod = 2 + i % 3,
                                        )
                                    ),
                            )
                        }
                )
            }
            repo.updateSettings { it.copy(theme = "dark") }
        }
        ui.waitForIdle()
        ui.onNodeWithText("设置").performClick()
        ui.onNodeWithText("通用设置").assertDoesNotExist()
        picture("polish-settings-dark.png")
        settingsItem("个性化")
        ui.onNodeWithText("配色").performClick()
        ui.onNodeWithTag("palette-iris").performClick()
        ui.waitUntil(10000) {
            ui.waitForIdle()
            runBlocking { repo.currentSettings() }.palette == "iris"
        }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("鸢尾").assertExists()
        ui.onNodeWithContentDescription("返回").performClick()
        for (label in listOf("课表", "设置", "今日", "课表")) {
            ui.onNodeWithText(label).performClick()
            ui.waitForIdle()
        }
        picture("polish-week-dark.png")
        ui.onAllNodesWithText(names[0]).onFirst().assertExists()
        val image = ui.onRoot().captureToImage().asAndroidBitmap()
        val pixel = image.getPixel(2, image.height / 2)
        assertTrue("dark background must stay dark", android.graphics.Color.red(pixel) < 60)
        runBlocking { repo.updateSettings { it.copy(theme = "light", palette = "jade") } }
        ui.waitForIdle()
        picture("polish-week-light.png")
        runBlocking { repo.updateSettings { it.copy(fontScale = 1.5f, rowHeight = 48) } }
        ui.waitForIdle()
        ui.onAllNodesWithText(names[0]).onFirst().assertExists()
    }

    @Test
    fun newTableOpensEmptyEditableGrid() {
        runBlocking { repo.restore(Backup()) }
        ui.onNodeWithText("设置").performClick()
        settingsItem("课表列表")
        ui.onNodeWithContentDescription("新建课表").performClick()
        ui.onNodeWithText("课表名称").performTextReplacement("网格课表")
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10000) { repo.state.value!!.terms.any { it.name == "网格课表" } }
        ui.onNodeWithText("编辑课表").assertExists()
        assertTrue(repo.state.value!!.courses.isEmpty())
        ui.onNodeWithContentDescription("${LocalDate.now()} 第 1 节，添加课程").performClick()
        ui.onNodeWithText("课程名称 *").performTextInput("网络技术")
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10000) { repo.state.value!!.courses.size == 1 }
        val visible =
            SemanticsMatcher("visible grid cell") {
                it.boundsInRoot.center.x in 1f..410f && it.boundsInRoot.center.y in 80f..800f
            }
        ui.onAllNodesWithText("网络技术").filterToOne(visible).performClick()
        ui.onNodeWithText("编辑课程").assertExists()
        ui.onNodeWithText("课程名称 *").performTextReplacement("修订课程")
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10000) { repo.state.value!!.courses.single().name == "修订课程" }
    }

    @Test
    fun temporaryAdjustmentUpdatesGridAndCanUndo() {
        ui.onNodeWithText("设置").performClick()
        settingsItem("临时调整")
        wheel("开始", 2)
        wheel("结束", 1)
        val list = ui.onNodeWithTag("temporary-adjustment")
        list.performScrollToNode(hasText("应用到课表"))
        ui.onNodeWithText("应用到课表").performClick()
        ui.waitUntil(10000) { repo.state.value!!.exceptions.size == 1 }
        assertEquals(
            3,
            ScheduleEngine.onDate(repo.state.value!!, "t", LocalDate.now()).single().startPeriod,
        )
        val undo = "撤销 测试课程 的临时调整"
        list.performScrollToNode(hasContentDescription(undo))
        ui.onNodeWithContentDescription(undo).performClick()
        ui.waitUntil(10000) { repo.state.value!!.exceptions.isEmpty() }
        assertEquals(
            1,
            ScheduleEngine.onDate(repo.state.value!!, "t", LocalDate.now()).single().startPeriod,
        )
    }

    @Test
    fun qrSharingAndNativeRefractionShaderWork() {
        val document = TimetableDocument.from(repo.state.value!!, "t")
        val bytes = ExchangeFiles.export(document, ExchangeFormat.QR, 1)
        assertEquals(document, ExchangeFiles.decode(bytes).documents.single())
        val shader = RuntimeShader(EDGE_SHADER)
        shader.setFloatUniform("resolution", 200f, 80f)
        shader.setFloatUniform("radius", 30f)
        shader.setFloatUniform("strength", 4f)
        assertNotNull(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"))
        var uri: android.net.Uri? = null
        val vm = ViewModelProvider(ui.activity)[AppViewModel::class.java]
        ui.runOnUiThread { vm.shareDocument(document, ExchangeFormat.JSON, 1) { uri = it } }
        ui.waitUntil(10000) {
            ui.waitForIdle()
            uri != null || vm.failure.value != null
        }
        assertTrue("分享失败：${vm.failure.value}", uri != null)
        val text =
            ui.activity.contentResolver.openInputStream(uri!!)!!.bufferedReader().use {
                it.readText()
            }
        assertEquals(document, TimetableDocument.decode(text))
        val originalIntent = ui.activity.intent
        ui.runOnUiThread {
            val method =
                ui.activity.javaClass.getDeclaredMethod(
                    "onNewIntent",
                    android.content.Intent::class.java,
                )
            method.isAccessible = true
            method.invoke(
                ui.activity,
                android.content.Intent(android.content.Intent.ACTION_VIEW).setData(uri),
            )
        }
        ui.waitUntil(10000) {
            ui.waitForIdle()
            ui.onAllNodesWithText("导入预览").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("导入 1 门").performClick()
        ui.waitUntil(10000) {
            ui.waitForIdle()
            repo.state.value!!.terms.size == 2
        }
        ui.onNodeWithText("编辑课表").assertExists()
        assertEquals(2, repo.state.value!!.courses.size)
        ui.runOnUiThread { ui.activity.intent = originalIntent }
    }
}
