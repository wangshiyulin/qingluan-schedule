package com.qingluan.schedule.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import com.qingluan.schedule.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

/** Pending settings form a durable, replayable full-restore journal. */
@Entity(tableName = "schedule_state")
data class StateRow(
    @PrimaryKey val id: Int = 1,
    val document: String,
    val pendingSettings: String? = null,
)

@Dao
interface StateDao {
    @Query("SELECT * FROM schedule_state WHERE id=1") suspend fun read(): StateRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun write(row: StateRow)
}

@Database(entities = [StateRow::class], version = 1, exportSchema = true)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun stateDao(): StateDao
}

private val Context.preferences by
    preferencesDataStore(
        "qingluan_preferences",
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

class Repository(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val db =
        Room.databaseBuilder(app, ScheduleDatabase::class.java, "qingluan_schedule.db").build()
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    val state = MutableStateFlow<Snapshot?>(null)
    val error = MutableStateFlow<String?>(null)
    private val settingsKey = stringPreferencesKey("settings_v1")
    val settings: StateFlow<AppSettings> =
        app.preferences.data
            .map { prefs ->
                decodeSettings(prefs[settingsKey])
            }
            .catch {
                error.value = "设置读取失败，请重新启动或恢复备份"
                emit(AppSettings())
            }
            .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun decodeSettings(document: String?): AppSettings = runCatching {
        (document?.let { BackupCodec.json.decodeFromString<AppSettings>(it) } ?: AppSettings())
            .copy(animationsEnabled = true, predictiveBackEnabled = true)
    }
        .getOrElse {
            error.value = "偏好设置损坏，已使用默认设置；课表数据不受影响"
            AppSettings()
        }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val row = db.stateDao().read()
                val s =
                    row?.document?.let { BackupCodec.json.decodeFromString<Snapshot>(it) }
                        ?: Snapshot()
                BackupCodec.validate(Backup(data = s))
                // A process death after the SQLite commit resumes the same restore here.
                row?.pendingSettings?.let { document ->
                    val prefs = BackupCodec.json.decodeFromString<AppSettings>(document)
                    BackupCodec.validate(Backup(data = s, settings = prefs))
                    writeSettings(prefs)
                    db.withTransaction { db.stateDao().write(row.copy(pendingSettings = null)) }
                }
                state.value = s.coalesced()
            } catch (_: Exception) {
                error.value = "本地数据读取失败。请保留应用数据，尝试从备份恢复。"
            } finally {
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() = ready.await()

    suspend fun change(transform: (Snapshot) -> Snapshot) =
        withContext(Dispatchers.IO) {
            ready.await()
            mutex.withLock {
                val previous = checkNotNull(state.value) { "请先恢复本地备份" }
                val candidate = transform(previous)
                val settings = currentSettings()
                BackupCodec.validate(Backup(data = candidate, settings = settings))
                val next = candidate.coalesced(previous)
                BackupCodec.validate(Backup(data = next, settings = settings))
                db.withTransaction {
                    db.stateDao().write(StateRow(document = BackupCodec.json.encodeToString(next)))
                }
                state.value = next
            }
        }

    suspend fun currentSettings(): AppSettings =
        decodeSettings(app.preferences.data.first()[settingsKey])

    private suspend fun writeSettings(settings: AppSettings) {
        app.preferences.edit { it[settingsKey] = BackupCodec.json.encodeToString(settings) }
    }

    suspend fun updateSettings(change: (AppSettings) -> AppSettings) = mutex.withLock {
        app.preferences.edit { prefs ->
            val next = change(decodeSettings(prefs[settingsKey]))
            BackupCodec.validate(Backup(data = state.value ?: Snapshot(), settings = next))
            prefs[settingsKey] = BackupCodec.json.encodeToString(next)
        }
    }

    suspend fun export(): String {
        ready.await()
        return mutex.withLock {
            BackupCodec.encode(
                Backup(
                    data = checkNotNull(state.value) { "请先恢复本地备份" },
                    settings = currentSettings(),
                )
            )
        }
    }

    suspend fun restore(b: Backup) =
        withContext(Dispatchers.IO) {
            BackupCodec.validate(b)
            val restored = b.data.coalesced()
            BackupCodec.validate(b.copy(data = restored))
            ready.await()
            mutex.withLock {
                val oldRow = db.stateDao().read()
                val oldSettings = currentSettings()
                val document = BackupCodec.json.encodeToString(restored)
                val pending = BackupCodec.json.encodeToString(b.settings)
                var committed = false
                try {
                    // Data and the recovery instruction commit in one SQLite transaction.
                    db.withTransaction {
                        db.stateDao()
                            .write(StateRow(document = document, pendingSettings = pending))
                    }
                    committed = true
                    writeSettings(b.settings)
                    db.withTransaction { db.stateDao().write(StateRow(document = document)) }
                    state.value = restored
                    error.value = null
                } catch (e: Exception) {
                    if (committed)
                        withContext(NonCancellable) {
                            // Compensate ordinary failures, also journal the compensation itself.
                            val prior =
                                oldRow
                                    ?: StateRow(
                                        document = BackupCodec.json.encodeToString(Snapshot())
                                    )
                            db.withTransaction {
                                db.stateDao()
                                    .write(
                                        prior.copy(
                                            pendingSettings =
                                                BackupCodec.json.encodeToString(oldSettings)
                                        )
                                    )
                            }
                            writeSettings(oldSettings)
                            db.withTransaction { db.stateDao().write(prior) }
                        }
                    throw e
                }
            }
        }

    suspend fun saveCourse(c: Course) = change { it.withCourse(c) }

    suspend fun deleteCourse(id: String) = change { s ->
        s.copy(
            courses = s.courses.filterNot { it.id == id },
            exceptions = s.exceptions.filterNot { it.courseId == id },
        )
    }

    suspend fun deleteTerm(id: String) = change { s ->
        val removed = s.courses.filter { it.termId == id }.map { it.id }.toSet()
        val terms = s.terms.filterNot { it.id == id }
        s.copy(
            terms = terms,
            courses = s.courses.filterNot { it.id in removed },
            exceptions = s.exceptions.filterNot { it.courseId in removed },
            activeTermId =
                if (s.activeTermId == id) terms.firstOrNull()?.id.orEmpty() else s.activeTermId,
        )
    }

    fun close() = db.close()
}
