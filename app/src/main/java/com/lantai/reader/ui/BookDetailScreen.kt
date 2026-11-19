package com.lantai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.common.tf
import com.lantai.reader.data.offlineDb
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.BookStat
import com.lantai.reader.model.ChapterItem
import com.lantai.reader.model.MEDIA_ERROR
import com.lantai.reader.model.ReadListRef
import com.lantai.reader.model.ReadingStats
import com.lantai.reader.model.Store
import com.lantai.reader.model.isEbook
import com.lantai.reader.model.profileLabel
import com.lantai.reader.net.ApiError
import com.lantai.reader.net.offlineCache
import com.lantai.reader.net.OfflineDownloader
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.CoverViewer
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.FormatBadge
import com.lantai.reader.ui.comp.Mono
import com.lantai.reader.ui.comp.ProgressBar
import com.lantai.reader.ui.comp.ScreenHead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

private fun charsLabel(count: Int): String {
    if (count <= 0) return ""
    if (count < 1000) return t("$count 字")
    return t("${String.format(Locale.US, "%.1f", count / 1000.0)} 千字")
}

private fun emptyStats(): ReadingStats = ReadingStats()

private class DetailCtl(val scope: CoroutineScope) {
    var book by mutableStateOf(BookItem())
    var loading by mutableStateOf(true)
    var error by mutableStateOf("")
    var chapters by mutableStateOf<List<ChapterItem>>(emptyList())
    var chapterTotal by mutableStateOf(0)
    var stats by mutableStateOf(emptyStats())
    var bookStat by mutableStateOf<BookStat?>(null)
    var readlists by mutableStateOf<List<ReadListRef>>(emptyList())
    var showInfo by mutableStateOf(false)
    var showSummary by mutableStateOf(false)
    var tocMode by mutableStateOf("list")
    var tocPage by mutableStateOf(0)
    var listSize by mutableStateOf(10)
    var gridSize by mutableStateOf(50)
    var coverOpen by mutableStateOf(false)
    var wanted by mutableStateOf(false)
    var cached by mutableStateOf(false)
    var sizeOpen by mutableStateOf(false)
    var sizeText by mutableStateOf("")
    private var tocPinned = false

    fun boot() {
        val selected = Store.selected
        if (selected != null) book = selected
        tocMode = if (isEbook(book.profile)) "list" else "grid"
        scope.launch { load() }
    }

    suspend fun load() {
        if (book.id.isEmpty()) {
            loading = false
            error = t("未选择书籍")
            return
        }
        if (Store.offline) {
            cached = true
            loading = false
            error = ""
            return
        }
        try {
            val detail = Store.api.book(book.id)
            book = detail
            if (!tocPinned) tocMode = if (isEbook(detail.profile)) "list" else "grid"
            cached = cachedOf(detail.id)
            loading = false
            loadSide(detail.id)
        } catch (e: Throwable) {
            loading = false
            val status = (e as? ApiError)?.status ?: 0
            error = if (status == 404 || status == 410) {
                t("这本书已在兰台移除，本机副本与进度会同步丢弃")
            } else {
                e.message ?: t("读取失败")
            }
        }
    }

    private suspend fun loadSide(id: String) {
        try {
            val toc = Store.api.chapters(id)
            chapters = toc.items
            chapterTotal = toc.items.size
        } catch (e: Throwable) {
            chapters = emptyList()
            chapterTotal = 0
        }
        try {
            val result = Store.api.readingStats()
            stats = result
            bookStat = result.books.firstOrNull { it.bookId == id }
        } catch (e: Throwable) {
            stats = emptyStats()
            bookStat = null
        }
        try {
            readlists = Store.api.readlistsOfBook(id)
        } catch (e: Throwable) {
            readlists = emptyList()
        }
        try {
            wanted = Store.api.wantToReadState(id)
        } catch (e: Throwable) {
            wanted = false
        }
    }

    fun toggleWant() {
        val next = !wanted
        scope.launch {
            try {
                Store.api.setWantToRead(book.id, next)
                wanted = next
                Tip.show(if (next) t("已加入待读") else t("已移出待读"))
            } catch (e: Throwable) {
                Tip.show(t("操作失败：${e.message ?: ""}"))
            }
        }
    }

