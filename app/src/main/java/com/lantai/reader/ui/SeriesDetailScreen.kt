package com.lantai.reader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.common.tf
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.ReadListSummary
import com.lantai.reader.model.SeriesItem
import com.lantai.reader.model.Store
import com.lantai.reader.model.isEbook
import com.lantai.reader.model.profileLabel
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.CoverProgress
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.FormatBadge
import com.lantai.reader.ui.comp.Mono
import com.lantai.reader.ui.comp.ScreenHead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private class SeriesPickOption(
    val id: String = "",
    val name: String = "",
    val count: Int = 0,
    val contains: Boolean = false,
    val ids: List<String> = emptyList()
)

@Composable
private fun SeriesKv(label: String, value: String) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 11.sp, color = C.tx4, modifier = Modifier.width(74.dp))
        Text(
            if (value.isNotEmpty()) value else "—",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = C.tx2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LineDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Tm.pal.line))
}

@Composable
private fun SeriesRing(percent: Int, size: Dp = 46.dp) {
    val C = Tm.pal
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 4.dp.toPx()
            val radius = (this.size.width - stroke) / 2f
            drawCircle(color = C.bg4, radius = radius, style = Stroke(stroke))
            if (percent > 0) {
                drawArc(
                    color = C.acc,
                    startAngle = -90f,
                    sweepAngle = 360f * min(100, percent) / 100f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text("$percent%", fontSize = 10.sp, fontFamily = Mono, color = C.acc2)
    }
}

@Composable
private fun VolumeRow(book: BookItem, onTap: () -> Unit) {
    val C = Tm.pal
    val page = ProgressMarks.pageOf(book.id, book.readPage)
    val done = ProgressMarks.doneOf(book.id, book.readCompleted, page, book.pages)
    Column(Modifier.fillMaxWidth().clickable { onTap() }) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(4.dp))) {
                CoverImage(book.id, book.title, 40.dp, 56.dp, 4.dp)
                CoverProgress(
                    percent = if (done) 100 else F.percent(page, book.pages),
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    book.title,
                    fontSize = 12.5f.sp,
                    color = C.tx,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FormatBadge(profileLabel(book.profile, book.mediaStatus))
                    if (book.number.isNotEmpty()) FormatBadge("#${book.number}")
                    if (book.pages > 0) FormatBadge(t("${book.pages} 页"))
                }
                Text(
                    F.progressLabel(page, book.pages, done),
                    fontSize = 9.5f.sp,
                    color = if (done) C.ok else C.tx4,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Glyph("next", 13.dp, C.tx4)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    }
}

