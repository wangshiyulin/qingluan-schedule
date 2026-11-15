package com.qingluan.schedule.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qingluan.schedule.AppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private fun Context.activity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

@Composable
fun EducationImportScreen(vm: AppViewModel, initialUrl: String = "") {
    val background = MaterialTheme.colorScheme.surface
    val backgroundArgb = background.toArgb()
    val context = LocalContext.current
    val reader = remember { AcademicWebReader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    var rendererGone by remember { mutableStateOf(false) }
    var webGeneration by remember { mutableIntStateOf(0) }
    var address by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var activeUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(initialUrl.isNotBlank()) }
    var pageReady by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var extractMode by rememberSaveable { mutableStateOf("auto") }
    var modeDialog by rememberSaveable { mutableStateOf(false) }
    val modes = listOf("auto" to "自动识别", "table" to "网页表格", "data" to "页面数据")
    var canBack by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    val state = rememberSaveable(initialUrl) { Bundle() }
    fun open(url: String) {
        val normalized = com.qingluan.schedule.transfer.SchoolCatalog.normalizeAddress(url)
        if (normalized == null) {
            vm.message.value = "请输入 HTTPS 教务网址"
            return
        }
        activeUrl = normalized
        browser?.loadUrl(normalized)
    }
    BackHandler(enabled = canBack && !reading) { browser?.goBack() }
    Column(Modifier.fillMaxSize().background(background).imePadding().navigationBarsPadding()) {
        AcademicAddressBar(address, { address = it }, { open(address) }, !reading)

        if (activeUrl.isBlank()) {
            Column(
                Modifier.weight(1f).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("登录教务系统并打开完整周课表后，点击提取。")
                Text(
                    "无法读取的课表可先导出 Excel / HTML，再从导入课表页面打开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                if (!rendererGone)
                    key(webGeneration) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    browser = this
                                    setBackgroundColor(backgroundArgb)
                                    reader.attach(this)
                                    layoutParams =
                                        android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                    setInitialScale(0)
                                    settings.userAgentString =
                                        settings.userAgentString
                                            .replace(
                                                Regex("\\([^)]*Android[^)]*\\)"),
                                                "(X11; Linux x86_64)",
                                            )
                                            .replace("; wv", "")
                                            .replace(" Version/4.0", "")
                                            .replace(" Mobile", "")
                                    settings.textZoom = 100
                                    settings.setSupportZoom(true)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.mixedContentMode =
                                        WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    CookieManager.getInstance()
                                        .setAcceptThirdPartyCookies(this, true)
                                    webChromeClient = WebChromeClient()
                                    webViewClient =
                                        object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView,
                                                request: WebResourceRequest,
                                            ) = request.url.scheme !in listOf("https", "about")

                                            override fun onPageStarted(
                                                view: WebView,
                                                url: String?,
                                                favicon: Bitmap?,
                                            ) {
                                                loading = true
                                                pageReady = false
                                                pageError = null
                                            }

                                            override fun onPageCommitVisible(
                                                view: WebView,
                                                url: String?,
                                            ) {
                                                pageReady = true
                                                loading = false
                                            }

                                            override fun onPageFinished(
                                                view: WebView,
                                                url: String?,
                                            ) {
                                                loading = false
                                                canBack = view.canGoBack()
                                                address = url.orEmpty()
                                                view.evaluateJavascript(FIT_ACADEMIC_VIEWPORT, null)
                                                reader.pageFinished(view)
                                                view.postVisualStateCallback(
                                                    0,
                                                    object : WebView.VisualStateCallback() {
                                                        override fun onComplete(requestId: Long) {
                                                            if (pageError == null) pageReady = true
                                                        }
                                                    },
                                                )
                                            }

                                            override fun onRenderProcessGone(
                                                view: WebView,
                                                detail: RenderProcessGoneDetail,
                                            ): Boolean {
                                                readJob?.cancel()
                                                loading = false
                                                pageReady = false
                                                canBack = false
                                                rendererGone = true
                                                pageError = "网页进程已退出，请重试"
                                                browser = null
                                                state.clear()
                                                (view.parent as? android.view.ViewGroup)
                                                    ?.removeView(view)
                                                view.destroy()
                                                return true
                                            }

                                            override fun onReceivedError(
                                                view: WebView,
                                                request: WebResourceRequest,
                                                error: WebResourceError,
                                            ) {
                                                if (request.isForMainFrame) {
                                                    loading = false
                                                    pageReady = false
                                                    pageError = "页面无法打开，请检查网址或重试"
                                                }
                                            }
                                        }
                                    if (state.isEmpty) loadUrl(activeUrl) else restoreState(state)
                                }
                            },
                            modifier = Modifier.fillMaxSize().testTag("academic-webview"),
                            onRelease = { view ->
                                if (!rendererGone) view.saveState(state)
                                if (view.context.activity()?.isChangingConfigurations != true) {
                                    CookieManager.getInstance().removeAllCookies(null)
                                    CookieManager.getInstance().flush()
                                    if (!rendererGone) view.clearCache(true)
                                }
                                if (!rendererGone) {
                                    view.stopLoading()
                                    view.removeAllViews()
                                    view.destroy()
                                }
                                if (browser === view) browser = null
                            },
                        )
                    }
                if (!pageReady)
                    Surface(
                        Modifier.fillMaxSize().testTag("education-loading"),
                        color = background,
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (pageError == null) CircularProgressIndicator()
                            else {
                                Text(pageError!!)
                                TextButton(
                                    onClick = {
                                        pageError = null
                                        loading = true
                                        if (rendererGone) {
                                            webGeneration++
                                            rendererGone = false
                                        } else browser?.reload()
                                    }
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                    }
            }
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(background).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { browser?.goBack() }, enabled = canBack && !reading) {
                Icon(Icons.Outlined.ChevronLeft, "上一页")
            }
            TextButton(
                onClick = { modeDialog = true },
                enabled = !reading,
                modifier = Modifier.fillMaxHeight().heightIn(min = 48.dp).testTag("academic-mode"),
            ) {
                Text(modes.first { it.first == extractMode }.second)
            }
            Button(
                onClick = {
                    val view = browser
                    if (view != null)
                        readJob = scope.launch {
                            reading = true
                            try {
                                vm.previewWeb(
                                    withTimeout(12000) { reader.capture(view) },
                                    extractMode,
                                )
                            } catch (_: TimeoutCancellationException) {
                                vm.message.value = "读取超时，请重试或改用文件导入"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                vm.message.value = e.message?.take(180) ?: "无法读取课表，请重试"
                            } finally {
                                reading = false
                            }
                        }
                },
                enabled = browser != null && pageReady && !loading && !reading,
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 48.dp)
                        .testTag("academic-extract"),
            ) {
                Text(if (reading) "读取中…" else "提取课表")
            }
        }
    }
    if (modeDialog)
        SelectionDialog("提取方式", extractMode, modes, { modeDialog = false }) {
            extractMode = it
            modeDialog = false
        }
}

// Keep desktop teaching grids scrollable/zoomable instead of clipping fixed-width frames.
internal const val FIT_ACADEMIC_VIEWPORT =
    """
(()=>{let m=document.querySelector('meta[name="viewport"]');
if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m)}
m.content='width=1280,user-scalable=yes';})()
"""
