package com.qingluan.schedule.transfer

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray

@Serializable
data class TimetableDocument(
    val format: String = "QingluanTimetable",
    val version: Int = 1,
    val term: Term,
    val timetable: Timetable,
    val courses: List<Course>,
    val exceptions: List<CourseException> = emptyList(),
    val calendarRules: List<CalendarRule> = emptyList(),
) {
    fun snapshot() =
        Snapshot(
            activeTermId = term.id,
            terms = listOf(term),
            timetables = listOf(timetable),
            courses = courses,
            exceptions = exceptions,
            calendarRules = calendarRules,
        )

    fun validate(): TimetableDocument {
        require(format == "QingluanTimetable" && version == 1) { "不支持的课表格式" }
        BackupCodec.validate(Backup(data = snapshot()))
        return this
    }

    fun renamed(name: String, startDate: String, weeks: Int): TimetableDocument =
        copy(term = term.copy(name = name, startDate = startDate, weeks = weeks)).validate()

    fun appendTo(current: Snapshot, mergeTermId: String? = null): Snapshot {
        validate()
        val target = mergeTermId?.let { id ->
            current.terms.firstOrNull { it.id == id } ?: error("目标课表不存在")
        }
        val termId = target?.id ?: newId()
        val timeId = target?.timetableId ?: newId()
        if (target != null) {
            val time = current.timetables.first { it.id == timeId }
            require(
                time.periods == timetable.periods &&
                    target.startDate == term.startDate &&
                    target.weeks == term.weeks &&
                    target.firstDayOfWeek == term.firstDayOfWeek
            ) {
                "课表日期、周数和作息不同，请作为新课表导入"
            }
        }
        val ids = courses.associate { it.id to newId() }
        val meetingIds =
            courses.flatMap { c -> c.meetings.map { (c.id to it.id) to newId() } }.toMap()
        val imported = courses.map { c ->
            c.copy(
                id = ids.getValue(c.id),
                termId = termId,
                meetings = c.meetings.map { it.copy(id = meetingIds.getValue(c.id to it.id)) },
            )
        }
        val changes = exceptions.map { x ->
            x.copy(
                id = newId(),
                courseId = ids.getValue(x.courseId),
                meetingId = meetingIds.getValue(x.courseId to x.meetingId),
            )
        }
        val rules = calendarRules.map { it.copy(id = newId(), termId = termId) }
        val existingRules =
            current.calendarRules.filter { it.termId == termId }.associateBy { it.date }
        require(
            rules.none {
                existingRules[it.date]?.let { old -> old.teachingDate != it.teachingDate } == true
            }
        ) {
            "调休设置不同，请新建课表导入"
        }
        return current
            .copy(
                activeTermId = termId,
                terms =
                    if (target == null) current.terms + term.copy(id = termId, timetableId = timeId)
                    else current.terms,
                timetables =
                    if (target == null) current.timetables + timetable.copy(id = timeId)
                    else current.timetables,
                courses = current.courses + imported,
                exceptions = current.exceptions + changes,
                calendarRules = current.calendarRules + rules.filter { it.date !in existingRules },
            )
            .also { BackupCodec.validate(Backup(data = it)) }
    }

    companion object {
        fun from(s: Snapshot, id: String): TimetableDocument {
            val t = s.terms.firstOrNull { it.id == id } ?: error("请先创建课表")
            val courses = s.courses.filter { it.termId == id }
            val ids = courses.map { it.id }.toSet()
            return TimetableDocument(
                    term = t,
                    timetable = s.timetables.first { it.id == t.timetableId },
                    courses = courses,
                    exceptions = s.exceptions.filter { it.courseId in ids },
                    calendarRules = s.calendarRules.filter { it.termId == id },
                )
                .validate()
        }

        fun decode(text: String) =
            BackupCodec.json.decodeFromString<TimetableDocument>(text).validate()
    }
}

object ShareCode {
    const val PREFIX = "QINGLUAN1:"

    fun encode(doc: TimetableDocument): String {
        val output = ByteArrayOutputStream()
        val raw = BackupCodec.json.encodeToString(doc.validate()).toByteArray()
        require(raw.size <= BackupCodec.MAX_BYTES) { "课表超过 8 MiB" }
        DeflaterOutputStream(output).use { it.write(raw) }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
    }

    fun decode(code: String): TimetableDocument {
        require(code.trim().startsWith(PREFIX)) { "这不是青鸾课表分享口令" }
        val data = Base64.getUrlDecoder().decode(code.trim().removePrefix(PREFIX))
        val bytes =
            InflaterInputStream(data.inputStream()).use { it.readNBytes(BackupCodec.MAX_BYTES + 1) }
        require(bytes.size <= BackupCodec.MAX_BYTES) { "分享内容过大" }
        return TimetableDocument.decode(bytes.toString(Charsets.UTF_8))
    }
}

data class ImportResult(
    val documents: List<TimetableDocument>,
    val warnings: List<String> = emptyList(),
    val assessment: ImportAssessment? = null,
    val mapping: AcademicMappingData? = null,
    val sources: List<ImportSource> = emptyList(),
    val selectedSource: Int = 0,
) {
    val previewKey = Any()

    init {
        require(documents.isNotEmpty()) { "未识别到课程，请选择课表文件或打开完整课表页面" }
        documents.forEach { it.validate() }
    }
}

data class ImportAssessment(
    val source: String,
    val detectedRows: Int,
    val acceptedRows: Int,
    val rejected: List<String> = emptyList(),
    val dateUnknown: Boolean = false,
    val incomplete: Boolean = false,
    val timeUnknown: Boolean = false,
)

data class ImportSource(val label: String, val result: ImportResult)

data class AcademicMappingData(
    val rows: JsonArray,
    val metadata: JsonArray = JsonArray(emptyList()),
)

data class AcademicFieldSelection(
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
)

enum class ExchangeFormat(val title: String, val extension: String, val mime: String) {
    JSON("JSON 完整课表", "json", "application/json"),
    CSV("CSV 表格", "csv", "text/csv"),
    XLSX("Excel 表格", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ICS("ICS 日历", "ics", "text/calendar"),
    HTML("HTML 课表", "html", "text/html"),
    CODE("分享口令", "txt", "text/plain"),
    QR("课表二维码", "png", "image/png"),
    IMAGE("课表图片", "png", "image/png"),
    PDF("PDF 课表", "pdf", "application/pdf"),
}
