package com.lantai.reader.net

import com.lantai.reader.data.offlineDb
import com.lantai.reader.local.LocalMetas
import com.lantai.reader.model.Store

data class SweepReport(
    val supported: Boolean = true,
    val checked: Int = 0,
    val books: Int = 0,
    val sessions: Int = 0,
    val progress: Int = 0,
    val offline: Int = 0,
    val offlineIds: List<String> = emptyList(),
    val failed: Boolean = false
)

private const val BATCH = 200
private const val MAX_BOOKS = 600
private const val FALLBACK_BOOKS = 20
private const val MARKER = "/books/"

private fun sweepBookIdOfPath(path: String): String {
    val at = path.indexOf(MARKER)
    if (at < 0) return ""
    val rest = path.substring(at + MARKER.length)
    val slash = rest.indexOf('/')
    val id = if (slash > 0) rest.substring(0, slash) else rest
    val q = id.indexOf('?')
    return if (q >= 0) id.substring(0, q) else id
}

class OrphanSweep {
    private var running = false

    suspend fun run(session: LtSession, api: LantaiApi, purgeOffline: Boolean = false): SweepReport {
        if (running || !session.ready()) return SweepReport(failed = true)
        running = true
        var supported = true
        var checked = 0
        var books = 0
        var sessions = 0
        var progress = 0
        var offline = 0
        val offlineIds = ArrayList<String>()
        var failed = false
        try {
            val ids = ArrayList<String>()
            val seen = HashSet<String>()
            val offlineSet = HashSet<String>()

            for (id in offlineDb.readingBookIds()) add(ids, seen, id)
            for (row in offlineDb.outboxAll()) add(ids, seen, sweepBookIdOfPath(row.path))
            for (row in offlineDb.books()) {
                if (add(ids, seen, row.bookId)) offlineSet.add(row.bookId)
            }
            for (row in offlineDb.jobs()) {
                if (add(ids, seen, row.bookId)) offlineSet.add(row.bookId)
            }
            checked = ids.size
            if (ids.isEmpty()) {
                return SweepReport(checked = 0)
            }
            val target = if (ids.size > MAX_BOOKS) ids.take(MAX_BOOKS) else ids
            val probe = probe(api, target)
            supported = probe.first
            failed = probe.third
            if (failed) {
                return SweepReport(supported = supported, checked = checked, failed = true)
            }
            val missing = probe.second
            books = missing.size
            if (missing.isNotEmpty()) SeriesCache.clear()
            for (id in missing) {
                try {
                    sessions += offlineDb.dropReadingsByBook(id)
                } catch (e: Throwable) {
                }
                try {
                    progress += offlineDb.dropOutboxByBook(id)
                } catch (e: Throwable) {
                }
                if (offlineSet.contains(id)) {
                    offline += 1
                    offlineIds.add(id)
                    if (purgeOffline) {
                        try {
                            offlineCache.drop(id)
                        } catch (e: Throwable) {
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            failed = true
        } finally {
            running = false
        }
        return SweepReport(
            supported = supported,
            checked = checked,
            books = books,
            sessions = sessions,
            progress = progress,
            offline = offline,
            offlineIds = offlineIds,
            failed = failed
        )
    }

    private suspend fun probe(api: LantaiApi, ids: List<String>): Triple<Boolean, List<String>, Boolean> {
        val missing = ArrayList<String>()
        var start = 0
        while (start < ids.size) {
            val chunk = ids.drop(start).take(BATCH)
            try {
                val result = api.booksMissing(chunk)
                for (id in result) {
                    if (chunk.contains(id) && !missing.contains(id)) missing.add(id)
                }
            } catch (e: Throwable) {
                val status = if (e is ApiError) e.status else 0
                if (status != 404 && status != 405) {
                    return Triple(true, emptyList(), true)
                }
                return Triple(false, probeOneByOne(api, ids), false)
            }
            start += BATCH
        }
        return Triple(true, missing, false)
    }

    private suspend fun probeOneByOne(api: LantaiApi, ids: List<String>): List<String> {
        val missing = ArrayList<String>()
        val limit = if (ids.size > FALLBACK_BOOKS) FALLBACK_BOOKS else ids.size
        for (index in 0 until limit) {
            try {
                api.book(ids[index])
            } catch (e: Throwable) {
                val status = if (e is ApiError) e.status else 0
                if (status == 404 || status == 410) missing.add(ids[index])
            }
        }
        return missing
    }

    private fun add(ids: MutableList<String>, seen: MutableSet<String>, bookId: String): Boolean {
        if (bookId.isEmpty() || bookId.startsWith("local:")) return false
        if (seen.contains(bookId)) return false
        seen.add(bookId)
        ids.add(bookId)
        return true
    }
}

val orphanSweep: OrphanSweep = OrphanSweep()