    fun mark(completed: Boolean) {
        scope.launch {
            try {
                Store.api.markRead(book.id, completed)
                val detail = Store.api.book(book.id)
                ProgressMarks.settle(detail.id, completed, detail.pages)
                book = detail
                Store.selected = detail
                Tip.show(if (completed) t("已标记为读完") else t("已标记为未读"))
            } catch (e: Throwable) {
                Tip.show(t("操作失败：${e.message ?: ""}"))
            }
        }
    }

    private suspend fun cachedOf(id: String): Boolean {
        if (offlineCache.has("/api/lantai/v1/read/$id/bootstrap?images=inline&w=0&window=3")) return true
        return try {
            offlineDb.books().any { it.bookId == id }
        } catch (e: Throwable) {
            false
        }
    }

    fun cacheBook() {
        if (book.profile == "PDF") {
            Tip.show(t("PDF 暂不支持离线缓存"))
            return
        }
        if (cached) {
            Nav.push("downloads")
            return
        }
        scope.launch {
            OfflineDownloader.enqueue(book, book.profile)
            cached = true
            Tip.show(t("已加入下载队列"))
        }
    }

    fun unreadable(): Boolean = book.mediaStatus == MEDIA_ERROR

    fun page(): Int = ProgressMarks.pageOf(book.id, book.readPage)

    fun done(): Boolean = ProgressMarks.doneOf(book.id, book.readCompleted, page(), book.pages)

    fun percent(): Int = if (done()) 100 else F.percent(page(), book.pages)

    fun readStateLabel(): String = F.progressLabel(page(), book.pages, done())

    fun enterReader() {
        if (unreadable()) {
            Tip.show(t("文件损坏，无法阅读"))
            return
        }
        Store.localArchive = null
        Store.localArchiveId = ""
        Store.localArchivePage = 1
        Store.readerBookId = book.id
        Store.readerTitle = book.title
        Store.readerChapter = maxOf(1, page())
        Nav.push(if (isEbook(book.profile)) "reader" else "comic")
    }

    fun openReader(incognito: Boolean) {
        Store.readerIncognito = incognito
        enterReader()
    }

    fun openChapter(target: Int) {
        Store.readerIncognito = false
        Store.localArchive = null
        Store.localArchiveId = ""
        Store.localArchivePage = 1
        Store.readerBookId = book.id
        Store.readerTitle = book.title
        Store.readerChapter = target
        Nav.push(if (isEbook(book.profile)) "reader" else "comic")
    }

    fun listLabel(): String {
        if (readlists.isEmpty()) return ""
        if (readlists.size == 1) return readlists[0].name
        return "${readlists[0].name} +${readlists.size - 1}"
    }

    fun pagesLabel(): String {
        val pages = if (book.pages > 0) t("${book.pages} 页") else "—"
        if (isEbook(book.profile) && chapterTotal > 0) return t("$pages / $chapterTotal 章")
        return pages
    }

    fun progressLine(): String {
        val pages = if (book.pages > 0) t("${book.pages} 页") else ""
        if (isEbook(book.profile) && chapterTotal > 0) {
            return tf("{0} · {1} · {2} 章", listOf(readStateLabel(), pages, "$chapterTotal"))
        }
        if (book.readDate.isNotEmpty()) {
            return tf("{0} · {1} · 上次 {2}", listOf(readStateLabel(), pages, F.ago(book.readDate)))
        }
        return "${readStateLabel()} · $pages"
    }

    fun durationValue(): String = bookStat?.let { F.shortDuration(it.durationMs) } ?: "—"

    fun recentValue(): String {
        val stat = bookStat ?: return "—"
        if (stat.lastReadAt.isEmpty()) return "—"
        return F.ago(stat.lastReadAt)
    }

    fun pagesReadValue(): String = bookStat?.let { "${it.pagesRead}" } ?: "—"

    fun sessionValue(): String = bookStat?.let { "${it.sessionCount}" } ?: "—"

    fun pageSize(): Int = if (tocMode == "grid") gridSize else listSize

    fun pageCount(): Int = maxOf(1, (chapters.size + pageSize() - 1) / pageSize())

