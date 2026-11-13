package com.qingluan.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.data.AppSettings
import com.qingluan.schedule.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimeFieldLayoutTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun batchTimesChangeDraftAndPersistOnlyAfterSaving() {
        val table = com.qingluan.schedule.model.Timetable(id = "p")
        var saved: com.qingluan.schedule.model.Timetable? = null
        ui.setContent {
            QingluanTheme(AppSettings(theme = "dark")) {
                TimetableEditor(
                    com.qingluan.schedule.model.Snapshot(timetables = listOf(table)),
                    table,
                    false,
                    {},
                    { saved = it },
                )
            }
        }
        ui.onNodeWithText("批量设置时间").performClick()
        ui.onNodeWithText("每节课时长").performClick()
        ui.onNodeWithTag("wheel-picker").performSemanticsAction(SemanticsActions.SetProgress) {
            it(49f)
        }
        ui.onNodeWithText("确定").performClick()
        ui.onNodeWithText("应用至全部节次").performClick()
        ui.onNodeWithText("08:50").assertIsDisplayed()
        ui.onNodeWithText("09:00").assertIsDisplayed()
        assertNull(saved)
        ui.onNodeWithText("保存").performClick()
        assertEquals(com.qingluan.schedule.model.Period(480, 530), saved!!.periods.first())
        assertEquals(com.qingluan.schedule.model.Period(540, 590), saved!!.periods[1])
    }

    @Test
    fun compactPeriodRangesKeepLabelsAndValuesOnOneLine() {
        ui.setContent {
            QingluanTheme(AppSettings(theme = "dark")) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    Surface {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 34.dp)) {
                            PeriodRangeFields(3, 24, 24) { _, _ -> }
                        }
                    }
                }
            }
        }
        val image = ui.onRoot().captureToImage().asAndroidBitmap()
        java.io
            .File(System.getProperty("qingluan.screenshots"), "compact-period-selectors.png")
            .outputStream()
            .use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        listOf("开始", "结束", "3节", "24节").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            ui.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) {
                it(layouts)
            }
            val layout = layouts.single()
            assertEquals(1, layout.lineCount)
            assertFalse(
                "$label ${layout.size} / ${layout.layoutInput.style.fontSize} must remain readable",
                layout.isLineEllipsized(0),
            )
            assertTrue(layout.getLineRight(0) <= layout.size.width + .5f)
        }
    }

    @Test
    fun narrowTimeFieldsAlignAndSave() {
        var saved = -1
        ui.setContent {
            QingluanTheme(AppSettings(theme = "dark")) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Surface {
                        Row(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TimeField("开始", 360, { saved = it }, Modifier.weight(1f))
                            TimeField("结束", 395, {}, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        val start = ui.onNodeWithTag("time-field-开始").fetchSemanticsNode().boundsInRoot
        val end = ui.onNodeWithTag("time-field-结束").fetchSemanticsNode().boundsInRoot
        assertEquals(start.top, end.top, .1f)
        assertEquals(start.bottom, end.bottom, .1f)
        val image = ui.onRoot().captureToImage().asAndroidBitmap()
        java.io
            .File(System.getProperty("qingluan.screenshots"), "time-fields-aligned.png")
            .outputStream()
            .use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        listOf("开始", "结束", "06:00", "06:35").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            ui.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) {
                it(layouts)
            }
            assertEquals(1, layouts.single().lineCount)
            val layout = layouts.single()
            assertTrue("$label glyphs clipped", layout.getLineRight(0) <= layout.size.width + .5f)
            assertTrue("$label height clipped", layout.getLineBottom(0) <= layout.size.height + .5f)
        }
        ui.onNodeWithTag("time-field-开始").performClick()
        ui.onNodeWithText("确定").performClick()
        assertEquals(360, saved)
    }
}
