package com.qingluan.schedule.data

import com.qingluan.schedule.model.*
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
data class AppSettings(
    val theme: String = "system",
    val dynamicColor: Boolean = false,
    val showWeekend: Boolean = true,
    val fontScale: Float = 1f,
    val rowHeight: Int = 64,
    val startupPage: Int = 0,
    val autoBackupEnabled: Boolean = true,
    val autoBackupKeep: Int = 5,
    val courseFontScale: Float = 1f,
    val courseCornerRadius: Int = 10,
    val courseOpacity: Float = 1f,
    val showGridLines: Boolean = true,
    val showSectionTime: Boolean = true,
    val showDates: Boolean = true,
    val showTeacher: Boolean = true,
    val showLocation: Boolean = true,
    val centerCourseText: Boolean = false,
    val timeColumnWidth: Int = 44,
    val dayHeaderHeight: Int = 50,
    val courseOuterPadding: Int = 2,
    val courseInnerPadding: Int = 4,
    val centerCourseVertically: Boolean = false,
    val boldCourseNames: Boolean = true,
    val showCourseBorders: Boolean = true,
    val showCourseStartTime: Boolean = false,
    val reminders: Boolean = false,
    val leadMinutes: Int = 10,
    val showNonCurrentWeek: Boolean = false,
    val hapticsEnabled: Boolean = false,
    val animationsEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val palette: String = "jade",
)

@Serializable
data class Backup(
    val app: String = "QingluanSchedule",
    val version: Int = 1,
    val data: Snapshot = Snapshot(),
    val settings: AppSettings = AppSettings(),
)

