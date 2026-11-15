package com.qingluan.schedule.transfer

import com.qingluan.schedule.data.BackupCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class AcademicWebCapture(
    val format: String = "QingluanWebCapture",
    val records: JsonArray = JsonArray(emptyList()),
    val documents: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val incomplete: Boolean = false,
    val requestId: String = "",
)

/**
 * Keep independent sources selectable; prefer explicit data over heuristic visual reconstruction.
 */
object WebImportPipeline {
    fun decode(text: String, mode: String = "auto"): ImportResult {
        require(mode in setOf("auto", "table", "data")) { "未知提取方式" }
        require(text.toByteArray().size <= BackupCodec.MAX_BYTES) { "页面内容超过8 MiB，请打开独立课表页面" }
        val capture = BackupCodec.json.decodeFromString<AcademicWebCapture>(text)
        require(
            capture.format == "QingluanWebCapture" &&
                capture.documents.size <= 65 &&
                capture.records.size <= 10000
        ) {
            "网页数据无效"
        }
        val options = mutableListOf<ImportSource>()
        val errors = mutableListOf<String>()
        fun source(label: String, block: () -> ImportResult) {
            runCatching(block)
                .onSuccess { result ->
                    val assessment =
                        result.assessment
                            ?: ImportAssessment(
                                label,
                                result.documents.sumOf { d ->
                                    d.courses.sumOf { it.meetings.size }
                                },
                                result.documents.sumOf { d ->
                                    d.courses.sumOf { it.meetings.size }
                                },
                                dateUnknown = true,
                            )
                    val warnings =
                        result.warnings +
                            capture.warnings.take(20) +
                            if (capture.incomplete) listOf("部分内嵌页面尚未完整读取，请打开独立课表页面核对")
                            else emptyList()
                    options +=
                        ImportSource(
                            label,
                            result.copy(
                                warnings = warnings.distinct(),
                                assessment =
                                    assessment.copy(
                                        source = label,
                                        incomplete =
                                            assessment.incomplete ||
                                                capture.incomplete ||
                                                capture.warnings.isNotEmpty(),
                                    ),
                            ),
                        )
                }
                .onFailure { errors += it.message.orEmpty().take(150) }
        }
        if (mode != "table" && capture.records.isNotEmpty())
            source("网页接口 / 页面数据") {
                AcademicJsonCodec.decode(
                    listOf(JsonObject(mapOf("records" to capture.records))),
                    "网页接口 / 页面数据",
                )
            }
        if (mode != "data")
            capture.documents.forEachIndexed { index, html ->
                source("课表网页 ${index+1}") { HtmlAcademicAdapter.decode(html) }
            }
        require(options.isNotEmpty()) {
            "未能识别完整课表：${errors.firstOrNull().orEmpty()}。请打开完整学期课表，或使用教务导出的文件"
        }
        return choose(options)
    }

    fun choose(options: List<ImportSource>): ImportResult {
        val merged = runCatching {
            AcademicSourceUnion.combine(options.map { it.result })
        }
            .getOrNull()
        val all =
            if (merged != null) listOf(ImportSource("合并识别", merged)) + options
            else if (options.size > 1)
                options.map { option ->
                    option.copy(
                        result =
                            option.result.copy(
                                warnings =
                                    (option.result.warnings + "多个来源未自动合并，请切换来源核对完整课表").distinct(),
                                assessment = option.result.assessment?.copy(incomplete = true),
                            )
                    )
                }
            else options
        val preferred =
            if (merged != null) 0
            else
                all.indices.maxBy { index ->
                    val result = all[index].result
                    val courses = result.documents.flatMap { it.courses }
                    courses.sumOf { it.meetings.size } * 4 +
                        courses.count { it.teacher.isNotBlank() && it.location.isNotBlank() } * 2
                }
        return all[preferred].result.copy(sources = all, selectedSource = preferred)
    }

    fun remap(current: ImportResult, selection: AcademicFieldSelection): ImportResult {
        val data = current.mapping ?: error("此来源不支持字段映射")
        val result =
            AcademicJsonCodec.decodeMapped(data, selection, current.assessment?.source ?: "课表数据")
        if (current.sources.isEmpty()) return result
        val options =
            current.sources
                .filter { it.label != "合并识别" }
                .map { option ->
                    if (option.result.mapping == data) {
                        val old = option.result
                        ImportSource(
                            option.label,
                            result.copy(
                                warnings =
                                    (result.warnings +
                                            old.warnings.filter {
                                                it.contains("页面") || it.contains("上限")
                                            })
                                        .distinct(),
                                assessment =
                                    result.assessment?.copy(
                                        incomplete =
                                            result.assessment.incomplete ||
                                                old.assessment?.incomplete == true
                                    ),
                            ),
                        )
                    } else option
                }
        return choose(options)
    }
}
