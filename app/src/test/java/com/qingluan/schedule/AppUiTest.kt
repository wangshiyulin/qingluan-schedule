package com.qingluan.schedule

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (ui.activity.application as ScheduleApplication).repository

    private fun openMyItem(label: String) {
        val list = ui.onNodeWithTag("my-settings")
        list.performScrollToNode(hasText(label))
        val target = ui.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center.y
        val center = list.fetchSemanticsNode().boundsInRoot.center.y
        if (target > center)
            list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
                it(0f, target - center)
            }
        ui.onNodeWithText(label).performClick()
    }

    @Before
    fun reset() {
        runBlocking {
            repo.awaitReady()
            repo.restore(Backup())
        }
        ui.waitForIdle()
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        val file = File(System.getProperty("qingluan.screenshots", "/tmp/qingluan-previews"), name)
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun rotationKeepsCourseDraft() {
        val term =
            Term(
                id = "t",
                name = "测试学期",
                startDate = LocalDate.now().toString(),
                timetableId = "default",
            )
        runBlocking { repo.change { it.copy(activeTermId = "t", terms = listOf(term)) } }
        ui.onNodeWithText("设置").performClick()
        openMyItem("课程管理")
        ui.onNodeWithContentDescription("添加课程").performClick()
        ui.onNodeWithText("课程名称 *").performTextInput("旋转后仍保留")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("旋转后仍保留").assertExists()
    }

    @Test
    fun longCourseDetailsRemainScrollable() {
        val today = LocalDate.now()
        val term =
            Term(
                id = "t",
                name = "长备注测试",
                startDate = ScheduleEngine.monday(today).toString(),
                timetableId = "default",
            )
        runBlocking {
            repo.change {
                it.copy(
                    activeTermId = "t",
                    terms = listOf(term),
                    courses =
                        listOf(
                            Course(
                                id = "long",
                                termId = "t",
                                name = "长备注课程",
                                note = "课堂说明与参考资料。".repeat(150),
                                meetings = listOf(Meeting(weekday = today.dayOfWeek.value)),
                            )
                        ),
                )
            }
        }
        ui.onAllNodesWithText("长备注课程").onFirst().performClick()
        ui.onNodeWithText("编辑整门课").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun populatedWeekAndSettingsRender() {
        val today = LocalDate.now()
        val monday = ScheduleEngine.monday(today)
        val term =
            Term(
                id = "t",
                name = "2026 秋季学期",
                startDate = monday.minusWeeks(5).toString(),
                weeks = 20,
                timetableId = "default",
            )
        runBlocking {
            repo.change { s ->
                s.copy(
                    activeTermId = "t",
                    terms = listOf(term),
                    courses =
                        listOf(
                            Course(
                                id = "a",
                                termId = "t",
                                name = "高等数学",
                                location = "理科楼 A302",
                                teacher = "陈老师",
                                meetings = listOf(Meeting(id = "m1", weekday = 1)),
                            ),
                            Course(
                                id = "b",
                                termId = "t",
                                name = "大学英语",
                                location = "文科楼 B201",
                                color = 0xFF4D79CC,
                                meetings =
                                    listOf(
                                        Meeting(
                                            id = "m2",
                                            weekday = 3,
                                            startPeriod = 3,
                                            endPeriod = 4,
                                        )
                                    ),
                            ),
                            Course(
                                id = "c",
                                termId = "t",
                                name = "程序设计",
                                location = "实验楼 406",
                                color = 0xFF9565B8,
                                meetings =
                                    listOf(
                                        Meeting(
                                            id = "m3",
                                            weekday = today.dayOfWeek.value,
                                            startPeriod = 5,
                                            endPeriod = 6,
                                        )
                                    ),
                            ),
                        ),
                )
            }
        }
        screenshot("today-general.png")
        ui.onNodeWithText("课表").performClick()
        ui.onNodeWithTag("week-pager").assertExists()
        ui.onAllNodesWithText("高等数学", useUnmergedTree = true).onFirst().assertExists()
        screenshot("week-general.png")
        ui.onNodeWithText("设置").performClick()
        screenshot("my-general.png")
        openMyItem("个性化")
        screenshot("personalization-light.png")
        runBlocking { repo.updateSettings { it.copy(theme = "dark") } }
        ui.waitUntil(10000) { ui.onRoot().captureToImage().toPixelMap()[0, 0].red < .2f }
        screenshot("personalization-dark.png")
    }
}
