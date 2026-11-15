package com.qingluan.schedule.ui

import android.Manifest
import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.data.AppSettings
import com.qingluan.schedule.model.Snapshot
import com.qingluan.schedule.system.SystemCoordinator
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun ReminderDiagnostics(s: Snapshot, p: AppSettings, vm: AppViewModel) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val allowed = remember(revision) { SystemCoordinator.notificationsAllowed(ctx) }
    val channel =
        remember(revision) {
            ctx.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(SystemCoordinator.CHANNEL)
                ?.importance != NotificationManager.IMPORTANCE_NONE
        }
    val exact =
        remember(revision) {
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }
    val battery =
        remember(revision) {
            ctx.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(ctx.packageName)
        }
    val next by
        produceState("检查中", s, p, revision, owner) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    value =
                        withContext(Dispatchers.IO) {
                            if (!p.reminders) "未开启"
                            else if (!allowed || !channel) "通知被关闭"
                            else
                                SystemCoordinator.nextReminder(ctx, s, p)?.let {
                                    "${it.trigger.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))} · ${it.occurrence.course.name}"
                                } ?: "未来七天暂无课程"
                        }
                    delay(30_000)
                }
            }
        }
    fun open(intent: Intent) {
        runCatching { ctx.startActivity(intent) }.onFailure { vm.message.value = "无法打开系统设置" }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            revision++
            if (it) vm.testReminder() else vm.message.value = "未允许通知"
        }
    SettingsGroup {
        ValueRow(
            "通知权限",
            if (allowed && channel) "已允许" else "未允许",
            {
                open(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
            },
        )
        ValueRow(
            "准时提醒",
            if (exact) "已允许" else "可能延迟",
            {
                open(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${ctx.packageName}"),
                    )
                )
            },
        )
        ValueRow(
            "后台运行",
            if (battery) "不受电量优化限制" else "受系统电量优化管理",
            { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        )
        SettingsStatusRow("下一次提醒", next, Modifier.testTag("reminder-next"))
        ValueRow(
            "测试提醒",
            "发送一条通知",
            {
                if (allowed) vm.testReminder()
                else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }
}
