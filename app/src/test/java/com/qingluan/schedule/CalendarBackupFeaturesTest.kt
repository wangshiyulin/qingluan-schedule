package com.qingluan.schedule

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import com.qingluan.schedule.system.ReminderPlanner
import com.qingluan.schedule.transfer.*
import java.io.File
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

class CalendarBackupFeaturesTest {
    @get:Rule val temp = TemporaryFolder()
    private val term = Term(id = "t", startDate = "2026-09-07", weeks = 3, timetableId = "default")
    private val meeting = Meeting(id = "m", weeks = setOf(1), weekday = 1)
    private val course =
        Course(
            id = "c",
            termId = "t",
            name = "数学",
            teacher = "张老师",
            location = "A301",
            meetings = listOf(meeting),
        )
    private val base = Snapshot(activeTermId = "t", terms = listOf(term), courses = listOf(course))
    private val monday = LocalDate.parse("2026-09-07")

    private fun holiday(s: Snapshot = base) =
        s.copy(
            calendarRules = listOf(CalendarRule(id = "h", termId = "t", date = monday.toString()))
        )

    private fun makeup() =
        base.copy(
            calendarRules =
                listOf(
                    CalendarRule(
                        id = "r",
                        termId = "t",
                        date = "2026-09-19",
                        teachingDate = monday.toString(),
                    )
                )
        )

    @Test
    fun holidaySuppressesRoutineAndGhostsButKeepsExplicitExtra() {
        val s = holiday()
        assertTrue(ScheduleEngine.onDate(s, "t", monday).isEmpty())
        assertTrue(ScheduleEngine.nonCurrentOnDate(s, "t", monday).isEmpty())
        val added =
            s.withException(
                CourseException(
                    courseId = "c",
                    meetingId = "m",
                    targetDate = monday.toString(),
                    startPeriod = 3,
                    endPeriod = 4,
                )
            )
        BackupCodec.validate(Backup(data = added))
        assertEquals(3, ScheduleEngine.onDate(added, "t", monday).single().startPeriod)
    }

    @Test
    fun makeupUsesSourceWeekDayAndActualDateWithoutRecursiveHolidayRules() {
        val s = makeup().copy(calendarRules = makeup().calendarRules + holiday().calendarRules)
        val o = ScheduleEngine.onDate(s, "t", LocalDate.parse("2026-09-19")).single()
        assertEquals("2026-09-19", o.date.toString())
        assertEquals("张老师", o.teacher)
        assertTrue(ScheduleEngine.onDate(s, "t", monday).isEmpty())
        assertEquals(
            1,
            ReminderPlanner.plan(
                    s,
                    AppSettings(reminders = true),
                    LocalDate.parse("2026-09-18").atStartOfDay(),
                    emptySet(),
                )
                .size,
        )
    }

    @Test
    fun makeupCanBeCancelledAndMovedEvenAfterCalendarRuleRemoved() {
        val s =
            makeup()
                .withException(
                    CourseException(
                        id = "x",
                        courseId = "c",
                        meetingId = "m",
                        originalDate = "2026-09-19",
                        originalTeachingDate = monday.toString(),
                        targetDate = "2026-09-20",
                        startPeriod = 3,
                        endPeriod = 4,
                    )
                )
        BackupCodec.validate(Backup(data = s))
        assertTrue(ScheduleEngine.onDate(s, "t", LocalDate.parse("2026-09-19")).isEmpty())
        assertEquals(
            3,
            ScheduleEngine.onDate(s, "t", LocalDate.parse("2026-09-20")).single().startPeriod,
        )
        BackupCodec.validate(Backup(data = s.copy(calendarRules = emptyList())))
        assertEquals(s.exceptions, s.withCourse(course).exceptions)
    }

    @Test
    fun duplicateAndOutOfTermCalendarRulesAreRejected() {
        val s = holiday()
        assertTrue(
            runCatching {
                BackupCodec.validate(
                    Backup(
                        data =
                            s.copy(
                                calendarRules =
                                    s.calendarRules + s.calendarRules.single().copy(id = "h2")
                            )
                    )
                )
            }
                .isFailure
        )
        assertTrue(
            runCatching {
                BackupCodec.validate(
                    Backup(
                        data =
                            s.copy(
                                calendarRules =
                                    listOf(s.calendarRules.single().copy(date = "2027-01-01"))
                            )
                    )
                )
            }
                .isFailure
        )
    }

