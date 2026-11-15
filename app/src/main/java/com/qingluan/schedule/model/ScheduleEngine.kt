package com.qingluan.schedule.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Single source of truth for screens, reminders, and conflict checks. */
object ScheduleEngine {
    fun weekStart(date: LocalDate, firstDayOfWeek: Int = 1): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(firstDayOfWeek)))

    fun monday(date: LocalDate): LocalDate = weekStart(date)

    fun week(term: Term, date: LocalDate): Int {
        val start = LocalDate.parse(term.startDate)
        if (date < start) return 0
        return (ChronoUnit.DAYS.between(weekStart(start, term.firstDayOfWeek), date) / 7).toInt() +
            1
    }

    fun endDate(term: Term) =
        weekStart(LocalDate.parse(term.startDate), term.firstDayOfWeek)
            .plusWeeks(term.weeks.toLong())
            .minusDays(1)

    /** Reuse one index for a visible week, conflict scan or reminder batch. */
    fun prepare(s: Snapshot, termId: String): Prepared? {
        val term = s.terms.find { it.id == termId } ?: return null
        val periods = s.timetables.find { it.id == term.timetableId }?.periods ?: return null
        return Prepared(
            term,
            periods,
            s.courses.filter { it.termId == termId },
            s.exceptions,
            s.calendarRules.filter { it.termId == termId },
        )
    }

    class Prepared
    internal constructor(
        val term: Term,
        private val periods: List<Period>,
        courses: List<Course>,
        exceptions: List<CourseException>,
        rules: List<CalendarRule>,
    ) {
        private val calendar = rules.associateBy { it.date }

        private fun source(date: LocalDate): LocalDate? {
            val rule = calendar[date.toString()] ?: return date
            return rule.teachingDate.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        }

        private val byCourse = courses.associateBy { it.id }
        private val slots = courses.flatMap { course -> course.meetings.map { course to it } }
        private val byDay = slots.groupBy { it.second.weekday }
        private val byMeeting = slots.associateBy { it.first.id to it.second.id }
        private val relevant = exceptions.filter { it.courseId in byCourse }
        private val cancelled =
            relevant
                .filter { it.originalDate.isNotBlank() }
                .groupBy { it.originalDate }
                .mapValues { (_, items) -> items.map { it.courseId to it.meetingId }.toSet() }
        private val moved = relevant.filter { it.targetDate.isNotBlank() }.groupBy { it.targetDate }

        fun onDate(date: LocalDate): List<Occurrence> {
            val week = week(term, date)
            if (week !in 1..term.weeks) return emptyList()
            val day = date.toString()
            val skips = cancelled[day].orEmpty()
            val result = mutableListOf<Occurrence>()
            val reference = source(date)
            val teachingWeek = reference?.let { week(term, it) }
            byDay[reference?.dayOfWeek?.value].orEmpty().forEach { (course, meeting) ->
                if (teachingWeek in meeting.weeks && (course.id to meeting.id) !in skips)
                    occurrence(
                            course,
                            meeting,
                            date,
                            meeting.startPeriod,
                            meeting.endPeriod,
                            periods,
                            null,
                        )
                        ?.let(result::add)
            }
            moved[day].orEmpty().forEach { adjustment ->
                val (course, meeting) =
                    byMeeting[adjustment.courseId to adjustment.meetingId] ?: return@forEach
                occurrence(
                        course,
                        meeting,
                        date,
                        adjustment.startPeriod,
                        adjustment.endPeriod,
                        periods,
                        adjustment,
                    )
                    ?.let(result::add)
            }
            return result.sortedWith(
                compareBy({ it.startMinute }, { it.endMinute }, { it.course.name })
            )
        }

        fun nonCurrentOnDate(date: LocalDate): List<Occurrence> {
            val week = week(term, date)
            if (week !in 1..term.weeks) return emptyList()
            val reference = source(date) ?: return emptyList()
            val teachingWeek = week(term, reference)
            return byDay[reference.dayOfWeek.value]
                .orEmpty()
                .filter {
                    teachingWeek !in it.second.weeks &&
                        it.second.weeks.any { taughtWeek -> taughtWeek > teachingWeek }
                }
                .mapNotNull { (course, meeting) ->
                    occurrence(
                        course,
                        meeting,
                        date,
                        meeting.startPeriod,
                        meeting.endPeriod,
                        periods,
                        null,
                    )
                }
        }
    }

    fun onDate(s: Snapshot, termId: String, date: LocalDate): List<Occurrence> =
        prepare(s, termId)?.onDate(date).orEmpty()

    fun nonCurrentOnDate(s: Snapshot, termId: String, date: LocalDate): List<Occurrence> =
        prepare(s, termId)?.nonCurrentOnDate(date).orEmpty()

    private fun safeId(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    private fun occurrence(
        c: Course,
        m: Meeting,
        d: LocalDate,
        start: Int,
        end: Int,
        p: List<Period>,
        x: CourseException?,
    ): Occurrence? {
        if (start !in 1..p.size || end !in start..p.size) return null
        return Occurrence(
            if (x != null)
                "exception:${safeId(c.id)}:${safeId(x.id)}:$d:${p[start-1].start}:${p[end-1].end}"
            else "course:${safeId(c.id)}:${safeId(m.id)}:$d:${p[start-1].start}:${p[end-1].end}",
            c,
            m,
            d,
            start,
            end,
            p[start - 1].start,
            p[end - 1].end,
            x?.teacher?.ifBlank { null } ?: m.teacher.ifBlank { c.teacher },
            x?.location?.ifBlank { null } ?: m.location.ifBlank { c.location },
            x,
        )
    }

    fun teachingDate(s: Snapshot, termId: String, date: LocalDate): LocalDate? {
        val rule =
            s.calendarRules.firstOrNull { it.termId == termId && it.date == date.toString() }
                ?: return date
        return rule.teachingDate.takeIf(String::isNotBlank)?.let(LocalDate::parse)
    }

    fun conflicts(
        s: Snapshot,
        termId: String,
        focusCourseIds: Set<String>? = null,
        limit: Int = 500,
    ): List<Conflict> {
        require(limit in 1..5000)
        val prepared = prepare(s, termId) ?: return emptyList()
        val term = prepared.term
        val result = mutableListOf<Conflict>()
        var date = LocalDate.parse(term.startDate)
        while (date <= endDate(term)) {
            val list = prepared.onDate(date)
            for (i in list.indices) for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                if (b.startMinute >= a.endMinute) break
                if (
                    focusCourseIds != null &&
                        a.course.id !in focusCourseIds &&
                        b.course.id !in focusCourseIds
                )
                    continue
                if (a.startMinute < b.endMinute && b.startMinute < a.endMinute) {
                    result += Conflict(date, a, b)
                    if (result.size >= limit) return result
                }
            }
            date = date.plusDays(1)
        }
        return result
    }

    fun upcoming(
        s: Snapshot,
        termId: String,
        now: java.time.LocalDateTime,
        days: Int = 14,
    ): List<Occurrence> {
        val prepared = prepare(s, termId) ?: return emptyList()
        return (0..days)
            .flatMap { prepared.onDate(now.toLocalDate().plusDays(it.toLong())) }
            .filter { it.date.atStartOfDay().plusMinutes(it.endMinute.toLong()) > now }
    }
}

