package com.qingluan.schedule

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import com.qingluan.schedule.transfer.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as ScheduleApplication).repository
    val state = repo.state
    val settings = repo.settings
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val failure = MutableStateFlow<String?>(null)
    val pendingImport = MutableStateFlow<ImportResult?>(null)
    val localBackups = MutableStateFlow<List<LocalBackupEntry>>(emptyList())
    val pendingLocalBackup = MutableStateFlow<Pair<LocalBackupEntry, Backup>?>(null)
    val pendingBackup = MutableStateFlow<Backup?>(null)
    private val actionLock = Mutex()

    fun action(
        success: String? = null,
        onSuccess: () -> Unit = {},
        showBusy: Boolean = true,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            actionLock.withLock {
                if (showBusy) busy.value = true
                failure.value = null
                try {
                    withContext(Dispatchers.IO) { block() }
                    if (success != null) message.value = success
                    onSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val text = e.message?.take(180) ?: "操作失败，请重试"
                    message.value = text
                    failure.value = text
                } finally {
                    if (showBusy) busy.value = false
                }
            }
        }
    }

    fun saveCourse(c: Course, onSaved: () -> Unit = {}) =
        action("课程已保存", onSaved) { repo.saveCourse(c) }

    fun deleteCourse(id: String) = action("课程已删除") { repo.deleteCourse(id) }

    fun deleteTerm(id: String) = action("课表已删除") { repo.deleteTerm(id) }

    fun saveTerm(t: Term, activate: Boolean = false, onSaved: () -> Unit = {}) =
        action("课表已保存", onSaved) {
            repo.change { s ->
                s.copy(
                    terms = s.terms.filterNot { it.id == t.id } + t,
                    activeTermId = if (activate) t.id else s.activeTermId.ifBlank { t.id },
                )
            }
        }

    fun activate(id: String) = action { repo.change { it.copy(activeTermId = id) } }

    fun saveTimetable(t: Timetable, onSaved: () -> Unit = {}) =
        action("作息已保存", onSaved) {
            repo.change { s -> s.copy(timetables = s.timetables.filterNot { it.id == t.id } + t) }
        }

    fun deleteTimetable(id: String) =
        action("作息已删除") {
            repo.change { s ->
                require(s.terms.none { it.timetableId == id }) { "此作息仍被学期使用，请先更换绑定" }
                require(s.timetables.size > 1) { "至少保留一套作息" }
                s.copy(timetables = s.timetables.filterNot { it.id == id })
            }
        }

    fun saveException(x: CourseException, onSaved: () -> Unit = {}) =
        action("单次安排已保存", onSaved) {
            repo.change { s ->
                s.withException(x)
            }
        }

    fun removeException(id: String) =
        action("已撤销单次调整") {
            repo.change { it.copy(exceptions = it.exceptions.filterNot { x -> x.id == id }) }
        }

    fun loadLocalBackups() = action(showBusy = false) { localBackups.value = repo.backupHistory() }

    fun previewLocalBackup(entry: LocalBackupEntry) = action {
        pendingLocalBackup.value = entry to repo.readLocalBackup(entry.id)
    }

    fun restoreLocalBackup(id: String) =
        action("备份已恢复") {
            repo.restoreLocalBackup(id)
            pendingLocalBackup.value = null
            localBackups.value = repo.backupHistory()
        }

    fun undoLatestChange() =
        action("已撤销最近修改") {
            val entry = repo.backupHistory().firstOrNull() ?: error("暂无自动备份")
            repo.restoreLocalBackup(entry.id)
            localBackups.value = repo.backupHistory()
        }

    fun deleteLocalBackup(id: String) =
        action("备份已删除") {
            repo.deleteLocalBackup(id)
            localBackups.value = repo.backupHistory()
        }

    fun saveCalendarRange(
        termId: String,
        start: java.time.LocalDate,
        end: java.time.LocalDate,
        source: java.time.LocalDate?,
        name: String,
        editingId: String?,
        onSaved: () -> Unit,
    ) =
        action("安排已保存", onSaved) {
            require(end >= start && java.time.temporal.ChronoUnit.DAYS.between(start, end) <= 420) {
                "日期范围无效"
            }
            require(source == null || end == start) { "调休请一次选择一天" }
            val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
            repo.change(BackupReason.CALENDAR) { current ->
                val retained = current.calendarRules.filterNot { it.id == editingId }
                require(
                    retained.none {
                        it.termId == termId && java.time.LocalDate.parse(it.date) in dates
                    }
                ) {
                    "日期已有安排，请编辑原安排"
                }
                current.copy(
                    calendarRules =
                        retained +
                            dates.map {
                                CalendarRule(
                                    termId = termId,
                                    date = it.toString(),
                                    teachingDate = source?.toString().orEmpty(),
                                    name =
                                        name.trim().ifBlank { if (source == null) "节假日" else "调休" },
                                )
                            }
                )
            }
        }

    fun removeCalendarRule(id: String) =
        action("安排已删除") {
            repo.change(BackupReason.CALENDAR) {
                it.copy(calendarRules = it.calendarRules.filterNot { r -> r.id == id })
            }
        }

    fun testReminder() =
        action("已发送测试提醒") {
            com.qingluan.schedule.system.SystemCoordinator.sendTestNotification(getApplication())
        }

    fun updateSettings(change: (AppSettings) -> AppSettings) =
        action(showBusy = false) {
            repo.updateSettings(change)
        }

    fun previewRestore(uri: Uri) = action {
        val bytes =
            getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                it.readNBytes(BackupCodec.MAX_BYTES + 1)
            } ?: error("无法读取备份")
        require(bytes.size <= BackupCodec.MAX_BYTES) { "备份文件超过 8 MiB" }
        pendingBackup.value = ExchangeFiles.backup(bytes)
    }

    fun confirmRestore() {
        val b = pendingBackup.value ?: return
        action("备份已导入") {
            repo.restore(b)
            pendingBackup.value = null
        }
    }

    fun previewImport(uri: Uri) = action {
        pendingImport.value = ExchangeFiles.decode(ExchangeFiles.read(getApplication(), uri))
    }

    fun previewText(text: String) = action {
        require(text.toByteArray().size <= BackupCodec.MAX_BYTES) { "内容超过 8 MiB" }
        pendingImport.value = ImportCodec.decode(text.toByteArray())
    }

    fun previewWeb(text: String, mode: String = "auto") = action {
        pendingImport.value = WebImportPipeline.decode(text, mode)
    }

    fun chooseImportSource(index: Int) {
        val current = pendingImport.value ?: return
        val source = current.sources.getOrNull(index) ?: return
        pendingImport.value = source.result.copy(sources = current.sources, selectedSource = index)
    }

    fun remapImport(selection: AcademicFieldSelection) = action {
        pendingImport.value = pendingImport.value?.let { WebImportPipeline.remap(it, selection) }
    }

    fun importDocument(document: TimetableDocument, targetId: String?, onImported: () -> Unit) =
        action("课表已导入", onImported) {
            repo.change(BackupReason.IMPORT) { document.appendTo(it, targetId) }
            pendingImport.value = null
        }

    fun writeDocument(uri: Uri, document: TimetableDocument, format: ExchangeFormat, week: Int) =
        action("课表已导出") {
            val bytes = ExchangeFiles.export(document, format, week)
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(bytes)
            } ?: error("无法写入文件")
        }

    fun writeFullBackup(uri: Uri, zipped: Boolean) =
        action("备份已导出") {
            val text = repo.export()
            val bytes = if (zipped) ExchangeFiles.zipBackup(text) else text.toByteArray()
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(bytes)
            } ?: error("无法写入文件")
        }

    fun shareDocument(
        document: TimetableDocument,
        format: ExchangeFormat,
        week: Int,
        onReady: (Uri) -> Unit,
    ) {
        var result: Uri? = null
        action(onSuccess = { result?.let(onReady) }) {
            val app = getApplication<Application>()
            val folder = File(app.cacheDir, "exports").apply { mkdirs() }
            folder
                .listFiles()
                ?.filter {
                    System.currentTimeMillis() - it.lastModified() > 7L * 24 * 60 * 60 * 1000
                }
                ?.forEach { it.delete() }
            val name =
                document.term.name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(60).ifBlank {
                    "课程表"
                }
            val file = File(folder, "$name-${System.currentTimeMillis()}.${format.extension}")
            file.writeBytes(ExchangeFiles.export(document, format, week))
            result = FileProvider.getUriForFile(app, app.packageName + ".files", file)
        }
    }
}
