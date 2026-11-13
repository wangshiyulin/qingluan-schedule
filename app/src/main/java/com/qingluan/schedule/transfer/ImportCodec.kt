package com.qingluan.schedule.transfer

import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.*

object ImportCodec {
    fun decode(bytes: ByteArray, name: String = "课表"): ImportResult {
        require(bytes.size <= BackupCodec.MAX_BYTES) { "文件超过 8 MiB" }
        if (bytes.take(2) == listOf(0x50.toByte(), 0x4b.toByte())) {
            runCatching { ExchangeFiles.backup(bytes) }
                .getOrNull()
                ?.let { b ->
                    return ImportResult(b.data.terms.map { TimetableDocument.from(b.data, it.id) })
                }
            val rows = TabularCodec.readXlsx(bytes)
            return runCatching { TabularAcademicAdapter.decode(rows, "Excel 表格") }
                .getOrElse { first ->
                    runCatching { HtmlAcademicAdapter.decode(TabularCodec.html(rows)) }
                        .getOrElse { throw first }
                }
        }
        val text = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }
            .getOrElse { Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString() }
            .trim()
            .removePrefix("\uFEFF")
        if (text.startsWith(ShareCode.PREFIX)) return ImportResult(listOf(ShareCode.decode(text)))
        if (text.contains("BEGIN:VCALENDAR")) return CalendarCodec.decode(text)
        if (text.startsWith("<") || text.contains(Regex("<table[ >]", RegexOption.IGNORE_CASE)))
            return HtmlAcademicAdapter.decode(text)
        if (
            text.startsWith("{") ||
                text.startsWith("[") ||
                text.lineSequence().any { it.trim().startsWith("[{") }
        )
            return json(text)
        return TabularAcademicAdapter.decode(TabularCodec.readCsv(text), "CSV 表格")
    }

    private fun json(text: String): ImportResult {
        val root = runCatching { BackupCodec.json.parseToJsonElement(text) }.getOrNull()
        if (
            root is JsonObject &&
                root["format"]?.jsonPrimitive?.contentOrNull == "QingluanTimetable"
        )
            return ImportResult(listOf(TimetableDocument.decode(text)))
        if (root is JsonObject && root["app"]?.jsonPrimitive?.contentOrNull == "QingluanSchedule") {
            val b = BackupCodec.decode(text)
            return ImportResult(b.data.terms.map { TimetableDocument.from(b.data, it.id) })
        }
        val parts =
            if (root != null) listOf(root)
            else
                text
                    .lineSequence()
                    .mapNotNull {
                        runCatching { BackupCodec.json.parseToJsonElement(it.trim()) }.getOrNull()
                    }
                    .toList()
        return AcademicJsonCodec.decode(parts)
    }
}
