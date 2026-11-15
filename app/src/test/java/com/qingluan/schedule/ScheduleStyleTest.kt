package com.qingluan.schedule

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleStyleTest {
    @Test
    fun resettingAllScheduleFieldsPreservesOtherPreferences() {
        val original =
            AppSettings(
                theme = "dark",
                startupPage = 2,
                reminders = true,
                fontScale = 1.2f,
                rowHeight = 180,
                courseFontScale = 1.5f,
                courseCornerRadius = 24,
                courseOpacity = .3f,
                showGridLines = false,
                showSectionTime = false,
                showDates = false,
                showTeacher = false,
                showLocation = false,
                centerCourseText = true,
                timeColumnWidth = 72,
                dayHeaderHeight = 80,
                courseOuterPadding = 6,
                courseInnerPadding = 12,
                centerCourseVertically = true,
                boldCourseNames = false,
                showCourseBorders = false,
                showCourseStartTime = true,
            )
        assertEquals(
            AppSettings(theme = "dark", startupPage = 2, reminders = true, fontScale = 1.2f),
            original.resetScheduleAppearance(),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(Backup(settings = original)))
        assertEquals(original, restored.settings)
    }

    @Test
    fun geometrySettingsAreValidatedAndLegacyDefaultsStayUnchanged() {
        for (p in
            listOf(
                AppSettings(timeColumnWidth = 0),
                AppSettings(dayHeaderHeight = 1000),
                AppSettings(courseOuterPadding = -1),
                AppSettings(courseInnerPadding = 13),
            )) assertTrue(runCatching { BackupCodec.validate(Backup(settings = p)) }.isFailure)
        val old = BackupCodec.json.decodeFromString<AppSettings>("""{"theme":"light"}""")
        assertEquals(44, old.timeColumnWidth)
        assertEquals(50, old.dayHeaderHeight)
        assertFalse(old.showCourseStartTime)
    }

    @Test
    fun narrowColumnSpacingCannotConsumeTextArea() {
        for (width in listOf(1f, 8f, 16f, 24f, 52f, 120f)) {
            val p = courseSpacing(width, 6, 12, 20f)
            assertTrue(p.outer >= 0 && p.inner >= 0)
            assertTrue(2 * (p.outer + p.inner) <= width)
            assertTrue(p.textWidth(width) >= 1f)
        }
        assertEquals(CourseSpacing(2f, 4f), courseSpacing(52f, 2, 4, 15f))
    }
}
