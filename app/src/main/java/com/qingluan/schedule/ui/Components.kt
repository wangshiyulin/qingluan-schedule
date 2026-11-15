@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qingluan.schedule.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import com.qingluan.schedule.model.*
import java.time.LocalDate

@Composable
fun ScreenAppBar(
    centered: Boolean,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (centered)
        CenterAlignedTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
        )
    else
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
        )
}

@Composable
fun SectionTitle(title: String) {
    Column(Modifier.padding(top = 20.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(title: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp, 36.dp), contentAlignment = Alignment.Center) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun FormScreen(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String = "保存",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        GlassPage(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "关闭") }
                        },
                        actions = { TextButton(onClick = onSave) { Text(saveLabel) } },
                    )
                },
            ) { pad ->
                Column(
                    Modifier.padding(pad)
                        .consumeWindowInsets(pad)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun <T> Choice(
    label: String,
    value: T,
    options: List<Pair<T, String>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ValueRow(
        label,
        options.find { it.first == value }?.second ?: "请选择",
        { open = true },
        modifier,
        compact,
    )
    if (open)
        SelectionDialog(label, value, options, { open = false }) { selected ->
            onChange(selected)
            open = false
        }
}

@Composable
fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val row: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth()
                .settingsRow()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 12.dp),
        ) {
            Text(
                title,
                if (compact) Modifier else Modifier.weight(1f),
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                softWrap = !compact,
                style =
                    if (compact) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (value.isNotBlank())
                Text(
                    value,
                    modifier = if (compact) Modifier.weight(1f) else Modifier,
                    maxLines = if (compact) 1 else Int.MAX_VALUE,
                    overflow =
                        if (compact) androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        else androidx.compose.ui.text.style.TextOverflow.Clip,
                    textAlign =
                        if (compact) androidx.compose.ui.text.style.TextAlign.End
                        else androidx.compose.ui.text.style.TextAlign.Start,
                    style =
                        if (compact)
                            MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                letterSpacing = 0.sp,
                            )
                        else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                Modifier.size(if (compact) 12.dp else 18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (LocalSettingsGroup.current) androidx.compose.foundation.layout.Box(modifier) { row() }
    else GlassPanel(modifier.fillMaxWidth(), radius = 18.dp) { row() }
}

@Composable
fun <T> SelectionDialog(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    onClose: () -> Unit,
    onSelect: (T) -> Unit,
) {
    if (options.isEmpty()) return
    var selected by
        rememberSaveable(title, value.toString()) {
            mutableIntStateOf(options.indexOfFirst { it.first == value }.coerceAtLeast(0))
        }
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val size = with(androidx.compose.ui.platform.LocalDensity.current) { 18.sp.toPx() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    android.widget.NumberPicker(context).apply {
                        minValue = 0
                        maxValue = options.lastIndex
                        displayedValues = options.map { it.second }.toTypedArray()
                        wrapSelectorWheel = false
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setTextColor(color)
                        setTextSize(size)
                        setSelectionDividerHeight(1)
                        setOnValueChangedListener { _, _, next -> selected = next }
                    }
                },
                update = { picker ->
                    picker.value = selected
                    picker.setTextColor(color)
                },
                modifier =
                    Modifier.fillMaxWidth().height(144.dp).testTag("wheel-picker").semantics {
                        stateDescription = options[selected].second
                        progressBarRangeInfo =
                            androidx.compose.ui.semantics.ProgressBarRangeInfo(
                                selected.toFloat(),
                                0f..options.lastIndex.toFloat(),
                                (options.size - 2).coerceAtLeast(0),
                            )
                        setProgress { next ->
                            selected = next.toInt().coerceIn(options.indices)
                            true
                        }
                    },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSelect(options[selected].first) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
}

@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ValueRow(label, date.toString(), { open = true }, modifier)
    if (open) AppDateDialog(date, { open = false }, onChange)
}

@Composable
fun TimeField(label: String, time: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    GlassPanel(modifier.fillMaxWidth().testTag("time-field-$label"), radius = 18.dp) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(min = 76.dp)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(
                label,
                Modifier.fillMaxWidth(),
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clock(time),
                    Modifier.weight(1f),
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (open) AppTimeDialog(time, { open = false }, onChange)
}

@Composable
fun InfoCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    dense: Boolean = false,
) {
    if (LocalSettingsGroup.current) {
        SettingLink(title, subtitle, icon, onClick, trailing)
        return
    }
    val preferences = LocalPreferences.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && preferences.animationsEnabled) .98f else 1f,
            if (preferences.animationsEnabled) spring(dampingRatio = .75f, stiffness = 750f)
            else snap(),
            label = "card-press",
        )
    GlassPanel(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactions, indication = null, onClick = onClick),
        radius = 18.dp,
    ) {
        Row(
            Modifier.padding(if (dense) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 14.dp),
        ) {
            Box(Modifier.size(24.dp)) { icon() }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle.isNotBlank())
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (dense) 2.dp else 4.dp),
                    )
            }
            if (trailing != null) trailing() else Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun readableText(color: Color): Color {
    val light = 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
    return if (light > 0.62) Color(0xFF102A24) else Color.White
}

/** JSON savers keep edit drafts through activity and process recreation. */
@Composable
inline fun <reified T> rememberJsonState(crossinline initial: () -> T): MutableState<T> =
    androidx.compose.runtime.saveable.rememberSaveable(
        saver =
            androidx.compose.runtime.saveable.Saver<MutableState<T>, String>(
                save = {
                    com.qingluan.schedule.data.BackupCodec.json.encodeToString(
                        kotlinx.serialization.serializer<T>(),
                        it.value,
                    )
                },
                restore = {
                    mutableStateOf(
                        com.qingluan.schedule.data.BackupCodec.json.decodeFromString(
                            kotlinx.serialization.serializer<T>(),
                            it,
                        )
                    )
                },
            )
    ) {
        mutableStateOf(initial())
    }

@Composable
fun rememberDate(initial: () -> LocalDate): MutableState<LocalDate> =
    androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver =
            androidx.compose.runtime.saveable.Saver<LocalDate, String>(
                save = { it.toString() },
                restore = { LocalDate.parse(it) },
            )
    ) {
        mutableStateOf(initial())
    }
