package com.qingluan.schedule.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.Serializable

enum class BackupReason(val title: String) {
    EDIT("修改前"),
    DELETE("删除前"),
    IMPORT("导入前"),
    RESTORE("恢复前"),
    CALENDAR("调休变更前"),
}

data class LocalBackupEntry(
    val id: String,
    val createdAt: Long,
    val reason: BackupReason,
    val bytes: Long,
)

@Serializable
private data class LocalBackupRecord(val version: Int = 1, val payload: String, val sha256: String)

/** Private bounded snapshots. A snapshot is committed and synced before user data is changed. */
class LocalBackupStore(private val folder: File) {
    private val name = Regex("([0-9]{1,19})_([0-9]{1,19})_([A-Z]+)\\.qbak")

    private fun hash(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    @Synchronized
    fun entries(): List<LocalBackupEntry> =
        folder
            .listFiles()
            .orEmpty()
            .mapNotNull { file ->
                val match = name.matchEntire(file.name) ?: return@mapNotNull null
                val sequence = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val time = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val reason =
                    runCatching { BackupReason.valueOf(match.groupValues[3]) }.getOrNull()
                        ?: return@mapNotNull null
                if (!file.isFile || sequence < 1 || time < 0) null
                else LocalBackupEntry(file.name, time, reason, file.length())
            }
            .sortedByDescending { it.id.substringBefore('_').toLong() }

    @Synchronized
    fun save(backup: Backup, reason: BackupReason, keep: Int): LocalBackupEntry {
        require(keep in listOf(3, 5, 10))
        check(folder.isDirectory || folder.mkdirs()) { "无法创建自动备份目录，未修改数据" }
        val payload = BackupCodec.encode(backup)
        val record =
            BackupCodec.json
                .encodeToString(
                    LocalBackupRecord.serializer(),
                    LocalBackupRecord(payload = payload, sha256 = hash(payload)),
                )
                .toByteArray()
        require(record.size <= BackupCodec.MAX_BYTES * 2 + 4096) { "备份内容过大，未修改数据" }
        val sequence = (entries().firstOrNull()?.id?.substringBefore('_')?.toLong() ?: 0L) + 1
        require(sequence > 0)
        val time = System.currentTimeMillis().coerceAtLeast(0)
        val target = File(folder, "${sequence}_${time}_${reason.name}.qbak")
        val temp = File(folder, target.name + ".part")
        try {
            FileOutputStream(temp).use {
                it.write(record)
                it.fd.sync()
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                check(temp.renameTo(target)) { "自动备份未能保存，未修改数据" }
            }
        } finally {
            temp.delete()
        }
        trim(keep)
        return checkNotNull(entries().firstOrNull { it.id == target.name })
    }

    @Synchronized
    fun trim(keep: Int) {
        require(keep in listOf(3, 5, 10))
        entries().drop(keep).forEach { check(File(folder, it.id).delete()) { "自动备份清理失败" } }
    }

    @Synchronized
    fun read(id: String): Backup {
        require(name.matches(id)) { "备份编号无效" }
        val file = File(folder, id)
        require(file.canonicalFile.parentFile == folder.canonicalFile && file.isFile) { "备份不存在" }
        val limit = BackupCodec.MAX_BYTES * 2 + 4096
        val bytes = file.inputStream().use { it.readNBytes(limit + 1) }
        require(bytes.size <= limit) { "备份过大" }
        val record =
            BackupCodec.json.decodeFromString<LocalBackupRecord>(bytes.toString(Charsets.UTF_8))
        require(record.version == 1 && record.sha256 == hash(record.payload)) { "备份校验失败，未修改数据" }
        return BackupCodec.decode(record.payload)
    }

    @Synchronized
    fun delete(id: String) {
        require(name.matches(id))
        check(File(folder, id).delete()) { "备份不存在或无法删除" }
    }
}
