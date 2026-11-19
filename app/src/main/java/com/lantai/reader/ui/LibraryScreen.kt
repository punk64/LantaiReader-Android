package com.lantai.reader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.LtApp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.common.toast
import com.lantai.reader.data.offlineDb
import com.lantai.reader.local.ComicArchive
import com.lantai.reader.local.LocalBookSource
import com.lantai.reader.local.LocalLibrary
import com.lantai.reader.local.LocalMetas
import com.lantai.reader.local.LocalStore
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.BookQuery
import com.lantai.reader.model.LocalBook
import com.lantai.reader.model.LocalMeta
import com.lantai.reader.model.STATE_READ
import com.lantai.reader.model.STATE_READING
import com.lantai.reader.model.STATE_UNREAD
import com.lantai.reader.model.SeriesCard
import com.lantai.reader.model.Store
import com.lantai.reader.net.SeriesCache
import com.lantai.reader.ui.comp.BookGridCell
import com.lantai.reader.ui.comp.BookRow
import com.lantai.reader.ui.comp.Chip
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.CoverProgress
import com.lantai.reader.ui.comp.ErrorView
import com.lantai.reader.ui.comp.FilterOption
import com.lantai.reader.ui.comp.LibrarySkeleton
import com.lantai.reader.ui.comp.LoadingView
import com.lantai.reader.ui.comp.Mono
import com.lantai.reader.ui.comp.ScreenHead
import com.lantai.reader.ui.comp.SectionHead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SeriesGroup(val id: String, val title: String, val books: List<BookItem>)

private fun formats(): List<FilterOption> = listOf(
    FilterOption(t("全部"), ""),
    FilterOption("EPUB", "EPUB"),
    FilterOption(t("漫画"), "DIVINA"),
    FilterOption("PDF", "PDF")
)

private fun states(): List<FilterOption> = listOf(
    FilterOption(t("全部"), ""),
    FilterOption(t("未读"), STATE_UNREAD),
    FilterOption(t("在读"), STATE_READING),
    FilterOption(t("已读"), STATE_READ)
)

private fun sorts(): List<FilterOption> = listOf(
    FilterOption(t("按更新"), "lastModified,desc"),
    FilterOption(t("按标题"), "metadata.title,asc"),
    FilterOption(t("按添加"), "created,desc")
)

private fun sources(): List<FilterOption> = listOf(
    FilterOption(t("全部"), "all"),
    FilterOption(t("本地"), "local"),
    FilterOption(t("云库"), "cloud")
)

private fun modes(): List<FilterOption> = listOf(
    FilterOption(t("按系列"), "series"),
    FilterOption(t("全部书籍"), "books")
)

private fun letters(): List<FilterOption> {
    val out = ArrayList<FilterOption>()
    out.add(FilterOption(t("全部"), ""))
    for (ch in 'A'..'Z') out.add(FilterOption(ch.toString(), ch.toString()))
    return out
}

private fun serials(): List<FilterOption> = listOf(
    FilterOption(t("全部"), ""),
    FilterOption(t("连载中"), "ONGOING"),
    FilterOption(t("已完结"), "ENDED"),
    FilterOption(t("休刊"), "HIATUS"),
    FilterOption(t("弃坑"), "ABANDONED")
)

private class LibraryCtl(val scope: CoroutineScope) {
    var text by mutableStateOf("")
    var profile by mutableStateOf("")
    var readState by mutableStateOf("")
    var letter by mutableStateOf("")
    var serial by mutableStateOf("")
    var year by mutableStateOf("")
    var years by mutableStateOf<List<FilterOption>>(emptyList())
    var serverYears by mutableStateOf<List<String>>(emptyList())
    var sort by mutableStateOf("metadata.title,asc")
    var mode by mutableStateOf("series")
    var view by mutableStateOf("grid")
    var filterOpen by mutableStateOf(false)
    var searching by mutableStateOf(false)
    var moreOpen by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var jumpText by mutableStateOf("")
    var source by mutableStateOf("all")
    var localCount by mutableStateOf(0)
    var localScanning by mutableStateOf(false)
    var localBooks by mutableStateOf<List<LocalBook>>(emptyList())
    var localMetas by mutableStateOf<List<LocalMeta>>(emptyList())
    var localList by mutableStateOf<List<BookItem>>(emptyList())
    var archiving by mutableStateOf(false)
    var items by mutableStateOf<List<BookItem>>(emptyList())
    var groups by mutableStateOf<List<SeriesGroup>>(emptyList())
    var allGroups by mutableStateOf<List<SeriesGroup>>(emptyList())
    var cards by mutableStateOf<List<SeriesCard>>(emptyList())
    var seriesTotal by mutableStateOf(0)
    var total by mutableStateOf(0)
    var loading by mutableStateOf(true)
    var error by mutableStateOf("")
    var page by mutableStateOf(0)
    var pageSize by mutableStateOf(12)

    private val seriesIndex = HashMap<String, SeriesCard>()
    private val seq = AtomicInteger(0)
    private var seriesTick = 0L
    private var parsing = false
    private var searchJob: Job? = null

    fun plain(): Boolean {
        if (Store.offline) return false
        return mode == "series" && source != "local" && text.isEmpty() && profile.isEmpty() &&
            readState.isEmpty() && letter.isEmpty() && serial.isEmpty() && year.isEmpty()
    }

    private fun seriesSort(): String =
        if (sort == "metadata.title,asc") "metadata.titleSort,asc" else sort

    suspend fun boot() {
        if (Store.librarySource.isNotEmpty()) {
            source = Store.librarySource
            Store.librarySource = ""
        }
        hydrateLocal()
        if (!plain() && !Store.offline) {
            scope.launch { loadYears() }
            scope.launch { loadSeriesStat(false) }
        }
        refresh()
    }