    fun shownChapters(): List<ChapterItem> {
        val page = minOf(tocPage, pageCount() - 1)
        val size = pageSize()
        val from = page * size
        val to = minOf(from + size, chapters.size)
        if (from >= to) return emptyList()
        return chapters.subList(from, to)
    }

    fun pageList(): List<Int> {
        val total = pageCount()
        val cur = minOf(tocPage, total - 1)
        val out = ArrayList<Int>(9)
        if (total <= 7) {
            for (i in 0 until total) out.add(i)
            return out
        }
        var from = maxOf(1, cur - 1)
        var to = minOf(total - 2, cur + 1)
        if (cur <= 3) {
            from = 1
            to = 4
        }
        if (cur >= total - 4) {
            from = total - 5
            to = total - 2
        }
        out.add(0)
        if (from > 1) out.add(-1)
        for (i in from..to) out.add(i)
        if (to < total - 2) out.add(-1)
        out.add(total - 1)
        return out
    }

    fun gotoPage(page: Int) {
        tocPage = maxOf(0, minOf(pageCount() - 1, page))
    }

    fun applySize(value: Int) {
        if (tocMode == "grid") gridSize = value else listSize = value
        tocPage = 0
    }

    fun openSizeDialog() {
        sizeText = "${pageSize()}"
        sizeOpen = true
    }

    fun switchMode(mode: String) {
        if (tocMode == mode) return
        tocPinned = true
        tocMode = mode
        tocPage = 0
    }

    fun peak(): Long {
        var peak = 1L
        for (day in stats.days) {
            if (day.durationMs > peak) peak = day.durationMs
        }
        return peak
    }

    fun tocStart(): Int = minOf(tocPage, pageCount() - 1) * pageSize()

    fun gridHeight(): Int {
        val rows = maxOf(1, (shownChapters().size + 4) / 5)
        return rows * 34 + (rows - 1) * 6
    }
}

@Composable
private fun RowScope.InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    val C = Tm.pal
    Column(modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, fontSize = 9.sp, color = C.tx4, letterSpacing = 0.2.sp)
        Text(
            if (value.isNotEmpty()) value else "—",
            fontSize = 10.5.sp,
            color = C.tx2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LedgerLine(label: String, value: String, mono: Boolean = false) {
    val C = Tm.pal
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 9.5.sp, color = C.tx4, modifier = Modifier.width(58.dp))
        Text(
            if (value.isNotEmpty()) value else "—",
            fontSize = 10.5.sp,
            color = C.tx2,
            fontFamily = if (mono) Mono else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
    }
}

@Composable
private fun RowScope.LedgerCell(label: String, value: String, mono: Boolean = false, modifier: Modifier = Modifier) {
    val C = Tm.pal
    Column(modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, fontSize = 8.5.sp, color = C.tx4, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (value.isNotEmpty()) value else "—",
            fontSize = 10.5.sp,
            color = C.tx2,
            fontFamily = if (mono) Mono else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RowScope.ActCell(glyph: String, label: String, active: Boolean, dimmed: Boolean, onTap: () -> Unit) {
    val C = Tm.pal
    Column(
        Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) C.accBg else C.bg3)
            .border(1.dp, if (active) C.accLine else C.line, RoundedCornerShape(9.dp))
            .clickable(enabled = !dimmed) { onTap() }
            .alpha(if (dimmed) 0.32f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Glyph(glyph, 16.dp, if (active) C.acc2 else C.tx3)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 9.5.sp, color = if (active) C.acc2 else C.tx3, maxLines = 1)
    }
}

@Composable
private fun RowScope.ChipOp(glyph: String, label: String, active: Boolean, chevron: String, onTap: () -> Unit) {
    val C = Tm.pal
    Row(
        Modifier
            .weight(1f)
            .height(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) C.accBg else C.bg3)
            .border(1.dp, if (active) C.accLine else C.line, RoundedCornerShape(7.dp))
            .clickable { onTap() }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        Glyph(glyph, 11.dp, if (active) C.acc2 else C.tx3)
        if (label.isNotEmpty()) {
            Text(
                label,
                fontSize = 9.5.sp,
                color = if (active) C.acc2 else C.tx3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        if (chevron.isNotEmpty()) {
            Glyph(chevron, 10.dp, if (active) C.acc2 else C.tx4)
        }
    }
}

@Composable
private fun RowScope.StatCell(value: String, label: String) {
    val C = Tm.pal
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, fontSize = 12.sp, color = C.acc2, fontFamily = Disp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 9.sp, color = C.tx4)
    }
}

