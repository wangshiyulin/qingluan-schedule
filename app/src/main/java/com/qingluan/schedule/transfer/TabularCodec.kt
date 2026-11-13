package com.qingluan.schedule.transfer

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.xml.sax.InputSource

object TabularCodec {
    val headers =
        listOf(
            "课程名称",
            "教师",
            "地点",
            "星期",
            "开始节次",
            "结束节次",
            "周次",
            "备注",
            "课表名称",
            "开学日期",
            "总周数",
            "每周起始日",
            "作息",
        )

    fun rows(d: TimetableDocument): List<List<String>> {
        require(d.courses.sumOf { it.meetings.size } <= 9999) { "表格课次过多，请使用 JSON 导出" }
        return listOf(headers) +
            d.courses.flatMap { c ->
                c.meetings.map { m ->
                    listOf(
                        c.name,
                        m.teacher.ifBlank { c.teacher },
                        m.location.ifBlank { c.location },
                        m.weekday.toString(),
                        m.startPeriod.toString(),
                        m.endPeriod.toString(),
                        m.weeks.sorted().joinToString(","),
                        c.note,
                        d.term.name,
                        d.term.startDate,
                        d.term.weeks.toString(),
                        d.term.firstDayOfWeek.toString(),
                        BackupCodec.json.encodeToString(
                            kotlinx.serialization.serializer<List<Period>>(),
                            d.timetable.periods,
                        ),
                    )
                }
            }
    }

    fun csv(rows: List<List<String>>) =
        "\uFEFF" +
            rows.joinToString("\r\n") { row ->
                row.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
            }