    private fun hydrateLocal() {
        if (Store.localBooks.isNotEmpty()) {
            localBooks = Store.localBooks
            localCount = Store.localBooks.size
            localMetas = Store.localMetas
            scope.launch { reparseLocal() }
        }
        localList = localItems()
    }

    fun onTab() {
        if (Store.mainTab == 1) {
            hydrateLocal()
            if (Store.offline) scope.launch { refresh() }
        }
    }

    fun onProgressVer() {
        if (Store.localMetas.isNotEmpty()) {
            localMetas = Store.localMetas
            localList = localItems()
            if (Store.offline) scope.launch { refresh() }
        }
    }

    private fun resolved(meta: LocalMeta): Boolean =
        meta.author.isNotEmpty() || meta.pages > 0 || meta.cover.isNotEmpty() || meta.series.isNotEmpty()

    private fun findMeta(id: String): LocalMeta? = localMetas.firstOrNull { it.id == id }

    private fun putMeta(meta: LocalMeta) {
        val index = localMetas.indexOfFirst { it.id == meta.id }
        localMetas = if (index >= 0) {
            localMetas.toMutableList().also { it[index] = meta }
        } else {
            localMetas + meta
        }
    }

    private suspend fun reparseLocal() {
        if (parsing || localBooks.isEmpty()) return
        parsing = true
        for (book in localBooks) {
            val current = findMeta(LocalMetas.idOf(book.uri))
            if (current != null && resolved(current)) continue
            try {
                val meta = LocalMetas.enrich(book)
                val kept = findMeta(meta.id)
                if (kept != null) {
                    meta.chapter = kept.chapter
                    meta.progress = kept.progress
                }
                putMeta(meta)
                Store.localMetas = localMetas
                LocalStore.save(meta, Store.localRoot)
            } catch (e: Throwable) {
            }
        }
        parsing = false
        years = buildYearOptions()
        localList = localItems()
    }

