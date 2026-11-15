package com.qingluan.schedule

import com.qingluan.schedule.transfer.*
import org.junit.Assert.*
import org.junit.Test

class ImportedTimesTest {
    private fun html(first: String = "08:00-08:45", second: String = "08:55-09:40") =
        "<table><tr><th>节次</th>" +
            listOf("一", "二", "三", "四", "五").joinToString("") { "<th>周$it</th>" } +
            "</tr><tr><td>第1节<br>$first</td><td><b>数学</b><br>080359—007<br>(1-16周)(1-2节)<br>仙林校区 B4-108 张浩</td><td></td><td></td><td></td><td></td></tr><tr><td>第2节<br>$second</td><td></td><td></td><td></td><td></td><td></td></tr></table>"

    @Test
    fun readsSchoolPeriodsAndWrappedMetadata() {
        val result = ImportCodec.decode(html().toByteArray())
        val d = result.documents.single()
        assertEquals(2, d.timetable.periods.size)
        assertEquals(480, d.timetable.periods[0].start)
        assertEquals(580, d.timetable.periods[1].end)
        assertFalse(result.assessment!!.timeUnknown)
        assertEquals("张浩", d.courses.single().teacher)
        assertEquals("仙林校区 B4-108", d.courses.single().location)
    }

    @Test
    fun incompleteOrOverlappingPeriodsRequireConfirmation() {
        assertTrue(ImportCodec.decode(html(second = "").toByteArray()).assessment!!.timeUnknown)
        assertTrue(
            ImportCodec.decode(html(second = "08:30-09:15").toByteArray()).assessment!!.timeUnknown
        )
    }
}
