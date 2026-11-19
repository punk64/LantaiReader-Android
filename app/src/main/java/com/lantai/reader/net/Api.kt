package com.lantai.reader.net

import com.lantai.reader.common.J
import com.lantai.reader.common.t
import com.lantai.reader.model.AnnotationMapper
import com.lantai.reader.model.BookAnnotation
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.BookMapper
import com.lantai.reader.model.BookPage
import com.lantai.reader.model.BookQuery
import com.lantai.reader.model.ChapterMapper
import com.lantai.reader.model.ChapterPage
import com.lantai.reader.model.CollectionCard
import com.lantai.reader.model.HomePayload
import com.lantai.reader.model.LibraryRef
import com.lantai.reader.model.ReadListMapper
import com.lantai.reader.model.ReadListRef
import com.lantai.reader.model.ReadListSummary
import com.lantai.reader.model.ReadingStats
import com.lantai.reader.model.SeriesCard
import com.lantai.reader.model.SeriesItem
import com.lantai.reader.model.SeriesMapper
import com.lantai.reader.model.SeriesPage
import com.lantai.reader.model.StatsMapper
import com.lantai.reader.model.UserProfile
import com.lantai.reader.model.UserProfileMapper
import org.json.JSONArray
import org.json.JSONObject

class ApiError(val status: Int, message: String) : Exception(message)

class LantaiApi(private val session: LtSession) {

    suspend fun home(limit: Int): HomePayload {
        val res = session.fetch("/api/lantai/v1/app/home?limit=$limit")
        expect(res, t("首页"))
        val body = J.parse(session.decodeText(res.bytes))
        val counts = J.obj(body, "counts")
        val counter = HashMap<String, Int>()
        for (k in listOf("continueReading", "added", "published", "recentRead", "newSeries", "updatedSeries")) {
            counter[k] = J.num(counts, k)
        }
        val libraries = J.objList(body, "libraries").map { LibraryRef(J.str(it, "id"), J.str(it, "name")) }
        return HomePayload(
            counts = counter,
            libraries = libraries,
            continueReading = BookMapper.listOf(J.objList(body, "continueReading")),
            added = BookMapper.listOf(J.objList(body, "added")),
            published = BookMapper.listOf(J.objList(body, "published")),
            recentRead = BookMapper.listOf(J.objList(body, "recentRead")),
            newSeries = BookMapper.seriesOf(J.objList(body, "newSeries")),
            updatedSeries = BookMapper.seriesOf(J.objList(body, "updatedSeries")),
            generatedAt = J.str(body, "generatedAt")
        )
    }

    suspend fun books(query: BookQuery): BookPage {
        val body = JSONObject()
        val conditions = JSONArray()
        if (query.profile.isNotEmpty()) {
            conditions.put(JSONObject().put("mediaProfile", JSONObject().put("operator", "is").put("value", query.profile)))
        }
        if (query.readState.isNotEmpty()) {
            conditions.put(JSONObject().put("readStatus", JSONObject().put("operator", "is").put("value", query.readState)))
        }
        if (query.libraryId.isNotEmpty()) {
            conditions.put(JSONObject().put("libraryId", JSONObject().put("operator", "is").put("value", query.libraryId)))
        }
        if (query.readListId.isNotEmpty()) {
            conditions.put(JSONObject().put("readListId", JSONObject().put("operator", "is").put("value", query.readListId)))
        }
        if (query.serial.isNotEmpty() || query.letter.isNotEmpty()) {
            val filter = JSONObject()
            if (query.serial.isNotEmpty()) filter.put("seriesStatus", query.serial)
            if (query.letter.isNotEmpty()) filter.put("firstLetter", query.letter)
            conditions.put(JSONObject().put("bookSeriesFilter", filter))
        }
        if (query.year.isNotEmpty()) {
            val year = query.year.toIntOrNull() ?: 0
            var label = year.toString()
            while (label.length < 4) label = "0$label"
            conditions.put(
                JSONObject().put(
                    "releaseDate",
                    JSONObject().put("operator", "after").put("dateTime", "$label-01-01T00:00:00Z")
                )
            )
            conditions.put(
                JSONObject().put(
                    "releaseDate",
                    JSONObject().put("operator", "before").put("dateTime", "$label-12-31T23:59:59Z")
                )
            )
        }
        if (conditions.length() > 0) {
            body.put("condition", JSONObject().put("allOf", conditions))
        }
        if (query.text.isNotEmpty()) {
            body.put("fullTextSearch", query.text)
        }
        var path = "/api/v1/books/list?page=${query.page}&size=${query.size}&readerView=true"
        if (query.sort.isNotEmpty()) path += "&sort=${query.sort}"
        val res = session.postJson(path, body.toString())
        expect(res, t("书库"))
        return BookMapper.pageOf(J.parse(session.decodeText(res.bytes)))
    }