    private suspend fun loadSeriesStat(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - seriesTick < 20000) return
        seriesTick = now
        try {
            val list = Store.api.seriesStats()
            seriesIndex.clear()
            for (card in list) seriesIndex[card.id] = card
        } catch (e: Throwable) {
            seriesIndex.clear()
        }
    }

    fun seriesBooksOf(id: String): Int = seriesIndex[id]?.books ?: 0

    fun seriesReadOf(id: String): Int = seriesIndex[id]?.read ?: 0

    private fun seriesReadingOf(id: String): Int = seriesIndex[id]?.reading ?: 0

    fun groupTotal(group: SeriesGroup): Int {
        val total = seriesBooksOf(group.id)
        return if (total > 0) total else group.books.size
    }

    fun groupRead(group: SeriesGroup): Int {
        if (seriesBooksOf(group.id) > 0) return seriesReadOf(group.id)
        var read = 0
        for (book in group.books) {
            val page = ProgressMarks.pageOf(book.id, book.readPage)
            if (ProgressMarks.doneOf(book.id, book.readCompleted, page, book.pages)) read++
        }
        return read
    }

    fun groupReading(group: SeriesGroup): Int {
        if (seriesBooksOf(group.id) > 0) return seriesReadingOf(group.id)
        var reading = 0
        for (book in group.books) {
            val page = ProgressMarks.pageOf(book.id, book.readPage)
            if (!ProgressMarks.doneOf(book.id, book.readCompleted, page, book.pages) && page > 0) reading++
        }
        return reading
    }

    fun onFocusTick() {
        source = "all"
        searching = true
        moreOpen = false
    }

    private suspend fun loadYears() {
        serverYears = try {
            Store.api.facetYears()
        } catch (e: Throwable) {
            emptyList()
        }
        years = buildYearOptions()
    }

    private fun buildYearOptions(): List<FilterOption> {
        val values = ArrayList<String>()
        values.addAll(serverYears)
        for (meta in localMetas) {
            if (meta.date.length >= 4) values.add(meta.date.substring(0, 4))
        }
        values.sortWith { a, b -> when {
            a < b -> 1
            a > b -> -1
            else -> 0
        } }
        val out = ArrayList<FilterOption>()
        out.add(FilterOption(t("全部"), ""))
        val seen = HashSet<String>()
        for (value in values) {
            if (value.length != 4 || seen.contains(value)) continue
            seen.add(value)
            out.add(FilterOption(value, value))
        }
        return out
    }

    private fun buildQuery(page: Int): BookQuery = BookQuery(
        text = text.trim(),
        profile = profile,
        readState = readState,
        letter = letter,
        serial = serial,
        year = year,
        sort = sort,
        page = page,
        size = pageSize
    )

    suspend fun refresh() {
        val current = seq.incrementAndGet()
        error = ""
        if (Store.offline) {
            refreshOffline()
            return
        }
        if (plain()) {
            if (serverYears.isEmpty()) scope.launch { loadYears() }
            loadCards(current)
            return
        }
        scope.launch { loadSeriesStat(false) }
        loading = true
        try {
            if (mode == "series" && source != "local") {
                val all = loadAll(current)
                if (current != seq.get()) return
                allGroups = buildGroups(all)
                localList = localItems()
                total = all.size
                groups = sliceGroups()
                loading = false
                return
            }
            localList = localItems()
            if (source == "local") {
                val localPages = max(1, ceil(localList.size.toFloat() / pageSize).toInt())
                page = max(0, min(page, localPages - 1))
                items = emptyList()
                total = 0
                groups = buildGroups(localList)
                loading = false
                return
            }
            val size = pageSize
            val start = page * size
            val inline = max(0, min(size, localList.size - start))
            val need = size - inline
            val cloudStart = max(0, start - localList.size)
            val cloudPage = cloudStart / size
            val shift = cloudStart % size
            val query = buildQuery(cloudPage)
            val result = Store.api.books(query.copy(size = if (need > 0) size + shift else 1))
            if (current != seq.get()) return
            total = result.total
            items = if (need > 0) result.items.drop(shift).take(need) else emptyList()
            val pages = max(1, ceil((localList.size + total).toFloat() / size).toInt())
            if (page > pages - 1) {
                page = pages - 1
                refresh()
                return
            }
            groups = buildGroups(items)
            loading = false
        } catch (e: Throwable) {
            if (current != seq.get()) return
            loading = false
            error = e.message ?: ""
        } finally {
            if (current == seq.get()) refreshing = false
        }
    }

    private fun offlineAsBook(row: com.lantai.reader.data.OfflineBookRow): BookItem = BookItem(
        id = row.bookId,
        title = row.title,
        profile = row.profile,
        sizeBytes = row.bytes,
        size = F.bytes(row.bytes)
    )

    private fun matchCloud(book: BookItem): Boolean {
        val key = text.trim().lowercase()
        if (key.isNotEmpty() && !book.title.lowercase().contains(key)) return false
        if (profile.isNotEmpty() && book.profile != profile) return false
        return true
    }

    private suspend fun refreshOffline() {
        loading = true
        if (mode != "books") {
            mode = "books"
            page = 0
        }
        try {
            val rows = withContext(Dispatchers.IO) { offlineDb.books() }
            val cloud = ArrayList<BookItem>()
            for (row in rows) {
                val book = offlineAsBook(row)
                if (matchCloud(book)) cloud.add(book)
            }
            var list: List<BookItem> = if (source == "cloud") emptyList() else localItems()
            if (source != "local") list = list + cloud
            localList = list
            items = emptyList()
            total = 0
            groups = buildGroups(list)
            val pages = max(1, ceil(list.size.toFloat() / pageSize).toInt())
            if (page > pages - 1) page = pages - 1
            loading = false
            error = ""
        } catch (e: Throwable) {
            loading = false
            error = e.message ?: ""
        } finally {
            refreshing = false
        }
    }

    private suspend fun loadCards(current: Int) {
        val key = SeriesCache.keyOf(page, pageSize, seriesSort())
        val hit = SeriesCache.get(key)
        if (hit != null) {
            cards = hit.items
            seriesTotal = hit.total
            loading = false
            refreshing = false
            prefetchNext()
            return
        }
        if (cards.isEmpty()) loading = true
        try {
            val result = Store.api.seriesPage(page, pageSize, seriesSort())
            if (current != seq.get()) return
            SeriesCache.put(key, result)
            cards = result.items
            seriesTotal = result.total
            loading = false
            prefetchNext()
        } catch (e: Throwable) {
            if (current != seq.get()) return
            loading = false
            error = e.message ?: ""
        } finally {
            if (current == seq.get()) refreshing = false
        }
    }

    private fun prefetchNext() {
        val next = page + 1
        if (next >= pageCount()) return
        val key = SeriesCache.keyOf(next, pageSize, seriesSort())
        if (SeriesCache.get(key) != null) return
        scope.launch {
            try {
                val result = Store.api.seriesPage(next, pageSize, seriesSort())
                SeriesCache.put(key, result)
            } catch (e: Throwable) {
            }
        }
    }

    private suspend fun loadAll(current: Int): List<BookItem> {
        val out = ArrayList<BookItem>()
        val step = 500
        for (at in 0 until 20) {
            val result = Store.api.books(buildQuery(at).copy(size = step))
            if (current != seq.get()) return out
            out.addAll(result.items)
            if (result.items.size < step || out.size >= result.total) break
        }
        return out
    }

    private fun sliceGroups(): List<SeriesGroup> {
        val start = page * pageSize
        return allGroups.drop(start).take(pageSize)
    }

    fun onPullRefresh() {
        page = 0
        jumpText = ""
        refreshing = true
        scope.launch {
            if (!Store.offline) {
                loadYears()
                if (!plain()) loadSeriesStat(true)
            }
            refresh()
        }
    }

    private fun buildGroups(list: List<BookItem>): List<SeriesGroup> {
        val order = ArrayList<String>()
        val buckets = HashMap<String, MutableList<BookItem>>()
        for (book in list) {
            val key = if (book.seriesTitle.isNotEmpty()) book.seriesTitle else t("未归入系列")
            if (!buckets.containsKey(key)) {
                buckets[key] = ArrayList()
                order.add(key)
            }
            buckets[key]?.add(book)
        }
        val out = ArrayList<SeriesGroup>()
        for (key in order) {
            val bucket = buckets[key] ?: continue
            val head = bucket.first()
            out.add(SeriesGroup(head.seriesId, key, bucket))
        }
        return out
    }

    fun pageCount(): Int {
        if (plain()) return max(1, ceil(seriesTotal.toFloat() / pageSize).toInt())
        if (mode == "series" && source != "local") {
            return max(1, ceil(allGroups.size.toFloat() / pageSize).toInt())
        }
        if (source == "local") return max(1, ceil(localList.size.toFloat() / pageSize).toInt())
        return max(1, ceil((localList.size + total).toFloat() / pageSize).toInt())
    }

    fun turn(delta: Int) {
        val next = page + delta
        if (next < 0 || next >= pageCount()) return
        goto(next)
    }

    fun goto(target: Int) {
        val next = max(0, min(pageCount() - 1, target))
        jumpText = ""
        if (next == page) return
        page = next
        if (plain()) {
            scope.launch { refresh() }
            return
        }
        if (mode == "series" && source != "local") {
            groups = sliceGroups()
            return
        }
        scope.launch { refresh() }
    }

    fun jumpTo() {
        val raw = jumpText.trim()
        if (raw.isEmpty()) return
        val target = raw.toIntOrNull()
        if (target != null) goto(target - 1)
        jumpText = ""
    }

    fun applyPageSize(size: Int) {
        jumpText = ""
        if (size == pageSize) return
        pageSize = size
        page = 0
        if (plain()) {
            scope.launch { refresh() }
            return
        }
        if (mode == "series" && source != "local") {
            groups = sliceGroups()
            return
        }
        scope.launch { refresh() }
    }

    fun pgNums(): List<Int> {
        val pages = pageCount()
        val cur = page + 1
        val out = ArrayList<Int>()
        if (pages <= 7) {
            for (i in 1..pages) out.add(i)
            return out
        }
        out.add(1)
        val from = max(2, cur - 1)
        val to = min(pages - 1, cur + 1)
        if (from > 2) out.add(0)
        for (i in from..to) out.add(i)
        if (to < pages - 1) out.add(0)
        out.add(pages)
        return out
    }

    fun onSearchInput(value: String) {
        text = value
        searchJob?.cancel()
        if (Store.offline) {
            searchJob = scope.launch {
                delay(220)
                page = 0
                refresh()
            }
            return
        }
        if (source == "local") {
            page = 0
            localList = localItems()
            return
        }
        searchJob = scope.launch {
            delay(400)
            page = 0
            refresh()
        }
    }

    fun clearSearch() {
        text = ""
        page = 0
        if (Store.offline || source != "local") {
            scope.launch { refresh() }
        } else {
            localList = localItems()
        }
    }

    fun openDetail(book: BookItem) {
        Store.selected = book
        Nav.push("book")
    }

    fun openSeries(group: SeriesGroup) {
        if (group.id.isEmpty()) return
        Store.selectedSeriesId = group.id
        Nav.push("series")
    }

    fun openSeriesId(id: String) {
        if (id.isEmpty()) return
        Store.selectedSeriesId = id
        Nav.push("series")
    }

    fun apply(kind: String, value: String) {
        if (Store.offline) {
            when (kind) {
                "source" -> source = value
                "format" -> profile = value
                "state" -> readState = value
                "letter" -> letter = value
                "serial" -> serial = value
                "year" -> year = value
                "sort" -> sort = value
                else -> return
            }
            page = 0
            scope.launch { refresh() }
            return
        }
        if (kind == "mode") {
            mode = value
            page = 0
            if (source != "local") scope.launch { refresh() }
            return
        }
        if (kind == "source") {
            source = value
            page = 0
            if (value == "local") {
                localList = localItems()
                loading = false
                error = ""
                return
            }
            scope.launch { refresh() }
            return
        }
        when (kind) {
            "format" -> profile = value
            "state" -> readState = value
            "letter" -> letter = value
            "serial" -> serial = value
            "year" -> year = value
            "sort" -> sort = value
        }
        page = 0
        scope.launch { refresh() }
    }

    fun resetFilters() {
        text = ""
        profile = ""
        readState = ""
        letter = ""
        serial = ""
        year = ""
        sort = "metadata.title,asc"
        mode = "series"
        source = "all"
        filterOpen = false
        page = 0
        jumpText = ""
        scope.launch { refresh() }
    }

    private fun adoptLocal(books: List<LocalBook>) {
        localBooks = books
        localCount = books.size
        Store.localBooks = books
        Store.localMissing = emptyList()
        val seeds = ArrayList<LocalMeta>()
        for (book in books) {
            val seed = LocalMetas.seed(book)
            val kept = findMeta(seed.id)
            if (kept != null) {
                seed.chapter = kept.chapter
                seed.progress = kept.progress
            }
            seeds.add(seed)
        }
        localMetas = seeds
        Store.localMetas = seeds
        scope.launch {
            LocalStore.persist(seeds, Store.localRoot)
            parseLocal(books)
        }
    }

    private suspend fun parseLocal(books: List<LocalBook>) {
        for (book in books) {
            try {
                val meta = LocalMetas.enrich(book)
                val kept = findMeta(meta.id)
                if (kept != null) {
                    meta.chapter = kept.chapter
                    meta.progress = kept.progress
                }
                putMeta(meta)
                Store.localMetas = localMetas
                LocalStore.save(meta, Store.localRoot)
            } catch (e: Throwable) {
            }
        }
        years = buildYearOptions()
        localList = localItems()
    }

    suspend fun onFolderPicked(uri: Uri) {
        localScanning = true
        try {
            val ctx = LtApp.instance
            LocalLibrary.persist(ctx, uri)
            val scanned = withContext(Dispatchers.IO) { LocalLibrary.scanTree(ctx, uri) }
            val books = withContext(Dispatchers.IO) { LocalLibrary.adopt(ctx, scanned) }
            Store.localRoot = uri.toString()
            LocalStore.persistRoot(uri.toString())
            Store.localRootStale = false
            adoptLocal(books)
            source = "local"
            toast(t("扫描到 ${books.size} 本"))
        } catch (e: Throwable) {
            toast(t("扫描失败：${e.message ?: ""}"))
        } finally {
            localScanning = false
        }
    }

    suspend fun onFilesPicked(uris: List<Uri>) {
        localScanning = true
        try {
            val ctx = LtApp.instance
            val books = withContext(Dispatchers.IO) { LocalLibrary.importPicked(ctx, uris) }
            if (books.isEmpty()) {
                toast(t("所选文件中没有可识别的书籍格式"))
                return
            }
            Store.localRoot = "picked"
            LocalStore.persistRoot("picked")
            Store.localRootStale = false
            adoptLocal(books)
            source = "local"
            toast(t("已选 ${books.size} 本"))
        } catch (e: Throwable) {
            toast(t("扫描失败：${e.message ?: ""}"))
        } finally {
            localScanning = false
        }
    }

    suspend fun openLocal(book: LocalBook) {
        if (archiving) return
        val ctx = LtApp.instance
        if (ComicArchive.isReadable(book.name)) {
            archiving = true
            try {
                val path = LocalLibrary.toPath(book.uri)
                val opened = withContext(Dispatchers.IO) { ComicArchive.open(path, book.name, book.size) }
                val id = LocalMetas.idOf(book.uri)
                val meta = metaOf(id)
                Store.localArchive = opened
                Store.localArchiveId = id
                Store.localArchivePage = if (meta != null && meta.chapter > 0) meta.chapter else 1
                Store.readerBookId = ""
                Nav.push("comic")
            } catch (e: Throwable) {
                toast(t("打开失败：${e.message ?: ""}"))
            } finally {
                archiving = false
            }
            return
        }
        val meta = metaOf(LocalMetas.idOf(book.uri))
        if (meta == null) {
            toast(t("这本书还没有完成解析"))
            return
        }
        val blocked = LocalBookSource.preflight(meta.id)
        if (blocked.isNotEmpty()) {
            toast(t(blocked))
            return
        }
        Store.readerBookId = meta.id
        Store.readerTitle = if (meta.title.isNotEmpty()) meta.title else meta.name
        Store.readerChapter = if (meta.chapter > 0) meta.chapter else 1
        Store.readerIncognito = false
        Nav.push("reader")
    }

    private fun localAsBook(meta: LocalMeta): BookItem {
        val completed = meta.pages > 0 && meta.chapter >= meta.pages
        return BookItem(
            id = meta.id,
            title = meta.title,
            seriesTitle = meta.series,
            profile = meta.profile,
            pages = meta.pages,
            sizeBytes = meta.sizeBytes,
            size = if (meta.sizeBytes > 0) F.bytes(meta.sizeBytes) else "",
            authors = meta.author,
            publisher = meta.publisher,
            releaseDate = meta.date,
            fileName = meta.name,
            filePath = meta.uri,
            readPage = meta.chapter,
            readCompleted = completed,
            readState = when {
                completed -> STATE_READ
                meta.chapter > 0 -> STATE_READING
                else -> STATE_UNREAD
            }
        )
    }

    private fun matchLocal(meta: LocalMeta): Boolean {
        val key = text.trim().lowercase()
        if (key.isNotEmpty()) {
            val hay = "${meta.title} ${meta.author} ${meta.name}".lowercase()
            if (!hay.contains(key)) return false
        }
        if (profile.isNotEmpty() && meta.profile != profile) return false
        if (!Store.offline && year.isNotEmpty() && meta.date.take(4) != year) return false
        return true
    }

    fun metaOf(id: String): LocalMeta? = localMetas.firstOrNull { it.id == id }

    fun contentMeta(): String {
        if (plain()) return t("$seriesTotal 个系列")
        if (mode == "series" && source != "local") return t("${allGroups.size} 个系列")
        if (source == "local") return t("${localList.size} 册")
        return t("${localList.size + total} 册")
    }

    fun isEmptyList(): Boolean {
        if (plain()) return cards.isEmpty()
        if (mode == "series" && source != "local") return allGroups.isEmpty()
        if (source == "local") return localList.isEmpty()
        return localList.size + total == 0
    }

    fun emptyTitle(): String {
        if (Store.offline && source == "cloud") return t("还没有下载到本机的云库书")
        return t("没有符合条件的书")
    }

    fun emptySub(): String {
        if (Store.offline && source == "cloud") return t("在线时在书籍详情页选择缓存本机")
        return t("放宽筛选，或导入本地文件")
    }

    private fun localItems(): List<BookItem> {
        if (source == "cloud") return emptyList()
        val out = ArrayList<BookItem>()
        for (meta in localMetas) {
            if (matchLocal(meta)) out.add(localAsBook(meta))
        }
        return out
    }

    fun displayBooks(): List<BookItem> {
        val start = page * pageSize
        val out = ArrayList<BookItem>(localList.drop(start).take(pageSize))
        if (source != "local") out.addAll(items)
        return out
    }

    fun openAny(book: BookItem) {
        if (!book.id.startsWith("local:")) {
            openDetail(book)
            return
        }
        val meta = metaOf(book.id) ?: return
        if (Store.localMissing.contains(meta.id)) {
            toast(t("文件已缺失，请重新导入"))
            return
        }
        scope.launch { openLocal(LocalBook(name = meta.name, uri = meta.uri, size = meta.sizeBytes)) }
    }

    fun activeFilters(): Boolean {
        if (Store.offline) return text.isNotEmpty() || profile.isNotEmpty() || source != "all"
        return text.isNotEmpty() || profile.isNotEmpty() || readState.isNotEmpty() ||
            letter.isNotEmpty() || serial.isNotEmpty() || year.isNotEmpty() ||
            sort != "metadata.title,asc" || mode != "series" || source != "all"
    }

    fun toggleSearch(focus: FocusRequester) {
        searching = !searching
        if (searching) {
            moreOpen = false
            scope.launch {
                delay(260)
                try {
                    focus.requestFocus()
                } catch (e: Throwable) {
                }
            }
        } else {
            clearSearch()
        }
    }
}

