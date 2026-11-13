package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.transfer.*
import kotlinx.serialization.json.*

@Composable
internal fun ImportFieldDialog(
    data: AcademicMappingData,
    dismiss: () -> Unit,
    apply: (AcademicFieldSelection) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var teacher by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf("") }
    val choices =
        remember(data) {
            val examples = linkedMapOf<String, String>()
            data.rows.filterIsInstance<JsonObject>().take(1000).forEach { row ->
                row.forEach { (key, v) ->
                    if (
                        AcademicJsonCodec.safeField(key) &&
                            v is JsonPrimitive &&
                            v.contentOrNull?.isNotBlank() == true
                    )
                        examples.putIfAbsent(key, v.content.take(28))
                }
            }
            listOf("" to "自动识别") +
                examples.entries
                    .sortedBy { it.key }
                    .take(100)
                    .map { it.key to "${it.key} · ${it.value}" }
        }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("核对字段") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Choice("课程名称", name, choices, { name = it }, Modifier.fillMaxWidth())
                Choice("教师", teacher, choices, { teacher = it }, Modifier.fillMaxWidth())
                Choice("地点", room, choices, { room = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { apply(AcademicFieldSelection(name, teacher, room)) }) {
                Text("重新识别")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