    suspend fun facetYears(): List<String> {
        val res = session.fetch("/api/lantai/v1/catalog/facets")
        expect(res, t("年份分布"))
        val body = J.parse(session.decodeText(res.bytes))
        return J.objList(body, "years").mapNotNull {
            val v = J.str(it, "value")
            if (v.length == 4) v else null
        }
    }

    suspend fun book(id: String): BookItem {
        val res = session.fetch("/api/v1/books/$id")
        expect(res, t("书籍详情"))
        return BookMapper.fromDto(J.parse(session.decodeText(res.bytes)))
    }

    suspend fun booksMissing(ids: List<String>): List<String> {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        val res = session.postJson("/api/lantai/v1/app/books/existence", JSONObject().put("ids", arr).toString())
        expect(res, t("书籍检测"))
        return J.strList(J.parse(session.decodeText(res.bytes)), "missing")
    }

    suspend fun series(id: String): SeriesItem {
        val res = session.fetch("/api/v1/series/$id")
        expect(res, t("系列详情"))
        return SeriesMapper.fromDto(J.parse(session.decodeText(res.bytes)))
    }

    suspend fun seriesPage(page: Int, size: Int, sort: String): SeriesPage {
        val res = session.postJson(
            "/api/v1/series/list?page=$page&size=$size&sort=$sort&readerView=true", "{}"
        )
        expect(res, t("系列列表"))
        val body = J.parse(session.decodeText(res.bytes))
        val out = J.objList(body, "content").map {
            val metadata = J.obj(it, "metadata")
            val title = J.str(metadata, "title")
            SeriesCard(
                id = J.str(it, "id"),
                title = if (title.isNotEmpty()) title else J.str(it, "name"),
                books = J.num(it, "booksCount"),
                read = J.num(it, "booksReadCount"),
                reading = J.num(it, "booksInProgressCount"),
                at = J.str(it, "lastModified"),
                author = firstAuthor(it)
            )
        }
        return SeriesPage(out, J.num(body, "totalElements"))
    }

    suspend fun seriesCover(id: String): ByteArray {
        val res = session.fetch("/api/v1/series/$id/thumbnail")
        if (res.status != 200) throw ApiError(res.status, t("封面 HTTP ${res.status}"))
        return res.bytes
    }

    suspend fun seriesStats(): List<SeriesCard> {
        val res = session.postJson(
            "/api/v1/series/list?page=0&size=500&sort=lastModified,desc&readerView=true", "{}"
        )
        expect(res, t("系列统计"))
        val body = J.parse(session.decodeText(res.bytes))
        return J.objList(body, "content").map {
            SeriesCard(
                id = J.str(it, "id"),
                title = "",
                books = J.num(it, "booksCount"),
                read = J.num(it, "booksReadCount"),
                reading = J.num(it, "booksInProgressCount"),
                at = "",
                author = ""
            )
        }
    }

    suspend fun seriesBooks(id: String): List<BookItem> {
        val res = session.fetch("/api/v1/series/$id/books?page=0&size=2000&sort=metadata.numberSort,asc")
        expect(res, t("系列分册"))
        val body = J.parse(session.decodeText(res.bytes))
        return J.objList(body, "content").map { BookMapper.fromDto(it) }
    }

    suspend fun collections(): List<CollectionCard> {
        val res = session.fetch("/api/v1/collections?size=100&sort=name,asc")
        expect(res, t("系列合集"))
        val body = J.parse(session.decodeText(res.bytes))
        return J.objList(body, "content").map {
            CollectionCard(
                id = J.str(it, "id"),
                name = J.str(it, "name"),
                seriesCount = J.strList(it, "seriesIds").size,
                seriesIds = J.strList(it, "seriesIds"),
                at = J.str(it, "lastModifiedDate")
            )
        }
    }

    suspend fun collectionFirstSeries(id: String): String {
        val res = session.fetch("/api/v1/collections/$id/series?page=0&size=1")
        if (res.status != 200) return ""
        val body = J.parse(session.decodeText(res.bytes))
        return J.objList(body, "content").firstOrNull()?.let { J.str(it, "id") } ?: ""
    }

    suspend fun createCollection(name: String, seriesIds: List<String>) {
        val body = JSONObject().put("name", name).put("ordered", false)
            .put("seriesIds", arr(seriesIds)).toString()
        val status = session.sendJson("POST", "/api/v1/collections", body)
        if (status < 200 || status >= 300) throw ApiError(status, t("新建系列合集 HTTP $status"))
    }

