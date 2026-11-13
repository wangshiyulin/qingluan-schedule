package com.qingluan.schedule.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.qingluan.schedule.*
import com.qingluan.schedule.R
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.time.LocalDate

object AboutLinks {
    const val website = "https://schedule.qingluanx.com"
    const val github = "https://github.com/wangshiyulin/qingluan-schedule"
    const val blog = "https://qingluanx.com"
    const val updates = "$github/releases"
    const val feedback = "$github/issues"
}

@Composable
fun SettingsScreen(
    s: Snapshot,
    p: AppSettings,
    vm: AppViewModel,
    page: String,
    onNavigate: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val term = s.terms.find { it.id == s.activeTermId }
    val today = LocalDate.now()
    var license by rememberSaveable { mutableStateOf(false) }
    var paletteOpen by rememberSaveable { mutableStateOf(false) }
    var manualWeek by rememberSaveable { mutableStateOf(false) }
    val notify =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.updateSettings { it.copy(reminders = granted) }
            if (!granted) vm.message.value = "未允许通知"
        }
    fun link(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { vm.message.value = "无法打开链接" }
    }
    fun missing() {
        vm.message.value = "请先创建课表"
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag(if (page.isBlank()) "my-settings" else "settings-$page"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (page.isBlank()) {
            if (term != null)
                item {
                    SettingsGroup {
                        Choice(
                            "当前课表",
                            s.activeTermId,
                            s.terms.map { it.id to it.name },
                            { vm.activate(it) },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            item {
                SettingsGroup {
                    SettingLink(
                        "课表管理",
                        "",
                        { Icon(Icons.Outlined.CalendarMonth, null) },
                        { onNavigate("课表管理") },
                    )
                    SettingLink(
                        "课程管理",
                        "",
                        { Icon(Icons.Outlined.MenuBook, null) },
                        { onNavigate("课程管理") },
                    )
                    SettingLink(
                        "临时调整",
                        "",
                        { Icon(Icons.Outlined.EventRepeat, null) },
                        { onNavigate("临时调整") },
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingLink(
                        "个性化",
                        "",
                        { Icon(Icons.Outlined.Palette, null) },
                        { onNavigate("个性化") },
                    )
                    SettingLink(
                        "课程提醒",
                        "",
                        { Icon(Icons.Outlined.NotificationsNone, null) },
                        { onNavigate("课程提醒") },
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingLink(
                        "导入课表",
                        "",
                        { Icon(Icons.Outlined.Language, null) },
                        { onNavigate("导入课表") },
                    )
                    SettingLink(
                        "备份",
                        "",
                        { Icon(Icons.Outlined.SaveAlt, null) },
                        { onNavigate("备份") },
                    )
                    SettingLink("关于", "", { Icon(Icons.Outlined.Info, null) }, { onNavigate("关于") })
                }
            }
        }
        if (page == "课表管理" || page == "通用设置") {
            item {
                SettingsGroup {
                    if (term != null)
                        DateField(
                            "开学日期",
                            LocalDate.parse(term.startDate),
                            { vm.saveTerm(term.copy(startDate = it.toString())) },
                            Modifier.fillMaxWidth(),
                        )
                    else ValueRow("开学日期", "未设置", ::missing)
                    val week = term?.let { ScheduleEngine.week(it, today) } ?: 0
                    ValueRow(
                        "当前周",
                        when {
                            term == null -> "未设置"
                            week == 0 -> "未开学"
                            week > term.weeks -> "已结束"
                            else -> "第 $week 周"
                        },
                        { if (term == null) missing() else manualWeek = true },
                    )
                    if (term != null) {
                        Choice(
                            "总周数",
                            term.weeks,
                            (1..60).map { it to "$it 周" },
                            { vm.saveTerm(term.copy(weeks = it)) },
                            Modifier.fillMaxWidth(),
                        )
                        Choice(
                            "每周起始日",
                            term.firstDayOfWeek,
                            weekdayNames.mapIndexed { i, n -> i + 1 to n },
                            { vm.saveTerm(term.copy(firstDayOfWeek = it)) },
                            Modifier.fillMaxWidth(),
                        )
                    } else {
                        ValueRow("总周数", "未设置", ::missing)
                        ValueRow("每周起始日", "周一", ::missing)
                    }
                }
            }
            item {
                SettingsGroup {
                    SettingToggle("显示周末", "", p.showWeekend) { v ->
                        vm.updateSettings { it.copy(showWeekend = v) }
                    }
                    SettingToggle("显示非本周课程", "", p.showNonCurrentWeek) { v ->
                        vm.updateSettings { it.copy(showNonCurrentWeek = v) }
                    }
                }
            }
            item {
                SettingsGroup {
                    SettingLink(
                        "课表列表",
                        "",
                        { Icon(Icons.Outlined.CalendarMonth, null) },
                        { onNavigate("课表列表") },
                    )
                    SettingLink(
                        "作息管理",
                        "",
                        { Icon(Icons.Outlined.Schedule, null) },
                        { onNavigate("作息管理") },
                    )
                }
            }
        }
        if (page == "个性化") {
            item { SettingsHeading("外观") }
            item {
                SettingsGroup {
                    Choice(
                        "主题",
                        p.theme,
                        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                        { v -> vm.updateSettings { it.copy(theme = v) } },
                        Modifier.fillMaxWidth(),
                    )
                    val palette = AppPalette.find(p.palette)
                    ValueRow(
                        "配色",
                        if (p.dynamicColor) "系统动态" else palette.title,
                        { paletteOpen = true },
                    )
                    Choice(
                        "字号",
                        p.fontScale,
                        (listOf(.85f, 1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f) + p.fontScale)
                            .distinct()
                            .sorted()
                            .map { it to "${(it*100).toInt()}%" },
                        { v -> vm.updateSettings { it.copy(fontScale = v) } },
                        Modifier.fillMaxWidth(),
                    )
                    Choice(
                        "节次高度",
                        p.rowHeight,
                        (listOf(48, 56, 64, 72, 80, 96, 110) + p.rowHeight)
                            .distinct()
                            .sorted()
                            .map { it to it.toString() },
                        { v -> vm.updateSettings { it.copy(rowHeight = v) } },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
            item { SettingsHeading("操作反馈") }
            item {
                SettingsGroup {
                    SettingToggle("触觉反馈", "", p.hapticsEnabled) { v ->
                        vm.updateSettings { it.copy(hapticsEnabled = v) }
                    }
                }
            }
        }
        if (page == "课程提醒") {
            item {
                SettingsGroup {
                    Choice(
                        "提前时间",
                        p.leadMinutes,
                        (listOf(0, 5, 10, 15, 20, 30, 45, 60) + p.leadMinutes)
                            .distinct()
                            .sorted()
                            .map { it to if (it == 0) "上课时" else "$it 分钟" },
                        { v -> vm.updateSettings { it.copy(leadMinutes = v) } },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingToggle("课程提醒", "", p.reminders) { v ->
                        if (
                            v &&
                                ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                        )
                            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.updateSettings { it.copy(reminders = v) }
                    }
                }
            }
            item {
                SettingsGroup {
                    SettingLink(
                        "通知权限",
                        if (
                            ContextCompat.checkSelfPermission(
                                ctx,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                            "已允许"
                        else "未允许",
                        { Icon(Icons.Outlined.NotificationsNone, null) },
                        {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                )
                            }
                                .onFailure { vm.message.value = "无法打开通知设置" }
                        },
                    )
                    SettingLink(
                        "准时提醒",
                        if (ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
                            "已允许"
                        else "未允许",
                        { Icon(Icons.Outlined.Alarm, null) },
                        {
                            runCatching {
                                ctx.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${ctx.packageName}"),
                                    )
                                )
                            }
                                .onFailure { vm.message.value = "无法打开闹钟设置" }
                        },
                    )
                }
            }
        }
        if (page == "关于") {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painterResource(R.drawable.icon_art),
                        "青鸾课程表图标",
                        Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "青鸾课程表",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                            Triple("官网", Icons.Outlined.Language, AboutLinks.website),
                            Triple("GitHub", Icons.Outlined.Code, AboutLinks.github),
                            Triple("博客", Icons.AutoMirrored.Outlined.Article, AboutLinks.blog),
                        )
                        .forEach { (label, icon, url) ->
                            Column(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { link(url) }
                                    .padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    icon,
                                    null,
                                    Modifier.size(25.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                }
            }
            item {
                SettingsGroup {
                    SettingLink(
                        "检查更新",
                        "",
                        { Icon(Icons.Outlined.SystemUpdate, null) },
                        { link(AboutLinks.updates) },
                    )
                    SettingLink(
                        "问题反馈",
                        "",
                        { Icon(Icons.Outlined.Feedback, null) },
                        { link(AboutLinks.feedback) },
                    )
                    SettingLink(
                        "更新日志",
                        "",
                        { Icon(Icons.Outlined.History, null) },
                        { link(AboutLinks.updates) },
                    )
                    SettingLink(
                        "开源许可",
                        "",
                        { Icon(Icons.Outlined.Description, null) },
                        { license = true },
                    )
                }
            }
        }
    }
    if (paletteOpen)
        PalettePicker(if (p.dynamicColor) "system" else p.palette, { paletteOpen = false }) { key ->
            vm.updateSettings {
                it.copy(
                    dynamicColor = key == "system",
                    palette = if (key == "system") it.palette else key,
                )
            }
            paletteOpen = false
        }
    if (manualWeek && term != null)
        SelectionDialog(
            "校准当前周",
            ScheduleEngine.week(term, today).coerceIn(1, term.weeks),
            (1..term.weeks).map { it to "第 $it 周" },
            { manualWeek = false },
        ) { week ->
            vm.saveTerm(
                term.copy(
                    startDate =
                        ScheduleEngine.weekStart(today, term.firstDayOfWeek)
                            .minusWeeks((week - 1).toLong())
                            .toString()
                )
            )
            manualWeek = false
        }
    if (license)
        AlertDialog(
            onDismissRequest = { license = false },
            title = { Text("开源许可") },
            text = {
                Text(
                    remember {
                        ctx.resources
                            .openRawResource(R.raw.third_party_notices)
                            .bufferedReader()
                            .use { it.readText() }
                    },
                    Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { license = false }) { Text("关闭") } },
        )
}

@Composable
fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val preferences = LocalPreferences.current
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .settingsRow()
            .testTag("toggle-$title")
            .toggleable(
                checked,
                role = Role.Switch,
                onValueChange = { value ->
                    if (preferences.hapticsEnabled)
                        haptic.performHapticFeedback(
                            if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                        )
                    onChange(value)
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun SettingLink(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (LocalSettingsGroup.current) Modifier.settingsRow()
                else Modifier.clip(RoundedCornerShape(14.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            Box(Modifier.size(22.dp)) { icon() }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank())
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (trailing != null) trailing()
        else
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}
