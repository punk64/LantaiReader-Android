package com.lantai.reader.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lantai.reader.LtApp
import com.lantai.reader.model.LocalMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReadingRow(
    val clientId: String = "",
    val bookId: String = "",
    val startedAt: String = "",
    val endedAt: String = "",
    val durationMs: Long = 0L,
    val startPage: Int = 0,
    val lastPage: Int = 0,
    val pagesRead: Int = 0,
    val state: String = "",
    val retry: Int = 0,
    val at: Long = 0L
)

data class JobRow(
    val bookId: String = "",
    val title: String = "",
    val profile: String = "",
    val state: String = "",
    val total: Int = 0,
    val done: Int = 0,
    val bytes: Long = 0L,
    val pages: Int = 0,
    val size: Long = 0L,
    val at: Long = 0L,
    val msg: String = ""
)

data class OfflineBookRow(
    val bookId: String = "",
    val title: String = "",
    val profile: String = "",
    val items: Int = 0,
    val bytes: Long = 0L,
    val at: Long = 0L
)

data class OutboxRow(
    val id: Long = 0L,
    val method: String = "",
    val path: String = "",
    val body: String = "",
    val at: Long = 0L
)

private const val DB_NAME = "lantai_offline.db"

private const val CREATE_JOB = """CREATE TABLE IF NOT EXISTS JOB (
  server TEXT,
  book_id TEXT,
  title TEXT,
  profile TEXT,
  state TEXT,
  total INTEGER,
  done INTEGER,
  bytes INTEGER,
  pages INTEGER,
  size INTEGER,
  at INTEGER,
  msg TEXT,
  PRIMARY KEY (server, book_id)
)"""

private const val CREATE_BOOK = """CREATE TABLE IF NOT EXISTS OFFLINE_BOOK (
  server TEXT,
  book_id TEXT,
  title TEXT,
  profile TEXT,
  items INTEGER,
  bytes INTEGER,
  at INTEGER,
  PRIMARY KEY (server, book_id)
)"""

private const val CREATE_OUTBOX = """CREATE TABLE IF NOT EXISTS PROGRESS_OUTBOX (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  server TEXT,
  method TEXT,
  path TEXT,
  body TEXT,
  at INTEGER
)"""

private const val CREATE_READING = """CREATE TABLE IF NOT EXISTS READING_SESSION (
  server TEXT,
  client_id TEXT,
  book_id TEXT,
  started_at TEXT,
  ended_at TEXT,
  duration_ms INTEGER,
  start_page INTEGER,
  last_page INTEGER,
  pages_read INTEGER,
  state TEXT,
  retry INTEGER,
  at INTEGER,
  PRIMARY KEY (server, client_id)
)"""

private const val CREATE_LOCAL = """CREATE TABLE IF NOT EXISTS LOCAL_BOOK (
  local_id TEXT PRIMARY KEY,
  uri TEXT,
  name TEXT,
  size INTEGER,
  title TEXT,
  author TEXT,
  series TEXT,
  publisher TEXT,
  date TEXT,
  profile TEXT,
  pages INTEGER,
  kind TEXT,
  cover TEXT,
  root TEXT,
  at INTEGER,
  chapter INTEGER,
  progress REAL
)"""

class OfflineDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_JOB)
        db.execSQL(CREATE_BOOK)
        db.execSQL(CREATE_OUTBOX)
        db.execSQL(CREATE_READING)
        db.execSQL(CREATE_LOCAL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(CREATE_JOB)
        db.execSQL(CREATE_BOOK)
        db.execSQL(CREATE_OUTBOX)
        db.execSQL(CREATE_READING)
        db.execSQL(CREATE_LOCAL)
    }
}

class OfflineDb {
    private var helper: OfflineDbHelper? = null
    private var server: String = ""

    fun init(ctx: Context = LtApp.instance) {
        if (helper == null) {
            helper = OfflineDbHelper(ctx)
            migrate()
        }
    }

    fun setServer(key: String) {
        server = key
    }

    fun serverKey(): String = server

