package com.lantai.reader.local

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.lantai.reader.LtApp
import com.lantai.reader.model.LocalBook
import com.lantai.reader.model.LocalMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val IMAGE_EXT = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private fun extensionOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(dot + 1).lowercase() else ""
}

fun localKindOf(name: String): String {
    val ext = extensionOf(name)
    if (ext == "txt") return "txt"
    if (ext == "pdf") return "pdf"
    if (ext == "epub" || ext == "kepub") return "epub"
    if (ComicArchive.isReadable(name)) return "comic"
    return ""
}

private fun hashOf(text: String): String {
    var hash = 5381
    for (ch in text) hash = ((hash shl 5) + hash + ch.code) and 0x7fffffff
    return hash.toString(36)
}

private fun attributeOf(tag: String, key: String): String {
    val match = Regex("$key\\s*=\\s*\"([^\"]*)\"").find(tag)
    return match?.groupValues?.getOrNull(1) ?: ""
}

private fun decodeEntities(text: String): String = text
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")

private fun normalizeDir(href: String): String {
    val out = ArrayList<String>()
    for (part in href.split("/")) {
        if (part.isEmpty() || part == ".") continue
        if (part == "..") {
            if (out.isNotEmpty()) out.removeAt(out.size - 1)
            continue
        }
        out.add(part)
    }
    return out.joinToString("/")
}

private fun relativeTo(baseDir: String, href: String): String {
    if (href.startsWith("/")) return normalizeDir(href.substring(1))
    return normalizeDir(if (baseDir.isNotEmpty()) "$baseDir/$href" else href)
}

private fun textOf(xml: String, tag: String): String {
    val match = Regex("<dc:$tag[^>]*>([\\s\\S]*?)</dc:$tag>").find(xml) ?: return ""
    return decodeEntities(match.groupValues[1].trim())
}

object LocalMetas {
    private var base = ""
    private var coverDir = ""
    private var rootDir = ""
    private var enforceScheduled = false
    private val covers = HashMap<String, String>()

    fun init() {
        if (base.isNotEmpty()) return
        val files = LtApp.instance.filesDir.absolutePath
        base = "$files/local"
        coverDir = "$files/local-cover"
        rootDir = files
    }

    fun filesRoot(): String {
        init()
        return rootDir
    }

    fun dirOf(uri: String): String {
        init()
        return if (base.isEmpty()) "" else "$base/${hashOf(uri)}"
    }

    fun coverOf(uri: String): String {
        init()
        return if (coverDir.isEmpty()) "" else "$coverDir/${hashOf(uri)}.jpg"
    }

    fun coverPathOf(uri: String): String {
        init()
        return "$coverDir/${hashOf(uri)}.jpg"
    }

    fun forget(id: String) {
        covers.remove(id)
    }

    fun coverFile(id: String): String {
        init()
        val hit = covers[id]
        if (!hit.isNullOrEmpty()) return hit
        if (coverDir.isEmpty() || !id.startsWith("local:")) return ""
        val path = "$coverDir/${id.substring(6)}.jpg"
        return if (File(path).exists()) path else ""
    }

    fun isStableCover(path: String): Boolean {
        init()
        return coverDir.isNotEmpty() && path.startsWith(coverDir)
    }

    fun idOf(uri: String): String = "local:${hashOf(uri)}"

    fun seed(book: LocalBook): LocalMeta {
        val meta = LocalMeta()
        meta.id = idOf(book.uri)
        meta.uri = book.uri
        meta.name = book.name
        meta.title = book.name
        meta.sizeBytes = book.size
        val dot = book.name.lastIndexOf('.')
        if (dot > 0) meta.title = book.name.substring(0, dot)
        if (ComicArchive.isReadable(book.name)) {
            meta.kind = "comic"
            meta.profile = "DIVINA"
            return meta
        }
        val kind = localKindOf(book.name)
        meta.kind = kind
        if (kind == "epub") {
            meta.profile = "EPUB"
        } else if (kind == "pdf") {
            meta.profile = "PDF"
        } else if (kind == "txt") {
            meta.profile = "TXT"
        }
        return meta
    }

    suspend fun enrich(book: LocalBook): LocalMeta = withContext(Dispatchers.IO) {
        init()
        val meta = seed(book)
        if (meta.kind == "comic") {
            meta.cover = existingCover(book)
            return@withContext finish(meta)
        }
        if (base.isEmpty()) return@withContext finish(meta)
        try {
            val staged = stage(book.uri, book.name)
            if (meta.kind == "epub") {
                fillEpub(meta, staged)
            } else if (meta.kind == "pdf") {
                fillPdf(meta, staged)
            }
        } catch (e: Throwable) {
        }
        finish(meta)
    }

    private fun finish(meta: LocalMeta): LocalMeta {
        if (meta.cover.isNotEmpty() && !isStableCover(meta.cover)) {
            cacheCover(meta.uri, meta.cover)
            val stable = coverFile(meta.id)
            if (stable.isNotEmpty()) meta.cover = stable
        }
        covers[meta.id] = meta.cover
        return meta
    }

    fun cacheCover(uri: String, imagePath: String) {
        init()
        if (coverDir.isEmpty() || imagePath.isEmpty()) return
        if (isStableCover(imagePath)) return
        val target = "$coverDir/${hashOf(uri)}.jpg"
        val out = File(target)
        if (out.exists() && out.length() > 0) return
        try {
            LocalFiles.ensureDir(coverDir)
            LocalFiles.copyInto(File(imagePath), target)
        } catch (e: Throwable) {
        }
    }

