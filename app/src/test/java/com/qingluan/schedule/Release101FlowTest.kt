package com.qingluan.schedule

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import com.qingluan.schedule.system.SystemCoordinator
import com.qingluan.schedule.transfer.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Release101FlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (ui.activity.application as ScheduleApplication).repository

    @Before
    fun seed() {
        runBlocking {
            repo.awaitReady()
            val term =
                Term(
                    id = "release",
                    startDate = ScheduleEngine.monday(LocalDate.now()).toString(),
                    weeks = 3,
                    timetableId = "default",
                )
            val meeting =
                Meeting(id = "m", weekday = LocalDate.now().dayOfWeek.value, weeks = (1..3).toSet())
            val course =
                Course(
                    id = "math",
                    termId = term.id,
                    name = "数学",
                    teacher = "王老师",
                    location = "A301",
                    meetings = listOf(meeting),
                )
            repo.restore(
                Backup(
                    data =
                        Snapshot(
                            activeTermId = term.id,
                            terms = listOf(term),
                            timetables =
                                listOf(
                                    Timetable(id = "default"),
                                    Timetable(id = "other", name = "另一套作息"),
                                ),
                            courses = listOf(course, course.copy(id = "english", name = "英语")),
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

    private fun picture(name: String) {
        ui.waitForIdle()
        val file = File(System.getProperty("qingluan.screenshots"), name)
        file.parentFile!!.mkdirs()
        file.outputStream().use {
            ui.onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun screenshotsHaveActualIconActiveTimetableAndPinnedPreview() {
        runBlocking { repo.updateSettings { it.copy(theme="dark") } }
        root()
        open("关于")
        ui.onNodeWithTag("application-icon").assertIsDisplayed()
        ui.onNodeWithText("版本 v1.0.1").assertExists()
        picture("v101-about.png")
        back()
        open("作息管理")
        ui.onNodeWithTag("timetable-default").assertIsSelected()
        ui.onNodeWithTag("timetable-other").assertIsNotSelected()
        ui.onNodeWithText("当前使用 · 10 节 · 08:00–20:40").assertExists()
        picture("v101-active-timetable.png")
        back()
        open("个性化")
        ui.onNodeWithText("个性化课表").performClick()
        val before = ui.onNodeWithTag("appearance-preview").fetchSemanticsNode().boundsInRoot
        ui.onNodeWithTag("schedule-appearance").performScrollToNode(hasText("显示教师"))
        ui.onNodeWithText("显示教师").performClick()
        ui.waitUntil(10000) { !repo.settings.value.showTeacher }
        ui.waitUntil(10000) { ui.onAllNodesWithText("李老师").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("toggle-显示教师").assertIsOff()
        assertEquals(
            before,
            ui.onNodeWithTag("appearance-preview").fetchSemanticsNode().boundsInRoot,
        )
        picture("v101-pinned-preview.png")
    }

    @Test
    fun holidayAndConflictsReachRealSchedule() {
        root()
        open("课程冲突")
        ui.waitUntil(10000) { ui.onAllNodesWithText("3 处冲突").fetchSemanticsNodes().isNotEmpty() }
        picture("v101-conflicts.png")
        ui.onNodeWithText("${LocalDate.now()} · 第 1 周").performClick()
        ui.onNodeWithTag("week-pager").assertIsDisplayed()
        root()
        open("节假日与调休")
        ui.onNodeWithText("添加安排").performClick()
        ui.onNodeWithText("名称").performTextInput("放假")
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10000) { repo.state.value!!.calendarRules.isNotEmpty() }
        assertTrue(ScheduleEngine.onDate(repo.state.value!!, "release", LocalDate.now()).isEmpty())
        picture("v101-holiday.png")
        back()
        open("课程冲突")
        ui.waitUntil(10000) { ui.onAllNodesWithText("2 处冲突").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun backupsAndReminderHealthHaveWorkingEntries() {
        root()
        open("导入与备份")
        ui.onNodeWithTag("transfer-page").performScrollToNode(hasText("本地自动备份"))
        ui.onNodeWithText("本地自动备份").performClick()
        ui.onNodeWithText("保留份数").assertExists()
        runBlocking {
            repo.change { it.copy(courses = it.courses.filterNot { c -> c.id == "english" }) }
        }
        back()
        ui.onNodeWithTag("transfer-page").performScrollToNode(hasText("本地自动备份"))
        ui.onNodeWithText("本地自动备份").performClick()
        ui.onNodeWithText("撤销最近修改").performClick()
        ui.waitUntil(10000) { repo.state.value!!.courses.size == 2 }
        picture("v101-local-backups.png")
        back()
        back()
        open("课程提醒")
        ui.onNodeWithTag("settings-课程提醒").performScrollToNode(hasText("测试提醒"))
        ui.onNodeWithText("下一次提醒").assertExists()
        picture("v101-reminder-health.png")
        val ctx = ui.activity.application
        Shadows.shadowOf(ctx).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        SystemCoordinator.sendTestNotification(ctx)
        assertTrue(
            ctx.getSystemService(android.app.NotificationManager::class.java)
                .activeNotifications
                .any { it.tag == SystemCoordinator.TEST_TAG }
        )
    }

    @Test
    fun importAuditShowsMissingFieldsMismatchAndConflictBeforeWriting() {
        val before = repo.state.value!!
        val doc =
            TimetableDocument.from(before, "release").let { d ->
                d.copy(courses = d.courses.map { it.copy(teacher = "", location = "") })
            }
        ui.runOnIdle {
            ViewModelProvider(ui.activity)[AppViewModel::class.java].pendingImport.value =
                ImportResult(listOf(doc))
        }
        ui.onNodeWithText("导入核对").performClick()
        ui.onNodeWithText("原课表课程名称总数（可选）").performTextInput("5")
        ui.onNodeWithText("课程数量不符：原课表 5，已选 2").assertExists()
        ui.onNodeWithText("缺少教师 2 门 · 缺少教室 2 门 · 未识别 0 条").assertExists()
        picture("v101-import-audit.png")
        ui.onNodeWithText("完成").performClick()
        ui.onNodeWithText("导入 2 门").performClick()
        ui.onNodeWithText("仍然导入").performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("发现课程冲突").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before, repo.state.value)
        ui.onNodeWithText("返回核对").performClick()
        // Empty timetable documents remain importable and never crash the period calculation.
        ui.runOnIdle {
            ViewModelProvider(ui.activity)[AppViewModel::class.java].pendingImport.value =
                ImportResult(listOf(doc.copy(courses = emptyList())))
        }
        ui.onNodeWithText("导入 0 门").assertExists()
    }
}
