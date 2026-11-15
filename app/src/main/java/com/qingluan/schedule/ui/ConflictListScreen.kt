package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConflictListScreen(s: Snapshot, onOpenWeek: (Int) -> Unit) {
    val term = s.terms.firstOrNull { it.id == s.activeTermId }
    val conflicts by
        produceState<List<Conflict>?>(null, s) {
            value =
                withContext(Dispatchers.Default) {
                    term?.let { ScheduleEngine.conflicts(s, it.id) } ?: emptyList()
                }
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("conflict-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            conflicts == null -> item { Text("检查中") }
            conflicts!!.isEmpty() -> item { EmptyState("暂无课程冲突") }
            else -> {
                item {
                    Text(if (conflicts!!.size >= 500) "至少 500 处冲突" else "${conflicts!!.size} 处冲突")
                }
                items(conflicts!!.indices.toList(), key = { it }) { i ->
                    val c = conflicts!![i]
                    InfoCard(
                        "${c.date} · 第 ${ScheduleEngine.week(term!!,c.date)} 周",
                        "${c.first.course.name} / ${c.second.course.name}\n${clock(maxOf(c.first.startMinute,c.second.startMinute))}–${clock(minOf(c.first.endMinute,c.second.endMinute))}",
                        { Icon(Icons.Outlined.Info, null) },
                        { onOpenWeek(ScheduleEngine.week(term, c.date)) },
                    )
                }
            }
        }
    }
}
