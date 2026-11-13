package com.qingluan.schedule

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import com.qingluan.schedule.system.ReminderPlanner
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReviewRegressionTest {
    private val date = LocalDate.of(2026, 9, 7)
    private val course =
        Course(
            id = "c",
            termId = "t",
            name = "数学",
            meetings = listOf(Meeting(id = "m", weekday = 1, startPeriod = 1, endPeriod = 1)),
        )

    private fun snapshot() =
        Snapshot(
            activeTermId = "t",
            terms =
                listOf(Term(id = "t", name = "学期", startDate = date.toString(), timetableId = "p")),
            timetables =
                listOf(Timetable(id = "p", periods = listOf(Period(480, 525), Period(535, 580)))),
            courses = listOf(course),
        )

    @Test
    fun previewRetainsExtraClassesWhenAddingMeeting() {
        val s =
            snapshot()
                .copy(
                    exceptions =
                        listOf(
                            CourseException(
                                id = "extra",
                                courseId = "c",
                                meetingId = "m",
                                targetDate = "2026-09-08",
                                startPeriod = 1,
                                endPeriod = 1,
                            )
                        )
                )
        val updated =
            course.copy(
                meetings =
                    course.meetings +
                        Meeting(id = "tuesday", weekday = 2, startPeriod = 1, endPeriod = 1)
            )
        val next = s.withCourse(updated)
        assertEquals(1, next.exceptions.size)
        assertTrue(ScheduleEngine.conflicts(next, "t").any { it.date == date.plusDays(1) })
    }

    @Test
    fun delayedAlarmDoesNotSkipOtherUsefulDueReminders() {
        val second =
            course.copy(
                id = "d",
                meetings = listOf(Meeting(id = "n", weekday = 1, startPeriod = 2, endPeriod = 2)),
            )
        val s = snapshot().copy(courses = listOf(course, second))
        val now = date.atTime(8, 46)
        val plans =
            ReminderPlanner.plan(
                s,
                AppSettings(reminders = true, leadMinutes = 10),
                now,
                emptySet(),
            )
        assertTrue(
            plans.any {
                it.occurrence.course.id == "d" && it.occurrence.date == date && it.trigger > now
            }
        )
    }

    @Test
    fun changedCourseTimeCanReceiveANewReminder() {
        val s = snapshot()
        val key = ScheduleEngine.onDate(s, "t", date).single().key
        val changed =
            s.copy(
                timetables =
                    listOf(
                        Timetable(id = "p", periods = listOf(Period(600, 645), Period(655, 700)))
                    )
            )
        val plans =
            ReminderPlanner.plan(
                changed,
                AppSettings(reminders = true),
                date.atTime(9, 0),
                setOf(key),
            )
        assertTrue(plans.any { it.occurrence.date == date && it.occurrence.course.id == "c" })
    }

    @Test
    fun alreadyDeliveredOccurrenceIsNotPlannedAgain() {
        val s = snapshot()
        val key = ScheduleEngine.onDate(s, "t", date).single().key
        val plans =
            ReminderPlanner.plan(s, AppSettings(reminders = true), date.atTime(7, 0), setOf(key))
        assertTrue(plans.none { it.occurrence.key == key })
    }
}
