@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.model.*

@Composable
fun CourseDetails(
    o: Occurrence,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onTemporary: () -> Unit,
    onCopy: () -> Unit,
    alternatives: List<Occurrence> = emptyList(),
    onSelect: (Occurrence) -> Unit = {},
) {
    val block =
        remember(o, alternatives) {
            lessonBlocks((alternatives + o).distinctBy { it.key }).first { candidate ->
                candidate.members.any { it.key == o.key }
            }
        }
    val schedule = remember(o.course) { o.course.coalesced().meetings }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("course-detail-sheet"),
        shape =
            androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                o.course.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (alternatives.size > 1)
                Choice(
                    "上课时段",
                    o.key,
                    alternatives.map { it.key to periodLabel(it.startPeriod, it.endPeriod) },
                    { key -> onSelect(alternatives.first { it.key == key }) },
                    Modifier.fillMaxWidth(),
                )
            Text(
                "${o.date} · ${weekdayNames[o.date.dayOfWeek.value-1]} · ${periodLabel(block.startPeriod, block.endPeriod)}"
            )
            Text(
                "${clock(block.startMinute)}–${clock(block.endMinute)}",
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                listOf(block.location, block.teacher).filter { it.isNotBlank() }.joinToString(" · ")
            )
            schedule.forEach { m ->
                Text(
                    "${weekdayNames[m.weekday-1]} · ${periodLabel(m.startPeriod, m.endPeriod)} · ${weekLabel(m.weeks)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (o.course.note.isNotBlank())
                Text(o.course.note, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onTemporary, modifier = Modifier.weight(1f)) { Text("临时调整") }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("编辑整门课") }
            }
            TextButton(onClick = onCopy) { Text("复制这门课程") }
        }
    }
}