    suspend fun updateCollectionSeries(id: String, seriesIds: List<String>) {
        val body = JSONObject().put("seriesIds", arr(seriesIds)).toString()
        val status = session.sendJson("PATCH", "/api/v1/collections/$id", body)
        if (status < 200 || status >= 300) throw ApiError(status, t("加入系列合集 HTTP $status"))
    }

    suspend fun createReadList(name: String, bookIds: List<String>) {
        val body = JSONObject().put("name", name).put("summary", "").put("ordered", true)
            .put("bookIds", arr(bookIds)).toString()
        val status = session.sendJson("POST", "/api/v1/readlists", body)
        if (status < 200 || status >= 300) throw ApiError(status, t("新建书单 HTTP $status"))
    }

    suspend fun updateReadListBooks(id: String, bookIds: List<String>) {
        val body = JSONObject().put("bookIds", arr(bookIds)).toString()
        val status = session.sendJson("PATCH", "/api/v1/readlists/$id", body)
        if (status < 200 || status >= 300) throw ApiError(status, t("加入书单 HTTP $status"))
    }

    suspend fun chapters(id: String): ChapterPage {
        val res = session.fetch("/api/lantai/v1/read/$id/chapters?outline=true")
        expect(res, t("目录"))
        return ChapterMapper.fromDto(J.parse(session.decodeText(res.bytes)))
    }

    suspend fun outline(id: String, from: Int = 1, window: Int = 500): ChapterPage {
        val res = session.fetch("/api/lantai/v1/read/$id/outline?from=$from&window=$window")
        expect(res, t("目录"))
        return ChapterMapper.fromDto(J.parse(session.decodeText(res.bytes)))
    }

    suspend fun capabilities(): List<String> {
        val res = session.fetch("/api/lantai/v1/reading/capabilities")
        expect(res, t("服务能力"))
        val body = J.parse(session.decodeText(res.bytes))
        return listOf(
            if (J.bool(body, "heartbeat")) t("阅读会话心跳 · 支持") else t("阅读会话心跳 · 不支持"),
            if (J.bool(body, "offlineSync")) t("离线会话补报 · 支持") else t("离线会话补报 · 不支持"),
            t("阅读接口版本 · ${J.num(body, "apiVersion")}")
        )
    }

    suspend fun readingStats(): ReadingStats {
        val res = session.fetch("/api/lantai/v1/reading/stats")
        expect(res, t("阅读统计"))
        return StatsMapper.fromDto(J.parse(session.decodeText(res.bytes)))
    }

    suspend fun readlistsOfBook(id: String): List<ReadListRef> {
        val res = session.fetch("/api/v1/readlists?unpaged=true&bookId=${enc(id)}")
        if (res.status != 200) return emptyList()
        return ReadListMapper.ofBook(J.parse(session.decodeText(res.bytes)), id)
    }

    suspend fun readlists(): List<ReadListSummary> {
        val res = session.fetch("/api/v1/readlists?size=100&sort=lastModifiedDate,desc")
        expect(res, t("书单"))
        return ReadListMapper.summaries(J.parse(session.decodeText(res.bytes)))
    }