fun clock(minutes: Int): String =
    (minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0')

fun weekLabel(weeks: Set<Int>): String {
    val list = weeks.sorted()
    if (list.isEmpty()) return "未选择周次"
    if (list == (list.first()..list.last()).toList())
        return if (list.size == 1) "${list.first()}周" else "${list.first()}–${list.last()}周"
    if (list.size > 1 && list.zipWithNext().all { (a, b) -> b - a == 2 })
        return "${list.first()}–${list.last()}周（${if(list.first()%2==1)"单" else "双"}）"
    return list.joinToString(",") + "周"
}

fun parseWeeks(text: String, max: Int): Set<Int> {
    val normalized =
        text
            .replace('，', ',')
            .replace('、', ',')
            .replace('–', '-')
            .replace('—', '-')
            .replace(" ", "")
    require(normalized.isNotBlank()) { "请选择上课周次" }
    val result = mutableSetOf<Int>()
    normalized.split(',').forEach { part ->
        val range = part.split('-')
        require(range.size in 1..2) { "周次格式示例：1-8,10,12-16" }
        val start = range[0].toIntOrNull() ?: error("周次必须是数字")
        val end = if (range.size == 2) range[1].toIntOrNull() ?: error("周次范围不完整") else start
        require(start in 1..max && end in start..max) { "周次范围应为 1–$max" }
        result += (start..end)
    }
    return result
}
