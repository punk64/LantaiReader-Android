package com.lantai.reader.net

import android.util.Base64
import com.lantai.reader.common.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

data class SessionResponse(val status: Int, val contentType: String, val bytes: ByteArray)

class LtSession {
    private var origin: String = ""
    private var cookie: String = ""

    fun ready(): Boolean = origin.isNotEmpty() && cookie.isNotEmpty()

    fun serverOrigin(): String = origin

    fun cookieHeader(): String = cookie

    fun reset() {
        origin = ""
        cookie = ""
    }

    fun restore(o: String, c: String) {
        origin = o
        cookie = c
    }

    fun setOrigin(o: String) {
        origin = o
    }

    fun normalizeServer(input: String): String {
        var value = input.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length - 1)
        }
        return value
    }

    suspend fun login(server: String, account: String, password: String): String {
        val base = normalizeServer(server)
        if (base.isEmpty()) throw IllegalStateException(t("服务器地址不能为空"))
        if (account.isEmpty()) throw IllegalStateException(t("用户名不能为空"))
        val token = Base64.encodeToString("$account:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val res = send(
            base,
            "GET",
            SESSION_PATH,
            mutableMapOf("Authorization" to "Basic $token", "Accept" to "application/json"),
            null,
            20000
        )
        if (res.status != 200) throw IllegalStateException(t("登录失败 HTTP ${res.status}"))
        val ck = cookieFromSetCookie(res.setCookie)
        if (ck.isEmpty()) throw IllegalStateException(t("登录成功但未返回会话 Cookie"))
        origin = base
        cookie = ck
        return decodeText(res.bytes)
    }

    suspend fun fetch(path: String, accept: String = "*/*", readTimeout: Int = 20000): SessionResponse {
        if (!ready()) throw IllegalStateException(t("尚未登录"))
        val r = send(origin, "GET", path, mutableMapOf("Cookie" to cookie, "Accept" to accept), null, readTimeout)
        return SessionResponse(r.status, r.contentType, r.bytes)
    }

    suspend fun fetchText(path: String, accept: String = "application/json"): String {
        return decodeText(fetch(path, accept).bytes)
    }

    suspend fun request(method: String, path: String, body: String?): SessionResponse {
        if (!ready()) throw IllegalStateException(t("尚未登录"))
        val headers = mutableMapOf("Cookie" to cookie, "Accept" to "application/json")
        val r = send(origin, method.uppercase(), path, headers, body, 20000)
        return SessionResponse(r.status, r.contentType, r.bytes)
    }

    suspend fun verbs(method: String, path: String, body: String?): SessionResponse {
        return request(method, path, body)
    }

    suspend fun postJson(path: String, body: String): SessionResponse {
        return request("POST", path, body)
    }

    suspend fun sendJson(method: String, path: String, body: String?): Int {
        if (!ready()) throw IllegalStateException(t("尚未登录"))
        val target = if (path.startsWith("/")) path else "/$path"
        val payload = if (body == null || body.isEmpty() || body == "null") null else body
        val headers = mutableMapOf("Cookie" to cookie, "Accept" to "application/json")
        val r = send(origin, method.uppercase(), target, headers, payload, 20000)
        return r.status
    }

    fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private data class Raw(val status: Int, val setCookie: String, val bytes: ByteArray, val contentType: String)

    private suspend fun send(
        base: String,
        method: String,
        path: String,
        headers: MutableMap<String, String>,
        body: String?,
        readTimeout: Int
    ): Raw {
        val target = if (path.startsWith("http")) path else base + path
        try {
            return sendOnce(target, method, headers, body, readTimeout)
        } catch (e: UnknownHostException) {
            val alt = resolveFallback(base)
            if (alt != null) {
                return sendOnce(alt + path, method, headers, body, readTimeout)
            }
            throw IllegalStateException(t("域名无法解析"), e)
        } catch (e: java.net.ConnectException) {
            val alt = resolveFallback(base)
            if (alt != null) {
                return sendOnce(alt + path, method, headers, body, readTimeout)
            }
            throw IllegalStateException(t("连接被拒绝"), e)
        }
    }

    private suspend fun sendOnce(
        url: String,
        method: String,
        headers: MutableMap<String, String>,
        body: String?,
        readTimeout: Int
    ): Raw = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = readTimeout
            conn.instanceFollowRedirects = true
            conn.useCaches = false
            for ((k, v) in headers) {
                conn.setRequestProperty(k, v)
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val bytes = stream?.use { readAll(it) } ?: ByteArray(0)
            val setCookie = conn.headerFields
                ?.entries
                ?.filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                ?.flatMap { it.value ?: emptyList() }
                ?.joinToString("\n") ?: ""
            Raw(status, setCookie, bytes, conn.contentType ?: "")
        } catch (e: UnknownHostException) {
            throw e
        } catch (e: java.net.ConnectException) {
            throw e
        } catch (e: Throwable) {
            throw IllegalStateException(e.message ?: t("网络请求失败"), e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun readAll(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream(16384)
        val buf = ByteArray(16384)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun resolveFallback(base: String): String? {
        if (base.startsWith("https://")) return null
        return try {
            val u = URL(base)
            val ip = InetAddress.getAllByName(u.host).firstOrNull()?.hostAddress ?: return null
            val port = if (u.port > 0) u.port else 80
            val host = if (ip.contains(':')) "[$ip]" else ip
            "http://$host:$port"
        } catch (e: Throwable) {
            null
        }
    }

    private fun cookieFromSetCookie(raw: String): String {
        if (raw.isEmpty()) return ""
        val parts = ArrayList<String>()
        for (line in raw.split("\n")) {
            val text = line.trim()
            if (text.isEmpty()) continue
            val head = text.split(";")[0].trim()
            if (head.isEmpty()) continue
            val eq = head.indexOf('=')
            if (eq <= 0 || eq == head.length - 1) continue
            val key = head.substring(0, eq).lowercase()
            if (key == "path" || key == "expires" || key == "domain" || key == "max-age") continue
            parts.add(head)
        }
        return parts.joinToString("; ")
    }

    companion object {
        private const val SESSION_PATH = "/api/v2/users/me?remember-me=true"
    }
}
