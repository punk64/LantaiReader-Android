package com.lantai.reader.net

import com.lantai.reader.local.CacheQuota
import com.lantai.reader.local.ComicArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CachePurge {
    suspend fun measure(): Long = CacheQuota.measure()

    suspend fun purge(): Long = withContext(Dispatchers.IO) {
        val before = CacheQuota.measure()
        try {
            ComicArchive.clearAll()
        } catch (e: Throwable) {
        }
        for (dir in CacheQuota.paths()) {
            wipe(dir)
        }
        val after = CacheQuota.measure()
        if (before > after) before - after else before
    }

    private fun wipe(dir: String) {
        if (dir.isEmpty()) return
        try {
            val file = File(dir)
            if (file.exists()) {
                file.deleteRecursively()
            }
        } catch (e: Throwable) {
        }
    }
}