@Composable
private fun TocRowItem(title: String, meta: String, current: Boolean, onTap: () -> Unit) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(C.bg3),
            contentAlignment = Alignment.Center
        ) {
            Glyph("toc", 14.dp, C.tx3)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontSize = 12.5.sp,
                color = if (current) C.acc2 else C.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (meta.isNotEmpty()) {
                Text(meta, fontSize = 10.sp, color = C.tx4, maxLines = 1, modifier = Modifier.fillMaxWidth())
            }
        }
        Glyph("next", 13.dp, C.tx4)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
}

@Composable
private fun TocCell(label: String, current: Boolean, onTap: () -> Unit) {
    val C = Tm.pal
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (current) C.acc else C.bg3)
            .border(1.dp, if (current) C.acc else C.line, RoundedCornerShape(8.dp))
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, fontFamily = Mono, color = if (current) C.onAcc else C.tx2)
    }
}

@Composable
private fun TocNum(label: String, active: Boolean, gap: Boolean, onTap: () -> Unit) {
    val C = Tm.pal
    Box(
        Modifier
            .height(24.dp)
            .widthIn(min = 24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) C.acc else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = !gap) { onTap() }
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontFamily = Mono,
            color = if (active) C.onAcc else if (gap) C.tx4 else C.tx2
        )
    }
}

@Composable
private fun SizeDialog(value: String, onOk: (Int) -> Unit, onClose: () -> Unit) {
    val C = Tm.pal
    var text by remember { mutableStateOf(value) }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(Dp2.rL.dp))
                .background(C.bg1)
                .border(1.dp, C.line, RoundedCornerShape(Dp2.rL.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(t("自定义每页数量"), fontSize = 13.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.bg2)
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 13.sp, color = C.tx),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(C.acc),
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isEmpty()) Text(t("1 – 500"), fontSize = 13.sp, color = C.tx4)
            }
            Text(t("范围 1 – 500，超出按边界处理"), fontSize = 9.5.sp, color = C.tx4, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(C.bg3)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx2)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(C.acc)
                        .clickable {
                            val parsed = text.trim().toIntOrNull()
                            if (parsed != null) onOk(maxOf(1, minOf(500, parsed)))
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(t("确定"), fontSize = 12.sp, color = C.onAcc)
                }
            }
        }
    }
}

@Composable
fun BookDetailScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    val ctl = remember { DetailCtl(scope) }
    var sizeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ctl.boot() }
    val marks = ProgressMarks.ver

    if (ctl.sizeOpen) {
        SizeDialog(
            value = ctl.sizeText,
            onOk = { ctl.applySize(it) },
            onClose = { ctl.sizeOpen = false }
        )
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
            ScreenHead(title = t("书籍详情"), back = t("返回"), onBack = { Nav.pop() })

            if (ctl.loading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = C.acc, modifier = Modifier.size(32.dp))
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = Dp2.page.dp, end = Dp2.page.dp)
                ) {
                    Crumb(ctl)
                    Head(ctl, marks)
                    if (ctl.book.summary.isNotEmpty()) SummaryBox(ctl)
                    if (ctl.showInfo) FullInfo(ctl)
                    Dock(ctl, marks)
                    if (!Store.offline) {
                        StatCard(ctl, marks)
                        TocSection(ctl) { sizeMenu = true }
                    }
                    if (ctl.error.isNotEmpty()) {
                        Text(
                            ctl.error,
                            fontSize = 11.sp,
                            color = C.bad,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }

        if (ctl.coverOpen && ctl.book.id.isNotEmpty()) {
            CoverViewer(ctl.book.id, ctl.book.title) { ctl.coverOpen = false }
        }
    }

    LaunchedEffect(ctl.coverOpen) {
        if (ctl.coverOpen) {
            Store.backHook = {
                if (ctl.coverOpen) {
                    ctl.coverOpen = false
                    true
                } else {
                    false
                }
            }
        } else if (Store.backHook != null) {
            Store.backHook = null
        }
    }
}

