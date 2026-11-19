package com.lantai.reader.local

import com.lantai.reader.data.offlineDb
import com.lantai.reader.model.LocalBook
import com.lantai.reader.model.LocalMeta
import com.lantai.reader.model.Prefs
import com.lantai.reader.model.Store
import java.io.File

data class LocalRestore(
    val root: String,
    val books: List<LocalBook>,
    val metas: List<LocalMeta>,
    val missing: List<String>,
    val stale: Boolean
)

private const val KEY_ROOT = "localRoot"
const val PICKED = "picked"

object LocalStore {
    suspend fun restore(): LocalRestore {
        val root = Prefs.read(KEY_ROOT)
        val saved = offlineDb.locals()
        if (saved.isEmpty()) {
            return LocalRestore(root, emptyList(), emptyList(), emptyList(), false)
        }
        val stale = root.isNotEmpty() && root != PICKED && !File(root).exists()
        val books = ArrayList<LocalBook>()
        val missing = ArrayList<String>()
        for (meta in saved) {
            books.add(LocalBook(name = meta.name, uri = meta.uri, size = meta.sizeBytes))
            if (!LocalFiles.existsSync(LocalLibrary.toPath(meta.uri))) missing.add(meta.id)
        }
        return LocalRestore(root, books, saved, missing, stale)
    }

    fun persistRoot(uri: String) {
        Prefs.write(KEY_ROOT, uri)
    }

    suspend fun persist(metas: List<LocalMeta>, root: String) {
        offlineDb.saveLocals(metas, root)
    }

    suspend fun persistBooks(books: List<LocalBook>, root: String) {
        val seeds = ArrayList<LocalMeta>()
        val fresh = HashSet<String>()
        for (book in books) {
            val seed = LocalMetas.seed(book)
            for (current in Store.localMetas) {
                if (current.id == seed.id) {
                    seed.chapter = current.chapter
                    seed.progress = current.progress
                    break
                }
            }
            seeds.add(seed)
            fresh.add(seed.id)
        }
        offlineDb.saveLocals(seeds, root)
        val merged = ArrayList<LocalMeta>()
        for (current in Store.localMetas) {
            if (!fresh.contains(current.id)) merged.add(current)
        }
        merged.addAll(seeds)
        Store.localMetas = merged
    }

    suspend fun save(meta: LocalMeta, root: String) {
        offlineDb.saveLocal(meta, root)
    }

    suspend fun saveProgress(id: String, chapter: Int, progress: Double) {
        offlineDb.patchLocalProgress(id, chapter, progress)
        for (current in Store.localMetas) {
            if (current.id == id) {
                current.chapter = chapter
                current.progress = progress
                break
            }
        }
    }

    suspend fun remove(meta: LocalMeta) {
        release(meta)
        offlineDb.removeLocal(meta.id)
    }

    suspend fun purgeAll(metas: List<LocalMeta>): Long {
        var freed = 0L
        for (meta in metas) {
            release(meta)
            freed += meta.sizeBytes
        }
        clear()
        return freed
    }

    private fun release(meta: LocalMeta) {
        val filesRoot = LocalMetas.filesRoot()
        LocalMetas.forget(meta.id)
        LocalFiles.removeFile(LocalMetas.coverOf(meta.uri))
        LocalFiles.removeTree(LocalMetas.dirOf(meta.uri))
        LocalFiles.removeImport(meta.uri, filesRoot)
    }

    suspend fun clear() {
        offlineDb.clearLocals()
        Prefs.write(KEY_ROOT, "")
    }
}
