package com.qingluan.schedule

import com.qingluan.schedule.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PeriodPlanTest {
    @Test
    fun bulkPlanUsesRequestedMinutesAndRejectsOverflowWithoutWrapping() {
        val p = planPeriods(480, 10, 50, 10)
        assertEquals(Period(480, 530), p[0])
        assertEquals(Period(540, 590), p[1])
        assertEquals(Period(1020, 1070), p.last())
        assertTrue(p.zipWithNext().all { (a, b) -> b.start - a.end == 10 })
        assertTrue(runCatching { planPeriods(1380, 2, 50, 10) }.isFailure)
        assertTrue(runCatching { planPeriods(480, 10, 0, 10) }.isFailure)
    }

    @Test
    fun endedTeachingWeeksDisappearWhileFutureAndAlternatingMeetingsRemain() {
        val t = Term(id = "t", startDate = "2026-09-07", timetableId = "p")
        fun course(id: String, weeks: Set<Int>) =
            Course(
                id = id,
                termId = "t",
                name = id,
                meetings = listOf(Meeting(weekday = 1, weeks = weeks)),
            )
        val s =
            Snapshot(
                activeTermId = "t",
                terms = listOf(t),
                timetables = listOf(Timetable(id = "p")),
                courses =
                    listOf(
                        course("ended", (1..8).toSet()),
                        course("future", (12..16).toSet()),
                        course("alternating", setOf(10, 12)),
                        course("actual", setOf(11)),
                    ),
            )
        val date = LocalDate.parse(t.startDate).plusWeeks(10)
        assertEquals(
            setOf("future", "alternating"),
            ScheduleEngine.nonCurrentOnDate(s, "t", date).map { it.course.id }.toSet(),
        )
        assertEquals(listOf("actual"), ScheduleEngine.onDate(s, "t", date).map { it.course.id })
    }
}
