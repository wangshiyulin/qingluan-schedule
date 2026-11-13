package com.qingluan.schedule

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import com.qingluan.schedule.ui.AboutLinks
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneralUiTest {
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
                                        name = "我的课表",
                                        startDate =
                                            ScheduleEngine.monday(today).minusWeeks(2).toString(),
                                        timetableId = "default",
                                    )
                                ),
                            courses =
                                listOf(
                                    Course(
                                        id = "off",
                                        termId = "t",
                                        name = "非本周课程",
                                        meetings =
                                            listOf(
                                                Meeting(
                                                    weekday = today.dayOfWeek.value,
                                                    weeks = setOf(1),
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

    private fun openMyItem(label: String) {
        val managed = label in listOf("作息管理", "开学日期", "总周数", "当前周", "每周起始日", "显示周末", "显示非本周课程")
        if (managed && ui.onAllNodesWithTag("my-settings").fetchSemanticsNodes().isNotEmpty())
            ui.onNodeWithText("课表管理").performClick()
        if (!managed && ui.onAllNodesWithTag("settings-课表管理").fetchSemanticsNodes().isNotEmpty())
            ui.onNodeWithContentDescription("返回").performClick()
        val list = ui.onNodeWithTag(if (managed) "settings-课表管理" else "my-settings")
        list.performScrollToNode(hasText(label))
        val target = ui.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center.y
        val center = list.fetchSemanticsNode().boundsInRoot.center.y
        if (target > center)
            list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
                it(0f, target - center)
            }
        ui.onNodeWithText(label).performClick()
    }

    private fun selectWheel(index: Int) {
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress
        ) {
            it(index.toFloat())
        }
        ui.onNodeWithText("确定").performClick()
    }

    private fun screenshot(tag: String, name: String) {
        ui.waitForIdle()
        val image = ui.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val file = File(System.getProperty("qingluan.screenshots"), name)
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun generalOptionsAndCenteredWeekSelectorWork() {
        ui.onNodeWithText("第 3 周").assertExists()
        ui.onNodeWithText("设置").performClick()
        openMyItem("开学日期")
        ui.onNodeWithTag("app-date-dialog").assertExists()
        screenshot("app-date-dialog", "calendar-general.png")
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithText("每周起始日").performClick()
        selectWheel(6)
        ui.waitUntil(10000) { repo.state.value!!.terms.single().firstDayOfWeek == 7 }
        ui.onNodeWithText("当前周").performClick()
        selectWheel(3)
        ui.waitUntil(10000) {
            ScheduleEngine.week(repo.state.value!!.terms.single(), LocalDate.now()) == 4
        }
        if (ui.onAllNodesWithTag("settings-课表管理").fetchSemanticsNodes().isNotEmpty())
            ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("课表").performClick()
        ui.onNodeWithTag("week-selector").performClick()
        selectWheel(4)
        ui.waitUntil(10000) {
            ui.onNodeWithTag("week-selector")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .any { it.text == "第 5 周" }
        }
        ui.onNodeWithText("设置").performClick()
        openMyItem("个性化")
        ui.onNodeWithTag("toggle-触觉反馈").assertIsOff().performClick()
        ui.waitUntil(10000) { runBlocking { repo.currentSettings() }.hapticsEnabled }
        ui.onNodeWithTag("toggle-界面动效").assertDoesNotExist()
        assertTrue(runBlocking { repo.currentSettings() }.animationsEnabled)
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10000) {
            ui.onAllNodesWithTag("toggle-触觉反馈").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("toggle-触觉反馈").assertIsOn()
        ui.onNodeWithTag("toggle-界面动效").assertDoesNotExist()
    }

    @Test
    fun aboutLinksAndBackupOpenDirectly() {
        ui.onNodeWithText("设置").performClick()
        openMyItem("关于")
        ui.onNodeWithText("青鸾课程表").assertExists()
        ui.onNodeWithText("版本 v1.0.0").assertExists()
        screenshot("settings-关于", "about-general.png")
        listOf(
                "官网" to AboutLinks.website,
                "GitHub" to AboutLinks.github,
                "博客" to AboutLinks.blog,
                "检查更新" to AboutLinks.updates,
            )
            .forEach { (label, url) ->
                ui.onNodeWithText(label).performClick()
                assertEquals(url, shadowOf(ui.activity).nextStartedActivity.dataString)
            }
        ui.onNodeWithContentDescription("返回").performClick()
        openMyItem("备份")
        ui.onNodeWithTag("transfer-page").assertExists()
        ui.onNodeWithText("导出单张课表").assertExists()
    }

    @Test
    fun nonCurrentCoursePreviewOpensCourseEditor() {
        ui.onNodeWithText("设置").performClick()
        openMyItem("课表管理")
        ui.onNodeWithTag("toggle-显示非本周课程").performClick()
        ui.waitUntil(10000) { runBlocking { repo.currentSettings() }.showNonCurrentWeek }
        if (ui.onAllNodesWithTag("settings-课表管理").fetchSemanticsNodes().isNotEmpty())
            ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("课表").performClick()
        val current =
            SemanticsMatcher("visible course") {
                it.boundsInRoot.center.x in 1f..410f && it.boundsInRoot.center.y in 80f..800f
            }
        ui.onAllNodesWithText("非本周课程").filterToOne(current).performClick()
        ui.onNodeWithText("编辑课程").assertExists()
        assertTrue(repo.state.value!!.exceptions.isEmpty())
        assertTrue(ScheduleEngine.onDate(repo.state.value!!, "t", LocalDate.now()).isEmpty())
    }
}
