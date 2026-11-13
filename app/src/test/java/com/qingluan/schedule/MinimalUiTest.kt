package com.qingluan.schedule

import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
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
class MinimalUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (ui.activity.application as ScheduleApplication).repository

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

    @Before
    fun reset() {
        val today = LocalDate.now()
        runBlocking {
            repo.awaitReady()
            repo.restore(
                Backup(
                    data =
                        Snapshot(
                            activeTermId = "one",
                            terms =
                                listOf(
                                    Term(
                                        id = "one",
                                        name = "我的课表",
                                        startDate =
                                            ScheduleEngine.monday(today).minusWeeks(2).toString(),
                                        timetableId = "default",
                                    ),
                                    Term(
                                        id = "two",
                                        name = "第二课表",
                                        startDate =
                                            ScheduleEngine.monday(today).minusWeeks(2).toString(),
                                        timetableId = "default",
                                    ),
                                ),
                            courses =
                                listOf(
                                    Course(
                                        id = "a",
                                        termId = "one",
                                        name = "今天的课程",
                                        meetings = listOf(Meeting(weekday = today.dayOfWeek.value)),
                                    )
                                ),
                        )
                )
            )
        }
        ui.waitForIdle()
    }

    @Test
    fun todayContainsOnlyTodaySchedule() {
        ui.onNodeWithText("今天的课程").assertExists()
        ui.onNodeWithText("把今天，安排得刚刚好").assertDoesNotExist()
        ui.onNodeWithText("下一节课").assertDoesNotExist()
        ui.onNodeWithText("今日安排").assertDoesNotExist()
    }

    @Test
    fun toolbarOpensScheduleSwitcherAndSelectsTerm() {
        ui.onNodeWithContentDescription("切换课表").assertDoesNotExist()
        ui.onNodeWithText("课表").performClick()
        ui.onNodeWithText("周次").assertDoesNotExist()
        ui.onNodeWithText("本周").assertDoesNotExist()
        ui.onNodeWithContentDescription("切换课表").performClick()
        ui.onNode(isDialog()).assertExists()
        ui.onNodeWithText("第二课表").performClick()
        ui.waitUntil(10000) { repo.state.value?.activeTermId == "two" }
        ui.onNodeWithText("今天的课程").assertDoesNotExist()
    }

    @Test
    fun toggleChangesOnceWhenWholeCardIsTapped() {
        ui.onNodeWithText("设置", useUnmergedTree = true).performClick()
        openMyItem("课表管理")
        ui.onNodeWithTag("toggle-显示周末").assertIsOn().performClick()
        ui.waitUntil(10000) { !runBlocking { repo.currentSettings() }.showWeekend }
        ui.onNodeWithTag("toggle-显示周末").assertIsOff().performClick()
        ui.waitUntil(10000) { runBlocking { repo.currentSettings() }.showWeekend }
        ui.onNodeWithTag("toggle-显示周末").assertIsOn()
    }

    @Test
    fun managementAndSettingsAreCompactHubs() {
        ui.onNodeWithText("管理").assertDoesNotExist()
        ui.onNodeWithText("设置").performClick()
        ui.onNodeWithText("通用设置").assertDoesNotExist()
        openMyItem("课表管理")
        ui.onNodeWithTag("toggle-显示周末").performClick()
        ui.waitUntil(10000) { !runBlocking { repo.currentSettings() }.showWeekend }
        openMyItem("备份")
        ui.onNodeWithTag("transfer-page").assertExists()
        ui.onNodeWithText("导出单张课表").assertExists()
        ui.onNodeWithContentDescription("返回").performClick()
        runBlocking {
            com.qingluan.schedule.system.SystemCoordinator.refresh(
                ui.activity,
                checkNotNull(repo.state.value),
                repo.currentSettings(),
            )
        }
        Assert.assertTrue(
            org.robolectric.Shadows.shadowOf(
                    ui.activity.getSystemService(android.app.AlarmManager::class.java)
                )
                .scheduledAlarms
                .isEmpty()
        )
    }

    @Test
    fun appearanceCardsSelectValuesAndSurviveRecreation() {
        ui.onNodeWithText("设置", useUnmergedTree = true).performClick()
        openMyItem("个性化")
        ui.onNodeWithText("字号").performClick()
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(3f)
        }
        ui.onNodeWithText("确定").performClick()
        ui.waitUntil(10000) { runBlocking { repo.currentSettings() }.fontScale == 1.2f }
        ui.onNodeWithText("配色").performClick()
        ui.onNodeWithTag("palette-system").performClick()
        ui.waitUntil(10000) { runBlocking { repo.currentSettings() }.dynamicColor }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("120%").assertExists()
        ui.onNodeWithText("系统动态").assertExists()
        ui.onNodeWithText("节次高度").performClick()
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(4f)
        }
        ui.onNodeWithText("确定").performClick()
        ui.waitUntil(10000) { runBlocking { repo.currentSettings() }.rowHeight == 80 }
    }

    @Test
    fun swipeChangesWeekAndKeepsPositionAcrossTabs() {
        ui.onNodeWithText("课表").performClick()
        fun week(value: Int) =
            ui.onNodeWithTag("week-pager")
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "第 $value 周")
                )
        week(3)
        ui.onNodeWithTag("week-pager").performTouchInput { swipeLeft() }
        week(4)
        ui.onNodeWithTag("week-pager").performTouchInput { swipeRight() }
        week(3)
        ui.onNodeWithText("设置").performClick()
        ui.onNodeWithText("课表").performClick()
        week(3)
    }

    @Test
    fun predictiveBackCancelKeepsPageAndCommitReturnsToParent() {
        ui.onNodeWithText("设置").performClick()
        openMyItem("作息管理")
        ui.runOnUiThread {
            ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 400f, 0f, 0)
            )
        }
        ui.runOnUiThread {
            ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(80f, 400f, .5f, 0)
            )
        }
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        ui.onNodeWithText("作息管理").assertExists()
        ui.runOnUiThread {
            ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 400f, 0f, 0)
            )
        }
        ui.runOnUiThread {
            ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(120f, 400f, .8f, 0)
            )
        }
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntil(10000) {
            ui.onAllNodesWithTag("my-settings").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("my-settings").assertExists()
    }
}
