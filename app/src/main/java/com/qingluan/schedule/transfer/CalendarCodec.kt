package com.qingluan.schedule.transfer

import com.qingluan.schedule.model.*
import com.qingluan.schedule.model.Period
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Base64

object CalendarCodec {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private fun escape(s: String) =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun unescape(s: String) =
        s.replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")

    fun encode(d: TimetableDocument): String {
        d.validate()
        val lines =
            mutableListOf(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Qingluan//Schedule 1.0//ZH",
                "CALSCALE:GREGORIAN",
                "X-WR-CALNAME:${escape(d.term.name)}",
                "X-QINGLUAN-DATA:${ShareCode.encode(d)}",
            )
        var day = LocalDate.parse(d.term.startDate)
        var count = 0
        while (day <= ScheduleEngine.endDate(d.term)) {
            ScheduleEngine.onDate(d.snapshot(), d.term.id, day).forEach { o ->
                require(++count <= 20000) { "日程过多，请改用 JSON / CSV 导出" }
                lines +=
                    listOf(
                        "BEGIN:VEVENT",
                        "UID:${Base64.getUrlEncoder().withoutPadding().encodeToString(o.key.toByteArray())}@qingluanx.com",
                        "DTSTAMP:${stamp.format(LocalDateTime.now(ZoneOffset.UTC))}Z",
                        "DTSTART:${stamp.format(day.atStartOfDay().plusMinutes(o.startMinute.toLong()))}",
                        "DTEND:${stamp.format(day.atStartOfDay().plusMinutes(o.endMinute.toLong()))}",
                        "SUMMARY:${escape(o.course.name)}",
                        "LOCATION:${escape(o.location)}",
                        "DESCRIPTION:${escape(listOf(o.teacher,o.course.note).filter{it.isNotBlank()}.joinToString("\n"))}",
                        "END:VEVENT",
                    )
            }
            day = day.plusDays(1)
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { line ->
            val chunks = mutableListOf<String>()
            val b = StringBuilder()
            var bytes = 0
            line.forEach { ch ->
                val size = ch.toString().toByteArray().size
                if (bytes + size > 70) {
                    chunks += b.toString()
                    b.clear()
                    bytes = 0
                }
                b.append(ch)
                bytes += size
            }
            chunks += b.toString()
            chunks.joinToString("\r\n ")
        } + "\r\n"
    }

    fun decode(text: String): ImportResult {
        val lines = text.replace("\r\n", "\n").replace(Regex("\n[ \\t]"), "").lines()
        lines
            .firstOrNull { it.startsWith("X-QINGLUAN-DATA:") }
            ?.let {
                return ImportResult(listOf(ShareCode.decode(it.substringAfter(':'))))
            }
        val events = mutableListOf<Map<String, List<Pair<String, String>>>>()
        var current: MutableMap<String, MutableList<Pair<String, String>>>? = null
        lines.forEach { line ->
            when {
                line == "BEGIN:VEVENT" -> current = mutableMapOf()
                line == "END:VEVENT" -> {
                    current?.let { events += it }
                    current = null
                }
                current != null && ':' in line -> {
                    val key = line.substringBefore(':')
                    current!!.getOrPut(key.substringBefore(';')) { mutableListOf() } +=
                        key to line.substringAfter(':')
                }
            }
        }
        require(events.isNotEmpty()) { "日历没有课程事件" }
        fun date(pair: Pair<String, String>): LocalDateTime {
            val raw = pair.second.trim()
            if (raw.length == 8)
                return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
            val parsed = LocalDateTime.parse(raw.removeSuffix("Z").take(15), stamp)
            val zone =
                if (raw.endsWith('Z')) ZoneOffset.UTC
                else
                    Regex("TZID=([^;:]+)").find(pair.first)?.groupValues?.get(1)?.let {
                        runCatching { ZoneId.of(it) }.getOrNull()
                    } ?: ZoneId.systemDefault()
            return parsed.atZone(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        }
        data class Event(
            val uid: String,
            val name: String,
            val location: String,
            val note: String,
            val start: LocalDateTime,
            val end: LocalDateTime,
            val source: Map<String, List<Pair<String, String>>>,
        )
        val parsed = events.mapNotNull { e ->
            runCatching {
                val a = date(e.getValue("DTSTART").first())
                val b = e["DTEND"]?.firstOrNull()?.let(::date) ?: a.plusMinutes(45)
                Event(
                    e["UID"]?.firstOrNull()?.second.orEmpty().ifBlank { newId() },
                    unescape(e["SUMMARY"]?.firstOrNull()?.second.orEmpty()).ifBlank { "导入课程" },
                    unescape(e["LOCATION"]?.firstOrNull()?.second.orEmpty()),
                    unescape(e["DESCRIPTION"]?.firstOrNull()?.second.orEmpty()),
                    a,
                    b,
                    e,
                )
            }
                .getOrNull()
        }
        require(parsed.isNotEmpty()) { "日历日期格式无法识别" }
        val earliest = parsed.minOf { it.start.toLocalDate() }
        val endLimit = earliest.plusWeeks(60).minusDays(1)
        val expanded = mutableMapOf<Pair<String, LocalDateTime>, Event>()
        parsed
            .filter {
                it.source["RECURRENCE-ID"] == null &&
                    it.source["STATUS"]?.firstOrNull()?.second != "CANCELLED"
            }
            .forEach { event ->
                val dates = linkedSetOf(event.start)
                val rule =
                    event.source["RRULE"]?.firstOrNull()?.second?.split(';')?.associate {
                        it.substringBefore('=') to it.substringAfter('=')
                    }
                if (rule != null) {
                    require(rule["FREQ"] in listOf("WEEKLY", "DAILY")) { "只支持每日或每周重复的日历课程" }
                    val count = (rule["COUNT"]?.toIntOrNull() ?: 420).coerceAtMost(420)
                    val interval = (rule["INTERVAL"]?.toIntOrNull() ?: 1).coerceIn(1, 60)
                    val until =
                        rule["UNTIL"]?.let { date("UNTIL" to it) } ?: endLimit.atTime(23, 59)
                    val dayMap =
                        mapOf(
                            "MO" to 1,
                            "TU" to 2,
                            "WE" to 3,
                            "TH" to 4,
                            "FR" to 5,
                            "SA" to 6,
                            "SU" to 7,
                        )
                    val days =
                        rule["BYDAY"]?.split(',')?.mapNotNull { dayMap[it.takeLast(2)] }
                            ?: listOf(event.start.dayOfWeek.value)
                    var day = event.start.toLocalDate()
                    var n = 0
                    while (
                        day <= endLimit &&
                            day.atTime(event.start.toLocalTime()) <= until &&
                            n < count
                    ) {
                        val gap =
                            java.time.temporal.ChronoUnit.DAYS.between(
                                event.start.toLocalDate(),
                                day,
                            )
                        val match =
                            if (rule["FREQ"] == "DAILY") gap % interval == 0L
                            else
                                java.time.temporal.ChronoUnit.WEEKS.between(
                                    ScheduleEngine.monday(event.start.toLocalDate()),
                                    ScheduleEngine.monday(day),
                                ) % interval == 0L && day.dayOfWeek.value in days
                        if (match) {
                            dates += day.atTime(event.start.toLocalTime())
                            n++
                        }
                        day = day.plusDays(1)
                    }
                }
                event.source["RDATE"].orEmpty().forEach { p ->
                    p.second.split(',').forEach { dates += date(p.first to it) }
                }
                val excluded =
                    event.source["EXDATE"]
                        .orEmpty()
                        .flatMap { p -> p.second.split(',').map { date(p.first to it) } }
                        .toSet()
                dates
                    .filter { it !in excluded && it.toLocalDate() <= endLimit }
                    .forEach { start ->
                        val end = start.plus(java.time.Duration.between(event.start, event.end))
                        require(start.toLocalDate() == end.toLocalDate() && end > start) {
                            "不支持跨日课程"
                        }
                        expanded[event.uid to start] = event.copy(start = start, end = end)
                    }
            }
        parsed
            .filter { it.source["RECURRENCE-ID"] != null }
            .forEach { event ->
                val original = date(event.source.getValue("RECURRENCE-ID").first())
                expanded.remove(event.uid to original)
                if (event.source["STATUS"]?.firstOrNull()?.second != "CANCELLED")
                    expanded[event.uid to event.start] = event
            }
        require(expanded.isNotEmpty()) { "日历中没有有效课程" }
        require(expanded.size <= 20000) { "日历课程过多" }
        val ranges =
            expanded.values
                .map { e ->
                    Period(e.start.hour * 60 + e.start.minute, e.end.hour * 60 + e.end.minute)
                }
                .distinct()
                .sortedBy { it.start }
        val boundaries = ranges.flatMap { listOf(it.start, it.end) }.distinct().sorted()
        val periods =
            boundaries
                .zipWithNext()
                .filter { (a, b) -> ranges.any { it.start <= a && it.end >= b } }
                .map { Period(it.first, it.second) }
        require(periods.size in 1..24) { "日历包含超过 24 种时间段，请先精简日历" }
        val time = Timetable(name = "日历作息", periods = periods)
        val term =
            Term(
                name = "日历课表",
                startDate = ScheduleEngine.monday(earliest).toString(),
                weeks =
                    ((java.time.temporal.ChronoUnit.DAYS.between(
                            ScheduleEngine.monday(earliest),
                            expanded.values.maxOf { it.start.toLocalDate() },
                        ) / 7) + 1)
                        .toInt()
                        .coerceIn(1, 60),
                timetableId = time.id,
            )
        val courses =
            expanded.values.map { e ->
                val week = ScheduleEngine.week(term, e.start.toLocalDate())
                Course(
                    termId = term.id,
                    name = e.name,
                    location = e.location,
                    note = e.note,
                    meetings =
                        listOf(
                            Meeting(
                                weekday = e.start.dayOfWeek.value,
                                startPeriod =
                                    periods.indexOfFirst {
                                        it.start == e.start.hour * 60 + e.start.minute
                                    } + 1,
                                endPeriod =
                                    periods.indexOfLast {
                                        it.end == e.end.hour * 60 + e.end.minute
                                    } + 1,
                                weeks = setOf(week),
                            )
                        ),
                )
            }
        val combined =
            TabularCodec.combine(courses).map { c ->
                c.copy(
                    meetings =
                        c.meetings
                            .groupBy { listOf(it.weekday, it.startPeriod, it.endPeriod) }
                            .values
                            .map { same ->
                                same.first().copy(weeks = same.flatMap { it.weeks }.toSet())
                            }
                )
            }
        return ImportResult(
            listOf(TimetableDocument(term = term, timetable = time, courses = combined)),
            if (parsed.size < events.size) listOf("有 ${events.size-parsed.size} 条无效日历事件未导入")
            else emptyList(),
        )
    }
}
