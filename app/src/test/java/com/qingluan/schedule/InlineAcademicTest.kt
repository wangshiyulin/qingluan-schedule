package com.qingluan.schedule

import com.qingluan.schedule.transfer.ImportCodec
import org.junit.Assert.*
import org.junit.Test

class InlineAcademicTest {
    private fun grid(cell: String) =
        "<table><tr><th>节次</th>" +
            listOf("一", "二", "三", "四", "五").joinToString("") { "<th>周$it</th>" } +
            "</tr><tr><td>3节</td><td>$cell</td><td></td><td></td><td></td><td></td></tr></table>"

    @Test
    fun inlineTeacherAndCampusRoomAreSeparatedFromWeeksAndCourseCode() {
        val course =
            ImportCodec.decode(
                    grid(
                            "<b>Python高级应用</b><br>080253—005<br>(1-4,6-17周)(3-3节)仙林校区 B4-204 徐雪松<br>计算机253;计算机254"
                        )
                        .toByteArray()
                )
                .documents
                .single()
                .courses
                .single()
        assertEquals("徐雪松", course.teacher)
        assertEquals("仙林校区 B4-204", course.location)
        assertEquals((1..17).toSet() - 5, course.meetings.single().weeks)
    }

    @Test
    fun stackedCoursesKeepTheirOwnTeachersLocationsAndWeeks() {
        val html =
            grid(
                "<b>数据结构课程设计</b><br>080359—007<br>(12-14周)(3-3节) 李新霞<br>计算机253;计算机254<br><b>数据结构</b><br>400058—007<br>(1-5,7-9周)(3-3节)仙林校区B6-107李新霞<br>计算机253;计算机254"
            )
        val courses =
            ImportCodec.decode(html.toByteArray()).documents.single().courses.associateBy {
                it.name
            }
        assertEquals(setOf("数据结构课程设计", "数据结构"), courses.keys)
        assertEquals("李新霞", courses["数据结构课程设计"]!!.teacher)
        assertEquals("", courses["数据结构课程设计"]!!.location)
        assertEquals((12..14).toSet(), courses["数据结构课程设计"]!!.meetings.single().weeks)
        assertEquals("仙林校区B6-107", courses["数据结构"]!!.location)
        assertEquals((1..9).toSet() - 6, courses["数据结构"]!!.meetings.single().weeks)
    }

    @Test
    fun classroomWithoutTeacherDoesNotInventTeacher() {
        val course =
            ImportCodec.decode(
                    grid("<b>英语</b><br>079024—007<br>(4-6周)(4-5节)仙林校区 B4-108<br>英语253")
                        .toByteArray()
                )
                .documents
                .single()
                .courses
                .single()
        assertEquals("", course.teacher)
        assertEquals("仙林校区 B4-108", course.location)
    }

    @Test
    fun wrappedCampusRoomAndTeacherRemainOneRecord() {
        val c =
            ImportCodec.decode(
                    grid(
                            "<b>数学</b><br>080359—007<br>(1-16周)(3-3节)仙林校区<br>B4-108<br>张浩<br>计算机253;计算机254"
                        )
                        .toByteArray()
                )
                .documents
                .single()
                .courses
                .single()
        assertEquals("张浩", c.teacher)
        assertEquals("仙林校区 B4-108", c.location)
    }

    @Test
    fun namedSportsRoomDoesNotRequireNumericRoomCode() {
        val c =
            ImportCodec.decode(
                    grid("<b>体育</b><br>080359—007<br>(1-16周)(3-3节)仙林校区 自定义教室 王老师<br>计算机253;计算机254")
                        .toByteArray()
                )
                .documents
                .single()
                .courses
                .single()
        assertEquals("王老师", c.teacher)
        assertEquals("仙林校区 自定义教室", c.location)
    }

    @Test
    fun teacherBeforeRoomAndPlainRoomNumberAreRecognized() {
        val c =
            ImportCodec.decode(
                    grid("<b>物理</b><br>080359—007<br>(1-16周)(3-3节)张三<br>仙林校区 201室<br>计算机253;计算机254")
                        .toByteArray()
                )
                .documents
                .single()
                .courses
                .single()
        assertEquals("张三", c.teacher)
        assertEquals("仙林校区 201室", c.location)
    }

    @Test
    fun classNamesAndFollowingCourseNeverSupplyMissingMetadata() {
        val courses =
            ImportCodec.decode(
                    grid(
                            "<b>体育</b><br>080359—007<br>(1-16周)(3-3节)<br>计算机253;计算机254<br><b>英语</b><br>080358—007<br>(1-16周)(3-3节)仙林校区 B4-204 李老师"
                        )
                        .toByteArray()
                )
                .documents
                .single()
                .courses
                .associateBy { it.name }
        assertEquals("", courses.getValue("体育").teacher)
        assertEquals("", courses.getValue("体育").location)
        assertEquals("李老师", courses.getValue("英语").teacher)
    }
}
