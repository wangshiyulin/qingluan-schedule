package com.qingluan.schedule.transfer

import com.qingluan.schedule.data.BackupCodec
import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
data class SchoolEntry(
    val id: String,
    val name: String,
    val initial: String = "",
    val label: String = "",
    val url: String,
    val category: String = "undergraduate",
)

object SchoolCatalog {
    fun decode(text: String): List<SchoolEntry> {
        require(text.toByteArray().size <= 1024 * 1024) { "学校目录过大" }
        val entries = BackupCodec.json.decodeFromString<List<SchoolEntry>>(text)
        require(entries.size <= 2000)
        return entries
            .filter {
                val uri = runCatching { URI(it.url) }.getOrNull()
                it.category in setOf("undergraduate", "postgraduate", "general") &&
                    it.name.isNotBlank() &&
                    it.id.isNotBlank() &&
                    uri?.scheme == "https" &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            }
            .distinctBy { Triple(it.name, it.url, it.category) }
    }

    fun normalizeAddress(address: String): String? {
        val normalized = address.trim().let { if ("://" in it) it else "https://$it" }
        val uri = runCatching { URI(normalized) }.getOrNull()
        return normalized.takeIf {
            uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }
    }

    fun search(entries: List<SchoolEntry>, query: String): List<SchoolEntry> {
        val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return entries.filter { entry ->
            words.all { word ->
                listOf(entry.name, entry.label, entry.id, entry.initial).any {
                    it.contains(word, ignoreCase = true)
                }
            }
        }
    }
}