@Composable
private fun RowScope.SeriesCell(glyph: String, label: String, active: Boolean = false, onTap: () -> Unit) {
    val C = Tm.pal
    Column(
        Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) C.accBg else C.bg3)
            .border(1.dp, if (active) C.accLine else C.line, RoundedCornerShape(9.dp))
            .clickable { onTap() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Glyph(glyph, 16.dp, if (active) C.acc2 else C.tx3)
        Text(
            label,
            fontSize = 9.5f.sp,
            color = if (active) C.acc2 else C.tx3,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private class SeriesCtl(val scope: CoroutineScope) {
    var series by mutableStateOf(SeriesItem())
    var books by mutableStateOf<List<BookItem>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf("")
    var showInfo by mutableStateOf(false)
    var showSummary by mutableStateOf(false)
    var page by mutableStateOf(0)
    var sheet by mutableStateOf("")
    var options by mutableStateOf<List<SeriesPickOption>>(emptyList())
    var newName by mutableStateOf("")
    var busy by mutableStateOf(false)
    var sheetNote by mutableStateOf("")

    fun boot() {
        scope.launch { load() }
    }

    suspend fun load() {
        val id = Store.selectedSeriesId
        if (id.isEmpty()) {
            loading = false
            error = t("未选择系列")
            return
        }
        try {
            series = Store.api.series(id)
            books = Store.api.seriesBooks(id)
            loading = false
        } catch (e: Throwable) {
            loading = false
            error = e.message ?: ""
        }
    }

    fun totalCount(): Int = if (series.booksCount > 0) series.booksCount else books.size

    fun doneCount(): Int {
        if (books.isEmpty()) return series.readCount
        return books.count { ProgressMarks.doneOf(it.id, it.readCompleted, ProgressMarks.pageOf(it.id, it.readPage), it.pages) }
    }

    fun readingCount(): Int {
        if (books.isEmpty()) return series.inProgressCount
        return books.count {
            val p = ProgressMarks.pageOf(it.id, it.readPage)
            p > 0 && !ProgressMarks.doneOf(it.id, it.readCompleted, p, it.pages)
        }
    }

    fun percent(): Int {
        val total = totalCount()
        if (total <= 0) return 0
        val touched = min(total, doneCount() + readingCount())
        return max(0, (touched * 100 + total / 2) / total)
    }

    fun pageSize(): Int = 24

    fun pageCount(): Int = max(1, (books.size + pageSize() - 1) / pageSize())

    fun shownBooks(): List<BookItem> {
        val current = min(page, pageCount() - 1)
        val from = current * pageSize()
        val to = min(from + pageSize(), books.size)
        if (from >= to) return emptyList()
        return books.subList(from, to)
    }

    fun bookIds(): List<String> = books.map { it.id }

    fun failText(e: Throwable): String {
        val text = e.message ?: ""
        return if (text.contains("403")) t("当前账号无管理员权限") else text
    }

    fun coverId(): String = if (books.isNotEmpty()) books[0].id else ""

    fun coverTitle(): String = if (books.isNotEmpty()) books[0].title else series.name

    fun nextBook(): BookItem? {
        books.firstOrNull {
            val p = ProgressMarks.pageOf(it.id, it.readPage)
            p > 0 && !ProgressMarks.doneOf(it.id, it.readCompleted, p, it.pages)
        }?.let { return it }
        books.firstOrNull {
            !ProgressMarks.doneOf(it.id, it.readCompleted, ProgressMarks.pageOf(it.id, it.readPage), it.pages)
        }?.let { return it }
        return books.firstOrNull()
    }

    fun actionLabel(): String {
        val book = nextBook() ?: return t("开始阅读")
        val number = if (book.number.isNotEmpty()) book.number else "${books.indexOf(book) + 1}"
        return if (ProgressMarks.pageOf(book.id, book.readPage) > 0) t("继续第 $number 册") else t("开始第 $number 册")
    }

    fun openBook(book: BookItem) {
        Store.selected = book
        Nav.push("book")
    }

    fun openCollections() {
        sheet = "collection"
        options = emptyList()
        newName = ""
        sheetNote = t("读取中…")
        scope.launch {
            try {
                val list = Store.api.collections()
                val sid = Store.selectedSeriesId
                options = list.map {
                    SeriesPickOption(
                        id = it.id,
                        name = it.name,
                        count = it.seriesCount,
                        contains = it.seriesIds.contains(sid),
                        ids = it.seriesIds
                    )
                }
                sheetNote = if (options.isEmpty()) t("服务端还没有系列合集，可在下方新建") else ""
            } catch (e: Throwable) {
                sheetNote = t("读取失败：${failText(e)}")
            }
        }
    }

    fun openReadLists() {
        sheet = "readlist"
        options = emptyList()
        newName = ""
        sheetNote = t("读取中…")
        scope.launch {
            try {
                val list: List<ReadListSummary> = Store.api.readlists()
                val ids = bookIds()
                options = list.map { item ->
                    var all = ids.isNotEmpty()
                    var cursor = 0
                    while (cursor < ids.size) {
                        if (!item.bookIds.contains(ids[cursor])) {
                            all = false
                            break
                        }
                        cursor++
                    }
                    SeriesPickOption(
                        id = item.id,
                        name = item.name,
                        count = item.bookCount,
                        contains = all,
                        ids = item.bookIds
                    )
                }
                sheetNote = if (options.isEmpty()) t("服务端还没有书单，可在下方新建") else ""
            } catch (e: Throwable) {
                sheetNote = t("读取失败：${failText(e)}")
            }
        }
    }

    fun pick(option: SeriesPickOption) {
        if (busy) return
        if (option.contains) {
            Tip.show(if (sheet == "collection") t("该系列已在此系列合集") else t("该系列已在此书单"))
            return
        }
        busy = true
        scope.launch {
            try {
                if (sheet == "collection") {
                    Store.api.updateCollectionSeries(option.id, option.ids + Store.selectedSeriesId)
                    Tip.show(t("已加入《${option.name}》"))
                } else {
                    val ids = bookIds()
                    Store.api.updateReadListBooks(option.id, option.ids + ids)
                    Tip.show(t("已把 ${books.size} 册加入《${option.name}》"))
                }
                sheet = ""
            } catch (e: Throwable) {
                Tip.show(t("操作失败：${failText(e)}"))
            }
            busy = false
        }
    }

    fun createAndJoin() {
        if (busy) return
        val name = newName.trim()
        if (name.isEmpty()) {
            Tip.show(t("先填一个名称"))
            return
        }
        busy = true
        scope.launch {
            try {
                if (sheet == "collection") {
                    Store.api.createCollection(name, listOf(Store.selectedSeriesId))
                    Tip.show(t("已新建系列合集《$name》并加入"))
                } else {
                    val ids = bookIds()
                    Store.api.createReadList(name, ids)
                    Tip.show(t("已新建书单《$name》并加入 ${ids.size} 册"))
                }
                sheet = ""
            } catch (e: Throwable) {
                Tip.show(t("新建失败：${failText(e)}"))
            }
            busy = false
        }
    }
}

@Composable
private fun Crumb(ctl: SeriesCtl) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            t("首页"),
            fontSize = 10.5f.sp,
            color = C.tx3,
            modifier = Modifier.clickable { Nav.pop() }
        )
        Glyph("next", 11.dp, C.tx4)
        Text(t("系列"), fontSize = 10.5f.sp, color = C.tx3)
        Glyph("next", 11.dp, C.tx4)
        Text(
            ctl.series.name,
            fontSize = 10.5f.sp,
            color = C.tx2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Head(ctl: SeriesCtl) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        CoverImage(ctl.coverId(), ctl.coverTitle(), 104.dp, 145.dp, 8.dp)
        Column(Modifier.weight(1f).height(145.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FormatBadge(
                    when (ctl.series.status) {
                        "ENDED" -> t("已完结")
                        "ONGOING" -> t("连载中")
                        else -> t("状态未知")
                    }
                )
                FormatBadge(t("${ctl.series.booksCount} 册"))
            }
            Text(
                ctl.series.name,
                fontSize = 18.sp,
                fontFamily = Disp,
                letterSpacing = 0.6.sp,
                lineHeight = 23.sp,
                color = C.tx,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(
                if (ctl.series.publisher.isNotEmpty()) ctl.series.publisher else ctl.series.genres.joinToString(" / "),
                fontSize = 11.sp,
                color = C.tx3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeriesRing(ctl.percent())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        t("已读 ${ctl.doneCount()} / ${ctl.totalCount()} 册"),
                        fontSize = 10.5f.sp,
                        color = C.tx2,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (ctl.readingCount() > 0) t("在读 ${ctl.readingCount()} 册") else t("暂无在读"),
                        fontSize = 9.5f.sp,
                        color = C.tx4,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBox(ctl: SeriesCtl) {
    val C = Tm.pal
    if (ctl.series.summary.isEmpty()) return
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
            ctl.series.summary,
            fontSize = 11.5f.sp,
            lineHeight = 20.sp,
            color = C.tx2,
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
private fun Dock(ctl: SeriesCtl) {
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
            Row(
                Modifier
                    .weight(1.6f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.acc)
                    .clickable {
                        ctl.nextBook()?.let { ctl.openBook(it) }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Glyph("book", 15.dp, C.bg)
                Text(
                    ctl.actionLabel(),
                    fontSize = 12.5f.sp,
                    color = C.bg,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.bg3)
                    .border(1.dp, C.line, RoundedCornerShape(10.dp))
                    .clickable {
                        val book = ctl.nextBook() ?: return@clickable
                        Store.readerIncognito = true
                        Store.readerBookId = book.id
                        Store.readerTitle = book.title
                        Store.readerChapter = maxOf(1, ProgressMarks.pageOf(book.id, book.readPage))
                        Nav.push(if (isEbook(book.profile)) "reader" else "comic")
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Glyph("eyeOff", 15.dp, C.tx2)
                Text(
                    t("隐身阅读"),
                    fontSize = 12.5f.sp,
                    color = C.tx2,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SeriesCell("info", t("系列信息"), ctl.showInfo) { ctl.showInfo = !ctl.showInfo }
            SeriesCell("collection", t("加入系列合集")) { ctl.openCollections() }
            SeriesCell("readlist", t("加入书单")) { ctl.openReadLists() }
        }
    }
}

@Composable
private fun VolumeList(ctl: SeriesCtl) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            t("分册"),
            fontSize = 16.sp,
            fontFamily = Disp,
            letterSpacing = 1.sp,
            color = C.tx
        )
        Text("${ctl.books.size}", fontSize = 10.sp, fontFamily = Mono, color = C.tx4)
        Spacer(Modifier.weight(1f))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(horizontal = 13.dp)
    ) {
        if (ctl.books.isEmpty()) {
            Text(
                if (ctl.loading) t("分册读取中…") else t("该系列暂无分册"),
                fontSize = 11.sp,
                color = C.tx4,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        } else {
            ctl.shownBooks().forEach { book ->
                VolumeRow(book) { ctl.openBook(book) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.bg3)
                        .border(1.dp, C.line, RoundedCornerShape(8.dp))
                        .clickable { if (ctl.page > 0) ctl.page-- },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        t("‹ 上一页"),
                        fontSize = 10.5f.sp,
                        color = if (ctl.page <= 0) C.tx4 else C.tx2
                    )
                }
                Text(
                    t("第 ${min(ctl.page + 1, ctl.pageCount())} / ${ctl.pageCount()} 页"),
                    fontSize = 9.5f.sp,
                    fontFamily = Mono,
                    color = C.tx4
                )
                Row(
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.bg3)
                        .border(1.dp, C.line, RoundedCornerShape(8.dp))
                        .clickable { if (ctl.page < ctl.pageCount() - 1) ctl.page++ },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        t("下一页 ›"),
                        fontSize = 10.5f.sp,
                        color = if (ctl.page >= ctl.pageCount() - 1) C.tx4 else C.tx2
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(ctl: SeriesCtl) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(13.dp)
    ) {
        Text(
            t("系列信息"),
            fontSize = 12.sp,
            color = C.tx,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        LineDivider()
        SeriesKv(t("系列名"), ctl.series.name)
        LineDivider()
        SeriesKv(t("出版社"), ctl.series.publisher)
        LineDivider()
        SeriesKv(t("语言"), ctl.series.language)
        LineDivider()
        SeriesKv(t("分类"), ctl.series.genres.joinToString(" / "))
        LineDivider()
        SeriesKv(t("标签"), ctl.series.tags.joinToString(" · "))
        LineDivider()
        SeriesKv(
            t("状态"),
            when (ctl.series.status) {
                "ENDED" -> t("已完结")
                "ONGOING" -> t("连载中")
                else -> ctl.series.status
            }
        )
        LineDivider()
        SeriesKv(
            t("总册数"),
            if (ctl.series.totalBookCount > 0) "${ctl.series.totalBookCount}" else "${ctl.series.booksCount}"
        )
        LineDivider()
        SeriesKv(t("入库时间"), F.stamp(ctl.series.created))
        LineDivider()
        SeriesKv(t("最近更新"), F.stamp(ctl.series.lastModified))
    }
}

@Composable
private fun SheetLayer(ctl: SeriesCtl) {
    val C = Tm.pal
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable { ctl.sheet = "" }
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Dp2.rL.dp, topEnd = Dp2.rL.dp))
                .background(C.bg1)
                .border(
                    1.dp,
                    C.line,
                    RoundedCornerShape(topStart = Dp2.rL.dp, topEnd = Dp2.rL.dp)
                )
                .padding(start = Dp2.page.dp, end = Dp2.page.dp, top = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ctl.sheet == "collection") t("加入系列合集") else t("加入书单"),
                    fontSize = 14.sp,
                    fontFamily = Disp,
                    letterSpacing = 0.8.sp,
                    color = C.tx
                )
                Spacer(Modifier.weight(1f))
                Text(
                    t("关闭"),
                    fontSize = 11.sp,
                    color = C.tx3,
                    modifier = Modifier.clickable { ctl.sheet = "" }
                )
            }

            Text(
                if (ctl.sheet == "collection") t("选中后把本系列加进该系列合集")
                else t("选中后把本系列 ${ctl.books.size} 册加进该书单"),
                fontSize = 10.sp,
                color = C.tx4,
                modifier = Modifier.fillMaxWidth()
            )

            if (ctl.sheetNote.isNotEmpty()) {
                Text(
                    ctl.sheetNote,
                    fontSize = 10.5f.sp,
                    color = C.tx3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ctl.options.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { ctl.pick(item) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                item.name,
                                fontSize = 12.sp,
                                color = C.tx,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                tf(
                                    "{0} {1}",
                                    listOf(
                                        "${item.count}",
                                        if (ctl.sheet == "collection") t("个系列") else t("册")
                                    )
                                ),
                                fontSize = 9.5f.sp,
                                color = C.tx4,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            if (item.contains) t("已在其中") else t("加入"),
                            fontSize = 10.5f.sp,
                            color = if (item.contains) C.tx4 else C.acc2
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = ctl.newName,
                    onValueChange = { ctl.newName = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 11.5f.sp, color = C.tx),
                    cursorBrush = SolidColor(C.acc),
                    modifier = Modifier.weight(1f).height(36.dp),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(9.dp))
                                .background(C.bg3)
                                .padding(horizontal = 11.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (ctl.newName.isEmpty()) {
                                Text(t("新建名称"), fontSize = 11.5f.sp, color = C.tx4, maxLines = 1)
                            }
                            inner()
                        }
                    }
                )
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (ctl.busy) C.bg3 else C.acc)
                        .clickable { ctl.createAndJoin() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (ctl.sheet == "collection") t("新建系列合集") else t("新建书单"),
                        fontSize = 11.sp,
                        color = C.bg
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    val ctl = remember { SeriesCtl(scope) }
    LaunchedEffect(Unit) { ctl.boot() }
    ProgressMarks.ver

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
            ScreenHead(title = t("系列详情"), back = t("首页"), onBack = { Nav.pop() })

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
                    Head(ctl)
                    SummaryBox(ctl)
                    Dock(ctl)
                    VolumeList(ctl)
                    if (ctl.showInfo) InfoCard(ctl)
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
        if (ctl.sheet.isNotEmpty()) {
            SheetLayer(ctl)
        }
    }
}
