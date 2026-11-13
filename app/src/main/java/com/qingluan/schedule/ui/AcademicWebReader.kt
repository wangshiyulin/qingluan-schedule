package com.qingluan.schedule.ui

import android.content.Context
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.qingluan.schedule.data.BackupCodec
import com.qingluan.schedule.transfer.AcademicWebCapture
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*

/** Document-start observation is local and read-only; no addJavascriptInterface is exposed. */
internal class AcademicWebReader(context: Context) {
    private val script =
        context.assets.open("import/capture.js").bufferedReader().use { it.readText() }
    var earlyObservation = false
        private set

    fun attach(view: WebView) {
        earlyObservation =
            runCatching {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*"))
                        true
                    } else false
                }
                .getOrDefault(false)
    }

    fun pageFinished(view: WebView) {
        if (!earlyObservation) view.evaluateJavascript(script, null)
    }

    private suspend fun evaluate(view: WebView, code: String): String =
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(code) { raw ->
                if (continuation.isActive) continuation.resume(raw ?: "null")
            }
        }

    suspend fun capture(view: WebView): String {
        // Also install on restored documents or providers lacking document-start support.
        evaluate(view, script)
        val ready = evaluate(view, "Boolean(window.__qingluanAcademic)")
        require(ready == "true") { "请先打开 HTTPS 完整课表页面" }
        val requestId =
            BackupCodec.json.decodeFromString<String>(
                evaluate(view, "window.__qingluanAcademic.request()")
            )
        delay(450)
        var payload = ""
        repeat(3) {
            val raw = evaluate(view, "JSON.stringify(window.__qingluanAcademic.snapshot())")
            require(raw.length <= BackupCodec.MAX_BYTES * 2) { "页面内容过大，请打开独立课表页面" }
            payload = BackupCodec.json.decodeFromString<String>(raw)
            require(payload.toByteArray().size <= BackupCodec.MAX_BYTES) { "页面内容超过8 MiB" }
            val result = BackupCodec.json.decodeFromString<AcademicWebCapture>(payload)
            require(result.requestId == requestId) { "网页已切换，请重新提取课表" }
            if (!result.incomplete) {
                return if (earlyObservation) payload
                else
                    BackupCodec.json.encodeToString(
                        AcademicWebCapture.serializer(),
                        result.copy(warnings = result.warnings + "仅识别了页面已加载的数据，请核对完整学期"),
                    )
            }
            delay(200)
        }
        return payload
    }
}