    fun ready(): Boolean = helper != null

    private fun db(): SQLiteDatabase? = try {
        helper?.writableDatabase
    } catch (e: Throwable) {
        null
    }

    private fun columnsOf(d: SQLiteDatabase, table: String): List<String> {
        val out = ArrayList<String>()
        try {
            d.rawQuery("PRAGMA table_info($table)", null).use { c ->
                val i = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    if (i >= 0) out.add(c.getString(i) ?: "")
                }
            }
        } catch (e: Throwable) {
        }
        return out
    }

    private fun migrate() {
        val d = db() ?: return
        addLocalColumns(d)
        val owner = server.replace("'", "''")
        val job = columnsOf(d, "JOB")
        val book = columnsOf(d, "OFFLINE_BOOK")
        val outbox = columnsOf(d, "PROGRESS_OUTBOX")
        val reading = columnsOf(d, "READING_SESSION")
        val needJob = job.isNotEmpty() && !job.contains("server")
        val needBook = book.isNotEmpty() && !book.contains("server")
        val needOutbox = outbox.isNotEmpty() && !outbox.contains("server")
        val needReading = reading.isNotEmpty() && !reading.contains("server")
        if (!needJob && !needBook && !needOutbox && !needReading) return
        try {
            d.beginTransaction()
            if (needJob) rebuild(d, "JOB", CREATE_JOB, listOf("book_id", "title", "profile", "state", "total", "done", "bytes", "pages", "size", "at", "msg"), owner)
            if (needBook) rebuild(d, "OFFLINE_BOOK", CREATE_BOOK, listOf("book_id", "title", "profile", "items", "bytes", "at"), owner)
            if (needOutbox) rebuild(d, "PROGRESS_OUTBOX", CREATE_OUTBOX, listOf("id", "method", "path", "body", "at"), owner)
            if (needReading) rebuild(d, "READING_SESSION", CREATE_READING, listOf("client_id", "book_id", "started_at", "ended_at", "duration_ms", "start_page", "last_page", "pages_read", "state", "retry", "at"), owner)
            d.setTransactionSuccessful()
        } catch (e: Throwable) {
        } finally {
            try {
                d.endTransaction()
            } catch (e: Throwable) {
            }
        }
    }

    private fun addLocalColumns(d: SQLiteDatabase) {
        val columns = columnsOf(d, "LOCAL_BOOK")
        if (columns.isEmpty()) return
        try {
            if (!columns.contains("chapter")) d.execSQL("ALTER TABLE LOCAL_BOOK ADD COLUMN chapter INTEGER DEFAULT 0")
            if (!columns.contains("progress")) d.execSQL("ALTER TABLE LOCAL_BOOK ADD COLUMN progress REAL DEFAULT 0")
        } catch (e: Throwable) {
        }
    }

    private fun rebuild(d: SQLiteDatabase, table: String, create: String, columns: List<String>, owner: String) {
        val legacy = "${table}_legacy"
        d.execSQL("DROP TABLE IF EXISTS $legacy")
        d.execSQL("ALTER TABLE $table RENAME TO $legacy")
        d.execSQL(create)
        val names = columns.joinToString(", ")
        d.execSQL("INSERT INTO $table (server, $names) SELECT '$owner', $names FROM $legacy")
        d.execSQL("DROP TABLE $legacy")
    }

    suspend fun saveJob(job: JobRow) = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext
        val v = ContentValues()
        v.put("server", server)
        v.put("book_id", job.bookId)
        v.put("title", job.title)
        v.put("profile", job.profile)
        v.put("state", job.state)
        v.put("total", job.total)
        v.put("done", job.done)
        v.put("bytes", job.bytes)
        v.put("pages", job.pages)
        v.put("size", job.size)
        v.put("at", job.at)
        v.put("msg", job.msg)
        try {
            d.insertWithOnConflict("JOB", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Throwable) {
        }
    }

    suspend fun patchJob(bookId: String, state: String, total: Int, done: Int, bytes: Long, msg: String) =
        withContext(Dispatchers.IO) {
            val d = db() ?: return@withContext
            val v = ContentValues()
            v.put("state", state)
            v.put("total", total)
            v.put("done", done)
            v.put("bytes", bytes)
            v.put("at", System.currentTimeMillis())
            v.put("msg", msg)
            try {
                d.update("JOB", v, "server=? AND book_id=?", arrayOf(server, bookId))
            } catch (e: Throwable) {
            }
        }

    suspend fun jobs(): List<JobRow> = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext emptyList()
        val out = ArrayList<JobRow>()
        try {
            d.rawQuery(
                "SELECT book_id,title,profile,state,total,done,bytes,pages,size,at,msg FROM JOB WHERE server=? ORDER BY at DESC",
                arrayOf(server)
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        JobRow(
                            c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "",
                            c.getString(3) ?: "", c.getInt(4), c.getInt(5), c.getLong(6),
                            c.getInt(7), c.getLong(8), c.getLong(9), c.getString(10) ?: ""
                        )
                    )
                }
            }
        } catch (e: Throwable) {
        }
        out
    }

    suspend fun removeJob(bookId: String) = withContext(Dispatchers.IO) {
        try {
            db()?.delete("JOB", "server=? AND book_id=?", arrayOf(server, bookId))
        } catch (e: Throwable) {
        }
    }

    suspend fun saveBook(book: OfflineBookRow) = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext
        val v = ContentValues()
        v.put("server", server)
        v.put("book_id", book.bookId)
        v.put("title", book.title)
        v.put("profile", book.profile)
        v.put("items", book.items)
        v.put("bytes", book.bytes)
        v.put("at", book.at)
        try {
            d.insertWithOnConflict("OFFLINE_BOOK", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Throwable) {
        }
    }

    suspend fun books(): List<OfflineBookRow> = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext emptyList()
        val out = ArrayList<OfflineBookRow>()
        try {
            d.rawQuery(
                "SELECT book_id,title,profile,items,bytes,at FROM OFFLINE_BOOK WHERE server=? ORDER BY at DESC",
                arrayOf(server)
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        OfflineBookRow(
                            c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "",
                            c.getInt(3), c.getLong(4), c.getLong(5)
                        )
                    )
                }
            }
        } catch (e: Throwable) {
        }
        out
    }

    suspend fun removeBook(bookId: String) = withContext(Dispatchers.IO) {
        try {
            db()?.delete("OFFLINE_BOOK", "server=? AND book_id=?", arrayOf(server, bookId))
        } catch (e: Throwable) {
        }
    }

    suspend fun queue(method: String, path: String, body: String) = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext
        val v = ContentValues()
        v.put("server", server)
        v.put("method", method)
        v.put("path", path)
        v.put("body", body)
        v.put("at", System.currentTimeMillis())
        try {
            d.insert("PROGRESS_OUTBOX", null, v)
        } catch (e: Throwable) {
        }
    }

    suspend fun outbox(): List<OutboxRow> = outboxRows(200)

    suspend fun outboxAll(): List<OutboxRow> = outboxRows(0)

    private suspend fun outboxRows(limit: Int): List<OutboxRow> = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext emptyList()
        val out = ArrayList<OutboxRow>()
        val tail = if (limit > 0) " LIMIT $limit" else ""
        try {
            d.rawQuery(
                "SELECT id,method,path,body,at FROM PROGRESS_OUTBOX WHERE server=? ORDER BY id ASC$tail",
                arrayOf(server)
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        OutboxRow(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "", c.getLong(4))
                    )
                }
            }
        } catch (e: Throwable) {
        }
        out
    }

    suspend fun dropOutbox(id: Long) = withContext(Dispatchers.IO) {
        try {
            db()?.delete("PROGRESS_OUTBOX", "id=?", arrayOf(id.toString()))
        } catch (e: Throwable) {
        }
    }

    suspend fun dropOutboxByBook(bookId: String): Int = withContext(Dispatchers.IO) {
        if (bookId.isEmpty()) return@withContext 0
        try {
            db()?.delete("PROGRESS_OUTBOX", "server=? AND path LIKE ?", arrayOf(server, "%/books/$bookId/%")) ?: 0
        } catch (e: Throwable) {
            0
        }
    }

    suspend fun outboxCount(): Int = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext 0
        try {
            d.rawQuery("SELECT COUNT(*) FROM PROGRESS_OUTBOX WHERE server=?", arrayOf(server)).use { c ->
                if (c.moveToNext()) c.getInt(0) else 0
            }
        } catch (e: Throwable) {
            0
        }
    }

    suspend fun saveReading(row: ReadingRow) = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext
        val v = ContentValues()
        v.put("server", server)
        v.put("client_id", row.clientId)
        v.put("book_id", row.bookId)
        v.put("started_at", row.startedAt)
        v.put("ended_at", row.endedAt)
        v.put("duration_ms", row.durationMs)
        v.put("start_page", row.startPage)
        v.put("last_page", row.lastPage)
        v.put("pages_read", row.pagesRead)
        v.put("state", row.state)
        v.put("retry", row.retry)
        v.put("at", row.at)
        try {
            d.insertWithOnConflict("READING_SESSION", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Throwable) {
        }
    }

    suspend fun openReading(): ReadingRow? = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext null
        try {
            d.rawQuery(
                "SELECT client_id,book_id,started_at,ended_at,duration_ms,start_page,last_page,pages_read,state,retry,at " +
                    "FROM READING_SESSION WHERE server=? AND state='open' ORDER BY at DESC LIMIT 1",
                arrayOf(server)
            ).use { c -> if (c.moveToNext()) readingOf(c) else null }
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun pendingReadings(limit: Int): List<ReadingRow> = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext emptyList()
        val out = ArrayList<ReadingRow>()
        try {
            d.rawQuery(
                "SELECT client_id,book_id,started_at,ended_at,duration_ms,start_page,last_page,pages_read,state,retry,at " +
                    "FROM READING_SESSION WHERE server=? AND state='pending' ORDER BY at ASC LIMIT $limit",
                arrayOf(server)
            ).use { c -> while (c.moveToNext()) out.add(readingOf(c)) }
        } catch (e: Throwable) {
        }
        out
    }

    suspend fun pendingReadingCount(): Int = countWhere("READING_SESSION", "pending")

    suspend fun readingBookIds(): List<String> = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext emptyList()
        val out = ArrayList<String>()
        try {
            d.rawQuery("SELECT DISTINCT book_id FROM READING_SESSION WHERE server=?", arrayOf(server)).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: ""
                    if (id.isNotEmpty()) out.add(id)
                }
            }
        } catch (e: Throwable) {
        }
        out
    }

    suspend fun dropReadingsByBook(bookId: String): Int = withContext(Dispatchers.IO) {
        try {
            db()?.delete("READING_SESSION", "server=? AND book_id=?", arrayOf(server, bookId)) ?: 0
        } catch (e: Throwable) {
            0
        }
    }

    private suspend fun countWhere(table: String, state: String): Int = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext 0
        try {
            d.rawQuery("SELECT COUNT(*) FROM $table WHERE server=? AND state=?", arrayOf(server, state)).use { c ->
                if (c.moveToNext()) c.getInt(0) else 0
            }
        } catch (e: Throwable) {
            0
        }
    }

    suspend fun dropReading(clientId: String) = withContext(Dispatchers.IO) {
        try {
            db()?.delete("READING_SESSION", "server=? AND client_id=?", arrayOf(server, clientId))
        } catch (e: Throwable) {
        }
    }

    suspend fun bumpReading(clientId: String) = withContext(Dispatchers.IO) {
        try {
            db()?.execSQL(
                "UPDATE READING_SESSION SET retry = retry + 1 WHERE client_id = ? AND server = ?",
                arrayOf<Any>(clientId, server)
            )
        } catch (e: Throwable) {
        }
    }

    suspend fun saveLocal(meta: LocalMeta, root: String) = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext
        try {
            d.insertWithOnConflict("LOCAL_BOOK", null, localValues(meta, root), SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Throwable) {
        }
    }

    suspend fun saveLocals(metas: List<LocalMeta>, root: String) = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext
        if (metas.isEmpty()) return@withContext
        try {
            d.beginTransaction()
            for (m in metas) {
                d.insertWithOnConflict("LOCAL_BOOK", null, localValues(m, root), SQLiteDatabase.CONFLICT_REPLACE)
            }
            d.setTransactionSuccessful()
        } catch (e: Throwable) {
        } finally {
            try {
                d.endTransaction()
            } catch (e: Throwable) {
            }
        }
    }

    suspend fun locals(): List<LocalMeta> = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext emptyList()
        val out = ArrayList<LocalMeta>()
        try {
            d.rawQuery(
                "SELECT local_id,uri,name,title,author,series,publisher,date,profile,pages,size,cover,kind,chapter,progress " +
                    "FROM LOCAL_BOOK ORDER BY at DESC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        LocalMeta(
                            id = c.getString(0) ?: "",
                            uri = c.getString(1) ?: "",
                            name = c.getString(2) ?: "",
                            title = c.getString(3) ?: "",
                            author = c.getString(4) ?: "",
                            series = c.getString(5) ?: "",
                            publisher = c.getString(6) ?: "",
                            date = c.getString(7) ?: "",
                            profile = c.getString(8) ?: "",
                            pages = c.getInt(9),
                            sizeBytes = c.getLong(10),
                            cover = c.getString(11) ?: "",
                            kind = c.getString(12) ?: "",
                            chapter = c.getInt(13),
                            progress = c.getDouble(14)
                        )
                    )
                }
            }
        } catch (e: Throwable) {
        }
        out
    }

    suspend fun patchLocalProgress(localId: String, chapter: Int, progress: Double) = withContext(Dispatchers.IO) {
        val d = db() ?: return@withContext
        val v = ContentValues()
        v.put("chapter", chapter)
        v.put("progress", progress)
        try {
            d.update("LOCAL_BOOK", v, "local_id=?", arrayOf(localId))
        } catch (e: Throwable) {
        }
    }

    suspend fun removeLocal(localId: String) = withContext(Dispatchers.IO) {
        try {
            db()?.delete("LOCAL_BOOK", "local_id=?", arrayOf(localId))
        } catch (e: Throwable) {
        }
    }

    suspend fun clearLocals() = withContext(Dispatchers.IO) {
        try {
            db()?.delete("LOCAL_BOOK", null, null)
        } catch (e: Throwable) {
        }
    }

    private fun localValues(meta: LocalMeta, root: String): ContentValues {
        val v = ContentValues()
        v.put("local_id", meta.id)
        v.put("uri", meta.uri)
        v.put("name", meta.name)
        v.put("size", meta.sizeBytes)
        v.put("title", meta.title)
        v.put("author", meta.author)
        v.put("series", meta.series)
        v.put("publisher", meta.publisher)
        v.put("date", meta.date)
        v.put("profile", meta.profile)
        v.put("pages", meta.pages)
        v.put("kind", meta.kind)
        v.put("cover", meta.cover)
        v.put("root", root)
        v.put("at", System.currentTimeMillis())
        v.put("chapter", meta.chapter)
        v.put("progress", meta.progress)
        return v
    }

    private fun readingOf(c: android.database.Cursor): ReadingRow = ReadingRow(
        clientId = c.getString(0) ?: "",
        bookId = c.getString(1) ?: "",
        startedAt = c.getString(2) ?: "",
        endedAt = c.getString(3) ?: "",
        durationMs = c.getLong(4),
        startPage = c.getInt(5),
        lastPage = c.getInt(6),
        pagesRead = c.getInt(7),
        state = c.getString(8) ?: "",
        retry = c.getInt(9),
        at = c.getLong(10)
    )
}

val offlineDb = OfflineDb()
