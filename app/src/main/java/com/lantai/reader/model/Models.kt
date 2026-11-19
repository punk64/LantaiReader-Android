package com.lantai.reader.model

import com.lantai.reader.common.J
import com.lantai.reader.common.t
import org.json.JSONObject

const val PROFILE_EPUB = "EPUB"
const val PROFILE_DIVINA = "DIVINA"
const val PROFILE_PDF = "PDF"
const val PROFILE_IMAGE = "IMAGE"
const val MEDIA_ERROR = "ERROR"

const val STATE_UNREAD = "UNREAD"
const val STATE_READING = "IN_PROGRESS"
const val STATE_READ = "READ"

data class BookItem(
    val id: String = "",
    val title: String = "",
    val seriesId: String = "",
    val seriesTitle: String = "",
    val number: String = "",
    val volume: String = "",
    val profile: String = "",
    val pages: Int = 0,
    val size: String = "",
    val sizeBytes: Long = 0L,
    val authors: String = "",
    val summary: String = "",
    val tags: List<String> = emptyList(),
    val created: String = "",
    val lastModified: String = "",
    val readPage: Int = 0,
    val readCompleted: Boolean = false,
    val readDate: String = "",
    val readState: String = STATE_UNREAD,
    val fileName: String = "",
    val filePath: String = "",
    val fileHash: String = "",
    val fileModified: String = "",
    val mediaType: String = "",
    val mediaStatus: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val language: String = "",
    val genres: List<String> = emptyList(),
    val releaseDate: String = "",
    val metaUpdated: String = "",
    val numberSort: Int = 0,
    val notableCount: Int = 0,
    val lockedCount: Int = 0,
    val linkCount: Int = 0,
    val oneshot: Boolean = false
)

data class ChapterItem(val n: Int = 0, val title: String = "", val depth: Int = 0, val chars: Int = 0)

data class ChapterPage(
    val chapters: Int = 0,
    val total: Int = 0,
    val from: Int = 0,
    val to: Int = 0,
    val items: List<ChapterItem> = emptyList()
)

data class DayStat(val date: String = "", val durationMs: Long = 0L, val pagesRead: Int = 0, val sessionCount: Int = 0)

data class BookStat(
    val bookId: String = "",
    val durationMs: Long = 0L,
    val pagesRead: Int = 0,
    val sessionCount: Int = 0,
    val lastReadAt: String = ""
)

data class ReadingStats(
    val from: String = "",
    val to: String = "",
    val totalDurationMs: Long = 0L,
    val totalPagesRead: Int = 0,
    val sessionCount: Int = 0,
    val bookCount: Int = 0,
    val books: List<BookStat> = emptyList(),
    val days: List<DayStat> = emptyList()
)

data class ReadListRef(val id: String = "", val name: String = "")

data class ReadListSummary(
    val id: String = "",
    val name: String = "",
    val summary: String = "",
    val ordered: Boolean = false,
    val bookCount: Int = 0,
    val bookIds: List<String> = emptyList(),
    val lastModified: String = ""
)

data class BookAnnotation(
    val id: String = "",
    val bookId: String = "",
    val href: String = "",
    val highlight: String = "",
    val color: String = "",
    val marker: String = "",
    val note: String = "",
    val createdDate: String = ""
)

data class SeriesItem(
    val id: String = "",
    val name: String = "",
    val booksCount: Int = 0,
    val readCount: Int = 0,
    val inProgressCount: Int = 0,
    val summary: String = "",
    val publisher: String = "",
    val language: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val totalBookCount: Int = 0,
    val status: String = "",
    val created: String = "",
    val lastModified: String = ""
)

data class SeriesCard(
    val id: String = "",
    val title: String = "",
    val books: Int = 0,
    val read: Int = 0,
    val reading: Int = 0,
    val at: String = "",
    val author: String = ""
)

data class SeriesPage(val items: List<SeriesCard> = emptyList(), val total: Int = 0)

data class CollectionCard(
    val id: String = "",
    val name: String = "",
    val seriesCount: Int = 0,
    val seriesIds: List<String> = emptyList(),
    val at: String = ""
)

data class LibraryRef(val id: String = "", val name: String = "")

data class HomePayload(
    val counts: Map<String, Int> = emptyMap(),
    val libraries: List<LibraryRef> = emptyList(),
    val continueReading: List<BookItem> = emptyList(),
    val added: List<BookItem> = emptyList(),
    val published: List<BookItem> = emptyList(),
    val recentRead: List<BookItem> = emptyList(),
    val newSeries: List<SeriesCard> = emptyList(),
    val updatedSeries: List<SeriesCard> = emptyList(),
    val generatedAt: String = ""
)

data class BookPage(
    val items: List<BookItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 24,
    val pages: Int = 0
)

data class BookQuery(
    val text: String = "",
    val profile: String = "",
    val readState: String = "",
    val libraryId: String = "",
    val readListId: String = "",
    val letter: String = "",
    val serial: String = "",
    val year: String = "",
    val sort: String = "metadata.title,asc",
    val page: Int = 0,
    val size: Int = 24
)