@Composable
private fun Crumb(ctl: DetailCtl) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(t("书库"), fontSize = 10.5.sp, color = C.tx3, modifier = Modifier.clickable { Nav.pop() })
        Glyph("next", 11.dp, C.tx4)
        Text(
            if (ctl.book.seriesTitle.isNotEmpty()) ctl.book.seriesTitle else t("未归入系列"),
            fontSize = 10.5.sp,
            color = C.tx3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp)
        )
        Glyph("next", 11.dp, C.tx4)
        Text(
            ctl.book.title,
            fontSize = 10.5.sp,
            color = C.tx2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Head(ctl: DetailCtl, marks: Int) {
    val C = Tm.pal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CoverImage(
            bookId = ctl.book.id,
            title = ctl.book.title,
            coverWidth = 112.dp,
            coverHeight = 156.dp,
            radius = 8.dp,
            modifier = Modifier.clickable { ctl.coverOpen = true }
        )
        Column(Modifier.weight(1f).height(156.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FormatBadge(label = if (Store.offline) t("离线副本") else t("云端"))
                FormatBadge(label = profileLabel(ctl.book.profile, ctl.book.mediaStatus))
                FormatBadge(
                    label = ctl.readStateLabel(),
                    tone = if (ctl.book.readCompleted) "done" else "normal"
                )
            }
            Text(
                ctl.book.title,
                fontSize = 18.sp,
                fontFamily = Disp,
                letterSpacing = 0.6.sp,
                color = C.tx,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (ctl.book.authors.isNotEmpty()) {
                Text(
                    ctl.book.authors,
                    fontSize = 11.sp,
                    color = C.tx3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                InfoCell(t("格式"), profileLabel(ctl.book.profile, ctl.book.mediaStatus))
            }
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                InfoCell(if (isEbook(ctl.book.profile)) t("页数 / 章节") else t("页数"), ctl.pagesLabel())
                InfoCell(t("出版"), F.day(ctl.book.releaseDate))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipOp(
                    glyph = "info",
                    label = if (ctl.showInfo) t("收起信息") else t("完整信息"),
                    active = ctl.showInfo,
                    chevron = if (ctl.showInfo) "up" else "down"
                ) { ctl.showInfo = !ctl.showInfo }
                if (!Store.offline) {
                    ChipOp(
                        glyph = "readlist",
                        label = ctl.listLabel(),
                        active = false,
                        chevron = ""
                    ) { Nav.push("readlists") }
                }
            }
        }
    }
}

@Composable
private fun SummaryBox(ctl: DetailCtl) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 13.dp)
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .clickable { ctl.showSummary = !ctl.showSummary }
            .padding(12.dp)
    ) {
        Text(
            ctl.book.summary,
            fontSize = 11.5.sp,
            color = C.tx2,
            lineHeight = 20.sp,
            maxLines = if (ctl.showSummary) 60 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (ctl.showSummary) t("收起") else t("展开"),
            fontSize = 10.sp,
            color = C.acc,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun Dock(ctl: DetailCtl, marks: Int) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(Dp2.rL.dp))
            .background(C.bg2)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rL.dp))
            .padding(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val unreadable = ctl.unreadable()
            Row(
                Modifier
                    .weight(1.6f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (unreadable) C.bg3 else C.acc)
                    .clickable { ctl.openReader(false) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Glyph("book", 15.dp, if (unreadable) C.tx4 else C.bg)
                Text(
                    if (unreadable) t("无法阅读") else if (ctl.page() > 0) t("继续阅读") else t("开始阅读"),
                    fontSize = 12.5.sp,
                    color = if (unreadable) C.tx4 else C.bg,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.bg3)
                    .border(1.dp, C.line, RoundedCornerShape(10.dp))
                    .clickable { ctl.openReader(true) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Glyph("eyeOff", 15.dp, C.tx2)
                Text(t("隐身阅读"), fontSize = 12.5.sp, color = C.tx2)
            }
        }

        if (!Store.offline) {
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActCell("check", t("标记已读"), false, ctl.book.readCompleted) { ctl.mark(true) }
                ActCell("close", t("标记未读"), false, !ctl.done() && ctl.page() <= 0) { ctl.mark(false) }
                ActCell(
                    if (ctl.wanted) "close" else "bookmark",
                    if (ctl.wanted) t("移除待读") else t("加入待读"),
                    ctl.wanted,
                    false
                ) { ctl.toggleWant() }
                ActCell(
                    "download",
                    if (ctl.cached) t("已离线") else t("缓存本机"),
                    ctl.cached,
                    false
                ) { ctl.cacheBook() }
            }
        }
    }
}

