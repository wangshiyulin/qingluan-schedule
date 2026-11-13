package com.qingluan.schedule.transfer

import com.qingluan.schedule.data.BackupCodec
import com.qingluan.schedule.model.Period
import com.qingluan.schedule.model.defaultPeriods
import kotlinx.serialization.json.*

/** Course metadata is joined before schedule parsing; unrelated name/id objects are not bases. */
object AcademicJsonCodec {
    private fun key(s: String) = s.lowercase().replace(Regex("[\\s_\\-：:]"), "")

    private val nameKeys = listOf("courseName", "kcmc", "课程名称", "课程名", "name", "title")
    private val teacherKeys =
        listOf(
            "teacherName",
            "teacherNames",
            "teachers",
            "teacherList",
            "jsxms",
            "skjsxm",
            "jsxm",
            "jsmc",
            "teacher",
            "instructor",
            "skjs",
            "xm",
            "任课教师",
            "授课教师",
            "教师",
            "老师",
        )
    private val roomKeys =
        listOf(
            "classroomName",
            "roomName",
            "roomList",
            "classrooms",
            "cdmc",
            "jxdd",
            "classroom",
            "room",
            "locationName",
            "location",
            "venue",
            "position",
            "上课地点",
            "教室",
            "地点",
        )
    private val dayKeys = listOf("weekday", "day", "weekDay", "dayOfWeek", "xqj", "xq", "星期", "星期几")
    private val startKeys =
        listOf(
            "startPeriod",
            "startNode",
            "startUnit",
            "startSection",
            "ksjc",
            "开始节次",
            "开始节",
            "start",
        )
    private val endKeys =
        listOf("endPeriod", "endNode", "endUnit", "endSection", "jsjc", "结束节次", "结束节", "end")
    private val sectionKeys =
        listOf("sections", "nodes", "jcs", "jcor", "skjc", "jc", "section", "节次", "上课节次", "period")
    private val weekKeys = listOf("weeks", "week", "zcd", "zc", "weekList", "周次", "上课周次")
    private val sensitive =
        Regex(
            "password|passwd|pwd|token|secret|privatekey|apikey|cookie|authorization|csrf|session|phone|mobile|email|身份证|密码|sfzh|credential|^mm$|studentid|studentno|^xh$",
            RegexOption.IGNORE_CASE,
        )

    fun safeField(name: String) = name.length <= 80 && !sensitive.containsMatchIn(name)

    private fun text(v: JsonElement?): String =
        when (v) {
            is JsonPrimitive -> v.contentOrNull.orEmpty().takeUnless { it == "null" }.orEmpty()
            is JsonArray ->
                v.mapNotNull { item ->
                        if (item is JsonPrimitive) item.contentOrNull
                        else
                            (item as? JsonObject)?.let {
                                value(it, listOf("name", "xm", "teacherName", "roomName"))
                            }
                    }
                    .joinToString(",")
            is JsonObject -> value(v, listOf("name", "xm", "teacherName", "roomName"))
            else -> ""
        }

    private fun value(row: JsonObject, keys: List<String>): String {
        val normalized = row.entries.associate { key(it.key) to it.value }
        return keys
            .firstNotNullOfOrNull { text(normalized[key(it)]).takeIf(String::isNotBlank) }
            .orEmpty()
            .trim()
    }

    private fun value(row: JsonObject, vararg keys: String) = value(row, keys.toList())

    private fun safeRow(row: JsonObject): JsonObject =
        JsonObject(
            buildMap {
                for ((k, v) in row) {
                    if (!safeField(k) || v.toString().length > 12000) continue
                    when (v) {
                        is JsonPrimitive -> put(k, v)
                        is JsonObject ->
                            if (key(k) in (teacherKeys + roomKeys + nameKeys).map(::key))
                                put(k, JsonPrimitive(text(v)))
                        is JsonArray ->
                            put(
                                k,
                                JsonArray(
                                    v.mapNotNull { item ->
                                        if (item is JsonPrimitive) item
                                        else
                                            (item as? JsonObject)?.let { obj ->
                                                value(
                                                        obj,
                                                        if (key(k) in sectionKeys.map(::key))
                                                            listOf("node", "index", "section")
                                                        else
                                                            listOf(
                                                                "name",
                                                                "xm",
                                                                "teacherName",
                                                                "roomName",
                                                                "week",
                                                            ),
                                                    )
                                                    .takeIf(String::isNotBlank)
                                                    ?.let(::JsonPrimitive)
                                            }
                                    }
                                ),
                            )
                        else -> {}
                    }
                }
            }
        )

    private fun hasSchedule(row: JsonObject) =
        value(row, dayKeys).isNotBlank() &&
            (value(row, startKeys).isNotBlank() || value(row, sectionKeys).isNotBlank())

