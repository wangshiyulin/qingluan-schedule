package com.qingluan.schedule.transfer

import com.qingluan.schedule.model.clock
import kotlinx.serialization.json.*

/** Deterministic file columns retain their existing date/time handling, with auditable outcomes. */
object TabularAcademicAdapter {
    fun decode(rows: List<List<String>>, source: String): ImportResult {
        val result = TabularCodec.documents(rows)
        fun norm(s: String) = s.lowercase().replace(Regex("[\\s_（）()：:]"), "")
        val header = rows.indexOfFirst { r ->
            r.any { norm(it) in listOf("课程名称", "课程名", "coursename", "name", "课程") }
        }
        val names = rows[header]
        val raw =
            rows
                .drop(header + 1)
                .filter { it.any(String::isNotBlank) }
                .map { row ->
                    buildJsonObject {
                        names.forEachIndexed { i, name ->
                            if (AcademicJsonCodec.safeField(name))
                                put(name, row.getOrNull(i).orEmpty())
                        }
                    }
                }
        val rejected = result.warnings.filter { it.startsWith("第 ") && it.contains("行") }
        val unknownDate = result.warnings.any { it.contains("开学日期") }
        val unknownTime = result.warnings.any { it.contains("作息") }
        val weeksColumn = names.indexOfFirst { norm(it) in listOf("周次", "上课周次", "weeks", "week") }
        val unknownWeeks =
            weeksColumn < 0 ||
                rows.drop(header + 1).any { it.getOrNull(weeksColumn).isNullOrBlank() }
        val metadata = mutableListOf<JsonObject>()
        result.documents.forEach { doc ->
            metadata += buildJsonObject {
                put("id", doc.term.id)
                put("tableName", doc.term.name)
                put("totalWeeks", doc.term.weeks)
                put("timeTableId", doc.timetable.id)
                if (!unknownDate) put("startDate", doc.term.startDate)
            }
            if (!unknownTime)
                doc.timetable.periods.forEachIndexed { i, p ->
                    metadata += buildJsonObject {
                        put("node", i + 1)
                        put("startTime", clock(p.start))
                        put("endTime", clock(p.end))
                        put("timeTableId", doc.timetable.id)
                    }
                }
        }
        return result.copy(
            assessment =
                ImportAssessment(
                    source,
                    raw.size,
                    (raw.size - rejected.size).coerceAtLeast(0),
                    rejected,
                    unknownDate,
                    unknownWeeks,
                    timeUnknown = unknownTime,
                ),
            warnings =
                (result.warnings + if (unknownWeeks) listOf("部分记录未识别周次，请核对") else emptyList())
                    .distinct(),
            mapping = AcademicMappingData(JsonArray(raw), JsonArray(metadata)),
        )
    }
}
