package com.lantai.reader.reader

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.Store
import com.lantai.reader.net.NativeBridge
import com.lantai.reader.net.ResourceProxy
import com.lantai.reader.net.TtsService
import java.net.URLEncoder

const val VIRTUAL_ORIGIN = "https://appassets.androidplatform.net"
const val ENTRY_FILE = "ebook.html"

class EbookAssetHandler(private val ctx: Context) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val name = path.trimStart('/')
        if (name.isEmpty() || name.contains("..")) return null
        return try {
            val stream = ctx.assets.open("ebook/$name")
            val mime = when {
                name.endsWith(".html") -> "text/html"
                name.endsWith(".css") -> "text/css"
                name.endsWith(".js") -> "text/javascript"
                else -> "application/octet-stream"
            }
            WebResourceResponse(mime, if (mime.startsWith("text/")) "utf-8" else null, stream)
        } catch (e: Throwable) {
            null
        }
    }
}

class ReaderWebView(ctx: Context) : WebView(ctx) {
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean =
        if (ReaderHost.visible) super.dispatchTouchEvent(ev) else false

    override fun onTouchEvent(ev: MotionEvent?): Boolean =
        if (ReaderHost.visible) super.onTouchEvent(ev) else false
}

object ReaderHost {
    var web: WebView? = null
    var loadedBookId: String = ""
    var wantedId: String = ""
    var visible by mutableStateOf(false)
    var safeTop: Int = 0
    var safeBottom: Int = 0
    var busy by mutableStateOf(false)

    fun open(bookId: String) {
        wantedId = bookId
        val view = web ?: return
        attach(view)
    }

    fun attach(view: WebView) {
        val id = wantedId
        if (id.isEmpty()) return
        if (loadedBookId == id) return
        loadedBookId = id
        val token = try {
            URLEncoder.encode(id, "UTF-8")
        } catch (e: Throwable) {
            id
        }
        val url = "$VIRTUAL_ORIGIN/$ENTRY_FILE?book=$token&st=$safeTop&sb=$safeBottom&r=${System.currentTimeMillis()}"
        try {
            view.post {
                try {
                    view.loadUrl(url)
                } catch (e: Throwable) {
                }
            }
        } catch (e: Throwable) {
        }
    }

    fun reload() {
        if (wantedId.isEmpty()) return
        loadedBookId = ""
        val view = web ?: return
        attach(view)
    }

    fun push(json: String) {
        val view = web ?: return
        if (loadedBookId.isEmpty()) return
        try {
            val escaped = org.json.JSONObject.quote(json)
            view.post {
                try {
                    view.evaluateJavascript(
                        "(function(){var e=window.EbookReader;if(e&&e.ttsSync){e.ttsSync($escaped);}})()",
                        null
                    )
                } catch (e: Throwable) {
                }
            }
        } catch (e: Throwable) {
        }
    }

    fun exec(js: String) {
        val view = web ?: return
        try {
            view.post {
                try {
                    view.evaluateJavascript(js, null)
                } catch (e: Throwable) {
                }
            }
        } catch (e: Throwable) {
        }
    }

    fun noteProgress(path: String) {
        if (Store.readerIncognito || !path.contains("/read-progress")) return
        try {
            val book = param(path, "book")
            if (book.isEmpty()) return
            val tp = param(path, "tp").toDoubleOrNull() ?: return
            if (tp <= 0) return
            val id = try {
                java.net.URLDecoder.decode(book, "UTF-8")
            } catch (e: Throwable) {
                book
            }
            val selected: BookItem? = Store.selected
            val hit = selected != null && selected.id == id
            val pages = if (hit) selected?.pages ?: 0 else 0
            if (pages <= 0) return
            val done = if (hit) selected?.readCompleted ?: false else false
            val page = Math.max(1, Math.min(pages, Math.round(tp * pages).toInt()))
            ProgressMarks.put(id, page, pages, done)
        } catch (e: Throwable) {
        }
    }

    private fun param(path: String, key: String): String {
        val at = path.indexOf("$key=")
        if (at < 0) return ""
        var rest = path.substring(at + key.length + 1)
        val amp = rest.indexOf('&')
        if (amp >= 0) rest = rest.substring(0, amp)
        return rest
    }

    fun noteUi(json: String) {
        try {
            val open = json.contains("\"open\":true")
            val idle = json.contains("\"idle\":true")
            val panel = json.contains("\"panel\":\"") && !json.contains("\"panel\":\"\"")
            busy = open && (
                panel || json.contains("\"sel\":true") || json.contains("\"lb\":true") ||
                    json.contains("\"ask\":true") || json.contains("\"guide\":true") || !idle
                )
        } catch (e: Throwable) {
        }
    }

    fun clear() {
        web = null
        loadedBookId = ""
        wantedId = ""
        visible = false
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReadingWebHost() {
    val shown = ReaderHost.visible

    Box(
        Modifier
            .fillMaxSize()
            .then(if (shown) Modifier else Modifier.offset(x = 10000.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                ReaderWebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0xFF121313.toInt())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.textZoom = 100
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    WebView.setWebContentsDebuggingEnabled(true)
                    visibility = View.INVISIBLE

                    val loader = WebViewAssetLoader.Builder()
                        .setDomain("appassets.androidplatform.net")
                        .addPathHandler("/", EbookAssetHandler(c.applicationContext))
                        .build()
                    val proxy = ResourceProxy(Store.session) { path -> ReaderHost.noteProgress(path) }
                    val bridge = NativeBridge(
                        onReport = { json -> ReaderHost.noteUi(json) },
                        onProgress = { path -> ReaderHost.noteProgress(path) },
                        onExit = { Store.readerExitHook?.invoke() },
                        onUi = { json -> ReaderHost.noteUi(json) }
                    )
                    addJavascriptInterface(bridge, "lantaiBridge")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            return try {
                                val path = request.url.path ?: return null
                                val headers = request.requestHeaders ?: emptyMap()
                                proxy.handle(path, request.method, headers)
                                    ?: loader.shouldInterceptRequest(request.url)
                            } catch (e: Throwable) {
                                null
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                        }
                    }
                    TtsService.notify = { json -> ReaderHost.push(json) }
                    ReaderHost.web = this
                    ReaderHost.attach(this)
                }
            },
            update = { view ->
                view.visibility = if (shown) View.VISIBLE else View.INVISIBLE
                if (shown) ReaderHost.attach(view)
            }
        )
    }

    LaunchedEffect(Unit) {
        ReaderHost.safeTop = 0
        ReaderHost.safeBottom = 0
    }
}
