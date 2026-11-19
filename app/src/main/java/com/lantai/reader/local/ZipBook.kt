package com.lantai.reader.local

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipBook private constructor(private val zip: ZipFile) {
    private var entries: List<ZipEntry>? = null

    fun list(): List<ZipEntry> {
        entries?.let { return it }
        val out = ArrayList<ZipEntry>()
        val all = zip.entries()
        var count = 0
        while (all.hasMoreElements() && count < COUNT_LIMIT) {
            val entry = all.nextElement()
            count++
            if (entry.isDirectory) continue
            if (!safePath(entry.name)) continue
            if (entry.size > ENTRY_LIMIT) continue
            out.add(entry)
        }
        entries = out
        return out
    }

    fun has(name: String): Boolean = list().any { it.name == name }

    fun read(name: String): ByteArray {
        val entry = list().firstOrNull { it.name == name }
            ?: throw IllegalStateException("EPUB 内没有 $name")
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    fun text(name: String): String = String(read(name), Charsets.UTF_8)

    fun close() {
        try {
            zip.close()
        } catch (e: Throwable) {
        }
    }

    companion object {
        private const val ENTRY_LIMIT = (96L * 1024L * 1024L).toInt()
        private const val COUNT_LIMIT = 50000

        fun open(path: String): ZipBook {
            val file = File(path)
            if (!file.exists() || file.length() < 22) {
                throw IllegalStateException("不是有效的 EPUB 压缩包")
            }
            return ZipBook(ZipFile(file))
        }

        fun safePath(name: String): Boolean {
            if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains("\u0000")) {
                return false
            }
            if (Regex("^[a-z]:", RegexOption.IGNORE_CASE).containsMatchIn(name)) return false
            val parts = name.split("/")
            return parts.none { it == ".." || it == "." }
        }
    }
}
