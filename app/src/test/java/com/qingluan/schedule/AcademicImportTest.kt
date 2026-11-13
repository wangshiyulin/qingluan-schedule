package com.qingluan.schedule

import com.qingluan.schedule.data.BackupCodec
import com.qingluan.schedule.transfer.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AcademicImportTest {
    private fun decode(json: String) =
        AcademicJsonCodec.decode(listOf(BackupCodec.json.parseToJsonElement(json)))

    private fun capture(
        records: String,
        html: String = "",
        incomplete: Boolean = false,
    ): ImportResult =
        WebImportPipeline.decode(
            BackupCodec.json.encodeToString(
                AcademicWebCapture.serializer(),
                AcademicWebCapture(
                    records = BackupCodec.json.parseToJsonElement(records).jsonArray,
                    documents = if (html.isBlank()) emptyList() else listOf(html),
                    incomplete = incomplete,
                ),
            )
        )

    @Test
    fun baseAndDetailJoinUsesTableAndCourseIds() {
        val result =
            decode(
                """[
          {"id":1,"tableId":10,"courseName":"数学"},
          {"id":1,"tableId":20,"courseName":"英语"},
          {"id":1,"name":"无关学生信息"},
          {"id":1,"tableId":10,"day":1,"startNode":1,"step":3,"startWeek":8,"endWeek":17,"teacher":"王明","room":"400446—004"},
          {"id":1,"tableId":20,"day":2,"startNode":4,"step":2,"weeks":[1,3],"teacher":"李华","room":"B205"},
          {"id":10,"tableName":"秋季","startDate":"2026-09-07"},
          {"id":20,"tableName":"春季","startDate":"2026-02-23"}
        ]"""
            )
        assertEquals(2, result.documents.size)
        assertEquals(listOf("秋季", "春季"), result.documents.map { it.term.name })
        val math = result.documents.single { it.term.name == "秋季" }.courses.single()
        assertEquals("数学", math.name)
        assertEquals("王明", math.teacher)
        assertEquals("400446—004", math.location)
        assertEquals((8..17).toSet(), math.meetings.single().weeks)
        assertEquals(3, math.meetings.single().endPeriod)
        assertEquals(
            "李华",
            result.documents.single { it.term.name == "春季" }.courses.single().teacher,
        )
        assertFalse(result.assessment!!.dateUnknown)
    }

    @Test
    fun sparseSectionsAndBadRowsAreNeverSilentlyFilledOrDropped() {
        val result =
            decode(
                """[
            {"courseName":"数学","day":1,"sections":[1,3],"weeks":[2,4]},
            {"courseName":"错误课程","day":9,"startNode":1,"weeks":[2]}
        ]"""
            )
        val course = result.documents.single().courses.single()
        assertEquals(listOf(1, 3), course.meetings.map { it.startPeriod })
        assertTrue(course.meetings.all { it.startPeriod == it.endPeriod })
        assertEquals(2, result.assessment!!.detectedRows)
        assertEquals(1, result.assessment!!.rejected.size)
        assertTrue(result.assessment!!.dateUnknown)
        assertTrue(result.assessment!!.timeUnknown)
    }

    @Test
    fun manualMappingCorrectsUnknownKeysWithoutInventingRoomFromCode() {
        val result =
            decode(
                """[{"courseName":"物理","day":1,"startNode":1,"weeks":[1],"lecturerCaption":"王老师","venueCaption":"实验楼301","courseCode":"110033","privateKey":"secret"}]"""
            )
        assertEquals("", result.documents.single().courses.single().location)
        assertEquals("", result.documents.single().courses.single().teacher)
        assertFalse(result.mapping!!.rows.toString().contains("secret"))
        val mapped =
            WebImportPipeline.remap(
                result,
                AcademicFieldSelection(teacher = "lecturerCaption", location = "venueCaption"),
            )
        val course = mapped.documents.single().courses.single()
        assertEquals("王老师", course.teacher)
        assertEquals("实验楼301", course.location)
    }

    @Test
    fun dictionaryAndNestedMetadataSurviveImport() {
        val result =
            decode(
                """[
          {"__academicDictionary":"teacher","id":"12","name":"王老师"},
          {"__academicDictionary":"room","id":"13","name":"400446—004"},
          {"courseName":"数学","day":1,"startNode":1,"weeks":[1],"teacherId":"12","roomId":"13"},
          {"day":2,"startNode":1,"weeks":[1],"course":{"name":"英语","teacherName":"李老师","classroomName":"B205"}},
          {"courseName":"物理","teacherName":"张老师","classroomName":"C301","meetings":[{"day":3,"startNode":1,"weeks":[1]}]}
        ]"""
            )
        val courses = result.documents.single().courses.associateBy { it.name }
        assertEquals(3, courses.size)
        assertEquals("王老师", courses["数学"]!!.teacher)
        assertEquals("400446—004", courses["数学"]!!.location)
        assertEquals("李老师", courses["英语"]!!.teacher)
        assertEquals("B205", courses["英语"]!!.location)
        assertEquals("张老师", courses["物理"]!!.teacher)
    }

    @Test
    fun partialApiAndHtmlMergeAllCellsAndKeepExplicitMetadata() {
        val html =
            """<table><tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th></tr><tr><td>1-2节</td><td><b>数学</b><br>教师：王老师<br>教室：A301<br>1-16周<br>1-2节</td><td><b>英语</b><br>教师：李老师<br>教室：B205<br>1-16周<br>1-2节</td><td></td><td></td><td></td></tr></table>"""
        val result =
            capture(
                """[{"courseName":"数学","day":1,"startNode":1,"step":2,"weeks":"1-16","teacherName":"王老师"}]""",
                html,
            )
        assertEquals("合并识别", result.assessment!!.source)
        val courses = result.documents.single().courses.associateBy { it.name }
        assertEquals(2, courses.size)
        assertEquals("A301", courses["数学"]!!.location)
        assertEquals("李老师", courses["英语"]!!.teacher)
        assertEquals(1, courses["数学"]!!.meetings.size)
        assertEquals((1..16).toSet(), courses["数学"]!!.meetings.single().weeks)
        assertEquals(3, result.sources.size)
    }

    @Test
    fun incompleteCaptureIsVisibleAndEmptyCaptureFails() {
        val result =
            capture(
                """[{"courseName":"数学","day":1,"startNode":1,"weeks":[1]}]""",
                incomplete = true,
            )
        assertTrue(result.assessment!!.incomplete)
        assertTrue(result.warnings.any { it.contains("内嵌") })
        assertTrue(runCatching { capture("[]") }.isFailure)
    }

    @Test
    fun zhengfangIconMetadataReadsValueInsteadOfCourseName() {
        val html =
            """<table><tr><td id="1-1"><div class="timetable_con"><span class="title">数学</span>
          <p>(1-3节)8-17周</p><p title="教师"><font>教师</font><font>王老师</font></p>
          <p title="教室"><font>教室</font><font>400446—004</font></p></div></td></tr></table>"""
        val course = HtmlAcademicAdapter.decode(html).documents.single().courses.single()
        assertEquals("王老师", course.teacher)
        assertEquals("400446—004", course.location)
        assertEquals((8..17).toSet(), course.meetings.single().weeks)
        assertEquals(3, course.meetings.single().endPeriod)
    }

    @Test
    fun fieldRemappingNeverTurnsFallbackTimetableIntoConfirmedTimes() {
        val result =
            ImportCodec.decode("课程名称,教师,地点,星期,开始节次,结束节次,周次\n数学,王老师,A301,1,1,2,8-17".toByteArray())
        assertTrue(result.assessment!!.timeUnknown)
        val mapped = WebImportPipeline.remap(result, AcademicFieldSelection())
        assertTrue(mapped.assessment!!.timeUnknown)
        assertTrue(mapped.assessment!!.dateUnknown)
    }

    @Test
    fun semesterIdsSeparateSameCourseNumber() {
        val result =
            decode(
                """[
            {"semesterId":"a","courseId":"1","courseName":"数学"},
            {"semesterId":"b","courseId":"1","courseName":"英语"},
            {"semesterId":"a","courseId":"1","day":1,"startNode":1,"weeks":[1]},
            {"semesterId":"b","courseId":"1","day":2,"startNode":1,"weeks":[1]}
        ]"""
            )
        assertEquals(
            setOf("数学", "英语"),
            result.documents.flatMap { it.courses }.map { it.name }.toSet(),
        )
    }
}