    fun readCsv(text: String): List<List<String>> {
        val delimiter =
            if (
                text.lineSequence().firstOrNull().orEmpty().count { it == '\t' } >
                    text.lineSequence().firstOrNull().orEmpty().count { it == ',' }
            )
                '\t'
            else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val input = text.removePrefix("\uFEFF")
        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' -> {
                    if (quoted && i + 1 < input.length && input[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else quoted = !quoted
                }
                !quoted && c == delimiter -> {
                    row += cell.toString()
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    row += cell.toString()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted) { "CSV 引号未闭合" }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) rows += row
        require(rows.size <= 10000) { "表格行数过多" }
        return rows
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[\\s_（）()：:]"), "")

    fun documents(rows: List<List<String>>): ImportResult {
        val start = rows.indexOfFirst { row ->
            row.any { norm(it) in listOf("课程名称", "课程名", "coursename", "name", "课程") }
        }
        require(start >= 0) { "未找到课程名称、星期、节次等表头" }
        val names = rows[start].map(::norm)
        fun cell(row: List<String>, vararg keys: String): String {
            val ix = names.indexOfFirst { it in keys.map(::norm) }
            return if (ix >= 0) row.getOrNull(ix).orEmpty().trim() else ""
        }
        val warnings = mutableListOf<String>()
        val valid =
            rows.drop(start + 1).filter {
                cell(it, "课程名称", "课程名", "courseName", "name", "课程").isNotBlank()
            }
        require(valid.isNotEmpty()) { "表格中没有课程" }
        val groups = valid.groupBy { cell(it, "课表名称", "tableName", "termName").ifBlank { "导入课表" } }
        require(groups.size <= 100) { "课表数量过多" }
        val docs = groups.map { (name, values) ->
            val first = values.first()
            val rawDate = cell(first, "开学日期", "startDate", "semesterStartDate")
            val startDate = runCatching {
                if (rawDate.isBlank()) error("missing")
                val serial = rawDate.toDoubleOrNull()
                when {
                    serial != null && serial > 100000000000 ->
                        java.time.Instant.ofEpochMilli(serial.toLong())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    serial != null && serial > 1000000000 ->
                        java.time.Instant.ofEpochSecond(serial.toLong())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    serial != null && serial in 1.0..100000.0 ->
                        LocalDate.of(1899, 12, 30).plusDays(serial.toLong()).toString()
                    else ->
                        rawDate.replace('/', '-').split('-').let {
                            LocalDate.of(it[0].toInt(), it[1].toInt(), it[2].toInt()).toString()
                        }
                }
            }
                .getOrElse {
                    warnings += "请确认开学日期"
                    ScheduleEngine.monday(LocalDate.now()).toString()
                }
            val inferred =
                values
                    .flatMap { row ->
                        Regex("\\d+")
                            .findAll(cell(row, "周次", "上课周次", "weeks", "week"))
                            .map { it.value.toInt() }
                            .toList()
                    }
                    .maxOrNull() ?: 20
            val count =
                cell(first, "总周数", "totalWeeks", "maxWeek").toIntOrNull() ?: maxOf(20, inferred)
            require(count in 1..60) { "总周数应为 1–60" }
            val periodText = cell(first, "作息", "periods")
            val periods =
                if (periodText.isNotBlank())
                    runCatching { BackupCodec.json.decodeFromString<List<Period>>(periodText) }
                        .getOrElse { error("作息数据无效") }
                else defaultPeriods()
            val time = Timetable(name = "导入作息", periods = periods)
            val term =
                Term(
                    name = name,
                    startDate = startDate,
                    weeks = count,
                    timetableId = time.id,
                    firstDayOfWeek = cell(first, "每周起始日", "firstDayOfWeek").toIntOrNull() ?: 1,
                )
            val courses = values.mapIndexedNotNull { index, row ->
                try {
                    val day = weekday(cell(row, "星期", "星期几", "weekday", "day"))
                    val combined =
                        cell(row, "节次", "上课节次", "period", "nodes").let {
                            Regex("\\d+").findAll(it).map { m -> m.value.toInt() }.toList()
                        }
                    val begin =
                        cell(row, "开始节次", "开始节", "startPeriod", "startNode", "startUnit")
                            .toIntOrNull() ?: combined.firstOrNull() ?: error("缺少开始节次")
                    val end =
                        cell(row, "结束节次", "结束节", "endPeriod", "endNode", "endUnit").toIntOrNull()
                            ?: combined.lastOrNull()
                            ?: begin
                    val weeksText = cell(row, "周次", "上课周次", "weeks", "week").ifBlank { "1-$count" }
                    val weeks = readWeeks(weeksText, count)
                    Course(
                        termId = term.id,
                        name = cell(row, "课程名称", "课程名", "courseName", "name", "课程"),
                        teacher =
                            cell(
                                row,
                                "教师",
                                "老师",
                                "任课教师",
                                "授课教师",
                                "任课老师",
                                "授课老师",
                                "上课教师",
                                "教师姓名",
                                "teacher",
                                "teacherName",
                                "jsxm",
                            ),
                        location =
                            cell(
                                row,
                                "地点",
                                "教室",
                                "上课地点",
                                "上课教室",
                                "教学地点",
                                "教学场地",
                                "教室名称",
                                "location",
                                "room",
                                "classroom",
                                "jxdd",
                            ),
                        note = cell(row, "备注", "note", "description"),
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
                } catch (e: Exception) {
                    warnings += "第 ${start+index+2} 行：${e.message}"
                    null
                }
            }
            require(courses.isNotEmpty()) { "没有可导入的课程：${warnings.firstOrNull().orEmpty()}" }
            TimetableDocument(term = term, timetable = time, courses = combine(courses)).validate()
        }
        if (valid.any { cell(it, "开学日期", "startDate", "semesterStartDate").isBlank() })
            warnings += "未包含开学日期，请在预览中确认"
        if (valid.any { cell(it, "作息", "periods").isBlank() }) warnings += "文件未提供完整作息，请在预览中核对"
        return ImportResult(docs, warnings.distinct())
    }

    fun weekday(value: String): Int {
        value.trim().toIntOrNull()?.let {
            require(it in 1..7) { "星期必须为 1–7" }
            return it
        }
        val n =
            mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
        n.entries
            .firstOrNull { value.contains("周${it.key}") || value.contains("星期${it.key}") }
            ?.let {
                return it.value
            }
        return listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
            .indexOf(value.lowercase())
            .let {
                require(it >= 0) { "无法识别星期：$value" }
                it + 1
            }
    }

    fun readWeeks(value: String, max: Int): Set<Int> {
        val normalized =
            value
                .replace(Regex("[第周\\s]"), "")
                .replace('~', '-')
                .replace('～', '-')
                .replace('至', '-')
                .replace(Regex("[（(]?[单双][）)]?"), "")
        val weeks = parseWeeks(normalized.replace(Regex("[（）()]"), ""), max)
        return weeks
            .filter {
                if (value.contains('单')) it % 2 == 1
                else if (value.contains('双')) it % 2 == 0 else true
            }
            .toSet()
            .also { require(it.isNotEmpty()) { "周次为空" } }
    }

    fun combine(courses: List<Course>) =
        courses
            .groupBy { listOf(it.name, it.teacher, it.location, it.note, it.color) }
            .values
            .map { same ->
                same
                    .first()
                    .copy(
                        meetings =
                            same
                                .flatMap { it.meetings }
                                .distinctBy {
                                    listOf(
                                        it.weekday,
                                        it.startPeriod,
                                        it.endPeriod,
                                        it.weeks,
                                        it.teacher,
                                        it.location,
                                    )
                                }
                    )
            }

    fun html(rows: List<List<String>>): String {
        fun esc(s: String) =
            s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        return "<!doctype html><meta charset=\"utf-8\"><title>课程表</title><table border=\"1\">" +
            rows
                .mapIndexed { i, row ->
                    "<tr>" +
                        row.joinToString("") {
                            "<${if(i==0)"th" else "td"}>${esc(it)}</${if(i==0)"th" else "td"}>"
                        } +
                        "</tr>"
                }
                .joinToString("") +
            "</table>"
    }

    fun readHtml(text: String): ImportResult {
        val doc = Jsoup.parse(text)
        doc.select("script,style,input,textarea,button,nav,header,footer").remove()
        val all =
            doc.select("table").map { table ->
                table.select("tr").map { row ->
                    row.children()
                        .filter { it.tagName() in listOf("th", "td") }
                        .map { it.wholeText().trim() }
                }
            }
        val candidates =
            all.mapNotNull { rows -> runCatching { documents(rows) }.getOrNull() } +
                listOfNotNull(runCatching { HtmlGridParser.parse(doc) }.getOrNull())
        return candidates.maxByOrNull { result ->
            result.documents.sumOf { d -> d.courses.sumOf { it.meetings.size } }
        } ?: error("未识别到完整课表，请打开完整周课表或选择导出的课表文件")
    }

    fun xlsx(rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        fun esc(s: String) =
            s.filter { it == '\n' || it == '\t' || it.code >= 32 }
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        ZipOutputStream(out).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            )
            put(
                "_rels/.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            put(
                "xl/workbook.xml",
                """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="课程表" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            )
            put(
                "xl/worksheets/sheet1.xml",
                """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
                    rows
                        .mapIndexed { i, row ->
                            "<row r=\"${i+1}\">" +
                                row.joinToString("") {
                                    "<c t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(it)}</t></is></c>"
                                } +
                                "</row>"
                        }
                        .joinToString("") +
                    "</sheetData></worksheet>",
            )
        }
        return out.toByteArray()
    }