@Composable
private fun StatCard(ctl: DetailCtl, marks: Int) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(13.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(t("阅读进度与时长"), fontSize = 12.sp, color = C.tx)
            Spacer(Modifier.weight(1f))
            FormatBadge(label = profileLabel(ctl.book.profile, ctl.book.mediaStatus))
            FormatBadge(label = "${ctl.percent()}%")
        }

        ProgressBar(percent = ctl.percent(), modifier = Modifier.padding(top = 8.dp))

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                ctl.progressLine(),
                fontSize = 10.sp,
                color = C.tx4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (ctl.bookStat != null && ctl.stats.days.isNotEmpty()) {
                val peak = ctl.peak()
                Row(
                    Modifier.width(76.dp).height(20.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ctl.stats.days.forEachIndexed { index, day ->
                        val h = maxOf(3, Math.round(day.durationMs.toDouble() / peak * 20).toInt())
                        Box(
                            Modifier
                                .weight(1f)
                                .height(h.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (index == ctl.stats.days.size - 1) C.acc2 else C.acc)
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 9.dp)) {
            StatCell(ctl.durationValue(), t("累计"))
            StatCell(ctl.recentValue(), t("上次"))
            StatCell(ctl.pagesReadValue(), t("累计页"))
            StatCell(ctl.sessionValue(), t("会话"))
        }
    }
}

@Composable
private fun TocSection(ctl: DetailCtl, onSizeMenu: () -> Unit) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (isEbook(ctl.book.profile)) t("章节列表") else t("页"),
            fontSize = 16.sp,
            fontFamily = Disp,
            letterSpacing = 1.sp,
            color = C.tx
        )
        Text("${ctl.chapterTotal}", fontSize = 10.sp, fontFamily = Mono, color = C.tx4)
        Spacer(Modifier.weight(1f))
        if (isEbook(ctl.book.profile)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ModeBtn("list", ctl.tocMode == "list") { ctl.switchMode("list") }
                ModeBtn("grid", ctl.tocMode == "grid") { ctl.switchMode("grid") }
            }
        } else {
            Text(tf("{0} 页 · 阅读器中翻阅", listOf("${ctl.book.pages}")), fontSize = 10.sp, color = C.tx4)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(horizontal = 13.dp)
    ) {
        if (ctl.chapters.isEmpty()) {
            Text(
                if (ctl.loading) t("目录读取中…") else t("该书籍未提供目录"),
                fontSize = 11.sp,
                color = C.tx4,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        } else {
            if (ctl.tocMode == "grid") {
                val shown = ctl.shownChapters()
                val start = ctl.tocStart()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(ctl.gridHeight().dp)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shown.chunked(5).forEachIndexed { rowIndex, rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEachIndexed { colIndex, item ->
                                val label = "${start + rowIndex * 5 + colIndex + 1}"
                                Box(Modifier.weight(1f)) {
                                    TocCell(label, item.n == ctl.page()) { ctl.openChapter(item.n) }
                                }
                            }
                            repeat(5 - rowItems.size) {
                                Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                val shown = ctl.shownChapters()
                shown.forEach { item ->
                    TocRowItem(
                        title = item.title,
                        meta = if (isEbook(ctl.book.profile)) charsLabel(item.chars) else "",
                        current = item.n == ctl.page()
                    ) { ctl.openChapter(item.n) }
                }
            }

            TocPager(ctl)
        }
    }
}

@Composable
private fun ModeBtn(glyph: String, active: Boolean, onTap: () -> Unit) {
    val C = Tm.pal
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) C.accBg else C.bg3)
            .border(1.dp, if (active) C.accLine else C.line, RoundedCornerShape(7.dp))
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Glyph(glyph, 12.dp, if (active) C.acc2 else C.tx4)
    }
}

