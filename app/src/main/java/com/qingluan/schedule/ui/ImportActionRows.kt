package com.qingluan.schedule.ui

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.AppViewModel

@Composable
fun ImportActionRows(vm: AppViewModel, onEducation: () -> Unit) {
    val context = LocalContext.current
    var paste by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(vm::previewImport)
        }
    SettingsGroup {
        InfoCard("教务处导入", "", { Icon(Icons.Outlined.Language, null) }, onEducation)
        InfoCard(
            "从文件或二维码导入",
            "JSON · CSV · Excel · ICS · HTML",
            { Icon(Icons.Outlined.Restore, null) },
            { open.launch(arrayOf("*/*")) },
        )
        InfoCard(
            "粘贴分享口令或课表内容",
            "",
            { Icon(Icons.Outlined.Description, null) },
            {
                text = ""
                paste = true
            },
        )
    }
    if (paste)
        AlertDialog(
            onDismissRequest = { paste = false },
            title = { Text("导入内容") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        text,
                        { text = it.take(200000) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        label = { Text("分享口令 / JSON / CSV / HTML") },
                    )
                    TextButton(
                        onClick = {
                            val clip =
                                context.getSystemService(ClipboardManager::class.java).primaryClip
                            text =
                                clip
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                    .orEmpty()
                                    .take(200000)
                        }
                    ) {
                        Text("粘贴")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        paste = false
                        vm.previewText(text)
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text("读取")
                }
            },
            dismissButton = { TextButton(onClick = { paste = false }) { Text("取消") } },
        )
}
