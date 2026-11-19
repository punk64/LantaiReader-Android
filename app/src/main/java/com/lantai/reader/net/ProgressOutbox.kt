package com.lantai.reader.net

import com.lantai.reader.data.offlineDb

data class FlushReport(
    val sent: Int = 0,
    val dropped: Int = 0,
    val failed: Int = 0,
    val remaining: Int = 0
)

object ProgressOutbox {
    private var flushing = false

    suspend fun enqueue(method: String, path: String, body: String) {
        try {
            offlineDb.queue(method, path, if (body.length > 4096) body.substring(0, 4096) else body)
        } catch (e: Throwable) {
        }
    }

    suspend fun flush(session: LtSession, onTick: ((Int, Int) -> Unit)? = null): FlushReport {
        var sent = 0
        var dropped = 0
        var failed = 0
        if (!session.ready() || flushing) {
            return FlushReport(remaining = count())
        }
        flushing = true
        try {
            val rows = offlineDb.outbox()
            val total = rows.size
            for ((index, row) in rows.withIndex()) {
                val status = try {
                    session.sendJson(row.method, row.path, row.body)
                } catch (e: Throwable) {
                    failed += 1
                    break
                }
                when {
                    status in 200..399 -> {
                        offlineDb.dropOutbox(row.id)
                        sent += 1
                    }

                    status == 401 || status == 403 || status == 429 || status >= 500 -> {
                        failed += 1
                        break
                    }

                    else -> {
                        offlineDb.dropOutbox(row.id)
                        dropped += 1
                    }
                }
                onTick?.invoke(index + 1, total)
            }
        } catch (e: Throwable) {
            failed += 1
        } finally {
            flushing = false
        }
        return FlushReport(sent, dropped, failed, count())
    }

    suspend fun dropBooks(bookIds: List<String>): Int {
        var dropped = 0
        for (id in bookIds) {
            try {
                dropped += offlineDb.dropOutboxByBook(id)
            } catch (e: Throwable) {
            }
        }
        return dropped
    }

    suspend fun count(): Int = try {
        offlineDb.outboxCount()
    } catch (e: Throwable) {
        0
    }

    suspend fun dropByBook(bookId: String): Int = offlineDb.dropOutboxByBook(bookId)
}
