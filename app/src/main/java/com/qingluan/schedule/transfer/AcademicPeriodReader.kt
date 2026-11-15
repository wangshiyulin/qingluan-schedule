package com.qingluan.schedule.transfer

import com.qingluan.schedule.model.Period
import org.jsoup.nodes.Document

/** Read only the time/section prefix of teaching-grid rows, never times embedded in course text. */
internal object AcademicPeriodReader {
    private val time = Regex("(?<![0-9])([01]?[0-9]|2[0-3])[:：]([0-5][0-9])(?![0-9])")
    private val allowed = Regex("第|节次|节|时间|上午|下午|中午|晚上|早晨|晚间|夜间|[0-9\\s:：~～至–—－\\-()（）]")

    private fun isPrefix(text: String) = time.replace(text, "").replace(allowed, "").isEmpty()

    fun read(doc: Document): List<Period>? {
        val found = linkedMapOf<Int, Period>()
        for (table in doc.select("table")) {
            if (
                table.select("tr").none { row ->
                    row.children().count {
                        runCatching { TabularCodec.weekday(it.text()) }.isSuccess
                    } >= 5
                }
            )
                continue
            var inferred = 0
            for (row in table.select("tr")) {
                val prefix =
                    row.children()
                        .takeWhile {
                            it.tagName() in listOf("th", "td") && isPrefix(it.text().trim())
                        }
                        .joinToString(" ") { it.text() }
                val clocks = time.findAll(prefix).toList()
                if (clocks.size != 2) continue
                val label = prefix.substring(0, clocks.first().range.first)
                val index = Regex("[0-9]{1,2}").find(label)?.value?.toIntOrNull() ?: (inferred + 1)
                if (index !in 1..24) continue
                inferred = index
                fun minute(m: MatchResult) =
                    m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                val period = Period(minute(clocks[0]), minute(clocks[1]))
                if (period.end <= period.start) return null
                if (found[index] != null && found[index] != period) return null
                found[index] = period
            }
        }
        if (found.isEmpty() || found.keys.sorted() != (1..found.size).toList()) return null
        val periods = found.toSortedMap().values.toList()
        if (periods.zipWithNext().any { (a, b) -> b.start < a.end }) return null
        return periods
    }

    fun apply(result: ImportResult, doc: Document): ImportResult {
        val periods = read(doc) ?: return result
        if (
            result.documents.any { d ->
                d.courses.any { c -> c.meetings.any { it.endPeriod > periods.size } }
            }
        )
            return result
        return result.copy(
            documents =
                result.documents.map {
                    it.copy(timetable = it.timetable.copy(periods = periods)).validate()
                },
            warnings = result.warnings.filterNot { it.contains("作息") },
            assessment = result.assessment?.copy(timeUnknown = false),
        )
    }
}
