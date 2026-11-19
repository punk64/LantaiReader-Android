package com.lantai.reader.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lantai.reader.common.t
import com.lantai.reader.model.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CoverCache {
    private const val MAX_BYTES = 32L * 1024L * 1024L

    private val map = object : LinkedHashMap<String, Bitmap>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = bytes > MAX_BYTES
    }
    private val inflight = HashMap<String, CompletableDeferred<Bitmap?>>()
    private var bytes = 0L
    private var fullKey = ""
    private var fullToken = 0

    fun get(key: String): Bitmap? = map[key]

    fun cached(id: String): Bitmap? = map[id]

    fun cachedSeries(id: String): Bitmap? = map["series:$id"]

    fun contains(key: String): Boolean = map.containsKey(key)

    fun put(key: String, bmp: Bitmap) {
        val prev = map.put(key, bmp)
        if (prev != null) bytes -= prev.byteCount
        bytes += bmp.byteCount
        trim()
    }

    suspend fun load(id: String, width: Int, series: Boolean = false): Bitmap {
        val key = if (series) "series:$id" else id
        map[key]?.let { return it }
        val pair = synchronized(inflight) {
            val hit = inflight[key]
            if (hit != null) hit to false
            else CompletableDeferred<Bitmap?>().also { inflight[key] = it } to true
        }
        val task = pair.first
        if (!pair.second) return task.await() ?: throw IllegalStateException(t("封面为空"))
        try {
            val bmp = decode(id, width, series)
            if (bmp != null) put(key, bmp)
            task.complete(bmp)
            return bmp ?: throw IllegalStateException(t("封面为空"))
        } catch (e: Exception) {
            task.complete(null)
            throw e
        } finally {
            synchronized(inflight) { inflight.remove(key) }
        }
    }

    suspend fun own(id: String, width: Int): Bitmap {
        map[id]?.let { return it.copy(Bitmap.Config.ARGB_8888, true) }
        return decode(id, width, false) ?: throw IllegalStateException(t("封面为空"))
    }

    suspend fun loadFull(id: String): Bitmap {
        val key = "full:$id"
        fullToken++
        if (fullKey.isNotEmpty() && fullKey != key) forget(fullKey)
        fullKey = key
        map[key]?.let { return it }
        val bmp = decode(id, 0, false) ?: throw IllegalStateException(t("封面为空"))
        put(key, bmp)
        return bmp
    }

    fun releaseFull(id: String) {
        val key = "full:$id"
        val token = ++fullToken
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (token != fullToken) return@postDelayed
            forget(key)
            if (fullKey == key) fullKey = ""
        }, 800)
    }

    fun forget(id: String) {
        map.remove(id)?.let { bytes -= it.byteCount }
    }

    fun clear() {
        map.clear()
        bytes = 0L
    }

    private suspend fun decode(id: String, width: Int, series: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val raw: ByteArray? = if (id.startsWith("local:")) {
            val path = Store.localMetas.firstOrNull { it.id == id }?.cover ?: ""
            if (path.isEmpty()) null else File(path).takeIf { it.exists() }?.readBytes()
        } else if (series) {
            Store.api.seriesCover(id)
        } else {
            Store.api.cover(id, width)
        }
        if (raw == null || raw.isEmpty()) null else decodeBytes(raw, width)
    }

    private fun decodeBytes(raw: ByteArray, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        if (width > 0 && bounds.outWidth > 0) {
            while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        }
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
    }

    private fun trim() {
        val it = map.entries.iterator()
        while (bytes > MAX_BYTES && it.hasNext()) {
            val e = it.next()
            bytes -= e.value.byteCount
            it.remove()
        }
    }
}
