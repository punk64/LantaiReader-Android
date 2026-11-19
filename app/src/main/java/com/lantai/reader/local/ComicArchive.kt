package com.lantai.reader.local

import com.github.junrar.Archive
import com.lantai.reader.common.t
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

interface ArchiveReader {
    fun read(name: String): ByteArray
    fun close()
}

private class ZipArchiveReader(path: String) : ArchiveReader {
    private val zip = ZipFile(File(path))

    override fun read(name: String): ByteArray = synchronized(zip) {
        val entry = zip.getEntry(name) ?: throw IllegalStateException("页不存在 $name")
        zip.getInputStream(entry).use { it.readBytes() }
    }

    override fun close() {
        try {
            zip.close()
        } catch (e: Throwable) {
        }
    }
}

private class RarArchiveReader(path: String) : ArchiveReader {
    private val archive = Archive(File(path))

    override fun read(name: String): ByteArray = synchronized(archive) {
        val header = archive.fileHeaders.firstOrNull { it.fileName == name && !it.isDirectory }
            ?: throw IllegalStateException("页不存在 $name")
        archive.getInputStream(header).use { it.readBytes() }
    }

    override fun close() {
        try {
            archive.close()
        } catch (e: Throwable) {
        }
    }
}

private class SevenArchiveReader(path: String) : ArchiveReader {
    private val file: SevenZFile = SevenZFile.builder().setFile(File(path)).get()
    private val index = HashMap<String, SevenZArchiveEntry>()

    init {
        var entry: SevenZArchiveEntry? = file.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) index[entry.name] = entry
            entry = file.nextEntry
        }
    }

    override fun read(name: String): ByteArray = synchronized(file) {
        val target = index[name] ?: throw IllegalStateException("页不存在 $name")
        file.getInputStream(target).use { it.readBytes() }
    }

    override fun close() {
        try {
            file.close()
        } catch (e: Throwable) {
        }
    }
}

class ArchiveBook(
    val key: String,
    val title: String,
    val pages: List<String>,
    private val reader: ArchiveReader
) {
    fun readPage(n: Int): ByteArray {
        if (n < 1 || n > pages.size) throw IllegalStateException(t("页码超出范围"))
        return reader.read(pages[n - 1])
    }

    fun size(): Int = pages.size

    fun close() {
        reader.close()
    }
}

object ComicArchive {
    private val IMAGE_EXT = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "avif", "heic")
    private val ZIP_EXT = listOf("zip", "cbz")
    private val RAR_EXT = listOf("rar", "cbr")
    private val SEVEN_EXT = listOf("7z", "cb7")
    private val cache = LinkedHashMap<String, ArchiveBook>()
    private const val LIMIT = 3

    fun isArchive(name: String): Boolean {
        val ext = extensionOf(name)
        return ZIP_EXT.contains(ext) || RAR_EXT.contains(ext) || SEVEN_EXT.contains(ext)
    }

    fun isReadable(name: String): Boolean = isArchive(name)

    fun open(path: String, name: String, size: Long): ArchiveBook {
        if (!isArchive(name)) {
            throw IllegalStateException("不支持的压缩包格式：${extensionOf(name).uppercase()}")
        }
        val bytes = if (size > 0) size else LocalFiles.statSize(path)
        val key = "${bytes.toString(36)}${hashOf(path)}"
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val ext = extensionOf(name)
        val names = when {
            ZIP_EXT.contains(ext) -> namesOfZip(path)
            RAR_EXT.contains(ext) -> namesOfRar(path)
            SEVEN_EXT.contains(ext) -> namesOfSeven(path)
            else -> emptyList()
        }
        if (names.isEmpty()) throw IllegalStateException(t("压缩包内没有可显示的图片"))
        val reader: ArchiveReader = when {
            ZIP_EXT.contains(ext) -> ZipArchiveReader(path)
            RAR_EXT.contains(ext) -> RarArchiveReader(path)
            else -> SevenArchiveReader(path)
        }
        val book = ArchiveBook(key, name, names, reader)
        synchronized(cache) {
            cache[key] = book
            while (cache.size > LIMIT) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)?.close()
            }
        }
        return book
    }

    private fun namesOfZip(path: String): List<String> {
        val out = ArrayList<String>()
        try {
            ZipFile(File(path)).use { zip ->
                val all = zip.entries()
                while (all.hasMoreElements()) {
                    val entry: ZipEntry = all.nextElement()
                    if (entry.isDirectory) continue
                    if (!IMAGE_EXT.contains(extensionOf(entry.name))) continue
                    if (!ZipBook.safePath(entry.name)) continue
                    if (entry.name.contains("__MACOSX/")) continue
                    out.add(entry.name)
                }
            }
        } catch (e: Throwable) {
        }
        out.sortWith { left, right -> naturalCompare(left, right) }
        return out
    }

    private fun namesOfRar(path: String): List<String> {
        val out = ArrayList<String>()
        try {
            Archive(File(path)).use { archive ->
                for (header in archive.fileHeaders) {
                    if (header.isDirectory) continue
                    val entryName = header.fileName
                    if (entryName.contains("__MACOSX/")) continue
                    if (!IMAGE_EXT.contains(extensionOf(entryName))) continue
                    out.add(entryName)
                }
            }
        } catch (e: Throwable) {
        }
        out.sortWith { left, right -> naturalCompare(left, right) }
        return out
    }

    private fun namesOfSeven(path: String): List<String> {
        val out = ArrayList<String>()
        try {
            val file = SevenZFile.builder().setFile(File(path)).get()
            try {
                var entry = file.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && IMAGE_EXT.contains(extensionOf(entry.name))) {
                        out.add(entry.name)
                    }
                    entry = file.nextEntry
                }
            } finally {
                file.close()
            }
        } catch (e: Throwable) {
        }
        out.sortWith { left, right -> naturalCompare(left, right) }
        return out
    }

    fun clearAll() {
        synchronized(cache) {
            cache.values.forEach { it.close() }
            cache.clear()
        }
    }

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(dot + 1).lowercase() else ""
    }

    fun hashOf(text: String): String {
        var hash = 5381
        for (ch in text) hash = ((hash shl 5) + hash + ch.code) and 0x7fffffff
        return hash.toString(36)
    }

    fun naturalCompare(left: String, right: String): Int {
        val a = chunksOf(left)
        val b = chunksOf(right)
        val size = minOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a[i]
            val y = b[i]
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            if (nx != null && ny != null) {
                if (nx != ny) return nx - ny
            } else if (x != y) {
                return if (x < y) -1 else 1
            }
        }
        return a.size - b.size
    }

    private fun chunksOf(name: String): List<String> {
        val raw = name.lowercase().split(Regex("(\\d+)"))
        return raw.filter { it.isNotEmpty() }
    }
}