object BackupCodec {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        prettyPrint = true
    }
    const val MAX_BYTES = 8 * 1024 * 1024

    fun encode(b: Backup): String {
        validate(b)
        val encoded = json.encodeToString(b)
        require(encoded.toByteArray().size <= MAX_BYTES) { "备份超过 8 MiB，请分别导出课表" }
        return encoded
    }

    fun decode(text: String): Backup {
        require(text.toByteArray().size <= MAX_BYTES) { "备份文件超过 8 MiB" }
        val envelope = json.parseToJsonElement(text).jsonObject
        require(envelope.keys.containsAll(listOf("app", "version", "data", "settings"))) {
            "这不是完整的青鸾课程表备份"
        }
        val payload = envelope.getValue("data").jsonObject
        require(
            payload.keys.containsAll(
                listOf(
                    "formatVersion",
                    "activeTermId",
                    "terms",
                    "timetables",
                    "courses",
                    "exceptions",
                )
            )
        ) {
            "备份缺少必要的课表数据"
        }
        return json.decodeFromString<Backup>(text).also(::validate)
    }

    fun validate(b: Backup) {
        require(b.app == "QingluanSchedule" && b.version == 1 && b.data.formatVersion == 1) {
            "不支持此备份格式"
        }
        val s = b.data
        require(
            s.terms.size <= 100 &&
                s.timetables.size in 1..100 &&
                s.courses.size <= 3000 &&
                s.exceptions.size <= 10000 &&
                s.calendarRules.size <= 2000
        ) {
            "备份数据过多"
        }
        fun ids(values: List<String>) {
            require(
                values.all { it.isNotBlank() && it.length <= 100 } &&
                    values.distinct().size == values.size
            ) {
                "数据标识重复或无效"
            }
        }
        ids(s.terms.map { it.id })
        ids(s.timetables.map { it.id })
        ids(s.courses.map { it.id })
        ids(s.exceptions.map { it.id })
        require(
            s.activeTermId.isBlank() && s.terms.isEmpty() || s.terms.any { it.id == s.activeTermId }
        ) {
            "当前学期不存在"
        }
        s.timetables.forEach { t ->
            require(t.name.isNotBlank() && t.name.length <= 100 && t.periods.size in 1..24) {
                "作息名称或节数无效"
            }
            t.periods.forEach {
                require(it.start in 0..1438 && it.end in it.start + 1..1439) { "作息时间无效" }
            }
            require(t.periods.zipWithNext().all { (a, b) -> a.end <= b.start }) { "节次时间不能重叠或倒序" }
        }
        s.terms.forEach { t ->
            require(t.firstDayOfWeek in 1..7) { "每周起始日无效" }
            require(t.name.isNotBlank() && t.name.length <= 100 && t.weeks in 1..60) { "学期名称或周数无效" }
            val d = LocalDate.parse(t.startDate)
            require(d.year in 2000..2100) { "日期应在 2000–2100 年" }
            require(s.timetables.any { it.id == t.timetableId }) { "学期缺少作息表" }
        }
        s.courses.forEach { c ->
            val t = s.terms.find { it.id == c.termId } ?: error("课程所属学期不存在")
            val p = s.timetables.first { it.id == t.timetableId }
            require(
                c.name.isNotBlank() &&
                    c.name.length <= 120 &&
                    c.note.length <= 4000 &&
                    c.teacher.length <= 200 &&
                    c.location.length <= 200
            ) {
                "课程字段长度无效"
            }
            require(c.meetings.size in 1..30) { "每门课程需要 1–30 个上课时段" }
            ids(c.meetings.map { it.id })
            c.meetings.forEach { m ->
                require(
                    m.weekday in 1..7 &&
                        m.startPeriod in 1..p.periods.size &&
                        m.endPeriod in m.startPeriod..p.periods.size
                ) {
                    "课程星期或节次超出范围"
                }
                require(m.weeks.isNotEmpty() && m.weeks.all { it in 1..t.weeks }) { "上课周次超出学期范围" }
                require(m.teacher.length <= 200 && m.location.length <= 200) { "时段字段过长" }
            }
        }
        ids(s.calendarRules.map { it.id })
        val ruleDates = mutableSetOf<Pair<String, String>>()
        s.calendarRules.forEach { rule ->
            val term = s.terms.firstOrNull { it.id == rule.termId } ?: error("调休所属课表不存在")
            val date = LocalDate.parse(rule.date)
            require(ScheduleEngine.week(term, date) in 1..term.weeks && rule.name.length <= 80) {
                "调休日期超出学期或名称过长"
            }
            require(ruleDates.add(rule.termId to rule.date)) { "同一天的调休重复" }
            if (rule.teachingDate.isNotBlank()) {
                val source = LocalDate.parse(rule.teachingDate)
                require(source != date && ScheduleEngine.week(term, source) in 1..term.weeks) {
                    "来源日期无效或超出学期"
                }
            }
        }
        val sourceKeys = mutableSetOf<String>()
        s.exceptions.forEach { x ->
            val c = s.courses.find { it.id == x.courseId } ?: error("单次调整的课程不存在")
            val m = c.meetings.find { it.id == x.meetingId } ?: error("单次调整的时段不存在")
            val t = s.terms.first { it.id == c.termId }
            val count = s.timetables.first { it.id == t.timetableId }.periods.size
            require(x.originalDate.isNotBlank() || x.originalTeachingDate.isBlank()) {
                "补课不应包含原教学日期"
            }
            require(x.originalDate.isNotBlank() || x.targetDate.isNotBlank()) { "单次调整缺少日期" }
            if (x.originalDate.isNotBlank()) {
                val actual = LocalDate.parse(x.originalDate)
                require(ScheduleEngine.week(t, actual) in 1..t.weeks) { "单次调整日期超出学期" }
                val d = LocalDate.parse(x.originalTeachingDate.ifBlank { x.originalDate })
                require(d.dayOfWeek.value == m.weekday && ScheduleEngine.week(t, d) in m.weeks) {
                    "单次调整来源不属于该课程"
                }
                require(sourceKeys.add("${c.id}:${m.id}:${x.originalDate}")) { "同一课次重复调整" }
            }
            if (x.targetDate.isNotBlank()) {
                val d = LocalDate.parse(x.targetDate)
                require(ScheduleEngine.week(t, d) in 1..t.weeks) { "调课日期超出学期" }
                require(x.startPeriod in 1..count && x.endPeriod in x.startPeriod..count) {
                    "调课节次无效"
                }
            }
            require(x.location.length <= 200 && x.teacher.length <= 200) { "单次调整字段过长" }
        }
        val p = b.settings
        require(
            p.theme in listOf("system", "light", "dark") &&
                p.palette in listOf("jade", "iris", "ocean", "rose", "amber", "slate") &&
                p.fontScale in 0.85f..1.5f &&
                p.rowHeight in 48..180 &&
                p.startupPage in 0..2 &&
                p.courseFontScale.isFinite() &&
                p.courseFontScale in .75f..1.5f &&
                p.timeColumnWidth in 32..72 &&
                p.dayHeaderHeight in 36..80 &&
                p.courseOuterPadding in 0..6 &&
                p.courseInnerPadding in 0..12 &&
                p.courseCornerRadius in 0..24 &&
                p.courseOpacity.isFinite() &&
                p.courseOpacity in .3f..1f &&
                p.autoBackupKeep in listOf(3, 5, 10) &&
                p.leadMinutes in 0..120
        ) {
            "设置参数无效"
        }
    }
}