    fun decode(parts: List<JsonElement>, source: String = "JSON 课表"): ImportResult {
        val objects = mutableListOf<JsonObject>()
        val rows = mutableListOf<JsonObject>()
        fun walk(e: JsonElement, parent: JsonObject?, depth: Int) {
            require(depth <= 14 && objects.size <= 10000) { "JSON 数据层级或记录过多" }
            when (e) {
                is JsonArray -> {
                    require(e.size <= 10000) { "JSON 记录过多" }
                    e.forEach { walk(it, parent, depth + 1) }
                }
                is JsonObject -> {
                    objects += e
                    val nested =
                        listOf("course", "lesson", "subject").firstNotNullOfOrNull {
                            e[it] as? JsonObject
                        }
                    val effective =
                        if (nested != null)
                            JsonObject(
                                nested +
                                    e +
                                    buildMap {
                                        value(nested, nameKeys).takeIf(String::isNotBlank)?.let {
                                            put("courseName", JsonPrimitive(it))
                                        }
                                        value(nested, "id").takeIf(String::isNotBlank)?.let {
                                            put("courseId", JsonPrimitive(it))
                                        }
                                    }
                            )
                        else e
                    val name = value(effective, nameKeys)
                    val inherited =
                        parent
                            ?.let { p ->
                                buildMap<String, JsonElement> {
                                    put("courseName", JsonPrimitive(value(p, nameKeys)))
                                    put("courseId", JsonPrimitive(value(p, "courseId", "id")))
                                    value(p, "tableId", "termId", "semesterId")
                                        .takeIf(String::isNotBlank)
                                        ?.let { put("tableId", JsonPrimitive(it)) }
                                    value(p, teacherKeys).takeIf(String::isNotBlank)?.let {
                                        put("teacher", JsonPrimitive(it))
                                    }
                                    value(p, roomKeys).takeIf(String::isNotBlank)?.let {
                                        put("location", JsonPrimitive(it))
                                    }
                                }
                            }
                            .orEmpty()
                    if (hasSchedule(effective))
                        rows +=
                            JsonObject(
                                inherited +
                                    effective.filterValues {
                                        text(it).isNotBlank() || it is JsonObject
                                    }
                            )
                    val next = if (name.isNotBlank() && e["meetings"] is JsonArray) e else parent
                    e.entries
                        .filter {
                            safeField(it.key) &&
                                key(it.key) !in setOf("users", "students", "credentials")
                        }
                        .map { it.value }
                        .filter { it is JsonObject || it is JsonArray }
                        .forEach { walk(it, next, depth + 1) }
                }
                else -> {}
            }
        }
        parts.forEach { walk(it, null, 0) }
        val bases = objects.filter { row ->
            !hasSchedule(row) &&
                (value(row, "courseName", "kcmc", "课程名称").isNotBlank() ||
                    row["meetings"] is JsonArray)
        }
        fun id(row: JsonObject) = value(row, "courseId", "id", "kcid")
        fun table(row: JsonObject) = value(row, "tableId", "termId", "semesterId", "table")
        val teachers =
            objects
                .filter { value(it, "__academicDictionary") == "teacher" }
                .associate { value(it, "id") to value(it, "name") }
        val rooms =
            objects
                .filter { value(it, "__academicDictionary") == "room" }
                .associate { value(it, "id") to value(it, "name") }
        val joined =
            rows
                .map { row ->
                    val matching = bases.filter { id(it).isNotBlank() && id(it) == id(row) }
                    val exact = matching.filter { table(it) == table(row) }
                    val base = exact.singleOrNull() ?: matching.singleOrNull()
                    val merged = JsonObject(base.orEmpty() + row)
                    val teacher = value(merged, teacherKeys)
                    val teacherId =
                        value(merged, "teacherId", "instructorId", "jsid", "jsbh").ifBlank {
                            teacher
                        }
                    val room = value(merged, roomKeys)
                    val roomId =
                        value(merged, "classroomId", "roomId", "venueId", "cdid").ifBlank { room }
                    safeRow(
                        JsonObject(
                            merged +
                                buildMap {
                                    teachers[teacherId]?.let {
                                        put("teacherName", JsonPrimitive(it))
                                    }
                                    rooms[roomId]?.let { put("classroomName", JsonPrimitive(it)) }
                                }
                        )
                    )
                }
                .distinct()
        require(joined.isNotEmpty()) { "未识别到课程的星期和节次，请改用完整课表页面或文件" }
        val meta =
            objects
                .filter { row ->
                    value(
                            row,
                            "tableName",
                            "termName",
                            "startDate",
                            "semesterStartDate",
                            "startTime",
                            "endTime",
                        )
                        .isNotBlank()
                }
                .map(::safeRow)
        return decodeMapped(
            AcademicMappingData(JsonArray(joined), JsonArray(meta)),
            AcademicFieldSelection(),
            source,
        )
    }

