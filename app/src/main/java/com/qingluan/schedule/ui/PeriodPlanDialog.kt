package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.model.*

@Composable
internal fun PeriodPlanDialog(
    periods: List<Period>,
    dismiss: () -> Unit,
    apply: (List<Period>) -> Unit,
) {
    var start by rememberSaveable { mutableIntStateOf(periods.first().start) }
    var duration by rememberSaveable {
        mutableIntStateOf((periods.first().end - periods.first().start).coerceIn(1, 180))
    }
    var rest by rememberSaveable {
        mutableIntStateOf(
            if (periods.size > 1) (periods[1].start - periods[0].end).coerceIn(0, 180) else 10
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("批量设置时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(
                    "第一节开始",
                    start,
                    {
                        start = it
                        error = null
                    },
                )
                Choice(
                    "每节课时长",
                    duration,
                    (1..180).map { it to "$it 分钟" },
                    {
                        duration = it
                        error = null
                    },
                    Modifier.fillMaxWidth(),
                )
                Choice(
                    "课间休息",
                    rest,
                    (0..180).map { it to "$it 分钟" },
                    {
                        rest = it
                        error = null
                    },
                    Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    runCatching { planPeriods(start, periods.size, duration, rest) }
                        .onSuccess(apply)
                        .onFailure { error = it.message }
                }
            ) {
                Text("应用至全部节次")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
