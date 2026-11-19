package com.lantai.reader.net

import android.os.Build
import android.os.Handler
import android.os.Looper
import com.lantai.reader.common.J
import com.lantai.reader.data.ReadingRow
import com.lantai.reader.data.offlineDb
import com.lantai.reader.model.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SessionFlushReport(
    val sent: Int = 0,
    val dropped: Int = 0,
    val failed: Int = 0,
    val remaining: Int = 0
)

private data class FlushOutcome(val confirmed: Int = 0, val dropped: Int = 0, val moved: Boolean = false)

private const val KEY_DEVICE = "deviceId"
private const val CLIENT = "lantai-android/0.1.0"
private const val HEARTBEAT_MS = 20000L
private const val FLUSH_BATCH = 100
private const val FLUSH_ROUNDS = 10
private const val SEAL_GAP_MS = 300000L
private const val STALE_MS = 1800000L

private fun iso(ms: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(ms))
}

private fun missingBook(status: Int): Boolean = status == 404 || status == 410

class ReadingSession {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var session: LtSession? = null
    private var deviceId = ""
    private var deviceName = ""
    private var bookId = ""
    private var clientId = ""
    private var startedAt = ""
    private var startPage = 0
    private var lastPage = 0
    private var activeMs = 0L
    private var segmentAt = 0L
    private var hiddenAt = 0L
    private var online = true
    private var flushing = false
    private var armed = false

