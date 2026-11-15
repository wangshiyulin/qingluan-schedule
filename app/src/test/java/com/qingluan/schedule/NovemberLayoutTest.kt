package com.qingluan.schedule

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
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
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NovemberLayoutTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (ui.activity.application as ScheduleApplication).repository

    @Before
    fun seed() {
        runBlocking {
            repo.awaitReady()
            val tomorrow = LocalDate.now().plusDays(1)
            val term =
                Term(
                    id = "term",
                    startDate = ScheduleEngine.monday(LocalDate.now()).toString(),
                    weeks = 3,
                    timetableId = "default",
                )
            repo.restore(
                Backup(
                    data =
                        Snapshot(
                            activeTermId = term.id,
                            terms = listOf(term),
                            courses =
                                listOf(
                                    Course(
                                        id = "c",
                                        termId = term.id,
                                        name = "数学",
                                        teacher = "张老师",
                                        location = "A301",
                                        meetings =
                                            listOf(
                                                Meeting(
                                                    weekday = tomorrow.dayOfWeek.value,
                                                    weeks = (1..3).toSet(),
                                                )
                                            ),
                                    )
                                ),
                        ),
                    settings = AppSettings(theme = "dark"),
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
    fun dividerResizesPreviewAndKeepsItsHeightWhileChangingOptions() {
        root()
        open("个性化")
        ui.onNodeWithText("个性化课表").performClick()
        val before = ui.onNodeWithTag("appearance-preview").fetchSemanticsNode().boundsInRoot.height
        ui.onNodeWithTag("preview-divider").performTouchInput {
            swipe(center, center + Offset(0f, 120f), 300)
        }
        val after = ui.onNodeWithTag("appearance-preview").fetchSemanticsNode().boundsInRoot.height
        assertTrue("divider did not drag", after > before + 70f)
        ui.onNodeWithTag("schedule-appearance").performScrollToNode(hasText("显示教师"))
        ui.onNodeWithText("显示教师").performClick()
        ui.waitUntil(10000) { !repo.settings.value.showTeacher }
        assertEquals(
            after,
            ui.onNodeWithTag("appearance-preview").fetchSemanticsNode().boundsInRoot.height,
            1f,
        )
        picture("nov15-resizable-preview.png")
    }

    @Test
    fun schoolCategoriesAndAcademicControlsAreCompactAndAligned() {
        root()
        open("导入与备份")
        ui.onNodeWithText("教务处导入").performClick()
        ui.onNodeWithText("教务网址").assertDoesNotExist()
        ui.onNodeWithText("本科/专科").assertExists()
        picture("nov15-schools.png")
        ui.onNodeWithText("研究生").performClick()
        ui.onNodeWithText("搜索学校").performTextInput("中国地质")
        ui.onNodeWithText("中国地质大学(武汉)").assertExists()
        picture("nov15-postgraduate.png")
        ui.onNodeWithText("通用").performClick()
        ui.onNodeWithText("搜索学校").assertDoesNotExist()
        ui.onNodeWithText("教务网址").performTextInput("https://school.example.edu/student")
        assertSameHeight("academic-address", "academic-open")
        picture("nov15-general-import.png")
        ui.onNodeWithText("打开").performClick()
        ui.onNodeWithTag("academic-webview").assertExists()
        assertSameHeight("academic-address", "academic-open")
        assertSameHeight("academic-mode", "academic-extract")
        picture("nov15-browser-controls.png")
        back()
        ui.onNodeWithText("教务网址").assertExists()
    }

    private fun assertSameHeight(a: String, b: String) {
        assertEquals(
            ui.onNodeWithTag(a).fetchSemanticsNode().boundsInRoot.height,
            ui.onNodeWithTag(b).fetchSemanticsNode().boundsInRoot.height,
            1f,
        )
    }

    @Test
    fun reminderStatusIsAnAlignedRowAndShowsRealNextReminder() {
        root()
        open("课程提醒")
        ui.onNodeWithTag("settings-课程提醒").performScrollToNode(hasText("下一次提醒"))
        ui.waitUntil(10000) { ui.onAllNodesWithText("未开启").fetchSemanticsNodes().isNotEmpty() }
        val title = ui.onNodeWithText("下一次提醒").fetchSemanticsNode().boundsInRoot
        val status = ui.onNodeWithText("未开启").fetchSemanticsNode().boundsInRoot
        assertEquals(title.center.y, status.center.y, 1f)
        assertTrue(status.left > title.right)
        picture("nov15-reminder-off.png")
        back()
        Shadows.shadowOf(ui.activity.application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { repo.updateSettings { it.copy(reminders = true) } }
        open("课程提醒")
        ui.onNodeWithTag("settings-课程提醒").performScrollToNode(hasText("下一次提醒"))
        val expected = "${LocalDate.now().plusDays(1).toString().substring(5)} 07:50 · 数学"
        ui.waitUntil(10000) { ui.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        picture("nov15-reminder-next.png")
    }

    @Test
    fun backupListStillRestoresAfterCompactLayout() {
        runBlocking { repo.change { it.copy(courses = emptyList()) } }
        root()
        open("导入与备份")
        ui.onNodeWithTag("transfer-page").performScrollToNode(hasText("本地自动备份"))
        ui.onNodeWithText("本地自动备份").performClick()
        ui.onNodeWithText("撤销最近修改").performClick()
        ui.waitUntil(10000) { repo.state.value!!.courses.size == 1 }
        picture("nov15-compact-backups.png")
    }
}
