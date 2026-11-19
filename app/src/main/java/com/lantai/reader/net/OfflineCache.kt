package com.lantai.reader.net

import android.content.Context
import com.lantai.reader.LtApp
import org.json.JSONObject
import java.io.File

data class CacheEntry(val f: String = "", val m: String = "", val s: Long = 0L)

private const val ROOT = "offline"
private const val INDEX = "index.json"
private const val BLOBS = "blobs"
private const val LAYOUT = ".layout"
private const val LAYOUT_VERSION = "2"

private val READ = Regex("^/api/lantai/v1/read/([^/?]+)/")
private val BOOKS = Regex("^/api/v1/books/([^/?]+)/")

fun bookIdOfPath(path: String): String {
    READ.find(path)?.groupValues?.getOrNull(1)?.let { if (it.isNotEmpty()) return it }
    BOOKS.find(path)?.groupValues?.getOrNull(1)?.let { if (it.isNotEmpty()) return it }
    return ""
}

fun hashName(path: String): String {
    var hash = 0x811c9dc5.toInt()
    for (ch in path) {
        hash = hash xor ch.code
        hash *= 0x01000193
    }
    val u = hash.toLong() and 0xFFFFFFFFL
    return "${java.lang.Long.toString(u, 16)}-${java.lang.Integer.toString(path.length, 16)}"
}

class OfflineCache {
    private var root: String = ""
    private var server: String = ""
    private val maps = HashMap<String, MutableMap<String, CacheEntry>>()
    private val dirty = HashSet<String>()

    fun setServer(key: String) {
        if (server == key) return
        maps.clear()
        dirty.clear()
        server = key
    }

    fun serverKey(): String = server

    fun init(ctx: Context = LtApp.instance) {
        root = File(ctx.filesDir, ROOT).absolutePath
        val dir = File(root)
        if (!dir.exists()) dir.mkdirs()
        ensureLayout()
    }

    private fun ensureLayout() {
        val marker = File(root, LAYOUT)
        if (marker.exists()) return
        val target = File(root, server)
        try {
            if (!target.exists()) target.mkdirs()
            val names = File(root).list() ?: emptyArray()
            for (name in names) {
                if (name == LAYOUT || name == server) continue
                val from = File(root, name)
                try {
                    if (from.isDirectory) {
                        from.renameTo(File(target, name))
                    }
                } catch (e: Throwable) {
                }
            }
        } catch (e: Throwable) {
        }
        try {
            marker.writeText(LAYOUT_VERSION)
        } catch (e: Throwable) {
        }
    }

    fun ready(): Boolean = root.isNotEmpty()

    private fun bookDir(bookId: String): File = File(File(root, server), bookId)

    private fun blobDir(bookId: String): File = File(bookDir(bookId), BLOBS)

    private fun mapOf(bookId: String): MutableMap<String, CacheEntry> {
        maps[bookId]?.let { return it }
        val map = HashMap<String, CacheEntry>()
        val file = File(bookDir(bookId), INDEX)
        if (file.exists()) {
            try {
                val obj = JSONObject(file.readText())
                val items = obj.optJSONObject("items")
                if (items != null) {
                    val keys = items.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val e = items.optJSONObject(k) ?: continue
                        map[k] = CacheEntry(e.optString("f"), e.optString("m"), e.optLong("s"))
                    }
                }
            } catch (e: Throwable) {
            }
        }
        maps[bookId] = map
        return map
    }

    fun has(path: String): Boolean {
        val bookId = bookIdOfPath(path)
        if (bookId.isEmpty()) return false
        val entry = mapOf(bookId)[path] ?: return false
        return File(blobDir(bookId), entry.f).exists()
    }

    fun mimeOf(path: String): String {
        val bookId = bookIdOfPath(path)
        if (bookId.isEmpty()) return ""
        return mapOf(bookId)[path]?.m ?: ""
    }

    fun read(path: String): ByteArray? {
        val bookId = bookIdOfPath(path)
        if (bookId.isEmpty()) return null
        val entry = mapOf(bookId)[path] ?: return null
        val file = File(blobDir(bookId), entry.f)
        return try {
            if (!file.exists() || file.length() <= 0) null else file.readBytes()
        } catch (e: Throwable) {
            null
        }
    }

    fun put(bookId: String, path: String, mime: String, bytes: ByteArray): Long {
        if (bookId.isEmpty() || bytes.isEmpty()) return 0L
        return try {
            val dir = blobDir(bookId)
            if (!dir.exists()) dir.mkdirs()
            val name = "${hashName(path)}.bin"
            File(dir, name).writeBytes(bytes)
            mapOf(bookId)[path] = CacheEntry(name, mime, bytes.size.toLong())
            dirty.add(bookId)
            bytes.size.toLong()
        } catch (e: Throwable) {
            0L
        }
    }

    fun flush() {
        for (bookId in dirty.toList()) {
            val map = mapOf(bookId)
            val items = JSONObject()
            try {
                for ((k, v) in map) {
                    items.put(k, JSONObject().put("f", v.f).put("m", v.m).put("s", v.s))
                }
                val dir = bookDir(bookId)
                if (!dir.exists()) dir.mkdirs()
                File(dir, INDEX).writeText(JSONObject().put("items", items).toString())
            } catch (e: Throwable) {
            }
        }
        dirty.clear()
    }

    fun sizeOf(bookId: String): Long {
        var total = 0L
        for ((_, v) in mapOf(bookId)) total += v.s
        return total
    }

    fun countOf(bookId: String): Int = mapOf(bookId).size

    fun bookIds(): List<String> {
        val base = File(root, server)
        return try {
            if (!base.exists()) emptyList()
            else (base.list() ?: emptyArray()).filter { File(base, it).isDirectory }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun drop(bookId: String) {
        maps.remove(bookId)
        dirty.remove(bookId)
        deleteTree(bookDir(bookId))
    }

    fun dropAll() {
        maps.clear()
        dirty.clear()
        val base = File(root, server)
        deleteTree(base)
        try {
            base.mkdirs()
        } catch (e: Throwable) {
        }
    }

    private fun deleteTree(file: File) {
        try {
            if (!file.exists()) return
            val children = file.listFiles()
            if (children != null) for (c in children) deleteTree(c)
            file.delete()
        } catch (e: Throwable) {
        }
    }
}

val offlineCache = OfflineCache()

fun offlinePage(bookId: String, page: Int, convert: String): ByteArray? =
    offlineCache.read("/api/v1/books/$bookId/pages/$page?convert=$convert")