@Composable
private fun SeriesCell(
    title: String,
    count: Int,
    read: Int,
    reading: Int,
    author: String,
    coverId: String,
    cellWidth: Dp,
    modifier: Modifier = Modifier,
    seriesCover: Boolean = false,
    onTap: () -> Unit
) {
    val C = Tm.pal
    val percent = if (count <= 0) 0 else {
        val touched = min(count, read + reading)
        max(0, (touched * 100f / count).roundToInt())
    }
    val progressText = when {
        count <= 0 -> ""
        read >= count -> t("已读完 · $count 册")
        read <= 0 && reading <= 0 -> t("未开始 · 共 $count 册")
        reading > 0 -> t("已读 $read/$count · 在读 $reading")
        else -> t("已读 $read/$count 册")
    }
    Column(modifier.width(cellWidth).clickable { onTap() }) {
        Box(
            Modifier
                .size(cellWidth, cellWidth * 1.42f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            CoverImage(coverId, title, cellWidth, cellWidth * 1.42f, 6.dp, series = seriesCover)
            Text(
                t("$count 册"),
                fontSize = 9.sp,
                fontFamily = Mono,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
            CoverProgress(percent = percent, modifier = Modifier.align(Alignment.BottomStart))
        }
        Text(
            title,
            fontSize = 11.5f.sp,
            lineHeight = 15.sp,
            color = C.tx,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(30.dp)
        )
        Text(
            if (author.isNotEmpty()) author else "—",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = C.tx4,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(14.dp)
        )
        Text(
            progressText,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = if (read >= count && count > 0) C.ok else C.tx4,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 1.dp).height(14.dp)
        )
    }
}

@Composable
private fun PagerBtn(
    modifier: Modifier = Modifier,
    label: String = "",
    glyph: String = "",
    active: Boolean = false,
    usable: Boolean = true,
    onTap: () -> Unit
) {
    val C = Tm.pal
    Row(
        modifier
            .height(26.dp)
            .alpha(if (usable) 1f else 0.38f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) C.acc else Color.Transparent)
            .border(1.dp, if (active) C.acc else C.line2, RoundedCornerShape(8.dp))
            .clickable(enabled = usable) { onTap() }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (glyph.isNotEmpty()) {
            Glyph(glyph, 12.dp, if (active) C.onAcc else C.tx2)
        } else {
            Text(
                label,
                fontSize = 11.sp,
                color = if (active) C.onAcc else C.tx2,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    glyph: String,
    active: Boolean = false,
    onTap: () -> Unit
) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable { onTap() }
            .padding(start = 12.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Glyph(glyph, 14.dp, if (active) C.acc2 else C.tx3)
        Text(
            label,
            fontSize = 12.5f.sp,
            color = if (active) C.acc2 else C.tx,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (active) Glyph("check", 13.dp, C.acc2)
    }
}

@Composable
private fun FlowChips(options: List<FilterOption>, current: String, onPick: (String) -> Unit) {
    val lines = remember(options, current) {
        val out = ArrayList<MutableList<FilterOption>>()
        var line = ArrayList<FilterOption>()
        var width = 0
        for (option in options) {
            val w = option.label.length * 9 + 28
            if (width + w > 250 && line.isNotEmpty()) {
                out.add(line)
                line = ArrayList()
                width = 0
            }
            line.add(option)
            width += w
        }
        if (line.isNotEmpty()) out.add(line)
        out
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (line in lines) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (option in line) {
                    Chip(
                        option.label,
                        active = option.value == current,
                        compact = true,
                        onTap = { onPick(option.value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<FilterOption>,
    current: String,
    first: Boolean = false,
    tail: String = "",
    tailOn: Boolean = false,
    onPick: (String) -> Unit,
    onTail: () -> Unit = {}
) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 10.5f.sp,
            color = C.tx4,
            letterSpacing = 0.5.sp,
            modifier = Modifier.width(38.dp).padding(top = 5.dp)
        )
        Box(Modifier.weight(1f)) {
            FlowChips(options, current, onPick)
        }
        if (tail.isNotEmpty()) {
            Row(
                Modifier
                    .padding(start = 4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (tailOn) C.accBg else C.bg3)
                    .border(1.dp, if (tailOn) C.accLine else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onTail() }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(tail, fontSize = 10.5f.sp, color = if (tailOn) C.acc2 else C.tx3, maxLines = 1)
                Glyph(if (tailOn) "up" else "down", 11.dp, if (tailOn) C.acc2 else C.tx3)
            }
        }
    }
}

@Composable
private fun SearchBox(hint: String, ctl: LibraryCtl, focus: FocusRequester) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(C.bg2)
            .border(1.dp, C.line2, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Glyph("search", 15.dp, C.tx4)
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = ctl.text,
                onValueChange = { ctl.onSearchInput(it) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.5f.sp, color = C.tx),
                cursorBrush = SolidColor(C.acc),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    if (ctl.text.isEmpty()) {
                        Text(hint, fontSize = 12.5f.sp, color = C.tx4, maxLines = 1)
                    }
                    inner()
                }
            )
        }
        if (ctl.text.isNotEmpty()) {
            Glyph("close", 14.dp, C.tx4, modifier = Modifier.clickable { ctl.clearSearch() })
        }
    }
}

@Composable
private fun FilterBox(ctl: LibraryCtl) {
    val C = Tm.pal
    val offline = Store.offline
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(Dp2.rL.dp))
            .background(C.bg2)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rL.dp))
            .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 4.dp)
    ) {
        FilterRow(t("来源"), sources(), ctl.source, first = true, onPick = { ctl.apply("source", it) })
        if (!offline) {
            FilterRow(t("显示"), modes(), ctl.mode, onPick = { ctl.apply("mode", it) })
        }
        if (ctl.filterOpen && !offline) {
            FilterRow(t("首字母"), letters(), ctl.letter, onPick = { ctl.apply("letter", it) })
            FilterRow(t("连载"), serials(), ctl.serial, onPick = { ctl.apply("serial", it) })
            FilterRow(t("年份"), ctl.years, ctl.year, onPick = { ctl.apply("year", it) })
        }
        FilterRow(t("格式"), formats(), ctl.profile, onPick = { ctl.apply("format", it) })
        FilterRow(t("阅读"), states(), ctl.readState, onPick = { ctl.apply("state", it) })
        FilterRow(
            t("排序"),
            sorts(),
            ctl.sort,
            tail = if (ctl.filterOpen) t("收起") else t("更多"),
            tailOn = ctl.filterOpen,
            onPick = { ctl.apply("sort", it) },
            onTail = { ctl.filterOpen = !ctl.filterOpen }
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.End) {
            val active = ctl.activeFilters()
            Row(
                Modifier
                    .height(24.dp)
                    .alpha(if (active) 1f else 0.45f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.bg3)
                    .border(1.dp, C.line, RoundedCornerShape(12.dp))
                    .clickable(enabled = active) { ctl.resetFilters() }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Glyph("sync", 11.dp, if (active) C.tx3 else C.tx4)
                Text(t("重置全部筛选"), fontSize = 10.5f.sp, color = if (active) C.tx3 else C.tx4, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BookGrid(
    items: List<BookItem>,
    seriesBooks: (String) -> Int,
    seriesRead: (String) -> Int,
    onTap: (BookItem) -> Unit
) {
    val rows = ceil(items.size / 3f).toInt()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - 20.dp) / 3
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 3) {
                        val index = r * 3 + c
                        if (index < items.size) {
                            val item = items[index]
                            BookGridCell(
                                book = item,
                                cellWidth = cellWidth,
                                modifier = Modifier.width(cellWidth),
                                seriesBooks = seriesBooks(item.seriesId),
                                seriesRead = seriesRead(item.seriesId),
                                onTap = { onTap(item) }
                            )
                        } else {
                            Spacer(Modifier.width(cellWidth))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesGrid(cards: List<SeriesCard>, onTap: (SeriesCard) -> Unit) {
    val rows = ceil(cards.size / 3f).toInt()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - 20.dp) / 3
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 3) {
                        val index = r * 3 + c
                        if (index < cards.size) {
                            val card = cards[index]
                            SeriesCell(
                                title = card.title,
                                count = card.books,
                                read = card.read,
                                reading = card.reading,
                                author = card.author,
                                coverId = card.id,
                                cellWidth = cellWidth,
                                modifier = Modifier.width(cellWidth),
                                seriesCover = true,
                                onTap = { onTap(card) }
                            )
                        } else {
                            Spacer(Modifier.width(cellWidth))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupGrid(
    groups: List<SeriesGroup>,
    total: (SeriesGroup) -> Int,
    read: (SeriesGroup) -> Int,
    reading: (SeriesGroup) -> Int,
    onTap: (SeriesGroup) -> Unit
) {
    val rows = ceil(groups.size / 3f).toInt()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - 20.dp) / 3
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 3) {
                        val index = r * 3 + c
                        if (index < groups.size) {
                            val group = groups[index]
                            SeriesCell(
                                title = group.title,
                                count = total(group),
                                read = read(group),
                                reading = reading(group),
                                author = group.books.firstOrNull()?.authors ?: "",
                                coverId = group.books.firstOrNull()?.id ?: "",
                                cellWidth = cellWidth,
                                modifier = Modifier.width(cellWidth),
                                onTap = { onTap(group) }
                            )
                        } else {
                            Spacer(Modifier.width(cellWidth))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerBar(ctl: LibraryCtl) {
    val C = Tm.pal
    var sizeOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PagerBtn(glyph = "back", usable = ctl.page > 0) { ctl.turn(-1) }
                for (num in ctl.pgNums()) {
                    if (num == 0) {
                        Text("\u2026", fontSize = 11.sp, color = C.tx4, modifier = Modifier.padding(horizontal = 1.dp))
                    } else {
                        PagerBtn(label = "$num", active = num == ctl.page + 1) { ctl.goto(num - 1) }
                    }
                }
                PagerBtn(glyph = "next", usable = ctl.page < ctl.pageCount() - 1) { ctl.turn(1) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("\u6bcf\u9875"), fontSize = 10.sp, color = C.tx3, maxLines = 1)
                Box {
                    Row(
                        Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(C.bg2)
                            .border(1.dp, C.line2, RoundedCornerShape(8.dp))
                            .clickable { sizeOpen = true }
                            .padding(start = 6.dp, end = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("${ctl.pageSize}", fontSize = 11.sp, fontFamily = Mono, color = C.tx2)
                        Glyph("down", 10.dp, C.tx3)
                    }
                    androidx.compose.material3.DropdownMenu(expanded = sizeOpen, onDismissRequest = { sizeOpen = false }) {
                        for (size in listOf(6, 12, 24)) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("$size", fontSize = 12.sp, color = C.tx) },
                                onClick = {
                                    sizeOpen = false
                                    ctl.applyPageSize(size)
                                }
                            )
                        }
                    }
                }
                Text(t("\u8df3"), fontSize = 10.sp, color = C.tx3, maxLines = 1)
                BasicTextField(
                    value = ctl.jumpText,
                    onValueChange = { ctl.jumpText = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 11.sp, fontFamily = Mono, color = C.tx, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(C.acc),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(38.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.bg2)
                        .border(1.dp, C.line2, RoundedCornerShape(8.dp)),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (ctl.jumpText.isEmpty()) {
                                Text("${ctl.page + 1}", fontSize = 11.sp, fontFamily = Mono, color = C.tx4)
                            }
                            inner()
                        }
                    }
                )
                Text("/${ctl.pageCount()}", fontSize = 10.sp, fontFamily = Mono, color = C.tx3)
            }
        }
    }
}

@Composable
private fun MoreMenu(ctl: LibraryCtl, onScan: () -> Unit) {
    val C = Tm.pal
    Column(
        Modifier
            .width(158.dp)
            .shadow(13.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(C.bg1)
            .border(1.dp, C.line2, RoundedCornerShape(13.dp))
            .padding(vertical = 5.dp)
    ) {
        MenuRow(t("网格视图"), "grid", ctl.view == "grid") {
            ctl.view = "grid"
            ctl.moreOpen = false
        }
        MenuRow(t("列表视图"), "list", ctl.view == "list") {
            ctl.view = "list"
            ctl.moreOpen = false
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 5.dp).height(1.dp).background(C.line))
        MenuRow(t("导入本地文件"), "upload") {
            ctl.moreOpen = false
            Nav.push("import")
        }
        MenuRow(t("扫描本地库"), "folder") {
            ctl.moreOpen = false
            onScan()
        }
        if (!Store.offline) {
            MenuRow(t("连接云库"), "server") {
                ctl.moreOpen = false
                Nav.push("server")
            }
        }
        MenuRow(t("下载队列"), "download") {
            ctl.moreOpen = false
            Nav.push("downloads")
        }
    }
}

@Composable
fun LibraryScreen(embedded: Boolean = false, focusTick: Int = 0) {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    val ctl = remember { LibraryCtl(scope) }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()

    val tick = Store.uiTick
    val progressVer = ProgressMarks.ver
    val offline = Store.offline
    val offlineTick = Store.offlineTick

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { ctl.onFolderPicked(uri) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { ctl.onFilesPicked(uris) }
    }

    LaunchedEffect(Unit) { ctl.boot() }
    LaunchedEffect(focusTick) {
        if (focusTick > 0) ctl.onFocusTick()
    }
    LaunchedEffect(progressVer) { ctl.onProgressVer() }
    LaunchedEffect(offlineTick) { ctl.onTab() }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHead(
                title = t("书库"),
                actionIcon3 = "search",
                actionOn3 = ctl.searching,
                onAction3 = { ctl.toggleSearch(focus) },
                actionIcon2 = "more",
                actionOn2 = ctl.moreOpen,
                onAction2 = {
                    ctl.moreOpen = !ctl.moreOpen
                    if (ctl.moreOpen) ctl.searching = false
                }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(start = Dp2.page.dp, end = Dp2.page.dp)
                    .padding(bottom = 100.dp)
            ) {
                if (ctl.searching) {
                    SearchBox(t("书名 / 作者 / 系列 / 格式"), ctl, focus)
                }
                FilterBox(ctl)
                if (!offline) {
                    Text(
                        if (ctl.plain()) t("${ctl.seriesTotal} 个系列")
                        else t("共 ${ctl.localList.size + ctl.total} 册 · 本地 ${ctl.localMetas.size} 本"),
                        fontSize = 10.sp,
                        color = C.tx4,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }

                SectionHead(title = t("书库内容"), meta = ctl.contentMeta())

                when {
                    ctl.loading && ctl.localMetas.isEmpty() -> {
                        if (ctl.plain()) {
                            LibrarySkeleton()
                        } else {
                            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                                LoadingView(t("查询书库"))
                            }
                        }
                    }

                    ctl.error.isNotEmpty() && ctl.localMetas.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                            ErrorView(message = ctl.error, onRetry = { scope.launch { ctl.refresh() } })
                        }
                    }

                    ctl.isEmptyList() -> {
                        Column(
                            Modifier.fillMaxWidth().height(320.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Glyph("search", 24.dp, C.tx4, 1.4f)
                            Text(
                                ctl.emptyTitle(),
                                fontSize = 12.5f.sp,
                                color = C.tx2,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Text(
                                ctl.emptySub(),
                                fontSize = 10.5f.sp,
                                color = C.tx4,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                            Text(
                                t("重置筛选"),
                                fontSize = 11.5f.sp,
                                color = C.tx,
                                modifier = Modifier
                                    .padding(top = 14.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(C.bg2)
                                    .border(1.dp, C.line, RoundedCornerShape(9.dp))
                                    .clickable { ctl.resetFilters() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    else -> {
                        if (ctl.plain()) {
                            SeriesGrid(ctl.cards) { ctl.openSeriesId(it.id) }
                        } else if (ctl.mode == "series" && ctl.source != "local") {
                            GroupGrid(
                                ctl.groups,
                                total = { ctl.groupTotal(it) },
                                read = { ctl.groupRead(it) },
                                reading = { ctl.groupReading(it) },
                                onTap = { ctl.openSeries(it) }
                            )
                        } else if (ctl.view == "grid") {
                            BookGrid(
                                ctl.displayBooks(),
                                seriesBooks = { ctl.seriesBooksOf(it) },
                                seriesRead = { ctl.seriesReadOf(it) },
                                onTap = { ctl.openAny(it) }
                            )
                        } else {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Dp2.rM.dp))
                                    .background(C.bg1)
                                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                                    .padding(horizontal = 13.dp)
                            ) {
                                val books = ctl.displayBooks()
                                books.forEachIndexed { index, item ->
                                    BookRow(
                                        book = item,
                                        seriesBooks = ctl.seriesBooksOf(item.seriesId),
                                        seriesRead = ctl.seriesReadOf(item.seriesId),
                                        onTap = { ctl.openAny(item) }
                                    )
                                    if (index < books.size - 1) {
                                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                                    }
                                }
                            }
                        }
                        PagerBar(ctl)
                    }
                }
            }
        }

        if (ctl.moreOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x01000000))
                    .clickable { ctl.moreOpen = false }
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 61.dp, end = Dp2.page.dp),
                horizontalAlignment = Alignment.End
            ) {
                MoreMenu(ctl, onScan = { folderPicker.launch(null) })
            }
        }
    }
}
