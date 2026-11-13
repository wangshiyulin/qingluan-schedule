package com.qingluan.schedule.model

/** Uniform spacing, applied to a draft only after the whole plan passes validation. */
fun planPeriods(start: Int, count: Int, lessonMinutes: Int, breakMinutes: Int): List<Period> {
    require(start in 0..1439 && count in 1..24) { "开始时间或节次无效" }
    require(lessonMinutes in 1..180 && breakMinutes in 0..180) { "课时或休息时长无效" }
    val end = start + count * lessonMinutes + (count - 1) * breakMinutes
    require(end <= 1439) { "排时超出当天，请缩短时长或提前开始" }
    return List(count) { index ->
        val minute = start + index * (lessonMinutes + breakMinutes)
        Period(minute, minute + lessonMinutes)
    }
}
