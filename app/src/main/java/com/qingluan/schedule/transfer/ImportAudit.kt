package com.qingluan.schedule.transfer

/** Source course counts are optional: generic webpages cannot prove completeness by themselves. */
data class ImportAudit(
    val distinctNames: Int,
    val courses: Int,
    val meetings: Int,
    val missingTeacher: Int,
    val missingLocation: Int,
    val rejected: Int,
    val expectedNames: Int?,
    val countMismatch: Boolean,
    val incomplete: Boolean,
) {
    companion object {
        fun inspect(
            document: TimetableDocument,
            assessment: ImportAssessment?,
            expectedNames: Int? = null,
        ): ImportAudit {
            require(expectedNames == null || expectedNames in 0..5000)
            val names = document.courses.map { it.name.trim() }.distinct().size
            return ImportAudit(
                names,
                document.courses.size,
                document.courses.sumOf { it.meetings.size },
                document.courses.count { c ->
                    c.meetings.any { it.teacher.ifBlank { c.teacher }.isBlank() }
                },
                document.courses.count { c ->
                    c.meetings.any { it.location.ifBlank { c.location }.isBlank() }
                },
                assessment?.rejected?.size ?: 0,
                expectedNames,
                expectedNames != null && expectedNames != names,
                assessment?.incomplete == true,
            )
        }
    }
}
