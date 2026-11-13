package com.qingluan.schedule

import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import com.qingluan.schedule.data.AppSettings
import com.qingluan.schedule.model.*
import com.qingluan.schedule.ui.*
import java.io.File
import java.time.LocalDateTime
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuietTodayTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun completedLessonsAreStruckThroughButRemainClickable() {
        val now = LocalDateTime.of(2026, 11, 13, 13, 0)
        val time =
            Timetable(
                id = "p",
                periods =
                    listOf(Period(480, 525), Period(600, 645), Period(780, 825), Period(900, 945)),
            )
        val term = Term(id = "t", startDate = "2026-11-09", timetableId = "p")
        val names = listOf("已结束课程", "接下来上课", "下午课程")
        val courses = names.mapIndexed { i, name ->
            Course(
                id = "$i",
                termId = "t",
                name = name,
                location = "教学楼 A30${i+1}",
                meetings =
                    listOf(
                        Meeting(
                            weekday = 5,
                            startPeriod = if (i == 0) 1 else i + 2,
                            endPeriod = if (i == 0) 1 else i + 2,
                        )
                    ),
            )
        }
        val s =
            Snapshot(
                activeTermId = "t",
                terms = listOf(term),
                timetables = listOf(time),
                courses = courses,
            )
        var tapped = false
        ui.setContent {
            QingluanTheme(AppSettings(theme = "dark")) {
                Surface { TodayScreen(s, now, { tapped = true }) }
            }
        }
        ui.onNodeWithText(names[0], useUnmergedTree = true).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) { action ->
            val results = mutableListOf<TextLayoutResult>()
            action(results)
            assertEquals(
                TextDecoration.LineThrough,
                results.single().layoutInput.style.textDecoration,
            )
        }
        ui.onNodeWithText("已结束").assertDoesNotExist()
        ui.onNodeWithText("正在上课").assertDoesNotExist()
        ui.onNodeWithText("未开始").assertDoesNotExist()
        ui.onNodeWithText(names[0]).performClick()
        assertTrue(tapped)
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        File(System.getProperty("qingluan.screenshots"), "glass-return-today-dark.png")
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
