package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.model.*
import java.time.LocalDate

@Composable
fun CalendarRulesScreen(s: Snapshot, vm: AppViewModel) {
    val term = s.terms.firstOrNull { it.id == s.activeTermId }
    var open by rememberSaveable { mutableStateOf(false) }
    var editing by rememberJsonState<CalendarRule?> { null }
    var deleting by rememberJsonState<CalendarRule?> { null }
    val rules = s.calendarRules.filter { it.termId == term?.id }.sortedBy { it.date }
    LazyColumn(
        Modifier.fillMaxSize().testTag("calendar-rules"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(
                onClick = {
                    editing = null
                    open = true
                },
                enabled = term != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加安排")
            }
        }
        if (term == null) item { EmptyState("请先创建课表") }
        else if (rules.isEmpty()) item { EmptyState("暂无节假日或调休") }
        items(rules, key = { it.id }) { rule ->
            InfoCard(
                "${rule.date} · ${rule.name}",
                if (rule.teachingDate.isBlank()) "全天停课" else "按 ${rule.teachingDate} 的常规课表上课",
                { Icon(Icons.Outlined.CalendarMonth, null) },
                {
                    editing = rule
                    open = true
                },
                trailing = {
                    IconButton(onClick = { deleting = rule }) {
                        Icon(Icons.Outlined.DeleteOutline, "删除安排 ${rule.date}")
                    }
                },
            )
        }
    }
    if (open && term != null)
        key(editing?.id) {
            var start by rememberDate {
                editing?.date?.let(LocalDate::parse)
                    ?: LocalDate.now()
                        .coerceIn(LocalDate.parse(term.startDate), ScheduleEngine.endDate(term))
            }
            var end by rememberDate { start }
            var source by rememberDate {
                editing?.teachingDate?.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                    ?: LocalDate.parse(term.startDate)
            }
            var mode by rememberSaveable {
                mutableStateOf(
                    if (editing?.teachingDate?.isNotBlank() == true) "makeup" else "holiday"
                )
            }
            var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
            FormScreen(
                if (editing == null) "添加安排" else "编辑安排",
                { open = false },
                {
                    vm.saveCalendarRange(
                        term.id,
                        start,
                        if (mode == "holiday") end else start,
                        if (mode == "makeup") source else null,
                        name,
                        editing?.id,
                    ) {
                        open = false
                    }
                },
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Choice(
                    "安排类型",
                    mode,
                    listOf("holiday" to "节假日停课", "makeup" to "调休上课"),
                    { mode = it },
                    Modifier.fillMaxWidth(),
                )
                DateField(
                    if (mode == "holiday") "开始日期" else "上课日期",
                    start,
                    {
                        start = it
                        if (end < it) end = it
                    },
                    Modifier.fillMaxWidth(),
                )
                if (mode == "holiday") DateField("结束日期", end, { end = it }, Modifier.fillMaxWidth())
                else DateField("采用哪一天的常规课表", source, { source = it }, Modifier.fillMaxWidth())
            }
        }
    deleting?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这天的安排？") },
            text = { Text("将恢复常规课表，临时调整保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeCalendarRule(rule.id)
                        deleting = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}