    fun readXlsx(bytes: ByteArray): List<List<String>> {
        val entries = mutableMapOf<String, String>()
        var total = 0
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val data = zip.readNBytes(BackupCodec.MAX_BYTES + 1)
                total += data.size
                require(total <= BackupCodec.MAX_BYTES) { "表格解压内容过大" }
                if (
                    !e.isDirectory &&
                        (e.name == "xl/sharedStrings.xml" ||
                            e.name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml")))
                )
                    entries[e.name] = data.toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        val strings =
            entries["xl/sharedStrings.xml"]
                ?.let { xml(it).getElementsByTagName("si") }
                .let { nodes ->
                    if (nodes == null) emptyList()
                    else (0 until nodes.length).map { nodes.item(it).textContent }
                }
        val sheet =
            entries
                .filterKeys { it.startsWith("xl/worksheets/") }
                .toSortedMap()
                .values
                .firstOrNull() ?: error("Excel 中没有工作表")
        val rows = xml(sheet).getElementsByTagName("row")
        require(rows.length <= 10000) { "表格行数过多" }
        return (0 until rows.length).map { index ->
            val cells = (rows.item(index) as org.w3c.dom.Element).getElementsByTagName("c")
            val row = mutableListOf<String>()
            (0 until cells.length).forEach { i ->
                val c = cells.item(i) as org.w3c.dom.Element
                val ref = c.getAttribute("r").takeWhile { it.isLetter() }
                val col =
                    if (ref.isBlank()) row.size
                    else ref.fold(0) { n, ch -> n * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
                require(col < 200) { "表格列数过多" }
                while (row.size <= col) row += ""
                val v = c.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                row[col] =
                    when (c.getAttribute("t")) {
                        "s" -> strings.getOrElse(v.toIntOrNull() ?: -1) { "" }
                        "inlineStr" -> c.getElementsByTagName("is").item(0)?.textContent.orEmpty()
                        else -> v
                    }
            }
            row
        }
    }

    private fun xml(text: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) {
            "不支持含外部实体的表格"
        }
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return builder.parse(InputSource(StringReader(text)))
    }
}
