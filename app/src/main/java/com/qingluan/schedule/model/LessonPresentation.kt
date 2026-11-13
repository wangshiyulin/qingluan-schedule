package com.qingluan.schedule.model

import java.text.Normalizer
import java.util.Locale

const val DEFAULT_COURSE_COLOR: Long = 0xFF1B9B85
// Copyright (C) 2025 XingHeYuZhuan — Apache-2.0.
// Paired day/night constants from Shiguang Schedule / ScheduleGridStyle.kt.
val courseColors =
    listOf(
        0xFFFFCC99,
        0xFFFFE699,
        0xFFE6FF99,
        0xFFCCFF99,
        0xFF99FFB3,
        0xFF99FFE6,
        0xFF99FFFF,
        0xFF99E6FF,
        0xFFB399FF,
        0xFFFF99E6,
        0xFFFF99CC,
        0xFFFF99B3,
    )
val nightCourseColors =
    listOf(
        0xFF663300,
        0xFF664D00,
        0xFF4D6600,
        0xFF336600,
        0xFF00661A,
        0xFF00664D,
        0xFF006666,
        0xFF004D66,
        0xFF1A0066,
        0xFF66004D,
        0xFF660033,
        0xFF66001A,
    )

private val whitespace = Regex("[\\s\\p{Z}\u200B-\u200D\uFEFF]+")

private fun String.courseIdentity() =
    Normalizer.normalize(this, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(whitespace, "")

private val unknownPlaces = setOf("自定义教室", "自定义", "未知", "待定", "未设置", "无", "暂无", "--", "-")

private fun List<String>.displayValues() = filter {
    it.isNotBlank() && it.courseIdentity() !in unknownPlaces
}
    .distinctBy { it.courseIdentity() }
    .joinToString(" / ")

/** Default-colored imports get distinct, stable colors; explicit user choices are respected. */
fun courseColorMap(courses: List<Course>): Map<String, Long> = buildMap {
    courses
        .groupBy { it.termId }
        .values
        .forEach { termCourses ->
            val usage = IntArray(courseColors.size)
            val names = mutableMapOf<String, Long>()
            termCourses
                .filter { !it.automaticColor || it.color != DEFAULT_COURSE_COLOR }
                .forEach { course ->
                    put(course.id, course.color)
                    names.putIfAbsent(course.name.courseIdentity(), course.color)
                    val index = courseColors.indexOf(course.color)
                    if (index >= 0) usage[index]++
                }
            termCourses
                .filter { it.automaticColor && it.color == DEFAULT_COURSE_COLOR }
                .groupBy { it.name.courseIdentity() }
                .toSortedMap()
                .forEach { (name, group) ->
                    val chosen =
                        names.getOrPut(name) {
                            val preferred = Math.floorMod(name.hashCode(), courseColors.size)
                            val index =
                                (courseColors.indices)
                                    .map { (it + preferred) % courseColors.size }
                                    .minBy { usage[it] }
                            usage[index]++
                            courseColors[index]
                        }
                    group.forEach { put(it.id, chosen) }
                }
        }
}

data class LessonBlock(val members: List<Occurrence>) {
    val first
        get() = members.first()

    val startPeriod
        get() = members.minOf { it.startPeriod }

    val endPeriod
        get() = members.maxOf { it.endPeriod }

    val startMinute
        get() = members.minOf { it.startMinute }

    val endMinute
        get() = members.maxOf { it.endMinute }

    val location
        get() = members.map { it.location }.displayValues()

    val teacher
        get() = members.map { it.teacher }.displayValues()

    val adjusted
        get() = members.any { it.exception != null }
}

/** Merge display runs, retaining every original id and the details of each segment. */
fun lessonBlocks(occurrences: List<Occurrence>): List<LessonBlock> =
    occurrences
        .groupBy { it.date to it.course.name.courseIdentity() }
        .values
        .flatMap { group ->
            val blocks = mutableListOf<LessonBlock>()
            group.sortedWith(compareBy({ it.startPeriod }, { it.endPeriod })).forEach { current ->
                val candidates =
                    blocks.indices.filter { index ->
                        val block = blocks[index]
                        current.startPeriod == block.endPeriod + 1 ||
                            (current.startPeriod <= block.endPeriod &&
                                block.members.all {
                                    it.course.id == current.course.id &&
                                        it.teacher.trim() == current.teacher.trim() &&
                                        it.location.trim() == current.location.trim()
                                })
                    }
                val sameSource = candidates.filter { index ->
                    blocks[index].members.any { it.course.id == current.course.id }
                }
                val sameDetails = candidates.filter { index ->
                    blocks[index].members.any {
                        it.teacher.trim() == current.teacher.trim() &&
                            it.location.trim() == current.location.trim()
                    }
                }
                val target =
                    when {
                        sameSource.size == 1 -> sameSource.single()
                        sameDetails.size == 1 -> sameDetails.single()
                        candidates.size == 1 -> candidates.single()
                        else -> null
                    }
                if (target != null) blocks[target] = LessonBlock(blocks[target].members + current)
                else blocks += LessonBlock(listOf(current))
            }
            blocks
        }
        .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }, { it.first.course.name }))
