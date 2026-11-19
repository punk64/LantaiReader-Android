package com.lantai.reader.net

import android.webkit.WebResourceResponse
import com.lantai.reader.model.Store
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

class ResourceProxy(
    private val session: LtSession,
    private val onProgress: ((String) -> Unit)? = null
) {
    fun handle(path: String, method: String, headers: Map<String, String>): WebResourceResponse? {
        if (!path.startsWith("/api/")) return null
        val verb = method.uppercase()
        if (verb != "GET" && verb != "HEAD") return null
        if (path.contains("/progression")) {
            onProgress?.invoke(path)
        }
        try {
            if (offlineCache.has(path)) {
                val mime = offlineCache.mimeOf(path).ifEmpty { guessMime(path) }
                val bytes = offlineCache.read(path)
                if (bytes != null) return response(200, mime, bytes)
            }
        } catch (e: Throwable) {
        }
        if (!session.ready()) {
            return response(401, "application/json", "{\"message\":\"offline\"}".toByteArray())
        }
        val accept = headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value ?: "*/*"
        val isImage = accept.contains("image/")
        val payload: SessionResponse = try {
            runBlocking { session.fetch(path, if (isImage) accept else "*/*", if (isImage) 20000 else 8000) }
        } catch (e: Throwable) {
            return response(504, "application/json", "{\"message\":\"network\"}".toByteArray())
        }
        val bookId = bookIdOfPath(path)
        if (payload.status == 200 && bookId.isNotEmpty() && payload.bytes.isNotEmpty()) {
            val mime = payload.contentType.ifEmpty { guessMime(path) }
            val cacheable = isImage || path.contains("/bootstrap") || path.contains("/chapter") ||
                path.contains("/outline") || path.contains("/chapters")
            if (cacheable) {
                try {
                    offlineCache.put(bookId, path, mime, payload.bytes)
                } catch (e: Throwable) {
                }
            }
        }
        return response(payload.status, payload.contentType.ifEmpty { guessMime(path) }, payload.bytes)
    }

    private fun response(status: Int, mime: String, bytes: ByteArray): WebResourceResponse {
        val safeMime = if (mime.isEmpty()) "application/octet-stream" else mime.substringBefore(';')
        val headers = HashMap<String, String>()
        headers["Access-Control-Allow-Origin"] = "*"
        headers["Cache-Control"] = "no-store"
        return WebResourceResponse(
            safeMime,
            "utf-8",
            status,
            if (status in 200..299) "OK" else "",
            headers,
            ByteArrayInputStream(bytes)
        )
    }

    private fun guessMime(path: String): String = when {
        path.contains("/cover/") || path.contains("/thumbnail") || path.contains("/pages/") -> "image/jpeg"
        path.contains(".js") -> "text/javascript"
        path.contains(".css") -> "text/css"
        path.contains(".html") -> "text/html"
        else -> "application/json"
    }
}
