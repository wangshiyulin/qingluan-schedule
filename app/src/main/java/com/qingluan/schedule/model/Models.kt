package com.qingluan.schedule.model

import java.util.UUID
import kotlinx.serialization.Serializable

fun newId(): String = UUID.randomUUID().toString()

@Serializable data class Period(val start: Int, val end: Int)

@Serializable
data class Timetable(
    val id: String = newId(),
    val name: String = "标准作息",
    val periods: List<Period> = defaultPeriods(),
)

fun defaultPeriods() =
    listOf(480, 535, 610, 665, 840, 895, 970, 1025, 1140, 1195).map { Period(it, it + 45) }

@Serializable
data class Term(
    val id: String = newId(),
    val name: String = "新学期",
    val startDate: String,
    val weeks: Int = 20,
    val timetableId: String,
    val firstDayOfWeek: Int = 1,
)

@Serializable
data class Meeting(
    val id: String = newId(),
    val weekday: Int = 1,
    val startPeriod: Int = 1,
    val endPeriod: Int = 2,
    val weeks: Set<Int> = (1..20).toSet(),
    val teacher: String = "",
    val location: String = "",
)

@Serializable
data class Course(
    val id: String = newId(),
    val termId: String,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val note: String = "",
    val color: Long = DEFAULT_COURSE_COLOR,
    val meetings: List<Meeting>,
    val automaticColor: Boolean = true,
)

@Serializable
data class CourseException(
    val id: String = newId(),
    val courseId: String,
    val meetingId: String,
    val originalDate: String = "",
    val targetDate: String = "",
    val startPeriod: Int = 1,
    val endPeriod: Int = 1,
    val location: String = "",
    val teacher: String = "",
    val originalTeachingDate: String = "",
)

@Serializable
data class CalendarRule(
    val id: String = newId(),
    val termId: String,
    val date: String,
    val teachingDate: String = "",
    val name: String = "节假日",
)

@Serializable
data class Snapshot(
    val formatVersion: Int = 1,
    val activeTermId: String = "",
    val terms: List<Term> = emptyList(),
    val timetables: List<Timetable> = listOf(Timetable(id = "default")),
    val courses: List<Course> = emptyList(),
    val exceptions: List<CourseException> = emptyList(),
    val calendarRules: List<CalendarRule> = emptyList(),
)

@Serializable
data class Occurrence(
    val key: String,
    val course: Course,
    val meeting: Meeting,
    @Serializable(with = LocalDateSerializer::class) val date: java.time.LocalDate,
    val startPeriod: Int,
    val endPeriod: Int,
    val startMinute: Int,
    val endMinute: Int,
    val teacher: String,
    val location: String,
    val exception: CourseException? = null,
)

data class Conflict(val date: java.time.LocalDate, val first: Occurrence, val second: Occurrence)
