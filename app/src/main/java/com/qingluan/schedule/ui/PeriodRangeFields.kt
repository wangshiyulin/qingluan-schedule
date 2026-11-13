package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PeriodRangeFields(start: Int, end: Int, count: Int, change: (Int, Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice(
            "开始",
            start,
            (1..count).map { it to "${it}节" },
            { change(it, maxOf(it, end)) },
            Modifier.weight(1f),
            compact = true,
        )
        Choice(
            "结束",
            end,
            (start..count).map { it to "${it}节" },
            { change(start, it) },
            Modifier.weight(1f),
            compact = true,
        )
    }
}