data class UserProfile(
    val userId: String = "",
    val username: String = "",
    val nickname: String = "",
    val hasAvatar: Boolean = false,
    val avatarUpdated: Long = 0L
)

fun profileLabel(profile: String, status: String = ""): String {
    if (status == MEDIA_ERROR) return t("无法解析")
    return when (profile) {
        PROFILE_EPUB -> "EPUB"
        PROFILE_DIVINA -> t("漫画")
        PROFILE_PDF -> "PDF"
        PROFILE_IMAGE -> t("图片")
        else -> if (profile.isNotEmpty()) profile else t("未知")
    }
}

fun isEbook(profile: String): Boolean = profile == PROFILE_EPUB

fun isComic(profile: String): Boolean = profile == PROFILE_DIVINA || profile == PROFILE_IMAGE

object BookMapper {
    fun fromDto(m: JSONObject): BookItem {
        val media = J.obj(m, "media")
        val metadata = J.obj(m, "metadata")
        val progress = J.obj(m, "readProgress")
        val page = J.num(progress, "page")
        val done = J.bool(progress, "completed")
        val title = J.str(metadata, "title")
        return BookItem(
            id = J.str(m, "id"),
            title = if (title.isNotEmpty()) title else J.str(m, "name"),
            seriesId = J.str(m, "seriesId"),
            seriesTitle = J.str(m, "seriesTitle"),
            number = J.str(metadata, "number"),
            volume = J.str(m, "volume"),
            profile = J.str(media, "mediaProfile"),
            pages = J.num(media, "pagesCount"),
            size = J.str(m, "size"),
            sizeBytes = J.lnum(m, "sizeBytes"),
            authors = authorsOf(metadata),
            summary = J.str(metadata, "summary"),
            tags = J.strList(metadata, "tags"),
            created = J.str(m, "created"),
            lastModified = J.str(m, "lastModified"),
            readPage = page,
            readCompleted = done,
            readDate = J.str(progress, "readDate"),
            readState = stateOf(page, done),
            fileName = J.str(m, "name"),
            filePath = J.str(m, "url"),
            fileHash = J.str(m, "fileHash"),
            fileModified = J.str(m, "fileLastModified"),
            mediaType = J.str(media, "mediaType"),
            mediaStatus = J.str(media, "status"),
            publisher = J.str(metadata, "publisher"),
            isbn = J.str(metadata, "isbn"),
            language = J.str(metadata, "language"),
            genres = J.strList(metadata, "genres"),
            releaseDate = J.str(metadata, "releaseDate"),
            metaUpdated = J.str(metadata, "lastModified"),
            numberSort = J.num(metadata, "numberSort"),
            notableCount = J.arr(metadata, "notableEntities").length(),
            lockedCount = lockedOf(metadata),
            linkCount = J.arr(metadata, "links").length(),
            oneshot = J.bool(m, "oneshot")
        )
    }

    fun lockedOf(metadata: JSONObject): Int {
        var count = 0
        val keys = metadata.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.endsWith("Lock") && J.bool(metadata, key)) count++
        }
        return count
    }

    fun fromHomeCard(m: JSONObject): BookItem {
        val page = J.num(m, "page")
        val done = J.bool(m, "completed")
        return BookItem(
            id = J.str(m, "id"),
            title = J.str(m, "title"),
            seriesId = J.str(m, "seriesId"),
            seriesTitle = J.str(m, "seriesTitle"),
            number = J.str(m, "number"),
            profile = J.str(m, "profile"),
            pages = J.num(m, "pages"),
            readPage = page,
            readCompleted = done,
            readDate = J.str(m, "at"),
            readState = stateOf(page, done)
        )
    }

    fun stateOf(page: Int, completed: Boolean): String = when {
        completed -> STATE_READ
        page > 0 -> STATE_READING
        else -> STATE_UNREAD
    }

    fun authorsOf(metadata: JSONObject): String {
        val names = ArrayList<String>()
        for (o in J.objList(metadata, "authors")) {
            val n = J.str(o, "name")
            if (n.isNotEmpty()) names.add(n)
        }
        return names.joinToString(" / ")
    }

    fun listOf(raw: List<JSONObject>): List<BookItem> = raw.map { fromHomeCard(it) }

    fun seriesOf(raw: List<JSONObject>): List<SeriesCard> = raw.map {
        SeriesCard(
            id = J.str(it, "id"),
            title = J.str(it, "title"),
            books = J.num(it, "books"),
            read = J.num(it, "read"),
            reading = J.num(it, "reading"),
            at = J.str(it, "at"),
            author = J.str(it, "author")
        )
    }

    fun pageOf(m: JSONObject): BookPage {
        val items = J.objList(m, "content").map { fromDto(it) }
        if (items.isNotEmpty()) {
            return BookPage(
                items = items,
                total = J.num(m, "totalElements"),
                page = J.num(m, "number"),
                size = J.num(m, "size"),
                pages = J.num(m, "totalPages")
            )
        }
        return BookPage(
            items = J.objList(m, "items").map { fromDto(it) },
            total = J.num(m, "total"),
            page = J.num(m, "page"),
            size = J.num(m, "size"),
            pages = J.num(m, "pages")
        )
    }
}

