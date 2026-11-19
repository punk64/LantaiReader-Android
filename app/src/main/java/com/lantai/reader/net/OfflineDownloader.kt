package com.lantai.reader.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lantai.reader.common.J
import com.lantai.reader.common.t
import com.lantai.reader.data.JobRow
import com.lantai.reader.data.OfflineBookRow
import com.lantai.reader.data.offlineDb
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lantai.reader.LtApp
import java.util.regex.Pattern

private val IMG: Pattern = Pattern.compile("(?:src|href)=\"([^\"]+)\"")

private class PullResult(val body: ByteArray, val size: Long)

object OfflineDownloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: LtSession? = null
    private var busy = false
    private val paused = HashSet<String>()
    private val stopped = HashSet<String>()

    var current by mutableStateOf("")
        private set

    var tick by mutableStateOf(0)
        private set

    fun attach(target: LtSession) {
        session = target
    }

    private fun emit() {
        tick += 1
    }

    suspend fun reset() {
        val rows = offlineDb.jobs()
        for (row in rows) {
            if (row.state == "RUNNING") {
                offlineDb.patchJob(row.bookId, "PAUSED", row.total, row.done, row.bytes, t("上次未完成"))
            }
        }
        emit()
    }

    suspend fun enqueue(book: BookItem, profile: String) {
        if (!offlineCache.ready()) return
        val existed = offlineDb.jobs()
        for (row in existed) {
            if (row.bookId == book.id && row.state == "DONE") return
        }
        val job = JobRow(
            bookId = book.id,
            title = book.title,
            profile = profile,
            state = "PENDING",
            total = 0,
            done = 0,
            bytes = 0L,
            pages = book.pages,
            size = book.sizeBytes,
            at = System.currentTimeMillis(),
            msg = ""
        )
        offlineDb.saveJob(job)
        stopped.remove(book.id)
        paused.remove(book.id)
        emit()
        pump()
    }

    fun pump() {
        if (busy) return
        scope.launch { drain() }
    }

    private suspend fun drain() {
        if (session == null) return
        while (true) {
            val rows = offlineDb.jobs()
            val next = rows.firstOrNull { it.state == "PENDING" } ?: return
            busy = true
            current = next.bookId
            offlineDb.patchJob(next.bookId, "RUNNING", next.total, next.done, next.bytes, "")
            emit()
            BackgroundTask.acquire()
            try {
                run(next)
            } catch (e: Throwable) {
                val message = e.message ?: ""
                if (stopped.contains(next.bookId)) {
                    offlineDb.removeJob(next.bookId)
                } else if (paused.contains(next.bookId)) {
                    offlineDb.patchJob(next.bookId, "PAUSED", next.total, next.done, next.bytes, t("已暂停"))
                } else {
                    offlineDb.patchJob(
                        next.bookId, "FAILED", next.total, next.done, next.bytes,
                        if (message.isNotEmpty()) message else t("下载失败")
                    )
                }
            }
            offlineCache.flush()
            busy = false
            current = ""
            BackgroundTask.release()
            emit()
        }
    }

    suspend fun pause(bookId: String) {
        paused.add(bookId)
        if (current != bookId) {
            offlineDb.patchJob(bookId, "PAUSED", 0, 0, 0L, t("已暂停"))
        }
        emit()
    }

    suspend fun resume(bookId: String) {
        paused.remove(bookId)
        stopped.remove(bookId)
        val rows = offlineDb.jobs()
        for (row in rows) {
            if (row.bookId == bookId) {
                offlineDb.saveJob(row.copy(state = "PENDING", at = System.currentTimeMillis(), msg = ""))
                break
            }
        }
        emit()
        pump()
    }

    suspend fun remove(bookId: String) {
        stopped.add(bookId)
        offlineCache.drop(bookId)
        offlineDb.removeJob(bookId)
        offlineDb.removeBook(bookId)
        emit()
    }

    suspend fun pauseAll() {
        val rows = offlineDb.jobs()
        for (row in rows) {
            if (row.state == "PENDING" || row.state == "RUNNING") {
                paused.add(row.bookId)
                if (current != row.bookId) {
                    offlineDb.patchJob(row.bookId, "PAUSED", row.total, row.done, row.bytes, t("已暂停"))
                }
            }
        }
        emit()
    }

    suspend fun clearDone() {
        val rows = offlineDb.jobs()
        for (row in rows) {
            if (row.state == "DONE" || row.state == "FAILED") {
                offlineDb.removeJob(row.bookId)
            }
        }
        emit()
    }

    private fun metered(): Boolean {
        if (!Prefs.bool("wifiOnly", true)) return false
        return try {
            val cm = LtApp.instance.getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                false
            } else {
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        } catch (e: Throwable) {
            false
        }
    }

    private suspend fun run(job: JobRow) {
        if (metered()) {
            paused.add(job.bookId)
            offlineDb.patchJob(job.bookId, "PAUSED", job.total, job.done, job.bytes, t("等待 WiFi"))
            emit()
            return
        }
        if (job.profile == "DIVINA") {
            runComic(job)
            return
        }
        runEpub(job)
    }

    private suspend fun runComic(job: JobRow) {
        val total = if (job.pages > 0) job.pages else 1
        var done = minOf(job.done, total)
        var bytes = job.bytes
        tick(job, total, done, bytes, "")
        var n = done + 1
        while (n <= total) {
            guard(job.bookId)
            val path = "/api/v1/books/${job.bookId}/pages/$n?convert=jpeg"
            bytes += pull(job.bookId, path, "image/jpeg").size
            done = n
            tick(job, total, done, bytes, "")
            n += 1
        }
        complete(job, total, done, bytes)
    }

    private suspend fun runEpub(job: JobRow) {
        val base = "/api/lantai/v1/read/${job.bookId}"
        var bytes = job.bytes
        var done: Int
        var total: Int
        bytes += pullSize(job.bookId, "$base/bootstrap?images=inline&w=0&window=3", "application/json")
        bytes += pullSize(job.bookId, "$base/chapters?outline=true", "application/json")
        bytes += pullSize(job.bookId, "/api/v1/fonts/families", "application/json")
        total = 0
        val outline = offlineCache.read("$base/chapters?outline=true")
        val current = session
        if (outline != null && current != null) {
            total = chapterCount(current.decodeText(outline))
        }
        if (total <= 0) total = if (job.pages > 0) job.pages else 1
        val chapters = total
        total += 3
        done = 3
        tick(job, total, done, bytes, "")
        val seen = HashSet<String>()
        var n = 1
        while (n <= chapters) {
            guard(job.bookId)
            val path = "$base/chapter/$n?images=url&w=0&fmt=orig"
            val pulled = pull(job.bookId, path, "application/json")
            val html = current?.decodeText(pulled.body) ?: ""
            for (src in imageSources(html)) {
                if (seen.contains(src)) continue
                seen.add(src)
                guard(job.bookId)
                bytes += pull(job.bookId, src, "image/jpeg").size
            }
            done += 1
            tick(job, total, done, bytes, "")
            n += 1
        }
        complete(job, total, done, bytes)
    }

    private fun guard(bookId: String) {
        if (paused.contains(bookId)) throw IllegalStateException(t("已暂停"))
        if (stopped.contains(bookId)) throw IllegalStateException(t("已删除"))
    }

    private suspend fun tick(job: JobRow, total: Int, done: Int, bytes: Long, msg: String) {
        offlineCache.flush()
        offlineDb.patchJob(job.bookId, "RUNNING", total, done, bytes, msg)
        emit()
    }

    private suspend fun complete(job: JobRow, total: Int, done: Int, bytes: Long) {
        offlineCache.flush()
        offlineDb.patchJob(job.bookId, "DONE", total, done, bytes, "")
        offlineDb.saveBook(
            OfflineBookRow(
                bookId = job.bookId,
                title = job.title,
                profile = job.profile,
                items = offlineCache.countOf(job.bookId),
                bytes = offlineCache.sizeOf(job.bookId),
                at = System.currentTimeMillis()
            )
        )
    }

    private suspend fun pullSize(bookId: String, path: String, mime: String): Long = pull(bookId, path, mime).size

    private suspend fun pull(bookId: String, path: String, mime: String): PullResult {
        val cached = offlineCache.read(path)
        if (cached != null) return PullResult(cached, 0L)
        val current = session ?: throw IllegalStateException(t("尚未登录"))
        val response = current.fetch(path)
        if (response.status != 200) throw IllegalStateException(t("下载失败 HTTP ${response.status}"))
        val type = if (response.contentType.isNotEmpty()) response.contentType else mime
        val size = offlineCache.put(bookId, path, type, response.bytes)
        return PullResult(response.bytes, size)
    }

    private fun chapterCount(text: String): Int {
        val body = J.parse(text)
        val total = J.num(body, "total")
        if (total > 0) return total
        return J.arr(body, "chapters").length()
    }

    private fun imageSources(html: String): List<String> {
        val out = ArrayList<String>(8)
        val matcher = IMG.matcher(html)
        while (matcher.find()) {
            val raw = matcher.group(1) ?: continue
            if (raw.isEmpty()) continue
            if (raw.startsWith("data:") || raw.startsWith("http") || raw.startsWith("#")) continue
            out.add(if (raw.startsWith("/")) raw else "/$raw")
        }
        return out
    }
}
