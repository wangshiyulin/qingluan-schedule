package com.qingluan.schedule

import com.qingluan.schedule.transfer.*
import org.junit.Assert.*
import org.junit.Test

class SchoolCatalogTest {
    @Test
    fun catalogFiltersUnsafeUrlsAndSearchesSchoolNames() {
        val entries =
            SchoolCatalog.decode(
                """[
            {"id":"NJUCM","name":"南京中医药大学","url":"https://jwgl.njucm.edu.cn/student/home"},
            {"id":"bad","name":"不安全","url":"javascript:alert(1)"},
            {"id":"http","name":"明文","url":"http://school.example"},
            {"id":"credentials","name":"凭据","url":"https://user:password@school.example"},
            {"id":"duplicate","name":"南京中医药大学","url":"https://jwgl.njucm.edu.cn/student/home"}
        ]"""
            )
        assertEquals(1, entries.size)
        assertEquals(1, SchoolCatalog.search(entries, "南京 中医").size)
        assertEquals(1, SchoolCatalog.search(entries, "njucm").size)
        assertTrue(SchoolCatalog.search(entries, "其他").isEmpty())
    }

    @Test
    fun extractionModesKeepApiAndTableIndependent() {
        val text =
            """{"format":"QingluanWebCapture","records":[{"courseName":"接口课程","day":1,"startNode":1,"weeks":[1]}],"documents":["<table><tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th></tr><tr><td>1节</td><td><b>网页课程</b><br>1-2周<br>1节</td></tr></table>"]}"""
        fun names(mode: String) =
            WebImportPipeline.decode(text, mode)
                .documents
                .flatMap { it.courses }
                .map { it.name }
                .toSet()
        assertEquals(setOf("接口课程"), names("data"))
        assertEquals(setOf("网页课程"), names("table"))
        assertEquals(setOf("接口课程", "网页课程"), names("auto"))
        assertTrue(runCatching { names("unknown") }.isFailure)
    }

    @Test
    fun degreesRemainIndependentAndManualAddressRejectsCredentials() {
        val items =
            SchoolCatalog.decode(
                """[{"id":"a","name":"学校","url":"https://example.edu","category":"undergraduate"},{"id":"b","name":"学校","url":"https://example.edu","category":"postgraduate"},{"id":"c","name":"未知分类","url":"https://example.edu","category":"wrong"}]"""
            )
        assertEquals(2, items.size)
        assertEquals(
            "https://example.edu/student",
            SchoolCatalog.normalizeAddress(" example.edu/student "),
        )
        assertNull(SchoolCatalog.normalizeAddress("https://user:password@example.edu"))
        assertNull(SchoolCatalog.normalizeAddress("http://example.edu"))
        assertNull(SchoolCatalog.normalizeAddress("javascript:alert(1)"))
    }
}