@Composable
private fun TocPager(ctl: DetailCtl) {
    val C = Tm.pal
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val prevOn = ctl.tocPage > 0
        Box(
            Modifier
                .size(28.dp, 26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(C.bg3)
                .border(1.dp, C.line, RoundedCornerShape(7.dp))
                .clickable(enabled = prevOn) { ctl.gotoPage(ctl.tocPage - 1) },
            contentAlignment = Alignment.Center
        ) {
            Glyph("back", 12.dp, if (prevOn) C.tx2 else C.tx4)
        }

        Row(
            Modifier
                .weight(1f)
                .height(24.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ctl.pageList().forEach { item ->
                TocNum(
                    label = if (item < 0) "…" else "${item + 1}",
                    active = item == minOf(ctl.tocPage, ctl.pageCount() - 1),
                    gap = item < 0
                ) { ctl.gotoPage(item) }
            }
        }

        val nextOn = ctl.tocPage < ctl.pageCount() - 1
        Box(
            Modifier
                .size(28.dp, 26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(C.bg3)
                .border(1.dp, C.line, RoundedCornerShape(7.dp))
                .clickable(enabled = nextOn) { ctl.gotoPage(ctl.tocPage + 1) },
            contentAlignment = Alignment.Center
        ) {
            Glyph("next", 12.dp, if (nextOn) C.tx2 else C.tx4)
        }

        Box {
            Row(
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(C.bg3)
                    .border(1.dp, C.line, RoundedCornerShape(7.dp))
                    .clickable { menu = true }
                    .padding(start = 8.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(t("每页 ${ctl.pageSize()}"), fontSize = 9.5.sp, fontFamily = Mono, color = C.tx3)
                Glyph("down", 9.dp, C.tx4)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = C.bg1
            ) {
                listOf(10, 20, 50, 100).forEach { size ->
                    DropdownMenuItem(
                        text = { Text(t("每页 $size"), fontSize = 12.sp, color = C.tx2) },
                        onClick = {
                            ctl.applySize(size)
                            menu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(t("自定义…"), fontSize = 12.sp, color = C.tx2) },
                    onClick = {
                        menu = false
                        ctl.openSizeDialog()
                    }
                )
            }
        }
    }
}

@Composable
private fun FullInfo(ctl: DetailCtl) {
    val C = Tm.pal
    val book = ctl.book
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(t("文件信息"), fontSize = 12.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerCell(t("格式"), profileLabel(book.profile, book.mediaStatus))
            LedgerCell(t("页数"), if (book.pages > 0) "${book.pages}" else "")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerCell(t("媒体类型"), book.mediaType, mono = true)
            LedgerCell(t("出版"), F.day(book.releaseDate))
        }
        LedgerLine(t("文件名"), book.fileName, mono = true)
        LedgerLine(t("相对路径"), book.filePath, mono = true)
        LedgerLine(t("文件哈希"), book.fileHash, mono = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerCell(t("入库时间"), F.stamp(book.created))
            LedgerCell(t("文件修改"), F.stamp(book.fileModified))
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(t("元数据"), fontSize = 12.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
        LedgerLine(t("书名"), book.title)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerCell(t("作者"), book.authors)
            LedgerCell(t("出版社"), book.publisher)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerCell(t("序号"), book.number)
            LedgerCell(t("排序序号"), if (book.numberSort > 0) "${book.numberSort}" else "")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerCell(t("出版日期"), F.day(book.releaseDate))
            LedgerCell(t("语言"), book.language)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerCell("ISBN", book.isbn, mono = true)
            LedgerCell(t("分类"), book.genres.joinToString(" / "))
        }
        LedgerLine(t("标签"), book.tags.joinToString(" · "))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerCell(t("显著实体"), if (book.notableCount > 0) t("${book.notableCount} 个") else "")
            LedgerCell(t("锁定字段"), if (book.lockedCount > 0) t("${book.lockedCount} 个") else t("无"))
        }
        if (book.linkCount > 0) {
            LedgerLine(t("外链"), t("${book.linkCount} 个"))
        }
        LedgerLine(t("元数据更新"), F.stamp(book.metaUpdated))
    }
}
