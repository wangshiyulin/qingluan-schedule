package com.qingluan.schedule.transfer

import com.qingluan.schedule.model.*
import java.time.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Reads visual weekday grids without depending on a particular university or URL. */
object HtmlGridParser {
    private val weekPattern =
        Regex("(?:第)?([0-9]+(?:\\s*[-–~～至,，、]\\s*[0-9]+)*)\\s*[（(]?周[）)]?(?:\\s*[（(]?[单双][）)]?)?")
    private val periodPattern =
        Regex("(?:第)?(\\d{1,2})(?:\\s*[-–~～至,，]\\s*(\\d{1,2}))?\\s*[)）\\]】]?\\s*节")

    fun parse(document: Document): ImportResult {
        val candidates =
            document.select("table").mapNotNull { table ->
                runCatching { readTable(table) }.getOrNull()
            }
        require(candidates.isNotEmpty()) {
            "未识别到含星期、节次和课程的表格。请打开完整周课表，或导出 Excel / HTML 文件"
        }
        val documents = candidates.flatMap { it.documents }
        val first = documents.first()
        val courses =
            TabularCodec.combine(
                documents.flatMap { it.courses }.map { it.copy(termId = first.term.id) }
            )
        val timetable = documents.maxBy { it.timetable.periods.size }.timetable
        return ImportResult(
            listOf(
                first
                    .copy(
                        term =
                            first.term.copy(
                                timetableId = timetable.id,
                                weeks = documents.maxOf { it.term.weeks },
                            ),
                        timetable = timetable,
                        courses = courses,
                    )
                    .validate()
            ),
            candidates.flatMap { it.warnings }.distinct(),
        )
    }

    private fun courseParts(cell: Element): List<String> {
        // Legacy Zhengfang grids flatten each entry into name/time/room/teacher fonts.
        if (
            cell.hasClass("td_wrap") &&
                cell.closest("table")?.id()?.startsWith("kbgrid_table") == true &&
                cell.select(".timetable_con,.timetable-con").isEmpty()
        ) {
            val fonts =
                cell.select("font").filter { node ->
                    node.children().none { it.tagName() == "font" }
                }
            val entries =
                fonts.indices
                    .filter { index ->
                        index > 0 &&
                            index + 2 < fonts.size &&
                            periodPattern.containsMatchIn(fonts[index].text()) &&
                            weekPattern.containsMatchIn(fonts[index].text())
                    }
                    .map { index ->
                        Element("div")
                            .apply {
                                appendElement("span")
                                    .attr("data-field", "courseName")
                                    .text(fonts[index - 1].text())
                                appendElement("br")
                                appendElement("span").text(fonts[index].text())
                                appendElement("br")
                                appendElement("span")
                                    .attr("data-field", "location")
                                    .text(fonts[index + 1].text())
                                appendElement("br")
                                appendElement("span")
                                    .attr("data-field", "teacher")
                                    .text(fonts[index + 2].text())
                            }
                            .outerHtml()
                    }
            if (entries.isNotEmpty()) return entries
        }
        val blocks =
            cell.select(".kbcontent,.kbcontent1,.timetable_con,.timetable-con,.course-item")
        val leaves = blocks.filter { outer ->
            blocks.none { inner -> inner !== outer && inner.parents().contains(outer) }
        }
        return (if (leaves.isEmpty()) listOf(cell) else leaves).flatMap { block ->
            block.outerHtml().split(Regex("<hr[^>]*>|-{5,}", RegexOption.IGNORE_CASE)).flatMap {
                html ->
                val spaced = html.split(Regex("(?:<br\\s*/?>\\s*){2,}", RegexOption.IGNORE_CASE))
                // Keep incomplete name/metadata chunks together; split only at a new course.
                val parts = mutableListOf<String>()
                var current = ""
                fun scheduled(html: String): Boolean =
                    Jsoup.parse(html).text().let {
                        weekPattern.containsMatchIn(it) || periodPattern.containsMatchIn(it)
                    }
                spaced.forEach { chunk ->
                    val firstLine =
                        Jsoup.parseBodyFragment(chunk)
                            .body()
                            .wholeText()
                            .lineSequence()
                            .map { it.trim() }
                            .firstOrNull { it.isNotBlank() }
                            .orEmpty()
                    val startsCourse =
                        firstLine.isNotBlank() &&
                            !firstLine.contains(Regex("^(任课教师|授课教师|教师|老师|上课地点|教室|地点|周次|节次)[:：]")) &&
                            !weekPattern.containsMatchIn(firstLine) &&
                            !periodPattern.containsMatchIn(firstLine)
                    if (
                        current.isNotBlank() &&
                            scheduled(current) &&
                            scheduled(chunk) &&
                            startsCourse
                    ) {
                        parts += current
                        current = chunk
                    } else current += "<br>" + chunk
                }
                if (current.isNotBlank()) parts += current
                parts
            }
        }
    }