    @Test
    fun nativeAndShareRoundTripsPreserveCalendarAndExceptionSource() {
        val s =
            makeup()
                .withException(
                    CourseException(
                        courseId = "c",
                        meetingId = "m",
                        originalDate = "2026-09-19",
                        originalTeachingDate = monday.toString(),
                    )
                )
        assertEquals(s, BackupCodec.decode(BackupCodec.encode(Backup(data = s))).data)
        val doc = TimetableDocument.from(s, "t")
        assertEquals(doc, ShareCode.decode(ShareCode.encode(doc)))
        assertEquals(doc, CalendarCodec.decode(CalendarCodec.encode(doc)).documents.single())
        val imported = doc.appendTo(Snapshot())
        assertEquals("2026-09-07", imported.calendarRules.single().teachingDate)
        assertEquals(monday.toString(), imported.exceptions.single().originalTeachingDate)
        assertTrue(
            ScheduleEngine.onDate(imported, imported.activeTermId, LocalDate.parse("2026-09-19"))
                .isEmpty()
        )
    }

    @Test
    fun holidayAndMakeupAreReflectedInBoundedFocusedConflictChecks() {
        val s = base.copy(courses = listOf(course, course.copy(id = "c2")))
        assertEquals(1, ScheduleEngine.conflicts(s, "t").size)
        assertTrue(ScheduleEngine.conflicts(holiday(s), "t").isEmpty())
        assertTrue(ScheduleEngine.conflicts(s, "t", setOf("other")).isEmpty())
        assertEquals(1, ScheduleEngine.conflicts(s, "t", setOf("c"), 1).size)
    }

    @Test
    fun localHistoryRetainsNewestAndChecksIntegrityAndPathBeforeDecode() {
        val folder = temp.newFolder()
        val store = LocalBackupStore(folder)
        (1..5).forEach {
            store.save(
                Backup(data = base.copy(terms = listOf(term.copy(name = "学期$it")))),
                BackupReason.EDIT,
                3,
            )
        }
        val entries = store.entries()
        assertEquals(3, entries.size)
        assertEquals("学期5", store.read(entries.first().id).data.terms.single().name)
        assertTrue(runCatching { store.read("../anything") }.isFailure)
        val file = File(folder, entries.first().id)
        file.writeText(file.readText().replace("学期5", "学期X"))
        assertTrue(runCatching { store.read(entries.first().id) }.isFailure)
        store.delete(entries.first().id)
        assertEquals(2, store.entries().size)
        assertTrue(folder.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun invalidBackupDoesNotCreateHistoryAndBackwardClockStillSortsBySequence() {
        val folder = temp.newFolder()
        val store = LocalBackupStore(folder)
        val one = store.save(Backup(data = base), BackupReason.EDIT, 3)
        val future = one.id.replaceAfter('_', "9999999999999_EDIT.qbak")
        File(folder, one.id).renameTo(File(folder, future))
        val newest = store.save(Backup(data = holiday()), BackupReason.CALENDAR, 3)
        assertEquals(newest.id, store.entries().first().id)
        val before = store.entries()
        assertTrue(
            runCatching {
                store.save(
                    Backup(data = base.copy(activeTermId = "absent")),
                    BackupReason.EDIT,
                    3,
                )
            }
                .isFailure
        )
        assertEquals(before, store.entries())
    }

    @Test
    fun auditCountsMissingMeetingMetadataAndOptionalSourceTotal() {
        val c =
            course.copy(
                meetings = listOf(meeting.copy(id = "a"), meeting.copy(id = "b", weekday = 2)),
                teacher = "",
                location = "",
            )
        val doc = TimetableDocument.from(base.copy(courses = listOf(c)), "t")
        val a = ImportAudit.inspect(doc, ImportAssessment("网页", 4, 2, listOf("unparsed")), 2)
        assertEquals(1, a.distinctNames)
        assertEquals(2, a.meetings)
        assertEquals(1, a.missingTeacher)
        assertEquals(1, a.missingLocation)
        assertTrue(a.countMismatch)
        assertEquals(1, a.rejected)
        val b = ImportAudit.inspect(doc.copy(courses = listOf(course)), null, 1)
        assertFalse(b.countMismatch)
        assertEquals(0, b.missingTeacher)
    }
}
