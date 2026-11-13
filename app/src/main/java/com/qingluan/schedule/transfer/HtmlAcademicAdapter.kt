package com.qingluan.schedule.transfer

import kotlinx.serialization.json.*
import org.jsoup.Jsoup

/**
 * Known educational layouts are mapped to academic rows; generic grids are an explicit fallback.
 */
object HtmlAcademicAdapter {
    private val weeks =
        Regex("(?:第)?([0-9]+(?:\\s*[-–~～至,，、]\\s*[0-9]+)*)\\s*[（(]?周[）)]?(?:\\s*[（(]?[单双][）)]?)?")
    private val nodes = Regex("[（(\\[]?([0-9]+(?:\\s*[-–~～至,，、]\\s*[0-9]+)*)\\s*节")

    fun decode(html: String): ImportResult {
        val doc = Jsoup.parse(html)
        doc.select("script,style,input,textarea,button,nav,header,footer").remove()
        val units =
            doc.select(".timetable_con,.timetable-con").filter {
                it.select(".title,.coursename,.course-name").isNotEmpty()
            }
        if (units.isNotEmpty()) {
            val records = units.map { unit ->
                val name =
                    unit
                        .selectFirst(".title,.coursename,.course-name")!!
                        .text()
                        .replace(Regex("[●★○]"), "")
                        .trim()
                val cell = unit.closest("td")
                val day =
                    cell
                        ?.id()
                        ?.let { Regex("(?:^|[^0-9])([1-7])[-_]").find(it)?.groupValues?.get(1) }
                        .orEmpty()
                val body = unit.wholeText()
                val parts = nodes.find(body)?.groupValues?.get(1).orEmpty()
                val fields = AcademicFields.read(unit, doc)
                buildJsonObject {
                    put("courseName", name)
                    put("weekday", day)
                    put("teacher", fields.teacher.takeUnless { it == name }.orEmpty())
                    put("location", fields.room.takeUnless { it == name }.orEmpty())
                    put(
                        "sections",
                        JsonArray(
                            runCatching {
                                    TabularCodec.readWeeks(parts, 24).sorted().map(::JsonPrimitive)
                                }
                                .getOrDefault(emptyList())
                        ),
                    )
                    val w =
                        weeks
                            .findAll(body)
                            .flatMap {
                                runCatching { TabularCodec.readWeeks(it.value, 60) }
                                    .getOrDefault(emptySet())
                            }
                            .toSet()
                    if (w.isNotEmpty()) put("weeks", JsonArray(w.sorted().map(::JsonPrimitive)))
                }
            }
            // If the grid lacks stable day IDs, the general rowspan/weekday adapter has the
            // coordinates.
            if (records.any { it["weekday"]?.jsonPrimitive?.content?.isNotBlank() == true })
                return AcademicJsonCodec.decode(listOf(JsonArray(records)), "正方课表网页")
        }
        val result = TabularCodec.readHtml(html)
        val rejected = result.warnings.filter { it.startsWith("第 ") && it.contains("行") }
        val count = result.documents.sumOf { it.courses.sumOf { c -> c.meetings.size } }
        return result.copy(
            warnings = (result.warnings + "网页通用识别，请核对课程总数、教师和地点").distinct(),
            assessment =
                ImportAssessment(
                    "通用课表网页",
                    0,
                    count,
                    rejected,
                    dateUnknown = true,
                    incomplete = true,
                    timeUnknown = true,
                ),
        )
    }
}
