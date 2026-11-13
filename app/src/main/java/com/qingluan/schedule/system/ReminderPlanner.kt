package com.qingluan.schedule.system

import com.qingluan.schedule.data.AppSettings
import com.qingluan.schedule.model.*
import java.time.LocalDateTime

data class PlannedReminder(val occurrence: Occurrence, val trigger: LocalDateTime)

object ReminderPlanner {
    fun plan(
        s: Snapshot,
        p: AppSettings,
        now: LocalDateTime,
        delivered: Set<String>,
    ): List<PlannedReminder> {
        if (!p.reminders) return emptyList()
        return ScheduleEngine.upcoming(s, s.activeTermId, now, 7)
            .filter { it.key !in delivered }
            .map { o ->
                val due =
                    o.date.atStartOfDay().plusMinutes((o.startMinute - p.leadMinutes).toLong())
                PlannedReminder(o, if (due > now.plusSeconds(1)) due else now.plusSeconds(2))
            }
            .sortedBy { it.trigger }
    }
}
