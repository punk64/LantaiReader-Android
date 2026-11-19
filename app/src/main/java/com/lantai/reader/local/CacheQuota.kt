package com.lantai.reader.local

import com.lantai.reader.LtApp
import com.lantai.reader.model.PrefKeys
import com.lantai.reader.model.Prefs
import java.io.File

object CacheQuota {
    const val fallback: Long = 4L * 1024L * 1024L * 1024L
    const val lowest: Long = 256L * 1024L * 1024L
    const val highest: Long = 64L * 1024L * 1024L * 1024L
    private const val USED = ".used"
    private const val TOUCH_GAP = 300000L

    private var roots: MutableList<String> = ArrayList()
    private val touched = HashMap<String, Long>()

    fun init() {
        if (roots.isNotEmpty()) return
        val cache = LtApp.instance.cacheDir.absolutePath
        roots = arrayListOf("$cache/archive", "$cache/local")
    }

    fun ready(): Boolean = roots.isNotEmpty()

    fun paths(): List<String> = roots.toList()

    fun limit(): Long {
        val value = Prefs.int(PrefKeys.cacheLimit, 0).toLong()
        if (value < lowest || value > highest) return fallback
        return value
    }

    fun parse(text: String): Long {
        val raw = text.trim().uppercase().replace(Regex("\\s+"), "")
        if (raw.isEmpty()) return 0L
        val match = Regex("^([0-9]+(?:\\.[0-9]+)?)([KMG]?)B?$").find(raw) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        if (value <= 0) return 0L
        val factor = when (match.groupValues[2]) {
            "K" -> 1024.0
            "M" -> 1048576.0
            else -> 1073741824.0
        }
        val bytes = (value * factor).toLong()
        if (bytes < lowest || bytes > highest) return 0L
        return bytes
    }

    fun apply(bytes: Long) {
        val value = when {
            bytes < lowest -> lowest
            bytes > highest -> highest
            else -> bytes
        }
        Prefs.putInt(PrefKeys.cacheLimit, value.toInt())
        enforce("")
    }

    fun measure(): Long {
        init()
        var total = 0L
        for (root in roots) total += sizeOf(root)
        return total
    }

    fun touch(dir: String) {
        if (dir.isEmpty()) return
        val now = System.currentTimeMillis()
        val last = touched[dir]
        if (last != null && now - last < TOUCH_GAP) return
        touched[dir] = now
        try {
            val folder = File(dir)
            if (!folder.isDirectory) return
            File(folder, USED).writeText("$now")
        } catch (e: Throwable) {
        }
    }

    fun enforce(keep: String = ""): Long {
        init()
        return try {
            val limit = limit()
            val total = measure()
            if (total <= limit) return 0L
            var need = total - (limit * 0.9).toLong()
            if (need <= 0) return 0L
            val entries = ArrayList<Pair<String, Long>>()
            for (root in roots) entries.addAll(entriesOf(root))
            entries.sortBy { it.second }
            var freed = 0L
            for ((path, _) in entries) {
                if (need <= 0) break
                if (keep.isNotEmpty() && path == keep) continue
                val size = sizeOf(path)
                LocalFiles.removeTree(path)
                if (File(path).exists()) continue
                touched.remove(path)
                need -= size
                freed += size
            }
            freed
        } catch (e: Throwable) {
            0L
        }
    }

    private fun entriesOf(base: String): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        val dir = File(base)
        if (!dir.isDirectory) return out
        val children = dir.listFiles() ?: return out
        for (child in children) {
            if (!child.isDirectory) continue
            out.add(child.absolutePath to usedOf(child.absolutePath))
        }
        return out
    }

    private fun usedOf(dir: String): Long {
        try {
            val f = File(dir, USED)
            if (f.exists()) {
                val value = f.readText().trim().toLongOrNull() ?: 0L
                if (value > 0) return value
            }
        } catch (e: Throwable) {
        }
        return try {
            maxOf(1L, File(dir).lastModified())
        } catch (e: Throwable) {
            1L
        }
    }

    fun sizeOf(dir: String): Long {
        if (dir.isEmpty()) return 0L
        val file = File(dir)
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            if (child.name == USED) continue
            total += if (child.isDirectory) sizeOf(child.absolutePath) else child.length()
        }
        return total
    }
}
