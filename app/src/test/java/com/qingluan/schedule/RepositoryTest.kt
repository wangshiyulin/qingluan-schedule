package com.qingluan.schedule

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var repo: Repository
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun before() = runBlocking {
        context.deleteDatabase("qingluan_schedule.db")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = Repository(context, scope)
        repo.awaitReady()
        repo.restore(Backup())
    }

    @After
    fun after() {
        repo.close()
        scope.cancel()
    }

    private suspend fun seed() {
        repo.change { s ->
            val term =
                Term(id = "t", name = "秋季", startDate = "2026-09-07", timetableId = "default")
            s.copy(
                activeTermId = "t",
                terms = listOf(term),
                courses =
                    listOf(
                        Course(
                            id = "c",
                            termId = "t",
                            name = "数学",
                            meetings = listOf(Meeting(id = "m")),
                        )
                    ),
            )
        }
    }

    @Test
    fun invalidCourseMutationIsRejectedBeforeNormalizationAndPersistence() = runBlocking {
        seed()
        val before = repo.state.value!!
        val invalid = before.courses.single().copy(meetings = listOf(Meeting(endPeriod = 999)))
        assertTrue(runCatching { repo.change { it.copy(courses = listOf(invalid)) } }.isFailure)
        assertEquals(before, repo.state.value)
        assertEquals(before, BackupCodec.decode(repo.export()).data)
    }

    @Test
    fun oldStoredSegmentsNormalizeOnReopenWithoutChangingAdjustments() = runBlocking {
        seed()
        val before = repo.state.value!!
        val c =
            before.courses
                .single()
                .copy(
                    meetings =
                        listOf(
                            Meeting(id = "a", startPeriod = 1, endPeriod = 2),
                            Meeting(id = "b", startPeriod = 3, endPeriod = 3),
                        )
                )
        val raw = before.copy(courses = listOf(c))
        repo.close()
        scope.cancel()
        context.openOrCreateDatabase("qingluan_schedule.db", 0, null).use {
            it.execSQL(
                "UPDATE schedule_state SET document=? WHERE id=1",
                arrayOf(BackupCodec.json.encodeToString(Snapshot.serializer(), raw)),
            )
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = Repository(context, scope)
        repo.awaitReady()
        assertEquals(1, repo.state.value!!.courses.single().meetings.size)
        assertEquals(3, repo.state.value!!.courses.single().meetings.single().endPeriod)
        assertEquals(repo.state.value, BackupCodec.decode(repo.export()).data)
    }

    @Test
    fun stateSurvivesDatabaseReopen() = runBlocking {
        seed()
        repo.close()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = Repository(context, scope)
        repo.awaitReady()
        assertEquals("数学", repo.state.value!!.courses.single().name)
    }

    @Test
    fun deletingTermCascadesCourseAndExceptions() = runBlocking {
        seed()
        repo.change {
            it.copy(
                exceptions =
                    listOf(
                        CourseException(
                            id = "x",
                            courseId = "c",
                            meetingId = "m",
                            originalDate = "2026-09-07",
                        )
                    )
            )
        }
        repo.deleteTerm("t")
        assertTrue(repo.state.value!!.courses.isEmpty())
        assertTrue(repo.state.value!!.exceptions.isEmpty())
        assertEquals("", repo.state.value!!.activeTermId)
    }

    @Test
    fun invalidRestoreDoesNotOverwriteExistingData() = runBlocking {
        seed()
        val old = repo.state.value
        try {
            repo.restore(Backup(version = 99))
            fail("invalid restore accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(old, repo.state.value)
        assertEquals("数学", BackupCodec.decode(repo.export()).data.courses.single().name)
    }

    @Test
    fun backupRestoresPreferencesAndAdjustments() = runBlocking {
        seed()
        repo.updateSettings { it.copy(theme = "dark", leadMinutes = 30) }
        repo.change {
            it.copy(
                exceptions =
                    listOf(
                        CourseException(
                            id = "x",
                            courseId = "c",
                            meetingId = "m",
                            originalDate = "2026-09-07",
                        )
                    )
            )
        }
        val backup = BackupCodec.decode(repo.export())
        repo.deleteTerm("t")
        repo.restore(backup)
        assertEquals("dark", repo.currentSettings().theme)
        assertEquals(30, repo.currentSettings().leadMinutes)
        assertEquals(1, repo.state.value!!.exceptions.size)
    }

    @Test
    fun restoreRecoversAfterCorruptStoredState() = runBlocking {
        seed()
        val backup = BackupCodec.decode(repo.export())
        repo.close()
        scope.cancel()
        context.openOrCreateDatabase("qingluan_schedule.db", 0, null).use {
            it.execSQL("UPDATE schedule_state SET document='broken' WHERE id=1")
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = Repository(context, scope)
        runCatching { repo.awaitReady() }
        repo.restore(backup)
        repo.saveCourse(repo.state.value!!.courses.single().copy(name = "恢复后可编辑"))
        assertEquals("恢复后可编辑", BackupCodec.decode(repo.export()).data.courses.single().name)
    }

    @Test
    fun interruptedRestoreFinishesSettingsFromJournalOnReopen() = runBlocking {
        seed()
        val saved = repo.state.value!!
        repo.updateSettings { it.copy(theme = "light") }
        repo.close()
        scope.cancel()
        val encoded =
            BackupCodec.json.encodeToString(
                AppSettings.serializer(),
                AppSettings(theme = "dark", leadMinutes = 30),
            )
        context.openOrCreateDatabase("qingluan_schedule.db", 0, null).use {
            it.execSQL("UPDATE schedule_state SET pendingSettings=? WHERE id=1", arrayOf(encoded))
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = Repository(context, scope)
        repo.awaitReady()
        assertEquals(saved, repo.state.value)
        assertEquals("dark", repo.currentSettings().theme)
        assertEquals(30, repo.currentSettings().leadMinutes)
    }

    @Test
    fun renameKeepsValidSingleChanges() = runBlocking {
        seed()
        repo.change {
            it.copy(
                exceptions =
                    listOf(
                        CourseException(
                            id = "x",
                            courseId = "c",
                            meetingId = "m",
                            originalDate = "2026-09-07",
                        )
                    )
            )
        }
        repo.saveCourse(repo.state.value!!.courses.single().copy(name = "高等数学"))
        assertEquals(1, repo.state.value!!.exceptions.size)
    }

    @Test
    fun autoHistoryPrecedesChangesSkipsActivationAndInvalidDataAndCanUndo() = runBlocking {
        seed()
        val original = repo.state.value!!
        val initial = repo.backupHistory().size
        repo.change { it }
        assertEquals(initial, repo.backupHistory().size)
        assertTrue(runCatching { repo.change { it.copy(activeTermId = "bad") } }.isFailure)
        assertEquals(initial, repo.backupHistory().size)
        repo.change { it.copy(courses = it.courses.map { c -> c.copy(name = "新名称") }) }
        val latest = repo.backupHistory().first()
        assertEquals(original, repo.readLocalBackup(latest.id).data)
        repo.restoreLocalBackup(latest.id)
        assertEquals(original, repo.state.value)
        assertEquals(
            "新名称",
            repo.readLocalBackup(repo.backupHistory().first().id).data.courses.single().name,
        )
        val count = repo.backupHistory().size
        repo.updateSettings { it.copy(autoBackupEnabled = false) }
        repo.deleteTerm("t")
        assertEquals(count, repo.backupHistory().size)
    }

    @Test
    fun termDeletionRemovesCalendarRules() = runBlocking {
        seed()
        repo.change {
            it.copy(calendarRules = listOf(CalendarRule(termId = "t", date = "2026-09-07")))
        }
        repo.deleteTerm("t")
        assertTrue(repo.state.value!!.calendarRules.isEmpty())
    }
}
