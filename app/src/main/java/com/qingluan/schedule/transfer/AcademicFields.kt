package com.qingluan.schedule.transfer

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Semantic metadata first; positional fields are accepted only for a known teaching-grid layout.
 */
internal object AcademicFields {
    data class Values(val teacher: String, val room: String)

    private val teacherLabels =
        listOf("任课教师", "授课教师", "上课教师", "教师姓名", "主讲教师", "授课老师", "任课老师", "教师", "老师")
    private val roomLabels = listOf("上课教室", "上课地点", "教学地点", "教学场地", "教室名称", "教室", "地点")
    private val boundary = "任课教师|授课教师|教师|老师|上课地点|上课教室|教室|地点|课程代码|课程号|周次|节次"

    private fun clean(text: String, labels: List<String>): String {
        val value = text.trim()
        if (value in labels) return ""
        return value
            .replace(Regex("^(?:${labels.joinToString("|")})(?:\\s*[:：]\\s*|\\s+)"), "")
            .trim()
    }

    private fun labeled(text: String, labels: List<String>): String =
        Regex(
                "(?:^|[\\n;；|]|\\s)(?:${labels.joinToString("|")})\\s*[:：]\\s*(.*?)(?=\\s*(?:$boundary)\\s*[:：]|\\n|$)"
            )
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()

    fun read(item: Element, source: Document?): Values {
        fun field(
            labels: List<String>,
            selector: String,
            attributes: List<String>,
            icon: Regex,
        ): String {
            for (attribute in attributes) item
                .select("[$attribute]")
                .firstOrNull { it.attr(attribute).isNotBlank() }
                ?.let {
                    return clean(it.attr(attribute), labels)
                }
            item
                .select(selector)
                .firstOrNull { it.text().isNotBlank() }
                ?.let {
                    return clean(it.text(), labels)
                }
            for (node in
                item.select(
                    "[title],[data-label],[data-original-title],[data-content],[aria-label],dt"
                )) {
                val hints =
                    listOf(
                        node.attr("title"),
                        node.attr("data-label"),
                        node.attr("data-original-title"),
                        node.attr("data-content"),
                        node.attr("aria-label"),
                        if (node.tagName() == "dt") node.text() else "",
                    )
                for (hint in hints) {
                    labeled(hint, labels)
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return it
                        }
                    if (labels.any { hint.trim().trimEnd(':', '：') == it }) {
                        val fonts = node.select("font")
                        clean(
                                if (fonts.isNotEmpty()) fonts.last()!!.text() else node.text(),
                                labels,
                            )
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                return it
                            }
                        if (node.tagName() !in listOf("span", "i", "dt", "label", "font")) continue
                        val sibling = node.nextSibling()
                        clean(
                                if (sibling is Element) sibling.text()
                                else sibling?.toString().orEmpty(),
                                labels,
                            )
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                return it
                            }
                        node
                            .nextElementSibling()
                            ?.text()
                            ?.let { clean(it, labels) }
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                return it
                            }
                    }
                }
            }
            for (node in item.select("i,svg,use,span")) {
                val signature =
                    node.className() + " " + node.attr("href") + " " + node.attr("xlink:href")
                if (icon.containsMatchIn(signature)) {
                    val parent = node.closest("p,dd,li")
                    val candidate =
                        parent?.select("font")?.lastOrNull()?.text()
                            ?: node.nextElementSibling()?.text().orEmpty()
                    clean(candidate, labels)
                        .takeIf { it.isNotBlank() && !it.contains(Regex("\\d.*(?:周|节)")) }
                        ?.let {
                            return it
                        }
                }
            }
            labeled(item.wholeText().lineSequence().joinToString("\n") { it.trim() }, labels)
                .takeIf { it.isNotBlank() }
                ?.let {
                    return it
                }
            for (link in item.select("[aria-describedby],[data-target],[aria-controls]")) {
                val ids =
                    listOf(
                            link.attr("aria-describedby"),
                            link.attr("data-target"),
                            link.attr("aria-controls"),
                        )
                        .flatMap { it.trim().removePrefix("#").split(Regex("\\s+")) }
                for (id in ids) source?.getElementById(id)?.let { tooltip ->
                    labeled(tooltip.wholeText(), labels)
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            return it
                        }
                }
            }
            return ""
        }
        val paragraphs =
            item.select(".timetable_con > p,.timetable-con > p").filterNot { it.hasClass("title") }
        val timing = paragraphs.indexOfFirst { it.text().contains(Regex("\\d.*(?:周|节)")) }
        fun positional(offset: Int, labels: List<String>) =
            if (timing < 0) ""
            else
                paragraphs
                    .getOrNull(timing + offset)
                    ?.let { p ->
                        val fonts = p.select("font[color]")
                        clean(
                            if (fonts.isEmpty()) p.text()
                            else fonts.joinToString(" ") { it.text() },
                            labels,
                        )
                    }
                    .orEmpty()
        val teacher =
            field(
                    teacherLabels,
                    ".teacher,.teacher-name,.teacherName,[data-field=teacher],[data-field=teacherName],.jsxm",
                    listOf("data-teacher", "data-teacher-name"),
                    Regex("(?:glyphicon-user|fa-user|icon-(?:teacher|user))"),
                )
                .ifBlank { positional(2, teacherLabels) }
        val room =
            field(
                    roomLabels,
                    ".classroom,.location,.room,[data-field=classroom],[data-field=location],.jxdd",
                    listOf("data-classroom", "data-location", "data-room"),
                    Regex(
                        "(?:glyphicon-(?:map-marker|home)|fa-map-marker|icon-(?:classroom|location|home))"
                    ),
                )
                .ifBlank { positional(1, roomLabels) }
        return Values(teacher, room)
    }
}
