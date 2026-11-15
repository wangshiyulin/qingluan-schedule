package com.qingluan.schedule

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
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
class SettingsReorganizationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (ui.activity.application as ScheduleApplication).repository

    @Before
    fun reset() {
        runBlocking {
            repo.awaitReady()
            val term =
                Term(
                    id = "t",
                    name = "秋季课表",
                    startDate = ScheduleEngine.monday(LocalDate.now()).toString(),
                    timetableId = "default",
                )
            repo.restore(
                Backup(
                    data =
                        Snapshot(
                            activeTermId = "t",
                            terms = listOf(term, term.copy(id = "other", name = "另一张课表")),
                            courses =
                                listOf(
                                    Course(
                                        id = "c",
                                        termId = "t",
                                        name = "Python程序设计高级应用课程",
                                        teacher = "王老师",
                                        location = "仙林校区 B4-108 实验教室",
                                        meetings =
                                            listOf(
                                                Meeting(
                                                    id = "m",
                                                    weekday = LocalDate.now().dayOfWeek.value,
                                                )
                                            ),
                                    )
                                ),
                        )
                )
            )
        }
        ui.waitForIdle()
    }

    private fun root() {
        ui.onNodeWithText("设置").performClick()
    }

    private fun open(text: String) {
        ui.onNodeWithTag("my-settings").performScrollToNode(hasText(text))
        ui.onNodeWithText(text).performClick()
    }

    private fun back() {
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
    }

    private fun wheel(text: String, index: Int) {
        ui.onNodeWithText(text).performClick()
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(index.toFloat())
        }
        ui.onNodeWithText("确定").performClick()
    }

    private fun picture(name: String) {
        ui.waitForIdle()
        val b = ui.onRoot().captureToImage().asAndroidBitmap()
        val f = File(System.getProperty("qingluan.screenshots"), name)
        f.parentFile!!.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun settingsAndSelectableTablesHaveDirectRoutes() {
        root()
        ui.onNodeWithText("显示周末").assertExists()
        ui.onNodeWithText("显示非本周课程").assertExists()
        ui.onNodeWithText("课表管理").assertDoesNotExist()
        picture("organized-settings.png")
        open("课表列表")
        ui.onNodeWithText("当前课表").assertDoesNotExist()
        ui.onNodeWithTag("term-card-t").assertIsSelected()
        ui.onNodeWithTag("term-card-other").performClick()
        ui.waitUntil(10000) { repo.state.value!!.activeTermId == "other" }
        ui.onNodeWithTag("term-card-other").assertIsSelected()
        picture("selected-tables.png")
        ui.onNodeWithContentDescription("编辑课表 另一张课表").performClick()
        ui.onNodeWithText("开学日期").performClick()
        ui.onNodeWithTag("app-date-dialog").assertIsDisplayed()
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithText("当前周").performScrollTo().assertExists()
        ui.onNodeWithText("每周起始日").performScrollTo().assertExists()
        ui.onNodeWithText("保存").performClick()
        // The setup save enters the editable grid, keeping selection unchanged.
        ui.waitUntil(10000) { repo.state.value!!.terms.any { it.id == "other" } }
    }

    @Test
    fun freshLaunchUsesStartupPreference() {
        runBlocking { repo.updateSettings { it.copy(startupPage = 1) } }
        val intent =
            android.content.Intent(ui.activity.applicationContext, MainActivity::class.java)
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ui.waitUntil(10000) {
                ui.onAllNodesWithTag("week-pager").fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithTag("week-pager").assertIsDisplayed()
            scenario.onActivity { activity ->
                assertEquals(-1, activity.intent.getIntExtra("tab", -1))
            }
        }
    }

    @Test
    fun personalizationPersistsStartupAndScheduleOptions() {
        root()
        open("个性化")
        wheel("启动页面", 1)
        ui.waitUntil(10000) { runBlocking { repo.currentSettings().startupPage == 1 } }
        ui.onNodeWithText("个性化课表").performClick()
        ui.onNodeWithTag("schedule-appearance").performScrollToNode(hasText("显示教师"))
        ui.onNodeWithText("显示教师").performClick()
        ui.waitUntil(10000) { !repo.settings.value.showTeacher }
        ui.onNodeWithTag("schedule-appearance").performScrollToNode(hasText("恢复课表默认样式"))
        ui.onNodeWithText("恢复课表默认样式").performClick()
        ui.waitUntil(10000) { repo.settings.value.showTeacher }
        picture("schedule-appearance.png")
        assertEquals(1, runBlocking { repo.currentSettings().startupPage })
    }

    @Test
    fun unifiedTransferAndAcademicEntryDoNotExposeRemovedControls() {
        root()
        open("导入与备份")
        ui.onNodeWithText("导入课表").assertExists()
        ui.onNodeWithText("图片 / PDF 周次").assertDoesNotExist()
        picture("unified-transfer.png")
        ui.onNodeWithText("教务处导入").performClick()
        ui.onNodeWithText("搜索学校").assertExists()
        ui.onNodeWithText("教务网址").assertDoesNotExist()
        ui.onNodeWithText("搜索学校").performTextInput("南京中医药大学")
        ui.onAllNodesWithText("南京中医药大学").onLast().performClick()
        ui.onNodeWithText("子页面").assertDoesNotExist()
        ui.onNodeWithText("内嵌页面").assertDoesNotExist()
        ui.onNodeWithText("提取课表").assertExists()
        back()
        ui.onNodeWithText("搜索学校").assertExists()
    }

    @Test
    fun completeCourseTextFitsAndTimetableEditorIsCompact() {
        ui.onNodeWithText("课表").performClick()
        for (text in listOf("Python程序设计高级应用课程", "王老师", "仙林校区 B4-108 实验教室")) {
            ui.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(layouts))
                assertFalse(text, layouts.single().hasVisualOverflow)
            }
        }
        val left = ui.onNodeWithTag("period-time-0").fetchSemanticsNode().boundsInRoot
        val right = ui.onNodeWithTag("period-grid-0-0").fetchSemanticsNode().boundsInRoot
        assertEquals(left.bottom, right.bottom, .5f)
        picture("complete-course-text.png")
        root()
        open("课程管理")
        ui.onNodeWithText("搜索课程、教师、地点").assertDoesNotExist()
        back()
        open("作息管理")
        ui.onNodeWithText(repo.state.value!!.timetables.first().name).performClick()
        ui.onNodeWithText("批量设置时间").assertExists()
        ui.onNodeWithText("第 1 节").performClick()
        ui.onAllNodesWithText("开始").onLast().assertExists()
        ui.onAllNodesWithText("结束").onLast().assertExists()
        ui.onNodeWithText("取消").performClick()
        picture("compact-time-editor.png")
    }

    @Test
    fun academicRendererFailureOffersRetryWithoutLosingTimetable() {
        root()
        open("导入与备份")
        ui.onNodeWithText("教务处导入").performClick()
        ui.onNodeWithText("搜索学校").performTextInput("南京中医药大学")
        ui.onAllNodesWithText("南京中医药大学").onLast().performClick()
        fun find(view: android.view.View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup)
                for (i in 0 until view.childCount) find(view.getChildAt(i))?.let {
                    return it
                }
            return null
        }
        var previous: android.webkit.WebView? = null
        ui.runOnIdle {
            val web = find(ui.activity.window.decorView)!!
            previous = web
            val detail =
                object : android.webkit.RenderProcessGoneDetail() {
                    override fun didCrash() = true

                    override fun rendererPriorityAtExit() = 0
                }
            assertTrue(web.webViewClient.onRenderProcessGone(web, detail))
        }
        ui.onNodeWithText("网页进程已退出，请重试").assertIsDisplayed()
        ui.onNodeWithText("重试").performClick()
        ui.runOnIdle {
            val fresh = find(ui.activity.window.decorView)
            assertNotNull(fresh)
            assertNotSame(previous, fresh)
        }
        assertEquals(1, repo.state.value!!.courses.size)
    }

    @Test
    fun additionalStyleOptionsAndVectorAboutIconWork() {
        runBlocking {
            repo.updateSettings {
                it.copy(
                    timeColumnWidth = 64,
                    dayHeaderHeight = 72,
                    courseOuterPadding = 6,
                    courseInnerPadding = 12,
                    courseFontScale = 1.25f,
                    boldCourseNames = false,
                    showCourseBorders = false,
                    showCourseStartTime = true,
                    centerCourseVertically = true,
                )
            }
        }
        ui.onNodeWithText("课表").performClick()
        assertEquals(
            64f,
            ui.onNodeWithTag("period-time-0").fetchSemanticsNode().boundsInRoot.width,
            .5f,
        )
        assertEquals(
            72f,
            ui.onNodeWithTag("time-header").fetchSemanticsNode().boundsInRoot.height,
            .5f,
        )
        val key = ScheduleEngine.onDate(repo.state.value!!, "t", LocalDate.now()).single().key
        ui.onNode(
                hasText("08:00") and hasAnyAncestor(hasTestTag("course-block-$key")),
                useUnmergedTree = true,
            )
            .assertExists()
        for (text in listOf("Python程序设计高级应用课程", "王老师", "仙林校区 B4-108 实验教室")) {
            ui.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                action ->
                val values = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(values))
                assertFalse(text, values.single().hasVisualOverflow)
            }
        }
        picture("style101-course-layout.png")
        root()
        open("关于")
        ui.onNodeWithContentDescription("青鸾课程表图标").assertIsDisplayed()
        picture("style101-about-icon.png")
        val ctx = ui.activity
        for ((label, url) in
            listOf(
                "官网" to com.qingluan.schedule.ui.AboutLinks.website,
                "GitHub" to com.qingluan.schedule.ui.AboutLinks.github,
                "博客" to com.qingluan.schedule.ui.AboutLinks.blog,
                "检查更新" to com.qingluan.schedule.ui.AboutLinks.updates,
            )) {
            ui.onNodeWithTag("settings-关于").performScrollToNode(hasText(label))
            ui.onNodeWithText(label).performClick()
            assertEquals(url, org.robolectric.Shadows.shadowOf(ctx).nextStartedActivity.dataString)
        }
    }
}
