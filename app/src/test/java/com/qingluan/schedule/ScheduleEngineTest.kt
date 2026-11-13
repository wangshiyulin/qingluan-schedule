package com.qingluan.schedule

import com.qingluan.schedule.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ScheduleEngineTest {
    private val start = LocalDate.of(2026, 9, 7)
    private val term =
        Term(id = "t", name = "秋季", startDate = start.toString(), weeks = 20, timetableId = "p")
    private val slots =
        Timetable(
            id = "p",
            name = "默认作息",
            periods = listOf(Period(480, 525), Period(535, 580), Period(600, 645)),
        )
    private val meeting =
        Meeting(id = "m", weekday = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1, 3, 5))
    private val course = Course(id = "c", termId = "t", name = "数学", meetings = listOf(meeting))

    private fun state(exceptions: List<CourseException> = emptyList()) =
        Snapshot(
            terms = listOf(term),
            timetables = listOf(slots),
            courses = listOf(course),
            exceptions = exceptions,
            activeTermId = "t",
        )

    @Test
    fun weekBeforeStartIsZero() {
        assertEquals(0, ScheduleEngine.week(term, start.minusDays(1)))
    }

    @Test
    fun startSundayBelongsToWeekOne() {
        assertEquals(
            1,
            ScheduleEngine.week(term.copy(startDate = "2026-09-06"), LocalDate.parse("2026-09-06")),
        )
    }

    @Test
    fun nextMondayIsWeekTwo() {
        assertEquals(
            2,
            ScheduleEngine.week(term.copy(startDate = "2026-09-06"), LocalDate.parse("2026-09-07")),
        )
    }

    @Test
    fun oddWeekRuns() {
        assertEquals(1, ScheduleEngine.onDate(state(), "t", start).size)
    }

    @Test
    fun evenWeekDoesNotRun() {
        assertTrue(ScheduleEngine.onDate(state(), "t", start.plusWeeks(1)).isEmpty())
    }

    @Test
    fun irregularWeekDoesNotRun() {
        assertTrue(ScheduleEngine.onDate(state(), "t", start.plusWeeks(6)).isEmpty())
    }

    @Test
    fun termEndHasNoClasses() {
        assertTrue(ScheduleEngine.onDate(state(), "t", start.plusWeeks(21)).isEmpty())
    }

    @Test
    fun cancelOnlyOneDate() {
        val s =
            state(
                listOf(
                    CourseException(
                        id = "x",
                        courseId = "c",
                        meetingId = "m",
                        originalDate = start.toString(),
                    )
                )
            )
        assertTrue(ScheduleEngine.onDate(s, "t", start).isEmpty())
        assertEquals(1, ScheduleEngine.onDate(s, "t", start.plusWeeks(2)).size)
    }

    @Test
    fun moveAcrossWeekRemovesOriginalAndAddsTarget() {
        val target = start.plusWeeks(1).plusDays(1)
        val x =
            CourseException(
                id = "x",
                courseId = "c",
                meetingId = "m",
                originalDate = start.toString(),
                targetDate = target.toString(),
                startPeriod = 2,
                endPeriod = 3,
                location = "B201",
            )
        val s = state(listOf(x))
        assertTrue(ScheduleEngine.onDate(s, "t", start).isEmpty())
        val o = ScheduleEngine.onDate(s, "t", target).single()
        assertEquals("B201", o.location)
        assertEquals(535, o.startMinute)
    }

    @Test
    fun extraClassDoesNotRemoveOriginal() {
        val x =
            CourseException(
                id = "x",
                courseId = "c",
                meetingId = "m",
                targetDate = start.plusDays(1).toString(),
                startPeriod = 1,
                endPeriod = 1,
            )
        assertEquals(1, ScheduleEngine.onDate(state(listOf(x)), "t", start).size)
        assertEquals(1, ScheduleEngine.onDate(state(listOf(x)), "t", start.plusDays(1)).size)
    }

    @Test
    fun differentWeeksDoNotConflict() {
        val other =
            course.copy(id = "d", meetings = listOf(meeting.copy(id = "n", weeks = setOf(2, 4))))
        assertTrue(
            ScheduleEngine.conflicts(state().copy(courses = listOf(course, other)), "t").isEmpty()
        )
    }

    @Test
    fun matchingTimesConflict() {
        val other = course.copy(id = "d", name = "英语", meetings = listOf(meeting.copy(id = "n")))
        assertFalse(
            ScheduleEngine.conflicts(state().copy(courses = listOf(course, other)), "t").isEmpty()
        )
    }

    @Test
    fun adjacentTimesDoNotConflict() {
        val other =
            course.copy(
                id = "d",
                meetings = listOf(meeting.copy(id = "n", startPeriod = 3, endPeriod = 3)),
            )
        assertTrue(
            ScheduleEngine.conflicts(state().copy(courses = listOf(course, other)), "t").isEmpty()
        )
    }

    @Test
    fun yearBoundaryWeekIsContinuous() {
        assertEquals(17, ScheduleEngine.week(term, LocalDate.of(2026, 12, 28)))
    }

    @Test
    fun identicalMeetingIdsInDifferentCoursesHaveDifferentKeys() {
        val other = course.copy(id = "d", name = "英语")
        val items = ScheduleEngine.onDate(state().copy(courses = listOf(course, other)), "t", start)
        assertEquals(2, items.map { it.key }.distinct().size)
    }

    @Test
    fun emptyTermsReturnNoOccurrences() {
        assertTrue(ScheduleEngine.onDate(Snapshot(), "missing", start).isEmpty())
    }

    @Test
    fun sundayStartUsesSundayWeekBoundary() {
        val t = term.copy(startDate = "2026-09-07", firstDayOfWeek = 7, weeks = 2)
        assertEquals(1, ScheduleEngine.week(t, LocalDate.parse("2026-09-12")))
        assertEquals(2, ScheduleEngine.week(t, LocalDate.parse("2026-09-13")))
        assertEquals(LocalDate.parse("2026-09-19"), ScheduleEngine.endDate(t))
    }

    @Test
    fun nonCurrentPreviewDoesNotBecomeActualOccurrence() {
        val date = start.plusWeeks(1)
        assertTrue(ScheduleEngine.onDate(state(), "t", date).isEmpty())
        assertEquals(1, ScheduleEngine.nonCurrentOnDate(state(), "t", date).size)
    }
}
