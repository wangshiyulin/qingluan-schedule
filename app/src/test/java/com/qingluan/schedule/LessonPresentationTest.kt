package com.qingluan.schedule

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LessonPresentationTest {
    private val date = LocalDate.of(2026, 11, 13)
    private val term = Term(id = "t", startDate = "2026-11-09", timetableId = "p")
    private val time =
        Timetable(id = "p", periods = (0..5).map { Period(360 + it * 40, 395 + it * 40) })
    private val course =
        Course(
            id = "c",
            termId = "t",
            name = "离散数学",
            location = "A301",
            meetings =
                listOf(
                    Meeting(id = "a", weekday = 5, startPeriod = 1, endPeriod = 2),
                    Meeting(id = "b", weekday = 5, startPeriod = 3, endPeriod = 3),
                ),
        )

    private fun state(
        courses: List<Course> = listOf(course),
        exceptions: List<CourseException> = emptyList(),
    ) =
        Snapshot(
            activeTermId = "t",
            terms = listOf(term),
            timetables = listOf(time),
            courses = courses,
            exceptions = exceptions,
        )

    @Test
    fun adjacentLessonsUseOneBlockWithoutChangingRawTargets() {
        val original = state()
        val raw = ScheduleEngine.onDate(original, "t", date)
        val block = lessonBlocks(raw).single()
        assertEquals(1, block.startPeriod)
        assertEquals(3, block.endPeriod)
        assertEquals(listOf("a", "b"), block.members.map { it.meeting.id })
        assertEquals(2, ScheduleEngine.onDate(original, "t", date).size)
        val cancelled =
            state(
                exceptions =
                    listOf(
                        CourseException(
                            courseId = "c",
                            meetingId = "b",
                            originalDate = date.toString(),
                        )
                    )
            )
        assertEquals(
            2,
            lessonBlocks(ScheduleEngine.onDate(cancelled, "t", date)).single().endPeriod,
        )
    }

    @Test
    fun gapsAndIndependentOverlapsRemainSeparateWhileAdjacentMetadataChangesMerge() {
        val raw = ScheduleEngine.onDate(state(), "t", date)
        val changedRoom = lessonBlocks(listOf(raw[0], raw[1].copy(location = "A302"))).single()
        assertTrue(changedRoom.location.contains("A301") && changedRoom.location.contains("A302"))
        assertEquals(
            2,
            lessonBlocks(listOf(raw[0], raw[1].copy(startPeriod = 4, endPeriod = 4))).size,
        )
        assertEquals(
            1,
            lessonBlocks(listOf(raw[0], raw[1].copy(startMinute = 900, endMinute = 935))).size,
        )
        val parallel =
            raw[0].copy(course = course.copy(id = "other"), location = "A302", teacher = "另一位老师")
        assertEquals(2, lessonBlocks(listOf(raw[0], parallel)).size)
        val continued = raw[1].copy(location = "A301")
        val branches = lessonBlocks(listOf(raw[0], parallel, continued))
        assertEquals(2, branches.size)
        assertEquals(3, branches.first { it.first.course.id == "c" }.endPeriod)
    }

    @Test
    fun allCourseNamesMergeArbitraryRunsWithoutLosingOriginalDetails() {
        val base = ScheduleEngine.onDate(state(), "t", date).first()
        for (name in listOf("马克思主义基本原理", "离散数学", "Python高级应用", "Linux网络操作系统", "大学英语发展课程")) {
            val pieces =
                (1..6).map { i ->
                    base.copy(
                        key = "original-$i",
                        course =
                            course.copy(
                                id = "source-$i",
                                name =
                                    if (i % 2 == 0) name.toList().joinToString(" ") + "\u200B"
                                    else name,
                                note = "来源行 $i",
                            ),
                        meeting = base.meeting.copy(id = "segment-$i"),
                        startPeriod = i,
                        endPeriod = i,
                        startMinute = 360 + i * 40,
                        endMinute = 395 + i * 40,
                        location = if (i == 1) "自定义教室" else "A30$i",
                        teacher = "教师 $i",
                    )
                }
            val block = lessonBlocks(pieces).single()
            assertEquals(1, block.startPeriod)
            assertEquals(6, block.endPeriod)
            assertEquals(pieces.map { it.key }, block.members.map { it.key })
            assertFalse(block.location.contains("自定义教室"))
            assertTrue(block.location.contains("A306"))
            assertEquals(6, block.members.map { it.teacher }.distinct().size)
        }
        val half = base.copy(course = course.copy(name = "Python高级应用"))
        val full =
            base.copy(course = course.copy(name = "Ｐｙｔｈｏｎ高级应用"), startPeriod = 3, endPeriod = 3)
        assertEquals(1, lessonBlocks(listOf(half, full)).size)
    }

    @Test
    fun defaultImportsGetDistinctColorsButManualGreenIsRespected() {
        val items =
            listOf("大学英语", "离散数学", "Python高级应用", "计算机系统基础", "Linux网络操作系统", "体育III", "电子技术")
                .mapIndexed { i, n -> course.copy(id = "$i", name = n) }
        val colors = courseColorMap(items)
        assertEquals(items.size, colors.values.distinct().size)
        assertEquals(colors, courseColorMap(items.reversed()))
        val manual = course.copy(id = "manual", name = "手动配色", automaticColor = false)
        assertEquals(DEFAULT_COURSE_COLOR, courseColorMap(items + manual)["manual"])
        val sameName = items.first().copy(id = "copy")
        val shared = courseColorMap(items + sameName)
        assertEquals(shared[items.first().id], shared["copy"])
    }

    @Test
    fun allSevenWeekStartsValidateAndRoundTrip() {
        for (day in 1..7) {
            val snapshot = state().copy(terms = listOf(term.copy(firstDayOfWeek = day)))
            val restored = BackupCodec.decode(BackupCodec.encode(Backup(data = snapshot)))
            assertEquals(day, restored.data.terms.single().firstDayOfWeek)
            assertEquals(day, ScheduleEngine.weekStart(date, day).dayOfWeek.value)
            assertEquals(
                1,
                ScheduleEngine.week(snapshot.terms.single(), LocalDate.parse(term.startDate)),
            )
        }
    }

    @Test
    fun oldCoursesRemainReadableAndNewManualColorSurvivesBackup() {
        val old =
            BackupCodec.json.decodeFromString<Course>(
                """{"id":"old","termId":"t","name":"旧课程","meetings":[]}"""
            )
        assertTrue(old.automaticColor)
        val manual = course.copy(automaticColor = false)
        assertFalse(
            BackupCodec.decode(BackupCodec.encode(Backup(data = state(listOf(manual)))))
                .data
                .courses
                .single()
                .automaticColor
        )
    }
}
