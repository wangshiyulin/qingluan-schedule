package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import com.kyant.backdrop.Backdrop
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.data.AppSettings
import com.qingluan.schedule.model.Snapshot
import java.time.LocalDateTime

/** The real return destination is composed only while the system back gesture is active. */
@Composable
internal fun PredictiveDestination(
    s: Snapshot,
    p: AppSettings,
    vm: AppViewModel,
    tab: Int,
    page: String,
    now: LocalDateTime,
    pager: PagerState,
    backdrop: Backdrop,
) {
    val previewPager =
        rememberPagerState(initialPage = pager.currentPage, pageCount = { pager.pageCount })
    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .clearAndSetSemantics { testTag = "predictive-destination" }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                            it.consume()
                        }
                    }
                },
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ScreenAppBar(
                centered = page.isBlank(),
                title = {
                    Text(
                        page.ifBlank {
                            if (tab == 1) "第 ${pager.currentPage + 1} 周"
                            else listOf("今日", "课表", "设置")[tab]
                        }
                    )
                },
                navigationIcon = {
                    if (page.isNotBlank())
                        IconButton(onClick = {}) { Icon(Icons.Outlined.ArrowBack, null) }
                },
            )
        },
        bottomBar = { if (page.isBlank()) LiquidNavigation(tab, backdrop) {} },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                page == "导入课表" -> ImportTimetableScreen(vm) {}
                page.isNotBlank() || tab == 2 -> SettingsScreen(s, p, vm, page) {}
                tab == 0 -> TodayScreen(s, now, {})
                else -> WeekScreen(s, p, now, previewPager, {}, { _, _ -> }, {})
            }
        }
    }
}