    private fun readTable(table: Element): ImportResult {
        val rows = table.select("tr").filter { it.closest("table") == table }
        val occupancy = mutableMapOf<Pair<Int, Int>, Element>()
        val matrix = mutableListOf<Map<Int, Element>>()
        rows.forEachIndexed { r, row ->
            var col = 0
            val cells = mutableMapOf<Int, Element>()
            while (occupancy[r to col] != null) {
                cells[col] = occupancy.getValue(r to col)
                col++
            }
            row.children()
                .filter { it.tagName() in listOf("td", "th") }
                .forEach { cell ->
                    while (occupancy[r to col] != null) {
                        cells[col] = occupancy.getValue(r to col)
                        col++
                    }
                    val colspan = (cell.attr("colspan").toIntOrNull() ?: 1).coerceIn(1, 30)
                    val rowspan = (cell.attr("rowspan").toIntOrNull() ?: 1).coerceIn(1, 30)
                    repeat(colspan) { x ->
                        repeat(rowspan) { y -> occupancy[(r + y) to (col + x)] = cell }
                        cells[col + x] = cell
                    }
                    col += colspan
                }
            occupancy
                .filterKeys { it.first == r }
                .forEach { (pos, cell) -> cells[pos.second] = cell }
            matrix += cells
        }
        val header = matrix.indexOfFirst { row ->
            row.values.distinct().count {
                runCatching { TabularCodec.weekday(it.text()) }.isSuccess
            } >= 5
        }
        require(header >= 0)
        val columns =
            matrix[header]
                .mapNotNull { (c, cell) ->
                    runCatching { c to TabularCodec.weekday(cell.text()) }.getOrNull()
                }
                .toMap()
        val termId = newId()
        val courses = mutableListOf<Course>()
        val seen = mutableSetOf<Pair<Element, Int>>()
        var dataRow = 0
        matrix.drop(header + 1).forEach { row ->
            val dayCells = columns.mapNotNull { (col, day) -> row[col]?.let { day to it } }
            if (dayCells.isEmpty()) return@forEach
            val prefix =
                row.filterKeys { it < (columns.keys.minOrNull() ?: 0) }
                    .values
                    .joinToString(" ") { it.text() }
            if (prefix.contains(Regex("午休|晚休|休息|备注"))) return@forEach
            if (dayCells.all { it.second.tagName() == "th" }) return@forEach
            dataRow++
            val prefixPeriod =
                Regex("^(?:上午|下午|晚上)?\\s*(\\d{1,2})(?:\\s*[-–~～]\\s*(\\d{1,2}))?(?:\\s*节)?$")
                    .find(prefix.trim())
            val start = prefixPeriod?.groupValues?.get(1)?.toIntOrNull() ?: dataRow
            dayCells.forEach cellLoop@{ (day, cell) ->
                if (!seen.add(cell to day)) return@cellLoop
                val pieces = courseParts(cell)
                pieces.forEach partLoop@{ html ->
                    val item =
                        Jsoup.parseBodyFragment(
                                html.replace(
                                    Regex("</(?:p|div|li|section)\\s*>", RegexOption.IGNORE_CASE),
                                    "$0\n",
                                )
                            )
                            .body()
                    val text =
                        item.wholeText().lineSequence().joinToString("\n") { it.trim() }.trim()
                    if (text.isBlank() || text in listOf("无", "—", "-", "暂无课程")) return@partLoop
                    val weekMatch = weekPattern.find(text)
                    val periodMatch = periodPattern.find(text)
                    if (
                        weekMatch == null &&
                            periodMatch == null &&
                            item.select(".kbcontent,[class*=course],strong,b").isEmpty()
                    )
                        return@partLoop
                    val begin = periodMatch?.groupValues?.get(1)?.toIntOrNull() ?: start
                    val end =
                        periodMatch?.groupValues?.get(2)?.toIntOrNull()
                            ?: prefixPeriod?.groupValues?.get(2)?.toIntOrNull()
                            ?: begin + (cell.attr("rowspan").toIntOrNull() ?: 1) - 1
                    if (begin !in 1..24 || end !in begin..24) return@partLoop
                    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val name =
                        item
                            .selectFirst(
                                ".coursename,.course-name,[class*=course-name],[data-field=courseName],.kcmc,[title=课程名称],[title=课程名],.timetable_con .title,.timetable-con .title,strong,b"
                            )
                            ?.text()
                            ?.takeIf { it.isNotBlank() }
                            ?: lines.firstOrNull {
                                !it.contains(Regex("^(教师|老师|地点|教室|周次|节次|课程代码|课程号|课号)[:：]")) &&
                                    !it.matches(Regex("[A-Za-z]*\\d[\\dA-Za-z._-]*")) &&
                                    weekPattern.find(it) == null &&
                                    periodPattern.find(it) == null
                            }
                            ?: text
                                .substringBefore(weekMatch?.value ?: "\n")
                                .substringBefore(periodMatch?.value ?: "\n")
                                .trim()
                    if (name.isBlank() || name.length > 200) return@partLoop
                    val fields = AcademicFields.read(item, table.ownerDocument())
                    val teacher = fields.teacher.takeUnless { it == name }.orEmpty()
                    val room = fields.room.takeUnless { it == name }.orEmpty()
                    val weeks =
                        weekPattern
                            .findAll(text)
                            .flatMap { match ->
                                runCatching { TabularCodec.readWeeks(match.value, 60) }
                                    .getOrDefault(emptySet())
                            }
                            .toSet()
                            .ifEmpty { (1..20).toSet() }
                    courses +=
                        Course(
                            termId = termId,
                            name = name,
                            teacher = teacher.take(100),
                            location = room.take(200),
                            meetings =
                                listOf(
                                    Meeting(
                                        weekday = day,
                                        startPeriod = begin,
                                        endPeriod = end,
                                        weeks = weeks,
                                    )
                                ),
                        )
                }
            }
        }
        require(courses.isNotEmpty())
        val maxPeriod = courses.maxOf { it.meetings.maxOf { m -> m.endPeriod } }
        val periods =
            if (maxPeriod <= 10) defaultPeriods()
            else List(maxPeriod) { Period(360 + it * 40, 395 + it * 40) }
        val timetable = Timetable(name = "导入作息", periods = periods)
        val term =
            Term(
                id = termId,
                name = "教务课表",
                startDate = ScheduleEngine.monday(LocalDate.now()).toString(),
                weeks = maxOf(20, courses.maxOf { it.meetings.maxOf { m -> m.weeks.max() } }),
                timetableId = timetable.id,
            )
        return ImportResult(
            listOf(
                TimetableDocument(
                    term = term,
                    timetable = timetable,
                    courses = TabularCodec.combine(courses),
                )
            ),
            listOf("请确认开学日期、作息和识别字段"),
        )
    }
}
