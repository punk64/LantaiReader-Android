package com.lantai.reader.net

import com.lantai.reader.common.J
import com.lantai.reader.model.PrefKeys
import com.lantai.reader.model.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateResult(
    val state: String = "fail",
    val version: String = "",
    val notes: String = "",
    val url: String = "",
    val message: String = ""
)

object UpdateChecker {
    const val fallback: String = "punk64/LantaiReader-Android"

    fun source(): String {
        val saved = Prefs.read(PrefKeys.repo).trim()
        return if (saved.isNotEmpty()) saved else fallback
    }

    fun saveRepo(text: String) {
        Prefs.write(PrefKeys.repo, text.trim())
    }

    private fun host(source: String): String {
        val mark = source.indexOf("://")
        if (mark < 0) return ""
        val rest = source.substring(mark + 3)
        val slash = rest.indexOf('/')
        return if (slash < 0) rest else rest.substring(0, slash)
    }

    private fun segments(source: String): List<String> {
        var body = source.trim()
        val mark = body.indexOf("://")
        if (mark >= 0) {
            val rest = body.substring(mark + 3)
            val slash = rest.indexOf('/')
            body = if (slash < 0) "" else rest.substring(slash + 1)
        }
        if (body.endsWith(".git")) body = body.substring(0, body.length - 4)
        return body.split("/").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun endpoint(): String {
        val source = source()
        val parts = segments(source)
        if (parts.size < 2) return ""
        val owner = parts[parts.size - 2]
        val name = parts[parts.size - 1]
        val host = host(source)
        if (host.contains("github")) return "https://api.github.com/repos/$owner/$name/releases/latest"
        if (host.contains("gitee")) return "https://gitee.com/api/v5/repos/$owner/$name/releases/latest"
        if (host.isNotEmpty()) {
            val mark = source.indexOf("://")
            val scheme = source.substring(0, mark)
            return "$scheme://$host/api/v1/repos/$owner/$name/releases/latest"
        }
        return "https://api.github.com/repos/$owner/$name/releases/latest"
    }

    private fun numeric(version: String): List<Int> {
        var body = version.trim()
        while (body.isNotEmpty() && (body[0] < '0' || body[0] > '9')) body = body.substring(1)
        return body.split(".").map { it.toIntOrNull() ?: 0 }
    }

    fun newer(local: String, remote: String): Boolean {
        val a = numeric(local)
        val b = numeric(remote)
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (right > left) return true
            if (right < left) return false
        }
        return false
    }

    suspend fun check(local: String): UpdateResult = withContext(Dispatchers.IO) {
        val url = endpoint()
        if (url.isEmpty()) return@withContext UpdateResult(message = "INVALID_SOURCE")
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "LantaiReader")
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return@withContext UpdateResult(message = "HTTP $code")
            }
            val text = BufferedReader(conn.inputStream.reader()).use { it.readText() }
            conn.disconnect()
            val body = J.parse(text)
            val tag = J.str(body, "tag_name")
            if (tag.isEmpty()) return@withContext UpdateResult(message = "NO_RELEASE")
            UpdateResult(
                state = if (newer(local, tag)) "new" else "latest",
                version = tag,
                notes = J.str(body, "body"),
                url = J.str(body, "html_url")
            )
        } catch (e: Throwable) {
            UpdateResult(message = e.message ?: "ERROR")
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Throwable) {
            }
        }
    }
}
