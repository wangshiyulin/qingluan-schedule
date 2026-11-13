package com.qingluan.schedule.transfer

import com.qingluan.schedule.data.BackupCodec
import com.qingluan.schedule.model.*
import java.text.Normalizer
import kotlinx.serialization.json.*

/**
 * Structured data has priority; visual sources can fill missing cells and metadata, never overwrite
 * it.
 */
internal object AcademicSourceUnion {
    private data class Cell(val name: String, val day: Int, val week: Int, val node: Int)

    private data class Info(val name: String, val teacher: String, val room: String)

    private data class Pattern(
        val name: String,
        val teacher: String,
        val room: String,
        val day: Int,
        val start: Int,
        val end: Int,
    )

    fun combine(results: List<ImportResult>): ImportResult? {
        if (results.size < 2 || results.any { it.documents.size != 1 }) return null
        val knownDates =
            results
                .filter { it.assessment?.dateUnknown == false }
                .map { it.documents.single().term.startDate }
                .distinct()
        if (knownDates.size > 1) return null
        val baseResult =
            results.firstOrNull { it.assessment?.dateUnknown == false } ?: results.first()
        val base = baseResult.documents.single()
        val cells = linkedMapOf<Cell, Info>()
        for (result in results) for (course in result.documents.single().courses) for (m in
            course.meetings) for (w in m.weeks) for (node in m.startPeriod..m.endPeriod) {
            val name =
                Normalizer.normalize(course.name, Normalizer.Form.NFKC)
                    .replace(Regex("[\\s\\p{Z}]"), "")
                    .lowercase()
            val key = Cell(name, m.weekday, w, node)
            val incoming =
                Info(
                    course.name,
                    m.teacher.ifBlank { course.teacher },
                    m.location.ifBlank { course.location },
                )
            val old = cells[key]
            cells[key] =
                if (old == null) incoming
                else
                    old.copy(
                        teacher = old.teacher.ifBlank { incoming.teacher },
                        room = old.room.ifBlank { incoming.room },
                    )
            require(cells.size <= 150000) { "多来源课表过大，请选择单独的数据来源" }
        }
        val grouped = linkedMapOf<Pattern, MutableSet<Int>>()
        for ((date, items) in
            cells.entries.groupBy { Triple(it.key.name, it.key.day, it.key.week) }) {
            val sorted = items.sortedBy { it.key.node }
            var start = sorted.first().key.node
            var end = start
            var info = sorted.first().value
            fun add() {
                grouped
                    .getOrPut(
                        Pattern(info.name, info.teacher, info.room, date.second, start, end)
                    ) {
                        linkedSetOf()
                    }
                    .add(date.third)
            }
            for (item in sorted.drop(1)) {
                if (
                    item.key.node == end + 1 &&
                        item.value.teacher == info.teacher &&
                        item.value.room == info.room
                )
                    end = item.key.node
                else {
                    add()
                    start = item.key.node
                    end = start
                    info = item.value
                }
            }
            add()
        }
        val maxNode = grouped.keys.maxOf { it.end }
        val knownTimes =
            results
                .filter { it.assessment?.timeUnknown == false }
                .map { it.documents.single().timetable.periods }
                .distinct()
        val conflictingTimes = knownTimes.size > 1
        val periods =
            (knownTimes + results.map { it.documents.single().timetable.periods }).firstOrNull {
                it.size >= maxNode
            } ?: return null
        val total = maxOf(base.term.weeks, grouped.values.maxOf { it.max() })
        val times =
            BackupCodec.json.encodeToString(
                kotlinx.serialization.serializer<List<Period>>(),
                periods,
            )
        val rows =
            listOf(TabularCodec.headers) +
                grouped.map { (p, w) ->
                    listOf(
                        p.name,
                        p.teacher,
                        p.room,
                        p.day.toString(),
                        p.start.toString(),
                        p.end.toString(),
                        w.sorted().joinToString(","),
                        "",
                        base.term.name,
                        base.term.startDate,
                        total.toString(),
                        base.term.firstDayOfWeek.toString(),
                        times,
                    )
                }
        val result = TabularCodec.documents(rows)
        val warnings =
            (results.flatMap { it.warnings } +
                    if (conflictingTimes) listOf("不同来源的作息不一致，请重新确认作息") else emptyList())
                .distinct()
        return result.copy(
            warnings = warnings,
            assessment =
                ImportAssessment(
                    "合并识别",
                    results.sumOf { it.assessment?.detectedRows ?: 0 },
                    grouped.size,
                    results.flatMap { it.assessment?.rejected.orEmpty() },
                    knownDates.isEmpty(),
                    results.any { it.assessment?.incomplete == true },
                    timeUnknown =
                        conflictingTimes || results.all { it.assessment?.timeUnknown != false },
                ),
            mapping = results.firstNotNullOfOrNull { it.mapping },
        )
    }
}
