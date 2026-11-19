package com.lantai.reader.local

import android.content.Context
import com.lantai.reader.LtApp
import java.io.File

object LocalFiles {
    fun pathOf(uri: String): String = if (uri.startsWith("file://")) uri.substring(7) else uri

    fun ensureDir(dir: String) {
        if (dir.isEmpty()) return
        try {
            val f = File(dir)
            if (!f.exists()) f.mkdirs()
        } catch (e: Throwable) {
        }
    }

    fun exists(path: String): Boolean {
        if (path.isEmpty()) return false
        return try {
            val f = File(path)
            f.exists() && f.length() > 0
        } catch (e: Throwable) {
            false
        }
    }

    fun existsSync(path: String): Boolean {
        if (path.isEmpty()) return false
        return try {
            File(path).exists()
        } catch (e: Throwable) {
            false
        }
    }

    fun statSize(path: String): Long {
        return try {
            File(path).length()
        } catch (e: Throwable) {
            0L
        }
    }

    fun removeFile(path: String) {
        if (path.isEmpty()) return
        try {
            val f = File(path)
            if (f.exists()) f.delete()
        } catch (e: Throwable) {
        }
    }

    fun removeTree(path: String) {
        if (path.isEmpty()) return
        try {
            deleteTree(File(path))
        } catch (e: Throwable) {
        }
    }

    private fun deleteTree(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteTree(it) }
        }
        file.delete()
    }

    fun removeImport(uri: String, filesRoot: String) {
        if (filesRoot.isEmpty()) return
        val prefix = "file://$filesRoot/imports/"
        if (!uri.startsWith(prefix)) return
        val rest = uri.substring(prefix.length)
        val slash = rest.indexOf('/')
        if (slash <= 0) return
        removeTree("$filesRoot/imports/${rest.substring(0, slash)}")
    }

    fun copyInto(source: File, target: String) {
        val out = File(target)
        if (out.exists() && out.length() > 0) return
        ensureDir(out.parent ?: "")
        val part = File("$target.part")
        source.inputStream().use { input ->
            part.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        if (out.exists()) out.delete()
        part.renameTo(out)
    }

    fun stableId(text: String): String {
        var hash = 5381
        for (ch in text) {
            hash = ((hash shl 5) + hash + ch.code) and 0x7fffffff
        }
        return hash.toString(36)
    }

    fun importFile(uri: String, name: String, resolver: (String) -> ByteArray?): String {
        val filesDir = LtApp.instance.filesDir.absolutePath
        val dir = "$filesDir/imports/${stableId(uri)}"
        val target = "$dir/$name"
        ensureDir(dir)
        val out = File(target)
        if (!(out.exists() && out.length() > 0)) {
            val bytes = resolver(uri)
            if (bytes != null && bytes.isNotEmpty()) {
                val part = File("$target.part")
                part.outputStream().use { it.write(bytes) }
                if (out.exists()) out.delete()
                part.renameTo(out)
            }
        }
        return "file://$target"
    }

    fun importsDir(ctx: Context = LtApp.instance): String = "${ctx.filesDir.absolutePath}/imports"
}
