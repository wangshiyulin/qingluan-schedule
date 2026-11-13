package com.qingluan.schedule.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qingluan.schedule.AppViewModel
import com.qingluan.schedule.data.BackupCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private fun Context.activity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

@Composable
fun EducationImportScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val reader = remember { AcademicWebReader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var activeUrl by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    val state = rememberSaveable { Bundle() }
    var frames by remember { mutableStateOf(listOf<String>()) }
    var chooseFrame by remember { mutableStateOf(false) }
    fun open(url: String) {
        val normalized = url.trim().let { if ("://" in it) it else "https://$it" }
        val uri = android.net.Uri.parse(normalized)
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
            vm.message.value = "请输入 HTTPS 教务网址"
            return
        }
        activeUrl = normalized
        browser?.loadUrl(normalized)
    }
    BackHandler(enabled = canBack && !reading) { browser?.goBack() }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                address,
                { address = it },
                label = { Text("教务网址") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { open(address) },
                enabled = address.isNotBlank() && !reading,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("打开")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        browser = this
                        reader.attach(this)
                        layoutParams =
                            android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        setInitialScale(0)
                        settings.userAgentString =
                            settings.userAgentString
                                .replace(Regex("\\([^)]*Android[^)]*\\)"), "(X11; Linux x86_64)")
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
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
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
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    canBack = view.canGoBack()
                                    address = url.orEmpty()
                                    view.evaluateJavascript(FIT_ACADEMIC_VIEWPORT, null)
                                    reader.pageFinished(view)
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (request.isForMainFrame) {
                                        loading = false
                                        vm.message.value = "页面无法打开，请检查网址或使用文件导入"
                                    }
                                }
                            }
                        if (state.isEmpty) loadUrl(activeUrl) else restoreState(state)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onRelease = { view ->
                    view.saveState(state)
                    if (view.context.activity()?.isChangingConfigurations != true) {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        view.clearCache(true)
                    }
                    view.stopLoading()
                    view.removeAllViews()
                    view.destroy()
                    browser = null
                },
            )
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { browser?.goBack() }, enabled = canBack && !reading) {
                Icon(Icons.Outlined.ChevronLeft, "上一页")
            }
            OutlinedButton(
                onClick = {
                    browser?.evaluateJavascript(
                        "JSON.stringify(Array.from(document.querySelectorAll('iframe,frame')).map(x=>x.src).filter(x=>x.startsWith('https://'))) "
                    ) { raw ->
                        frames =
                            runCatching { BackupCodec.json.decodeFromString<String>(raw) }
                                .mapCatching { BackupCodec.json.decodeFromString<List<String>>(it) }
                                .getOrDefault(emptyList())
                        if (frames.isEmpty()) vm.message.value = "当前页面没有可打开的内嵌页面"
                        else chooseFrame = true
                    }
                },
                enabled = browser != null && !reading,
            ) {
                Text("内嵌页面")
            }
            Button(
                onClick = {
                    val view = browser
                    if (view != null)
                        scope.launch {
                            reading = true
                            try {
                                vm.previewWeb(reader.capture(view))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                vm.message.value = e.message?.take(180) ?: "无法读取课表，请重试"
                            } finally {
                                reading = false
                            }
                        }
                },
                enabled = browser != null && !loading && !reading,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (reading) "读取中…" else "提取课表")
            }
        }
    }
    if (chooseFrame)
        SelectionDialog(
            "选择课表页面",
            frames.first(),
            frames.map {
                it to
                    android.net.Uri.parse(it).host.orEmpty() +
                        android.net.Uri.parse(it).path.orEmpty()
            },
            { chooseFrame = false },
        ) {
            chooseFrame = false
            open(it)
        }
}

// Keep desktop teaching grids scrollable/zoomable instead of clipping fixed-width frames.
internal const val FIT_ACADEMIC_VIEWPORT =
    """
(()=>{let m=document.querySelector('meta[name="viewport"]');
if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m)}
m.content='width=1280,user-scalable=yes';})()
"""