    fun decodeMapped(
        data: AcademicMappingData,
        fields: AcademicFieldSelection,
        source: String,
    ): ImportResult {
        require(
            data.rows.size <= 10000 &&
                data.rows.toString().toByteArray().size <= BackupCodec.MAX_BYTES
        ) {
            "课表数据过多"
        }
        val rows = data.rows.filterIsInstance<JsonObject>()
        val metadata = data.metadata.filterIsInstance<JsonObject>()
        val termMeta =
            metadata.firstOrNull {
                value(it, "tableName", "termName", "semesterStartDate").isNotBlank()
            } ?: metadata.firstOrNull { !hasSchedule(it) && value(it, "startDate").isNotBlank() }
        val headers = metadata.filter {
            !hasSchedule(it) &&
                value(it, "tableName", "termName", "semesterStartDate", "startDate").isNotBlank()
        }
        fun metaFor(id: String, label: String): JsonObject? =
            headers.firstOrNull {
                id.isNotBlank() && value(it, "tableId", "termId", "semesterId", "id") == id
            }
                ?: headers.firstOrNull {
                    label.isNotBlank() && value(it, "tableName", "termName") == label
                }
                ?: termMeta
        val totalExplicit =
            value(termMeta ?: JsonObject(emptyMap()), "totalWeeks", "maxWeek", "semesterTotalWeeks")
                .toIntOrNull()
        val rejected = mutableListOf<String>()
        val notices = mutableListOf<String>()
        data class Row(
            val name: String,
            val teacher: String,
            val room: String,
            val day: Int,
            val start: Int,
            val end: Int,
            val weeks: Set<Int>,
            val table: String,
            val tableId: String,
        )
        val parsed = mutableListOf<Row>()
        var accepted = 0
        var noWeeks = false
        fun selected(row: JsonObject, override: String, keys: List<String>) =
            if (override.isBlank()) value(row, keys) else text(row[override]).trim()
        for ((index, row) in rows.withIndex()) try {
            val name = selected(row, fields.name, nameKeys)
            require(name.isNotBlank() && name.length <= 200) { "课程名称为空或过长" }
            val teacher =
                selected(row, fields.teacher, teacherKeys)
                    .takeUnless { fields.teacher.isBlank() && it.matches(Regex("[0-9]+")) }
                    .orEmpty()
            val room = selected(row, fields.location, roomKeys)
            require(teacher.length <= 200 && room.length <= 200) { "教师或地点字段过长，请核对字段映射" }
            val day = TabularCodec.weekday(value(row, dayKeys))
            val sectionValue = sectionKeys.firstNotNullOfOrNull { k ->
                row.entries.firstOrNull { key(it.key) == key(k) }?.value
            }
            val sections =
                if (sectionValue is JsonArray)
                    sectionValue.mapNotNull { text(it).toIntOrNull() }.toSet()
                else emptySet()
            val start = value(row, startKeys).toIntOrNull()
            val end = value(row, endKeys).toIntOrNull()
            val nodes =
                if (sections.isNotEmpty()) sections
                else if (start != null) {
                    val last =
                        end
                            ?: (start +
                                (value(row, "step", "duration", "length").toIntOrNull() ?: 1) - 1)
                    require(start in 1..24 && last in start..24) { "节次应为1–24" }
                    (start..last).toSet()
                } else TabularCodec.readWeeks(text(sectionValue).replace("节", ""), 24)
            require(nodes.isNotEmpty() && nodes.all { it in 1..24 }) { "节次应为1–24" }
            val rawWeeks = value(row, weekKeys)
            val first = value(row, "startWeek").toIntOrNull()
            val last = value(row, "endWeek").toIntOrNull()
            val weeks =
                if (rawWeeks.isNotBlank()) TabularCodec.readWeeks(rawWeeks, 60)
                    else {
                        if (first == null && last == null) noWeeks = true
                        val a = first ?: 1
                        val b = last ?: totalExplicit ?: 20
                        require(a in 1..60 && b in a..60) { "周次应为1–60" }
                        (a..b).toSet()
                    }
                    .filter {
                        when (value(row, "type", "weekType").lowercase()) {
                            "1",
                            "odd" -> it % 2 == 1
                            "2",
                            "even" -> it % 2 == 0
                            else -> true
                        }
                    }
                    .toSet()
            require(weeks.isNotEmpty()) { "上课周次为空" }
            val sorted = nodes.sorted()
            var firstNode = sorted.first()
            var lastNode = firstNode
            fun add() {
                parsed +=
                    Row(
                        name,
                        teacher,
                        room,
                        day,
                        firstNode,
                        lastNode,
                        weeks,
                        value(row, "tableName", "termName").ifBlank {
                            value(termMeta ?: JsonObject(emptyMap()), "tableName", "termName")
                                .ifBlank { "导入课表" }
                        },
                        value(row, "tableId", "termId", "semesterId"),
                    )
            }
            for (node in sorted.drop(1)) {
                if (node == lastNode + 1) lastNode = node
                else {
                    add()
                    firstNode = node
                    lastNode = node
                }
            }
            add()
            accepted++
        } catch (e: Exception) {
            rejected += "第 ${index+1} 条：${e.message.orEmpty().take(100)}"
        }
        require(parsed.isNotEmpty()) { "没有可识别的课程：${rejected.firstOrNull().orEmpty()}" }
        fun minute(s: String) = s.split(':').let { it[0].toInt() * 60 + it[1].toInt() }
        val tableRows = mutableListOf<List<String>>(TabularCodec.headers)
        var unknownDate = false
        var unknownTime = false
        val groups = parsed.groupBy { it.tableId.ifBlank { it.table } }
        require(groups.size <= 100) { "课表数量过多" }
        for ((groupIndex, entry) in groups.entries.withIndex()) {
            val items = entry.value
            val sample = items.first()
            val meta = metaFor(sample.tableId, sample.table) ?: JsonObject(emptyMap())
            val rawDate = value(meta, "startDate", "semesterStartDate").take(10)
            if (runCatching { java.time.LocalDate.parse(rawDate) }.isFailure) unknownDate = true
            val total =
                value(meta, "totalWeeks", "maxWeek", "semesterTotalWeeks").toIntOrNull()
                    ?: maxOf(20, items.maxOf { it.weeks.max() })
            require(total in 1..60) { "总周数应为1–60" }
            val timeId = value(meta, "timeTable", "timeTableId")
            val available = metadata.filter {
                value(it, "startTime").isNotBlank() && value(it, "endTime").isNotBlank()
            }
            val times =
                available
                    .filter {
                        if (timeId.isNotBlank()) value(it, "timeTable", "timeTableId") == timeId
                        else
                            available
                                .map { r -> value(r, "timeTable", "timeTableId") }
                                .distinct()
                                .size <= 1
                    }
                    .sortedBy { value(it, "node", "index").toIntOrNull() ?: 0 }
                    .distinctBy { value(it, "node", "startTime") }
            val periods = runCatching {
                times.map { Period(minute(value(it, "startTime")), minute(value(it, "endTime"))) }
            }
                .getOrDefault(emptyList())
            val maxNode = items.maxOf { it.end }
            val timetable = periods.ifEmpty {
                if (maxNode <= 10) defaultPeriods()
                else List(maxNode) { Period(360 + it * 40, 395 + it * 40) }
            }
            require(timetable.size >= maxNode) { "作息节数不足，请核对完整作息" }
            if (periods.isEmpty()) {
                unknownTime = true
                notices += "未提供作息，请核对上课时间"
            }
            val periodData =
                BackupCodec.json.encodeToString(
                    kotlinx.serialization.serializer<List<Period>>(),
                    timetable,
                )
            var label = value(meta, "tableName", "termName").ifBlank { sample.table }
            if (
                groups.values.count { other ->
                    val first = other.first()
                    val header = metaFor(first.tableId, first.table)
                    value(header ?: JsonObject(emptyMap()), "tableName", "termName").ifBlank {
                        first.table
                    } == label
                } > 1
            )
                label += " ${groupIndex+1}"
            items.forEach { r ->
                tableRows +=
                    listOf(
                        r.name,
                        r.teacher,
                        r.room,
                        r.day.toString(),
                        r.start.toString(),
                        r.end.toString(),
                        r.weeks.sorted().joinToString(","),
                        "",
                        label,
                        rawDate,
                        total.toString(),
                        "1",
                        periodData,
                    )
            }
        }
        if (noWeeks) notices += "部分记录未识别周次，暂按整学期，请核对"
        val result = TabularCodec.documents(tableRows)
        rejected += result.warnings.filter { it.startsWith("第 ") && it.contains("行") }
        return result.copy(
            warnings = (result.warnings + notices + rejected.take(20)).distinct(),
            assessment =
                ImportAssessment(
                    source,
                    rows.size,
                    accepted,
                    rejected,
                    unknownDate,
                    noWeeks,
                    unknownTime,
                ),
            mapping = data,
        )
    }
}
