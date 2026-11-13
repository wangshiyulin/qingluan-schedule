package com.qingluan.schedule.model

import java.time.LocalDate

/** Exactly the same candidate is previewed and committed. */
fun Snapshot.withCourse(course: Course): Snapshot {
    val term = terms.first { it.id == course.termId }
    val retained = exceptions.filter { x ->
        if (x.courseId != course.id) true
        else {
            val meeting = course.meetings.find { it.id == x.meetingId }
            meeting != null &&
                (x.originalDate.isBlank() ||
                    runCatching {
                            val date = LocalDate.parse(x.originalDate)
                            date.dayOfWeek.value == meeting.weekday &&
                                ScheduleEngine.week(term, date) in meeting.weeks
                        }
                        .getOrDefault(false))
        }
    }
    return copy(courses = courses.filterNot { it.id == course.id } + course, exceptions = retained)
}

fun Snapshot.withException(change: CourseException): Snapshot =
    copy(
        exceptions =
            exceptions.filterNot {
                it.id == change.id ||
                    (change.originalDate.isNotBlank() &&
                        it.courseId == change.courseId &&
                        it.meetingId == change.meetingId &&
                        it.originalDate == change.originalDate)
            } + change
    )