    private val tick = object : Runnable {
        override fun run() {
            beat()
            if (armed) handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    fun attach(session: LtSession) {
        this.session = session
    }

    fun init() {
        var id = Prefs.read(KEY_DEVICE)
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString()
            Prefs.write(KEY_DEVICE, id)
        }
        deviceId = id
        val model = Build.MODEL ?: ""
        deviceName = if (model.isNotEmpty()) model else "Android"
    }

    fun active(): Boolean = clientId.isNotEmpty()

    suspend fun start(book: String, page: Int, incognito: Boolean) {
        if (incognito || book.isEmpty() || deviceId.isEmpty()) return
        stop()
        val previous = recover(book)
        val now = System.currentTimeMillis()
        if (previous != null) {
            clientId = previous.clientId
            startedAt = previous.startedAt
            startPage = previous.startPage
            activeMs = previous.durationMs
            lastPage = if (previous.lastPage > page) previous.lastPage else page
        } else {
            clientId = UUID.randomUUID().toString()
            startedAt = iso(now)
            startPage = page
            lastPage = page
            activeMs = 0L
        }
        bookId = book
        segmentAt = now
        hiddenAt = 0L
        online = true
        persist("open")
        beat()
        armTimer()
    }

    fun track(page: Int) {
        if (clientId.isEmpty() || page <= 0) return
        if (page <= lastPage) return
        lastPage = page
        scope.launch { persist("open") }
    }

    fun pause() {
        if (clientId.isEmpty()) return
        accrue()
        segmentAt = 0L
        disarmTimer()
        hiddenAt = System.currentTimeMillis()
        scope.launch { persist("open") }
        beat()
    }

    fun resume() {
        if (clientId.isEmpty()) {
            hiddenAt = 0L
            return
        }
        val now = System.currentTimeMillis()
        val away = if (hiddenAt > 0) now - hiddenAt else 0L
        if (away > SEAL_GAP_MS) {
            val book = bookId
            val page = lastPage
            hiddenAt = 0L
            scope.launch {
                stop()
                start(book, page, false)
            }
            return
        }
        hiddenAt = 0L
        segmentAt = now
        armTimer()
        beat()
    }

    fun setForeground(value: Boolean) {
        if (value) resume() else pause()
    }

    suspend fun stop() {
        if (clientId.isEmpty()) return
        accrue()
        segmentAt = 0L
        disarmTimer()
        val id = clientId
        val book = bookId
        val current = session
        var settled = false
        if (online && current != null && current.ready()) {
            val body = JSONObject().put("bookId", book).put("deviceId", deviceId).toString()
            try {
                val status = current.sendJson("POST", "/api/lantai/v1/reading/stop", body)
                settled = status in 200..299 || missingBook(status)
            } catch (e: Throwable) {
                settled = false
            }
        }
        if (settled) {
            try {
                offlineDb.dropReading(id)
            } catch (e: Throwable) {
            }
        } else {
            persist("pending")
        }
        clientId = ""
        bookId = ""
        startedAt = ""
        startPage = 0
        lastPage = 0
        activeMs = 0L
        online = true
        hiddenAt = 0L
        if (current != null && current.ready()) flush(current, null)
    }

    suspend fun pendingCount(): Int = try {
        offlineDb.pendingReadingCount()
    } catch (e: Throwable) {
        0
    }

    suspend fun flush(session: LtSession, onTick: ((Int, Int) -> Unit)?): SessionFlushReport {
        var sent = 0
        var dropped = 0
        var failed = 0
        if (flushing || !session.ready() || deviceId.isEmpty()) {
            return SessionFlushReport(remaining = pendingCount())
        }
        flushing = true
        try {
            sealStale()
            val total = pendingCount()
            var done = 0
            var rounds = 0
            while (rounds < FLUSH_ROUNDS) {
                rounds++
                val rows = offlineDb.pendingReadings(FLUSH_BATCH)
                if (rows.isEmpty()) break
                val outcome = push(session, rows)
                sent += outcome.confirmed
                dropped += outcome.dropped
                done += outcome.confirmed + outcome.dropped
                onTick?.invoke(done, total)
                if (!outcome.moved) {
                    failed += 1
                    break
                }
            }
        } catch (e: Throwable) {
            failed += 1
        } finally {
            flushing = false
        }
        return SessionFlushReport(sent, dropped, failed, pendingCount())
    }

    private suspend fun push(session: LtSession, rows: List<ReadingRow>): FlushOutcome {
        val sessions = org.json.JSONArray()
        for (row in rows) {
            sessions.put(
                JSONObject()
                    .put("clientSessionId", row.clientId)
                    .put("bookId", row.bookId)
                    .put("startedAt", row.startedAt)
                    .put("endedAt", row.endedAt)
                    .put("durationMs", row.durationMs)
                    .put("startPage", row.startPage)
                    .put("lastPage", row.lastPage)
                    .put("pagesRead", row.pagesRead)
                    .put("client", CLIENT)
            )
        }
        val body = JSONObject()
            .put("device", JSONObject().put("id", deviceId).put("name", deviceName))
            .put("sessions", sessions)
            .toString()
        val response = try {
            session.request("POST", "/api/lantai/v1/reading/sync", body)
        } catch (e: Throwable) {
            return FlushOutcome()
        }
        if (response.status < 200 || response.status >= 300) return FlushOutcome()
        val map = try {
            JSONObject(session.decodeText(response.bytes))
        } catch (e: Throwable) {
            return FlushOutcome()
        }
        val confirmed = J.strList(map, "confirmed")
        var count = 0
        for (id in confirmed) {
            try {
                offlineDb.dropReading(id)
                count++
            } catch (e: Throwable) {
            }
        }
        val rejected = J.objList(map, "rejected")
        var dropped = 0
        for (item in rejected) {
            if (reject(J.str(item, "clientSessionId"), J.str(item, "reason"))) dropped++
        }
        return FlushOutcome(count, dropped, true)
    }

    private suspend fun reject(client: String, reason: String): Boolean {
        if (client.isEmpty()) return false
        return try {
            if (reason == "future_timestamp") {
                false
            } else if (reason == "duration_mismatch") {
                val rows = offlineDb.pendingReadings(FLUSH_BATCH)
                val hit = rows.firstOrNull { it.clientId == client }
                if (hit != null && hit.retry < 1) {
                    offlineDb.bumpReading(client)
                    false
                } else {
                    offlineDb.dropReading(client)
                    true
                }
            } else {
                offlineDb.dropReading(client)
                true
            }
        } catch (e: Throwable) {
            false
        }
    }

    private suspend fun sealStale() {
        val row = try {
            offlineDb.openReading()
        } catch (e: Throwable) {
            null
        } ?: return
        if (clientId.isNotEmpty() && row.clientId == clientId) return
        if (System.currentTimeMillis() - row.at < STALE_MS) return
        sealRow(row, row.at)
    }

    private suspend fun recover(book: String): ReadingRow? {
        val row = try {
            offlineDb.openReading()
        } catch (e: Throwable) {
            null
        } ?: return null
        if (row.bookId == book && row.clientId.isNotEmpty()) return row
        sealRow(row, System.currentTimeMillis())
        return null
    }

    private suspend fun sealRow(row: ReadingRow, endMs: Long) {
        val sealed = row.copy(
            endedAt = iso(endMs),
            pagesRead = if (row.lastPage > row.startPage) row.lastPage - row.startPage else 0,
            state = "pending",
            at = System.currentTimeMillis()
        )
        try {
            offlineDb.saveReading(sealed)
        } catch (e: Throwable) {
        }
    }

    private fun accrue() {
        if (segmentAt <= 0) return
        val now = System.currentTimeMillis()
        val delta = now - segmentAt
        if (delta > 0) activeMs += delta
        segmentAt = now
    }

    private suspend fun persist(state: String) {
        if (clientId.isEmpty()) return
        val pages = if (lastPage > startPage) lastPage - startPage else 0
        val row = ReadingRow(
            clientId = clientId,
            bookId = bookId,
            startedAt = startedAt,
            endedAt = if (state == "open") "" else iso(System.currentTimeMillis()),
            durationMs = activeMs,
            startPage = startPage,
            lastPage = lastPage,
            pagesRead = pages,
            state = state,
            retry = 0,
            at = System.currentTimeMillis()
        )
        try {
            offlineDb.saveReading(row)
        } catch (e: Throwable) {
        }
    }

    private fun beat() {
        if (clientId.isEmpty()) return
        accrue()
        val current = session
        val book = bookId
        val page = lastPage
        val id = clientId
        scope.launch {
            persist("open")
            if (!online || current == null || !current.ready()) {
                online = false
                return@launch
            }
            val body = JSONObject()
                .put("bookId", book)
                .put("page", page)
                .put("deviceId", deviceId)
                .put("deviceName", deviceName)
                .put("client", CLIENT)
                .toString()
            val status = try {
                current.sendJson("POST", "/api/lantai/v1/reading/heartbeat", body)
            } catch (e: Throwable) {
                online = false
                return@launch
            }
            if (missingBook(status)) {
                discard(id)
                return@launch
            }
            online = status in 200..299
        }
    }

    private suspend fun discard(id: String) {
        if (id.isEmpty() || clientId != id) return
        accrue()
        segmentAt = 0L
        disarmTimer()
        clientId = ""
        bookId = ""
        startedAt = ""
        startPage = 0
        lastPage = 0
        activeMs = 0L
        online = false
        hiddenAt = 0L
        try {
            offlineDb.dropReading(id)
        } catch (e: Throwable) {
        }
    }

    private fun armTimer() {
        if (armed) return
        armed = true
        handler.postDelayed(tick, HEARTBEAT_MS)
    }

    private fun disarmTimer() {
        if (!armed) return
        armed = false
        handler.removeCallbacks(tick)
    }
}

val readingTracker: ReadingSession = ReadingSession()
