package com.qingluan.schedule.transfer

import org.jsoup.nodes.Element

/** Recognize the explicit course-code + (weeks)(sections) format, not arbitrary Chinese names. */
internal object AcademicInlineText {
    private val code = Regex("[A-Za-z0-9]{5,16}\\s*[-—－]\\s*[A-Za-z0-9]{1,10}")
    private val slot =
        Regex("[（(]\\s*\\d[\\d\\s,，、~～至–—-]*周\\s*[）)]\\s*[（(]\\s*\\d[\\d\\s,，、~～至–—-]*节\\s*[）)]")
    private val person = Regex("[\\p{IsHan}·]{2,12}(?:[,，、/][\\p{IsHan}·]{2,12})*")
    private val room =
        Regex("(?:[A-Za-z]{1,3}\\s*)?\\d{2,6}(?:\\s*[-—－]\\s*[A-Za-z0-9]{1,8})*(?:室|教室)?")
    private val namedRoom =
        Regex("自定义教室|多媒体教室|教室|实验室|报告厅|体育馆|体育场|运动场|操场|田径场|篮球场|网球场|会议室|教学楼|实验楼|综合楼|线上(?:教学)?|网络教学")
    private val locationHint =
        Regex("校区|教室|实验室|报告厅|体育|运动场|操场|田径场|篮球场|网球场|会议室|教学楼|实验楼|综合楼|[一二三四五六七八九十0-9]号?楼|线上|网络教学")
    private val adminLabel = Regex("^(?:行政班|教学班|班级|选课人数|人数|学分|课程性质|考核|备注|选课)[:：]?.*")
    private val adminClass = Regex("[\\p{IsHan}]+\\d{2,}(?:级\\d+)?(?:班)?(?:[;；].*)?")

    private val unknownTeacher = setOf("待定", "未安排", "未知", "暂无")
    private val classSuffix =
        Regex("\\s+(?:行政班|教学班|班级)[:：]|\\s+[\\p{IsHan}]+\\d{2,}(?:级\\d+)?(?:班)?(?:[;；]|$)")

    private fun roomEnd(text: String): Int? =
        (room.findAll(text) + namedRoom.findAll(text)).maxOfOrNull { it.range.last + 1 }

    private fun isTeacher(text: String) =
        person.matches(text) && !locationHint.containsMatchIn(text) && text !in unknownTeacher

    private fun isAdministrative(text: String) =
        adminLabel.matches(text) ||
            (adminClass.matches(text) && !locationHint.containsMatchIn(text))

    private fun lines(item: Element): List<String> =
        item.wholeText().lines().map { it.trim() }.filter(String::isNotBlank)

    fun split(item: Element): List<String> {
        val lines = lines(item)
        val starts =
            lines.indices.filter { i ->
                i + 2 < lines.size &&
                    !code.matches(lines[i]) &&
                    code.matches(lines[i + 1]) &&
                    slot.containsMatchIn(lines[i + 2])
            }
        if (starts.size < 2) return emptyList()
        return starts.mapIndexed { index, start ->
            val part = Element("div")
            part.appendElement("b").text(lines[start])
            for (line in lines.subList(start + 1, starts.getOrNull(index + 1) ?: lines.size)) {
                part.appendElement("br")
                part.appendText(line)
            }
            part.outerHtml()
        }
    }

    fun read(item: Element): AcademicFields.Values {
        val lines = lines(item)
        val i =
            lines.indices.firstOrNull { index ->
                index > 0 && code.matches(lines[index - 1]) && slot.containsMatchIn(lines[index])
            } ?: return AcademicFields.Values("", "")
        val match = slot.find(lines[i]) ?: return AcademicFields.Values("", "")
        // Browsers wrap campus, room and teacher into separate DOM lines. Stay inside this
        // code/timing record and stop at class lists or the next record, rather than guessing.
        val first = lines[i].substring(match.range.last + 1).trim().trimStart(':', '：')
        val metadata =
            sequenceOf(first)
                .plus(lines.drop(i + 1).asSequence())
                .map { it.replace('\u00a0', ' ').trim() }
                .filter(String::isNotBlank)
                .take(6)
                .takeWhile {
                    !code.matches(it) && !slot.containsMatchIn(it) && !isAdministrative(it)
                }
                .toList()
        var tail = metadata.joinToString(" ")
        if (tail.isBlank()) return AcademicFields.Values("", "")
        val classTail =
            classSuffix.findAll(tail).firstOrNull { !locationHint.containsMatchIn(it.value) }
        if (classTail != null) tail = tail.substring(0, classTail.range.first).trim()
        var leadingTeacher = ""
        val firstToken = tail.substringBefore(' ')
        val rest = tail.substringAfter(' ', "").trim()
        if (isTeacher(firstToken) && roomEnd(rest) != null) {
            leadingTeacher = firstToken
            tail = rest
        }
        val end = roomEnd(tail)
        if (end != null) {
            val teacher =
                tail.substring(end).trim().trim(':', '：', ',', '，').takeIf(::isTeacher).orEmpty()
            return AcademicFields.Values(
                teacher.ifBlank { leadingTeacher },
                tail.substring(0, end).trim(),
            )
        }
        return AcademicFields.Values(tail.takeIf(::isTeacher).orEmpty(), "")
    }
}
