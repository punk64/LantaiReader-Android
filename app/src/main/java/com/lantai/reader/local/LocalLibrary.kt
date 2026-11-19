package com.lantai.reader.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.lantai.reader.model.LocalBook

object LocalLibrary {
    private val BOOK_EXT = listOf(
        "epub", "kepub", "txt", "pdf", "zip", "cbz", "rar", "cbr", "7z", "cb7", "mobi", "azw3"
    )

    fun supportsFolders(): Boolean = true

    fun supportsFiles(): Boolean = true

    fun isBook(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot < 1) return false
        return BOOK_EXT.contains(name.substring(dot + 1).lowercase())
    }

    fun toPath(uri: String): String = LocalFiles.pathOf(uri)

    fun persist(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Throwable) {
        }
    }

    fun release(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Throwable) {
        }
    }

    fun scanTree(ctx: Context, treeUri: Uri): List<LocalBook> {
        val out = ArrayList<LocalBook>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            walk(ctx, treeUri, docId, out, 0)
        } catch (e: Throwable) {
        }
        return out
    }

    private fun walk(ctx: Context, treeUri: Uri, docId: String, out: MutableList<LocalBook>, depth: Int) {
        if (depth > 32 || out.size > 20000) return
        val children = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        } catch (e: Throwable) {
            return
        }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val cursor = try {
            ctx.contentResolver.query(children, projection, null, null, null)
        } catch (e: Throwable) {
            null
        } ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                val childId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(ctx, treeUri, childId, out, depth + 1)
                } else if (isBook(name)) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    out.add(LocalBook(name = name, uri = docUri.toString(), size = size))
                }
            }
        }
    }

    fun importOne(ctx: Context, uri: Uri, name: String): String {
        val files = ctx.filesDir.absolutePath
        val dir = "$files/imports/${LocalFiles.stableId(uri.toString())}"
        val target = "$dir/$name"
        LocalFiles.ensureDir(dir)
        val out = java.io.File(target)
        if (!(out.exists() && out.length() > 0)) {
            val part = java.io.File("$target.part")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                part.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            if (out.exists()) out.delete()
            part.renameTo(out)
        }
        return "file://$target"
    }

    fun adopt(ctx: Context, books: List<LocalBook>): List<LocalBook> {
        val out = ArrayList<LocalBook>()
        val prefix = "file://${ctx.filesDir.absolutePath}/imports/"
        for (book in books) {
            var uri = book.uri
            if (!uri.startsWith(prefix)) {
                uri = try {
                    importOne(ctx, Uri.parse(book.uri), book.name)
                } catch (e: Throwable) {
                    book.uri
                }
            }
            out.add(LocalBook(name = book.name, uri = uri, size = book.size))
        }
        return out
    }

    fun importPicked(ctx: Context, uris: List<Uri>): List<LocalBook> {
        val out = ArrayList<LocalBook>()
        for (uri in uris) {
            val name = displayName(ctx, uri) ?: continue
            if (!isBook(name)) continue
            val imported = try {
                importOne(ctx, uri, name)
            } catch (e: Throwable) {
                continue
            }
            out.add(LocalBook(name = name, uri = imported, size = LocalFiles.statSize(LocalFiles.pathOf(imported))))
        }
        return out
    }

    fun displayName(ctx: Context, uri: Uri): String? {
        if (uri.scheme == "file") return java.io.File(uri.path ?: "").name
        val cursor = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        } catch (e: Throwable) {
            null
        }
        if (cursor != null) {
            cursor.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return uri.lastPathSegment
    }
}
