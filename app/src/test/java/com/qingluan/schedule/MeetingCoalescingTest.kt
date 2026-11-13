package com.qingluan.schedule

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MeetingCoalescingTest {
    private val term = Term(id = "t", startDate = "2026-09-07", timetableId = "p")
    private val table = Timetable(id = "p")

    private fun snapshot(course: Course, changes: List<CourseException> = emptyList()) =
        Snapshot(
            activeTermId = "t",
            terms = listOf(term),
            timetables = listOf(table),
            courses = listOf(course),
            exceptions = changes,
        )

    private fun course(meetings: List<Meeting>) =
        Course(
            id = "c",
            termId = "t",
            name = "离散数学",
            teacher = "王老师",
            location = "A301",
            meetings = meetings,
        )

    private fun coverage(s: Snapshot, date: LocalDate) =
        ScheduleEngine.onDate(s, "t", date)
            .flatMap { o ->
                (o.startPeriod..o.endPeriod).map {
                    listOf(it, o.teacher, o.location, o.exception?.id.orEmpty())
                }
            }
            .toSet()

    private fun assertSameSchedule(before: Snapshot, after: Snapshot) {
        BackupCodec.validate(Backup(data = after))
        (0..139).forEach { day ->
            val date = LocalDate.parse(term.startDate).plusDays(day.toLong())
            assertEquals(date.toString(), coverage(before, date), coverage(after, date))
        }
    }

    @Test
    fun screenshotsBecomeContinuousWithoutAddingTeachingWeeks() {
        val weeks = (8..17).toSet()
        val sunday = setOf(3, 15, 16, 17)
        val original =
            snapshot(
                course(
                    listOf(
                        Meeting(
                            id = "a",
                            weekday = 2,
                            startPeriod = 1,
                            endPeriod = 2,
                            weeks = weeks,
                        ),
                        Meeting(
                            id = "b",
                            weekday = 2,
                            startPeriod = 3,
                            endPeriod = 3,
                            weeks = weeks,
                        ),
                        Meeting(
                            id = "c",
                            weekday = 7,
                            startPeriod = 1,
                            endPeriod = 2,
                            weeks = sunday,
                        ),
                        Meeting(
                            id = "d",
                            weekday = 7,
                            startPeriod = 3,
                            endPeriod = 3,
                            weeks = sunday,
                        ),
                    )
                )
            )
        val normalized = original.coalesced()
        assertEquals(
            listOf(1 to 3, 1 to 3),
            normalized.courses.single().meetings.map { it.startPeriod to it.endPeriod },
        )
        assertSameSchedule(original, normalized)
        assertEquals(normalized, normalized.coalesced())
        assertEquals(
            normalized,
            BackupCodec.decode(BackupCodec.encode(Backup(data = normalized))).data,
        )
    }

    @Test
    fun gapsDifferentWeeksAndRealMetadataDifferencesRemainSeparate() {
        val c =
            course(
                listOf(
                    Meeting(id = "a", startPeriod = 1, endPeriod = 2, weeks = setOf(1, 3)),
                    Meeting(id = "b", startPeriod = 3, endPeriod = 3, weeks = setOf(2, 4)),
                    Meeting(
                        id = "c",
                        startPeriod = 4,
                        endPeriod = 4,
                        weeks = setOf(2, 4),
                        location = "B302",
                    ),
                    Meeting(
                        id = "d",
                        startPeriod = 6,
                        endPeriod = 6,
                        weeks = setOf(2, 4),
                        location = "B302",
                    ),
                )
            )
        assertEquals(4, c.coalesced().meetings.size)
        assertSameSchedule(snapshot(c), snapshot(c.coalesced()))
    }

    @Test
    fun temporaryTargetsAndUndoKeepExactCoverage() {
        val c = course((1..3).map { Meeting(id = "m$it", startPeriod = it, endPeriod = it) })
        val change =
            CourseException(
                id = "x",
                courseId = "c",
                meetingId = "m1",
                originalDate = "2026-09-07",
                targetDate = "2026-09-08",
                startPeriod = 5,
                endPeriod = 5,
                teacher = "李老师",
                location = "B302",
            )
        val original = snapshot(c, listOf(change))
        val normalized = original.coalesced()
        assertEquals(change, normalized.exceptions.single())
        assertEquals(
            c.meetings.first(),
            normalized.courses.single().meetings.first { it.id == "m1" },
        )
        assertEquals(2, normalized.courses.single().meetings.size)
        assertSameSchedule(original, normalized)
        val undone = normalized.copy(exceptions = emptyList()).coalesced(normalized)
        assertEquals(1, undone.courses.single().meetings.size)
        assertSameSchedule(original.copy(exceptions = emptyList()), undone)
        val unchanged = normalized.coalesced(normalized)
        assertSame(normalized.courses.single(), unchanged.courses.single())
    }
}
