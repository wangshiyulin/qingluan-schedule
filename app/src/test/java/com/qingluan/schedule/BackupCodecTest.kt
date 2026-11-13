package com.qingluan.schedule

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private fun sample(): Backup {
        val t = Timetable(id = "p")
        val term = Term(id = "t", name = "秋季", startDate = "2026-09-07", timetableId = "p")
        return Backup(
            data =
                Snapshot(
                    activeTermId = "t",
                    terms = listOf(term),
                    timetables = listOf(t),
                    courses =
                        listOf(
                            Course(
                                id = "c",
                                termId = "t",
                                name = "数学",
                                meetings = listOf(Meeting(id = "m")),
                            )
                        ),
                ),
            settings = AppSettings(theme = "dark", reminders = true),
        )
    }

    @Test
    fun fullBackupRoundTrips() {
        val b = sample()
        assertEquals(b, BackupCodec.decode(BackupCodec.encode(b)))
    }

    @Test
    fun corruptJsonIsRejected() {
        assertThrows(Exception::class.java) { BackupCodec.decode("not json") }
    }

    @Test
    fun unsupportedVersionIsRejected() {
        assertThrows(Exception::class.java) { BackupCodec.validate(sample().copy(version = 999)) }
    }

    @Test
    fun orphanCourseIsRejected() {
        val b = sample()
        assertThrows(Exception::class.java) {
            BackupCodec.validate(b.copy(data = b.data.copy(terms = emptyList())))
        }
    }

    @Test
    fun duplicateIdsAreRejected() {
        val b = sample()
        assertThrows(Exception::class.java) {
            BackupCodec.validate(
                b.copy(data = b.data.copy(courses = b.data.courses + b.data.courses))
            )
        }
    }

    @Test
    fun invalidDateIsRejected() {
        val b = sample()
        assertThrows(Exception::class.java) {
            BackupCodec.validate(
                b.copy(
                    data =
                        b.data.copy(
                            terms = listOf(b.data.terms.single().copy(startDate = "2026-02-30"))
                        )
                )
            )
        }
    }

    @Test
    fun overlappingPeriodsAreRejected() {
        val b = sample()
        assertThrows(Exception::class.java) {
            BackupCodec.validate(
                b.copy(
                    data =
                        b.data.copy(
                            timetables =
                                listOf(
                                    Timetable(
                                        id = "p",
                                        periods = listOf(Period(480, 540), Period(520, 560)),
                                    )
                                )
                        )
                )
            )
        }
    }

    @Test
    fun outOfBoundsPeriodIsRejected() {
        val b = sample()
        val bad =
            b.data.courses
                .single()
                .copy(meetings = listOf(Meeting(startPeriod = 1, endPeriod = 99)))
        assertThrows(Exception::class.java) {
            BackupCodec.validate(b.copy(data = b.data.copy(courses = listOf(bad))))
        }
    }

    @Test
    fun emptyJsonObjectIsNotACompleteBackup() {
        assertThrows(Exception::class.java) { BackupCodec.decode("{}") }
    }

    @Test
    fun omittedPayloadIsRejected() {
        assertThrows(Exception::class.java) {
            BackupCodec.decode("""{"app":"QingluanSchedule","version":1,"settings":{}}""")
        }
    }

    @Test
    fun omittedCollectionsAreRejected() {
        assertThrows(Exception::class.java) {
            BackupCodec.decode("""{"app":"QingluanSchedule","version":1,"data":{},"settings":{}}""")
        }
    }

    @Test
    fun emptySnapshotIsValid() {
        BackupCodec.validate(Backup())
    }

    @Test
    fun newOptionsRoundTripAndOldBackupsUseDefaults() {
        val original = sample()
        val customized =
            original.copy(
                data =
                    original.data.copy(
                        terms = original.data.terms.map { it.copy(firstDayOfWeek = 7) }
                    ),
                settings =
                    original.settings.copy(
                        hapticsEnabled = true,
                        animationsEnabled = false,
                        predictiveBackEnabled = false,
                        showNonCurrentWeek = true,
                    ),
            )
        assertEquals(customized, BackupCodec.decode(BackupCodec.encode(customized)))
        val old =
            BackupCodec.encode(original)
                .replace(
                    Regex(
                        """,?\s*"(firstDayOfWeek|showNonCurrentWeek|hapticsEnabled|animationsEnabled|predictiveBackEnabled)"\s*:\s*(1|true|false)"""
                    ),
                    "",
                )
        assertFalse(old.contains("hapticsEnabled"))
        assertEquals(original, BackupCodec.decode(old))
    }
}
