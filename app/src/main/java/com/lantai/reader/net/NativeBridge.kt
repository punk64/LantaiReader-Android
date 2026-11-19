package com.lantai.reader.net

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.lantai.reader.model.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NativeBridge(
    private val onReport: (String) -> Unit,
    private val onProgress: (String) -> Unit,
    private val onExit: () -> Unit,
    private val onUi: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun report(json: String) {
        main.post {
            try {
                onReport(json)
                onUi(json)
            } catch (e: Throwable) {
            }
        }
    }

    @JavascriptInterface
    fun post(method: String, path: String, body: String) {
        val payload = if (body.isEmpty() || body == "null") null else body
        scope.launch {
            try {
                Store.session.verbs(method, path, payload)
            } catch (e: Throwable) {
            }
            onProgress(path)
        }
    }

    @JavascriptInterface
    fun exit() {
        main.post {
            try {
                onExit()
            } catch (e: Throwable) {
            }
        }
    }

    @JavascriptInterface
    fun ttsStart(json: String) {
        TtsService.start(json)
    }

    @JavascriptInterface
    fun ttsSeek(idx: Int) {
        TtsService.seek(idx)
    }

    @JavascriptInterface
    fun ttsPause() {
        TtsService.pause()
    }

    @JavascriptInterface
    fun ttsResume() {
        TtsService.resume()
    }

    @JavascriptInterface
    fun ttsStop() {
        TtsService.stop()
    }

    @JavascriptInterface
    fun ttsOnce(text: String) {
        TtsService.once(text)
    }

    @JavascriptInterface
    fun ttsVoices(): String = TtsService.voicesJson()

    @JavascriptInterface
    fun ttsTone(n: Int) {
        TtsService.setTone(n)
    }

    @JavascriptInterface
    fun ttsToneNow(): String = TtsService.toneNow()

    @JavascriptInterface
    fun ttsLoadVoices() {
        TtsService.loadVoices()
    }
}