object ChapterMapper {
    fun fromDto(m: JSONObject): ChapterPage {
        val items = J.objList(m, "outline").map {
            ChapterItem(
                n = J.num(it, "n"),
                title = J.str(it, "title"),
                depth = J.num(it, "depth"),
                chars = J.num(it, "chars")
            )
        }
        return ChapterPage(
            chapters = J.num(m, "chapters"),
            total = J.num(m, "total"),
            from = J.num(m, "from"),
            to = J.num(m, "to"),
            items = items
        )
    }
}

object StatsMapper {
    fun fromDto(m: JSONObject): ReadingStats {
        val books = J.objList(m, "books").map {
            BookStat(
                bookId = J.str(it, "bookId"),
                durationMs = J.lnum(it, "durationMs"),
                pagesRead = J.num(it, "pagesRead"),
                sessionCount = J.num(it, "sessionCount"),
                lastReadAt = J.str(it, "lastReadAt")
            )
        }
        val days = J.objList(m, "days").map {
            DayStat(
                date = J.str(it, "date"),
                durationMs = J.lnum(it, "durationMs"),
                pagesRead = J.num(it, "pagesRead"),
                sessionCount = J.num(it, "sessionCount")
            )
        }
        return ReadingStats(
            from = J.str(m, "from"),
            to = J.str(m, "to"),
            totalDurationMs = J.lnum(m, "totalDurationMs"),
            totalPagesRead = J.num(m, "totalPagesRead"),
            sessionCount = J.num(m, "sessionCount"),
            bookCount = J.num(m, "bookCount"),
            books = books,
            days = days
        )
    }

    fun bookOf(stats: ReadingStats, bookId: String): BookStat? = stats.books.firstOrNull { it.bookId == bookId }
}

object SeriesMapper {
    fun fromDto(m: JSONObject): SeriesItem {
        val metadata = J.obj(m, "metadata")
        val title = J.str(metadata, "title")
        return SeriesItem(
            id = J.str(m, "id"),
            name = if (title.isNotEmpty()) title else J.str(m, "name"),
            booksCount = J.num(m, "booksCount"),
            readCount = J.num(m, "booksReadCount"),
            inProgressCount = J.num(m, "booksInProgressCount"),
            summary = J.str(metadata, "summary"),
            publisher = J.str(metadata, "publisher"),
            language = J.str(metadata, "language"),
            genres = J.strList(metadata, "genres"),
            tags = J.strList(metadata, "tags"),
            totalBookCount = J.num(metadata, "totalBookCount"),
            status = J.str(metadata, "status"),
            created = J.str(m, "created"),
            lastModified = J.str(m, "lastModified")
        )
    }
}

object ReadListMapper {
    fun fromPage(m: JSONObject): List<ReadListRef> = J.objList(m, "content").map {
        ReadListRef(J.str(it, "id"), J.str(it, "name"))
    }

    fun ofBook(m: JSONObject, bookId: String): List<ReadListRef> = J.objList(m, "content").mapNotNull {
        if (J.strList(it, "bookIds").contains(bookId)) ReadListRef(J.str(it, "id"), J.str(it, "name")) else null
    }

    fun summaries(m: JSONObject): List<ReadListSummary> = J.objList(m, "content").map {
        ReadListSummary(
            id = J.str(it, "id"),
            name = J.str(it, "name"),
            summary = J.str(it, "summary"),
            ordered = J.bool(it, "ordered"),
            bookCount = J.arr(it, "bookIds").length(),
            bookIds = J.strList(it, "bookIds"),
            lastModified = J.str(it, "lastModifiedDate")
        )
    }
}

object UserProfileMapper {
    fun of(m: JSONObject): UserProfile {
        val username = J.str(m, "username")
        val nickname = J.str(m, "nickname")
        return UserProfile(
            userId = J.str(m, "userId"),
            username = username,
            nickname = if (nickname.isNotEmpty()) nickname else username,
            hasAvatar = J.bool(m, "hasAvatar"),
            avatarUpdated = J.lnum(m, "avatarUpdated")
        )
    }
}

object AnnotationMapper {
    fun listOf(raw: List<JSONObject>): List<BookAnnotation> = raw.map {
        BookAnnotation(
            id = J.str(it, "id"),
            bookId = J.str(it, "bookId"),
            href = J.str(it, "href"),
            highlight = J.str(it, "textHighlight"),
            color = J.str(it, "color"),
            marker = J.str(it, "marker"),
            note = J.str(it, "note"),
            createdDate = J.str(it, "createdDate")
        )
    }
}
