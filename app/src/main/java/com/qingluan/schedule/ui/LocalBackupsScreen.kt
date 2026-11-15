package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun LocalBackupsScreen(p: AppSettings, vm: AppViewModel) {
    val entries by vm.localBackups.collectAsStateWithLifecycle()
    val pending by vm.pendingLocalBackup.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<LocalBackupEntry?>(null) }
    LaunchedEffect(Unit) { vm.loadLocalBackups() }
    LazyColumn(
        Modifier.fillMaxSize().testTag("local-backups"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            SettingsGroup {
                SettingToggle("本地自动备份", "课表修改前保存", p.autoBackupEnabled) { v ->
                    vm.updateSettings { it.copy(autoBackupEnabled = v) }
                }
                Choice(
                    "保留份数",
                    p.autoBackupKeep,
                    listOf(3, 5, 10).map { it to "$it 份" },
                    { v ->
                        vm.updateSettings { it.copy(autoBackupKeep = v) }
                        vm.loadLocalBackups()
                    },
                    Modifier.fillMaxWidth(),
                )
                ValueRow(
                    "撤销最近修改",
                    entries.firstOrNull()?.reason?.title ?: "暂无备份",
                    { vm.undoLatestChange() },
                )
            }
        }
        if (entries.isEmpty()) item { EmptyState("暂无自动备份") }
        items(entries, key = { it.id }) { entry ->
            InfoCard(
                Instant.ofEpochMilli(entry.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")),
                "${entry.reason.title} · ${(entry.bytes+1023)/1024} KB",
                { Icon(Icons.Outlined.Restore, null) },
                { vm.previewLocalBackup(entry) },
                dense = true,
                trailing = {
                    IconButton(onClick = { deleting = entry }) {
                        Icon(Icons.Outlined.DeleteOutline, "删除自动备份 ${entry.id}")
                    }
                },
            )
        }
    }
    pending?.let { (entry, backup) ->
        AlertDialog(
            onDismissRequest = { vm.pendingLocalBackup.value = null },
            title = { Text("恢复自动备份") },
            text = {
                Text(
                    "${backup.data.terms.size} 张课表 · ${backup.data.courses.size} 门课程。${if(p.autoBackupEnabled)"当前数据会先保留备份。" else "自动备份已关闭，当前数据将被替换。"}"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.restoreLocalBackup(entry.id) }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { vm.pendingLocalBackup.value = null }) { Text("取消") }
            },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这份备份？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteLocalBackup(entry.id)
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