    suspend fun annotations(bookId: String): List<BookAnnotation> {
        val res = session.fetch("/api/lantai/v1/annotations?bookId=${enc(bookId)}")
        if (res.status != 200) throw ApiError(res.status, t("批注 HTTP ${res.status}"))
        val arr = J.parseArray(session.decodeText(res.bytes))
        val list = ArrayList<JSONObject>(arr.length())
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(it) }
        return AnnotationMapper.listOf(list)
    }

    suspend fun wantToReadState(bookId: String): Boolean {
        val res = session.fetch("/api/lantai/v1/want-to-read/book/$bookId")
        if (res.status != 200) return false
        return J.bool(J.parse(session.decodeText(res.bytes)), "wanted")
    }

    suspend fun setWantToRead(bookId: String, wanted: Boolean) {
        val status = session.sendJson(
            if (wanted) "POST" else "DELETE",
            "/api/lantai/v1/want-to-read/book/$bookId",
            if (wanted) "{}" else null
        )
        if (status < 200 || status >= 300) throw ApiError(status, t("待读 HTTP $status"))
    }

    suspend fun wantToRead(page: Int, size: Int): BookPage {
        val res = session.fetch("/api/lantai/v1/want-to-read?page=$page&size=$size")
        expect(res, t("待读"))
        val body = J.parse(session.decodeText(res.bytes))
        return BookPage(
            items = J.objList(body, "items").map { BookMapper.fromDto(it) },
            total = J.num(body, "total"),
            page = J.num(body, "page"),
            size = J.num(body, "size"),
            pages = 0
        )
    }

    suspend fun markRead(bookId: String, completed: Boolean) {
        val status = if (completed) {
            session.sendJson("PATCH", "/api/v1/books/$bookId/read-progress", "{\"completed\":true}")
        } else {
            session.sendJson("DELETE", "/api/v1/books/$bookId/read-progress", null)
        }
        if (status < 200 || status >= 300) {
            throw ApiError(status, if (completed) t("标记已读 HTTP $status") else t("标记未读 HTTP $status"))
        }
    }

    suspend fun page(id: String, n: Int, convert: String, width: Int = 0): ByteArray {
        val wide = if (width > 0) "&w=$width" else ""
        val res = session.fetch("/api/v1/books/$id/pages/$n?convert=$convert$wide", "image/jpeg", 64000)
        if (res.status != 200) throw ApiError(res.status, t("页图 HTTP ${res.status} · ${res.bytes.size} B"))
        val type = res.contentType.lowercase()
        if (type.isNotEmpty() && !type.startsWith("image/")) {
            throw ApiError(res.status, t("页图格式异常 ${res.contentType}"))
        }
        if (res.bytes.isEmpty()) throw ApiError(res.status, t("页图为空"))
        return res.bytes
    }

    suspend fun savePage(bookId: String, page: Int) {
        if (page <= 0) return
        val status = session.sendJson(
            "PATCH", "/api/v1/books/$bookId/read-progress", JSONObject().put("page", page).toString()
        )
        if (status >= 400) throw ApiError(status, t("记录进度 HTTP $status"))
    }

    suspend fun pageThumb(id: String, n: Int): ByteArray {
        val res = session.fetch("/api/v1/books/$id/pages/$n/thumbnail", "image/jpeg", 64000)
        if (res.status != 200) throw ApiError(res.status, t("缩略图 HTTP ${res.status}"))
        if (res.bytes.isEmpty()) throw ApiError(res.status, t("缩略图为空"))
        return res.bytes
    }

    suspend fun cover(id: String, width: Int): ByteArray {
        val res = session.fetch("/api/lantai/v1/app/cover/$id?w=$width&fmt=jpeg", "*/*", 30000)
        if (res.status != 200) throw ApiError(res.status, t("封面 HTTP ${res.status}"))
        return res.bytes
    }

    suspend fun me(): String {
        val res = session.fetch("/api/v2/users/me")
        if (res.status != 200) return ""
        val body = J.parse(session.decodeText(res.bytes))
        val name = J.str(body, "username")
        if (name.isNotEmpty()) return name
        val mail = J.str(body, "email")
        return if (mail.isNotEmpty()) mail else t("已连接")
    }

    suspend fun userProfile(): UserProfile {
        val res = session.fetch("/api/lantai/v1/user-profile/me")
        if (res.status != 200) throw ApiError(res.status, t("用户资料 HTTP ${res.status}"))
        return UserProfileMapper.of(J.parse(session.decodeText(res.bytes)))
    }

    suspend fun userAvatar(userId: String): ByteArray {
        val res = session.fetch("/api/lantai/v1/user-profile/$userId/avatar", "image/png", 30000)
        if (res.status != 200) throw ApiError(res.status, t("头像 HTTP ${res.status}"))
        val type = res.contentType.lowercase()
        if (type.isNotEmpty() && !type.startsWith("image/")) {
            throw ApiError(res.status, t("头像格式异常 ${res.contentType}"))
        }
        return res.bytes
    }

    private fun firstAuthor(item: JSONObject): String {
        val agg = J.obj(item, "booksMetadata")
        for (o in J.objList(agg, "authors")) {
            val name = J.str(o, "name")
            if (name.isNotEmpty()) return name
        }
        return ""
    }

    private fun arr(items: List<String>): JSONArray {
        val a = JSONArray()
        for (it in items) a.put(it)
        return a
    }

    private fun enc(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")

    private fun expect(res: SessionResponse, label: String) {
        if (res.status == 200) return
        if (res.status == 401) throw ApiError(401, t("会话已过期"))
        val text = session.decodeText(res.bytes)
        var message = "$label HTTP ${res.status}"
        val body = J.parse(text)
        val detail = J.str(body, "message")
        if (detail.isNotEmpty()) {
            message = detail
        } else if (text.isNotEmpty() && text.length < 200) {
            message = text
        }
        throw ApiError(res.status, message)
    }
}
