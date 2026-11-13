package com.qingluan.schedule

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import com.qingluan.schedule.transfer.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ExchangeCodecTest {
    private val time = Timetable(id = "p")
    private val term =
        Term(id = "t", name = "秋季课表", startDate = "2026-09-07", weeks = 20, timetableId = "p")
    private val course =
        Course(
            id = "c",
            termId = "t",
            name = "数学\"一\"",
            teacher = "张,老师",
            location = "A302",
            note = "参考资料\nA&B",
            meetings =
                listOf(
                    Meeting(
                        id = "m",
                        weekday = 1,
                        startPeriod = 1,
                        endPeriod = 2,
                        weeks = setOf(1, 3, 5),
                    )
                ),
        )

    private fun document() =
        TimetableDocument(
            term = term,
            timetable = time,
            courses = listOf(course),
            exceptions =
                listOf(
                    CourseException(
                        id = "x",
                        courseId = "c",
                        meetingId = "m",
                        originalDate = "2026-09-21",
                    )
                ),
        )

    @Test
    fun nativeDocumentAndCompressedShareCodePreserveAdjustments() {
        val d = document().validate()
        assertEquals(d, TimetableDocument.decode(BackupCodec.json.encodeToString(d)))
        assertEquals(d, ShareCode.decode(ShareCode.encode(d)))
        assertEquals(d, CalendarCodec.decode(CalendarCodec.encode(d)).documents.single())
    }

    @Test
    fun csvExcelAndHtmlKeepWeeksQuotesAndTimetable() {
        val d = document()
        val rows = TabularCodec.rows(d)
        val inputs =
            listOf(
                TabularCodec.csv(rows).toByteArray(),
                TabularCodec.xlsx(rows),
                TabularCodec.html(rows).toByteArray(),
            )
        inputs.forEach { bytes ->
            val imported = ImportCodec.decode(bytes).documents.single()
            val c = imported.courses.single()
            assertEquals(course.name, c.name)
            assertEquals(course.teacher, c.teacher)
            assertEquals(course.location, c.location)
            assertEquals(course.note, c.note)
            assertEquals(setOf(1, 3, 5), c.meetings.single().weeks)
            assertEquals(2, c.meetings.single().endPeriod)
            assertEquals(time.periods, imported.timetable.periods)
            assertEquals(term.startDate, imported.term.startDate)
        }
    }

    @Test
    fun academicMultiCourseBlocksAndMetadataBlankLinesKeepEveryCourse() {
        val names =
            listOf(
                "大学英语发展课程",
                "离散数学",
                "马克思主义基本原理",
                "Python高级应用",
                "计算机系统基础",
                "Linux网络操作系统",
                "体育III",
                "电子技术",
            )
        fun block(name: String, index: Int) =
            "<div class='timetable_con'><p class='title'>$name</p>" +
                "<br><br><p><span title='教师'>教师$index</span></p><p><span title='教室'>A30$index</span></p>" +
                "<p>1-8(周),10-16周</p><p>[01-02]节</p></div>"
        fun table(start: Int, end: Int) =
            "<table><tr><th>节次</th>" +
                listOf("一", "二", "三", "四", "五").joinToString("") { "<th>周$it</th>" } +
                "</tr><tr><td>1-2节</td><td><div class='kbcontent'>" +
                (start until end).joinToString("") { block(names[it], it) } +
                "</div></td><td></td><td></td><td></td><td></td></tr></table>"
        val first = table(0, 4)
        val result =
            ImportCodec.decode((first + table(4, 8) + first).toByteArray()).documents.single()
        assertEquals(names.toSet(), result.courses.map { it.name }.toSet())
        assertEquals(8, result.courses.size)
        assertEquals(8, result.courses.sumOf { it.meetings.size })
        result.courses.forEach { course ->
            val i = names.indexOf(course.name)
            assertEquals("教师$i", course.teacher)
            assertEquals("A30$i", course.location)
            assertEquals((1..16).filter { it != 9 }.toSet(), course.meetings.single().weeks)
            assertEquals(2, course.meetings.single().endPeriod)
        }
    }

    @Test
    fun blankLinesInsideUnstructuredCourseDoNotLoseTeacherOrRoom() {
        val html =
            "<table><tr><th>节次</th>" +
                listOf("一", "二", "三", "四", "五").joinToString("") { "<th>周$it</th>" } +
                "</tr><tr><td>1-2节</td><td>大学英语<br><br>教师：王明<br>教室：A301<br>1-16周<br>1-2节<br><br>离散数学<br>教师：李明<br>教室：B302<br>1-16周<br>1-2节</td><td></td><td></td><td></td><td></td></tr></table>"
        val courses = ImportCodec.decode(html.toByteArray()).documents.single().courses
        assertEquals(setOf("大学英语", "离散数学"), courses.map { it.name }.toSet())
        assertEquals(setOf("王明", "李明"), courses.map { it.teacher }.toSet())
        assertEquals(setOf("A301", "B302"), courses.map { it.location }.toSet())
    }

    @Test
    fun zhengfangParagraphAndLegacyFontFieldsPreserveTeacherAndNumericRoom() {
        fun grid(content: String) =
            "<table id='kbgrid_table_0'><tr><th>节次</th>" +
                listOf("一", "二", "三", "四", "五").joinToString("") { "<th>周$it</th>" } +
                "</tr><tr><td>1-2节</td><td class='td_wrap'>$content</td><td></td><td></td><td></td><td></td></tr></table>"
        val modern =
            "<div class='timetable_con text-left'><div class='title'><font>大学英语</font></div>" +
                "<p><font>时间</font><font color='blue'>(1-2节)1-16周</font></p>" +
                "<p><font>地点</font><font color='blue'>400446—004</font></p>" +
                "<p><font>教师</font><font color='blue'>David Chen</font></p></div>"
        val c = ImportCodec.decode(grid(modern).toByteArray()).documents.single().courses.single()
        assertEquals("大学英语", c.name)
        assertEquals("David Chen", c.teacher)
        assertEquals("400446—004", c.location)
        val legacy =
            "<font>离散数学</font><font>(1-2节)1-16周</font><font>022409</font><font>陈老师</font>" +
                "<font>课程代码：110033</font><font>电子技术</font><font>(3-4节)1-16周</font><font>400299</font><font>王老师</font>"
        val courses = ImportCodec.decode(grid(legacy).toByteArray()).documents.single().courses
        assertEquals(2, courses.size)
        assertEquals(setOf("022409", "400299"), courses.map { it.location }.toSet())
        assertEquals(setOf("陈老师", "王老师"), courses.map { it.teacher }.toSet())
    }

    @Test
    fun academicLabelIconsAndDataAttributesPreserveMetadata() {
        fun grid(content: String) =
            "<table><tr><th>节次</th>" +
                listOf("一", "二", "三", "四", "五").joinToString("") { "<th>周$it</th>" } +
                "</tr><tr><td>1-2节</td><td>$content</td><td></td><td></td><td></td><td></td></tr></table>"
        val icon =
            "<b>数学</b><p><i class='glyphicon-user'></i><font>张老师</font></p><p><i class='glyphicon-map-marker'></i><font>400446—004</font></p><p>1-16周 1-2节</p>"
        val c = ImportCodec.decode(grid(icon).toByteArray()).documents.single().courses.single()
        assertEquals("张老师", c.teacher)
        assertEquals("400446—004", c.location)
        val data =
            "<div class='course-item' data-teacher-name='陈老师' data-room='110033—055'><b>政策</b><br>1-16周<br>1-2节</div>"
        val d = ImportCodec.decode(grid(data).toByteArray()).documents.single().courses.single()
        assertEquals("陈老师", d.teacher)
        assertEquals("110033—055", d.location)
        val attrs =
            "<b>英语</b><span title='教师'></span>\n<font>刘老师</font><span data-original-title='教室：400446—004'></span><br>1-16周 1-2节"
        val e = ImportCodec.decode(grid(attrs).toByteArray()).documents.single().courses.single()
        assertEquals("刘老师", e.teacher)
        assertEquals("400446—004", e.location)
    }

    @Test
    fun zhengfangJsonUsesTeacherAndVenueInsteadOfCourseCode() {
        val data =
            """{"kbList":[{"kcmc":"数学","xm":"王老师","cdmc":"022409","kch":"110033","xqj":"2","jcs":"1-2","zcd":"1-16周"}]}"""
        val c = ImportCodec.decode(data.toByteArray()).documents.single().courses.single()
        assertEquals("王老师", c.teacher)
        assertEquals("022409", c.location)
        assertEquals(2, c.meetings.single().endPeriod)
    }

    @Test
    fun excelExternalEntitiesAreRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(
                """<!DOCTYPE sheet [<!ENTITY ext SYSTEM "file:///etc/passwd">]><worksheet><sheetData><row><c t="inlineStr"><is><t>&ext;</t></is></c></row></sheetData></worksheet>"""
                    .toByteArray()
            )
            zip.closeEntry()
        }
        assertTrue(runCatching { TabularCodec.readXlsx(out.toByteArray()) }.isFailure)
    }

    @Test
    fun genericAcademicHtmlGridExtractsWeekdayAndPeriods() {
        val html =
            """<table><tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th><th>周六</th><th>周日</th></tr><tr><td>1-2节</td><td><b>高等数学</b><br>教师：陈老师<br>教室：A301<br>1-16周(单)<br>1-2节</td><td></td><td></td><td></td><td></td><td></td><td></td></tr></table>"""
        val d = ImportCodec.decode(html.toByteArray()).documents.single()
        val c = d.courses.single()
        assertEquals("高等数学", c.name)
        assertEquals("陈老师", c.teacher)
        assertEquals("A301", c.location)
        assertEquals(1, c.meetings.single().weekday)
        assertEquals(2, c.meetings.single().endPeriod)
        assertEquals((1..16).filter { it % 2 == 1 }.toSet(), c.meetings.single().weeks)
    }

    @Test
    fun academicSemanticFieldsDoNotMistakeCourseCodesForRooms() {
        fun grid(content: String) =
            "<table><tr><th>节次</th>" +
                listOf("一", "二", "三", "四", "五").joinToString("") { "<th>周$it</th>" } +
                "</tr><tr><td>1-2节</td><td>$content</td><td></td><td></td><td></td><td></td></tr></table>"
        val content =
            "<b>高等数学</b><br><font title='课程号'>110033</font><br>" +
                "<font title='教师'>王明</font><br><font title='教室'>400446—004</font><br>1-16周<br>1-2节"
        val course =
            ImportCodec.decode(grid(content).toByteArray()).documents.single().courses.single()
        assertEquals("王明", course.teacher)
        assertEquals("400446—004", course.location)
        val ambiguous =
            ImportCodec.decode(grid("<b>高等数学</b><br>110033<br>王明<br>1-16周<br>1-2节").toByteArray())
                .documents
                .single()
                .courses
                .single()
        assertEquals("", ambiguous.teacher)
        assertEquals("", ambiguous.location)
        val csv = "课程名称,授课教师,课程代码,上课地点,星期,开始节次,结束节次,周次\n高等数学,王明,110033,A301,1,1,2,1-16"
        val tabular = ImportCodec.decode(csv.toByteArray()).documents.single().courses.single()
        assertEquals("王明", tabular.teacher)
        assertEquals("A301", tabular.location)
    }

    @Test
    fun emptyMeetingTeacherDoesNotEraseBaseTeacherAndAliasesAreRead() {
        val json =
            """[{"id":"c","courseName":"数学","teacher":"王明"},{"courseId":"c","day":1,"startPeriod":1,"endPeriod":2,"teacher":"","courseCode":"110033","room":"A301"}]"""
        val course = ImportCodec.decode(json.toByteArray()).documents.single().courses.single()
        assertEquals("王明", course.teacher)
        assertEquals("A301", course.location)
        val aliases =
            """[{"courseName":"数学","day":1,"startPeriod":1,"jsxm":"张老师","cdmc":"B205","courseCode":"110033"}]"""
        val aliased = ImportCodec.decode(aliases.toByteArray()).documents.single().courses.single()
        assertEquals("张老师", aliased.teacher)
        assertEquals("B205", aliased.location)
    }

    @Test
    fun plaintextMultiPartJsonAndNestedMeetingRecordsImport() {
        val text =
            """{"id":1,"tableName":"学期","startDate":"2026-09-07","totalWeeks":20,"timeTable":1}
[{"node":1,"startTime":"08:00","endTime":"08:45","timeTable":1},{"node":2,"startTime":"08:55","endTime":"09:40","timeTable":1}]
[{"id":1,"courseName":"数学","teacher":"陈老师"}]
[{"id":1,"day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":1,"room":"A301"}]"""
        val d = ImportCodec.decode(text.toByteArray()).documents.single()
        assertEquals("数学", d.courses.single().name)
        assertEquals("陈老师", d.courses.single().teacher)
        assertEquals(2, d.timetable.periods.size)
        val nested =
            """{"tableName":"学期","startDate":"2026-09-07","courses":[{"id":"c","name":"英语","meetings":[{"id":"m","weekday":2,"startPeriod":3,"endPeriod":4,"weeks":[1,3]}]}]}"""
        assertEquals(
            setOf(1, 3),
            ImportCodec.decode(nested.toByteArray())
                .documents
                .single()
                .courses
                .single()
                .meetings
                .single()
                .weeks,
        )
    }

    @Test
    fun standardCalendarRecurrenceExclusionsAndDatesImport() {
        val text =
            """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:test
DTSTART:20260907T080000
DTEND:20260907T084500
SUMMARY:数学
RRULE:FREQ=WEEKLY;COUNT=3;BYDAY=MO
EXDATE:20260914T080000
RDATE:20260929T080000
END:VEVENT
END:VCALENDAR"""
        val d = CalendarCodec.decode(text).documents.single()
        val s = d.snapshot()
        assertEquals(1, ScheduleEngine.onDate(s, d.term.id, LocalDate.parse("2026-09-07")).size)
        assertTrue(ScheduleEngine.onDate(s, d.term.id, LocalDate.parse("2026-09-14")).isEmpty())
        assertEquals(1, ScheduleEngine.onDate(s, d.term.id, LocalDate.parse("2026-09-29")).size)
    }

    @Test
    fun mergeRemapsIdsWithoutChangingExistingCourses() {
        val d = document()
        val original = d.snapshot()
        val appended = d.appendTo(original, term.id)
        assertEquals(1, appended.terms.size)
        assertEquals(2, appended.courses.size)
        assertEquals(2, appended.courses.map { it.id }.distinct().size)
        assertEquals(2, appended.exceptions.size)
        assertEquals(1, original.courses.size)
        assertThrows(Exception::class.java) {
            d.copy(term = term.copy(startDate = "2026-09-14")).appendTo(original, term.id)
        }
    }

    @Test
    fun fullCompressedBackupIsBoundedAndRoundTrips() {
        val b = Backup(data = document().snapshot())
        assertEquals(b, ExchangeFiles.backup(ExchangeFiles.zipBackup(BackupCodec.encode(b))))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("unexpected.bin"))
            zip.write(ByteArray(BackupCodec.MAX_BYTES + 1))
            zip.closeEntry()
        }
        assertThrows(Exception::class.java) { ExchangeFiles.backup(out.toByteArray()) }
        assertThrows(Exception::class.java) {
            ImportCodec.decode(ByteArray(BackupCodec.MAX_BYTES + 1))
        }
    }

    @Test
    fun commonCombinedPeriodColumnIsRecognized() {
        val csv = "课程名称,教师,教室,星期,节次,上课周次\n线性代数,陈老师,A301,周二,3-4节,1-16周(双)"
        val course = ImportCodec.decode(csv.toByteArray()).documents.single().courses.single()
        assertEquals(3, course.meetings.single().startPeriod)
        assertEquals(4, course.meetings.single().endPeriod)
        assertEquals((2..16 step 2).toSet(), course.meetings.single().weeks)
    }
}