    private fun existingCover(book: LocalBook): String {
        init()
        if (coverDir.isEmpty()) return ""
        val stable = "$coverDir/${hashOf(book.uri)}.jpg"
        if (File(stable).let { it.exists() && it.length() > 0 }) return stable
        val legacy = "$base/${hashOf(book.uri)}/cover.jpg"
        val legacyFile = File(legacy)
        if (legacyFile.exists() && legacyFile.length() > 0) {
            cacheCover(book.uri, legacy)
            if (File(stable).let { it.exists() && it.length() > 0 }) return stable
        }
        return ""
    }

    fun stagedPath(uri: String, name: String): String {
        init()
        val ext = extensionOf(name)
        return "$base/${hashOf(uri)}/book${if (ext.isEmpty()) "" else ".$ext"}"
    }

    fun ensureStaged(uri: String, name: String): Boolean {
        init()
        if (base.isEmpty()) return false
        val path = stagedPath(uri, name)
        if (LocalFiles.exists(path)) return true
        return try {
            LocalFiles.copyInto(File(LocalFiles.pathOf(uri)), path)
            LocalFiles.exists(path)
        } catch (e: Throwable) {
            false
        }
    }

    private fun stage(uri: String, name: String): String {
        init()
        val dir = "$base/${hashOf(uri)}"
        val ext = extensionOf(name)
        val path = "$dir/book${if (ext.isEmpty()) "" else ".$ext"}"
        if (LocalFiles.exists(path)) {
            CacheQuota.touch(dir)
            queueEnforce()
            return path
        }
        LocalFiles.copyInto(File(LocalFiles.pathOf(uri)), path)
        CacheQuota.touch(dir)
        queueEnforce()
        return path
    }

    private fun queueEnforce() {
        if (enforceScheduled) return
        enforceScheduled = true
        Thread {
            try {
                Thread.sleep(3000)
                CacheQuota.enforce()
            } catch (e: Throwable) {
            } finally {
                enforceScheduled = false
            }
        }.start()
    }

    private fun fillEpub(meta: LocalMeta, staged: String) {
        val book = try {
            ZipBook.open(staged)
        } catch (e: Throwable) {
            return
        }
        try {
            val container = book.text("META-INF/container.xml")
            val rootTag = Regex("<rootfile\\b[^>]*>").find(container)
            val opfRel = if (rootTag != null) attributeOf(rootTag.value, "full-path") else ""
            if (opfRel.isEmpty()) return
            val opfDir = if (opfRel.contains("/")) normalizeDir(opfRel.substring(0, opfRel.lastIndexOf('/'))) else ""
            val opf = book.text(normalizeDir(opfRel))
            val title = textOf(opf, "title")
            if (title.isNotEmpty()) meta.title = title
            meta.author = textOf(opf, "creator")
            meta.publisher = textOf(opf, "publisher")
            val date = textOf(opf, "date")
            meta.date = if (date.length >= 10) date.substring(0, 10) else date
            val seriesMatch = Regex("<meta\\b[^>]*name\\s*=\\s*\"calibre:series\"[^>]*>").find(opf)
            if (seriesMatch != null) meta.series = decodeEntities(attributeOf(seriesMatch.value, "content"))
            val coverId = coverIdOf(opf)
            var coverHref = ""
            var refCount = 0
            for (item in Regex("<item\\b[^>]*>").findAll(opf)) {
                val tag = item.value
                val id = attributeOf(tag, "id")
                val href = attributeOf(tag, "href")
                val properties = attributeOf(tag, "properties")
                if (coverHref.isEmpty() && href.isNotEmpty()) {
                    if ((coverId.isNotEmpty() && id == coverId) || properties.contains("cover-image")) {
                        coverHref = relativeTo(opfDir, href)
                    }
                }
            }
            if (coverHref.isNotEmpty() && IMAGE_EXT.contains(extensionOf(coverHref))) {
                writeCover(book, coverHref, File(staged).parent ?: "", meta)
            }
            refCount = Regex("<itemref\\b[^>]*>").findAll(opf).count()
            meta.pages = refCount
        } finally {
            book.close()
        }
    }

    private fun coverIdOf(opf: String): String {
        val match = Regex("<meta\\b[^>]*name\\s*=\\s*\"cover\"[^>]*>").find(opf) ?: return ""
        return attributeOf(match.value, "content")
    }

    private fun writeCover(book: ZipBook, href: String, dir: String, meta: LocalMeta) {
        val target = "$dir/cover.jpg"
        val out = File(target)
        if (out.exists() && out.length() > 0) {
            meta.cover = target
            return
        }
        try {
            val bytes = book.read(href)
            LocalFiles.ensureDir(dir)
            out.writeBytes(bytes)
            meta.cover = target
        } catch (e: Throwable) {
        }
    }

    private fun fillPdf(meta: LocalMeta, staged: String) {
        val file = File(staged)
        if (!file.exists()) return
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                meta.pages = renderer.pageCount
                val dir = file.parent ?: ""
                val target = File(dir, "cover.jpg")
                if (!(target.exists() && target.length() > 0) && renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    try {
                        val width = (page.width * 0.34f).toInt().coerceAtLeast(1)
                        val height = (page.height * 0.34f).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        LocalFiles.ensureDir(dir)
                        target.outputStream().use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        }
                        bmp.recycle()
                    } finally {
                        page.close()
                    }
                }
                if (target.exists() && target.length() > 0) meta.cover = target.absolutePath
            } finally {
                renderer.close()
            }
        } catch (e: Throwable) {
        } finally {
            try {
                pfd?.close()
            } catch (e: Throwable) {
            }
        }
    }
}
