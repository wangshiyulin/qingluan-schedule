@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qingluan.schedule.ui

import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import java.time.*

@Composable
private fun DialogGlass() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val manager = view.context.getSystemService(WindowManager::class.java)
        if (runCatching { manager?.isCrossWindowBlurEnabled == true }.getOrDefault(false))
            runCatching {
                window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window?.attributes = window?.attributes?.apply { blurBehindRadius = 24 }
                window?.setBackgroundBlurRadius(24)
            }
                .onFailure { window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND) }
        onDispose {}
    }
}

@Composable
fun AppDateDialog(date: LocalDate, dismiss: () -> Unit, select: (LocalDate) -> Unit) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val windowWidth = with(density) { window.width.toDp() }
    val windowHeight = with(density) { window.height.toDp() }
    val compact = windowHeight < 480.dp || density.fontScale > 1.3f
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis =
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker,
            yearRange = 2000..2100,
        )
    val width = (minOf(windowWidth - 32.dp, 360.dp) - 48.dp).coerceAtLeast(1.dp)
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val maxHeight = (windowHeight - 48.dp).coerceAtLeast(160.dp)
        val scale = width.value / 360f
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * scale, density.fontScale)
        ) {
            DialogGlass()
            Surface(
                Modifier.width(360.dp).heightIn(max = maxHeight / scale).testTag("app-date-dialog"),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DatePicker(
                        state,
                        title = null,
                        headline = null,
                        showModeToggle = compact,
                        colors =
                            DatePickerDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = dismiss) { Text("取消") }
                        TextButton(
                            enabled = state.selectedDateMillis != null,
                            onClick = {
                                state.selectedDateMillis?.let {
                                    select(
                                        Instant.ofEpochMilli(it)
                                            .atZone(ZoneOffset.UTC)
                                            .toLocalDate()
                                    )
                                }
                                dismiss()
                            },
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppTimeDialog(time: Int, dismiss: () -> Unit, select: (Int) -> Unit) {
    val state = rememberTimePickerState(time / 60, time % 60, true)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("时间") },
        text = {
            Box(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                TimeInput(state)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    select(state.hour * 60 + state.minute)
                    dismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
