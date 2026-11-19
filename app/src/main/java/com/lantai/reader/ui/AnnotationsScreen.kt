package com.lantai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.BookAnnotation
import com.lantai.reader.model.Store
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.EmptyView
import com.lantai.reader.ui.comp.ErrorView
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.ui.comp.LoadingView
import com.lantai.reader.ui.comp.ScreenHead
import com.lantai.reader.ui.comp.SegBar
import kotlinx.coroutines.launch

private data class NoteEntry(
    val id: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val quote: String = "",
    val note: String = "",
    val locator: String = "",
    val at: String = "",
    val color: String = "",
    val marker: String = ""
)

private const val MAX_BOOKS = 12
private const val FALLBACK_COLOR = "#C9A24B"

private fun safeColor(raw: String): Color {
    if (raw.length == 7 && raw.startsWith("#")) {
        return try {
            Color(android.graphics.Color.parseColor(raw))
        } catch (e: Throwable) {
            Color(android.graphics.Color.parseColor(FALLBACK_COLOR))
        }
    }
    if (raw.length == 9 && raw.startsWith("#")) {
        return try {
            Color(android.graphics.Color.parseColor(raw))
        } catch (e: Throwable) {
            Color(android.graphics.Color.parseColor(FALLBACK_COLOR))
        }
    }
    return Color(android.graphics.Color.parseColor(FALLBACK_COLOR))
}

private fun shortLocator(href: String, fragment: String): String {
    val raw = if (fragment.isNotEmpty()) fragment else href
    if (raw.isEmpty()) return t("无定位")
    val cut = raw.lastIndexOf('/')
    val tail = if (cut >= 0) raw.substring(cut + 1) else raw
    return if (tail.length > 28) "${tail.substring(0, 28)}…" else tail
}

@Composable
private fun HighlightCard(entry: NoteEntry) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(Dp2.rS.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rS.dp))
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(safeColor(entry.color)))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                if (entry.quote.isNotEmpty()) entry.quote else t("（无文本片段）"),
                fontSize = 12.5.sp,
                color = C.tx,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${entry.bookTitle} · ${entry.locator}",
                    fontSize = 9.5.sp,
                    color = C.tx4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(F.ago(entry.at), fontSize = 9.5.sp, color = C.tx4)
            }
        }
    }
}

@Composable
private fun BookmarkRow(entry: NoteEntry) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoverImage(
            bookId = entry.bookId,
            title = entry.bookTitle,
            coverWidth = 44.dp,
            coverHeight = 62.dp,
            radius = 4.dp
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                entry.bookTitle,
                fontSize = 12.5.sp,
                color = C.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                entry.locator,
                fontSize = 10.sp,
                color = C.tx3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (entry.at.isNotEmpty()) t("${F.ago(entry.at)} 添加") else "",
                fontSize = 9.5.sp,
                color = C.tx4,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NoteCardRow(entry: NoteEntry) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(Dp2.rS.dp))
            .background(C.bg1)
            .border(1.dp, safeColor(entry.color), RoundedCornerShape(Dp2.rS.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            "「${entry.quote}」",
            fontSize = 12.sp,
            color = C.acc2,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            t("注：${entry.note}"),
            fontSize = 11.sp,
            color = C.tx2,
            lineHeight = 18.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${entry.bookTitle} · ${entry.locator}",
                fontSize = 9.5.sp,
                color = C.tx4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(F.ago(entry.at), fontSize = 9.5.sp, color = C.tx4)
        }
    }
}

@Composable
fun AnnotationsScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<NoteEntry>>(emptyList()) }
    var scanned by remember { mutableStateOf(0) }
    var booksWithNotes by remember { mutableStateOf(0) }

    fun ofTab(index: Int): List<NoteEntry> = entries.filter { entry ->
        when (index) {
            2 -> entry.note.isNotEmpty()
            1 -> entry.marker == "BOOKMARK" && entry.note.isEmpty()
            else -> entry.marker != "BOOKMARK" && entry.note.isEmpty()
        }
    }

    suspend fun reload() {
        loading = true
        error = ""
        try {
            val stats = Store.api.readingStats()
            val sorted = stats.books.sortedByDescending { F.moment(it.lastReadAt) }
            val head = sorted.take(MAX_BOOKS)
            val collected = ArrayList<NoteEntry>()
            var books = 0
            var fails = 0
            for (stat in head) {
                val bookId = stat.bookId
                val title = try {
                    Store.api.book(bookId).title
                } catch (e: Throwable) {
                    t("已不在书库")
                }
                val list: List<BookAnnotation> = try {
                    Store.api.annotations(bookId)
                } catch (e: Throwable) {
                    fails += 1
                    emptyList()
                }
                if (list.isNotEmpty()) books += 1
                for (item in list) {
                    collected.add(
                        NoteEntry(
                            id = item.id,
                            bookId = bookId,
                            bookTitle = title,
                            quote = item.highlight,
                            note = item.note,
                            locator = shortLocator(item.href, ""),
                            at = item.createdDate,
                            color = item.color,
                            marker = item.marker
                        )
                    )
                }
            }
            entries = collected
            scanned = head.size
            booksWithNotes = books
            if (head.isNotEmpty() && fails == head.size) {
                error = t("批注接口无响应，请检查服务器版本")
            }
            loading = false
        } catch (e: Throwable) {
            error = e.message ?: t("读取失败")
            entries = emptyList()
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(
            title = t("批注与书签"),
            sub = if (loading) t("汇总中…") else t("近 $scanned 本在读 · 共 ${entries.size} 条"),
            back = t("我的"),
            action = "⋯",
            onBack = { Nav.pop() }
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Dp2.page.dp, end = Dp2.page.dp)
        ) {
            when {
                loading -> Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    LoadingView(label = t("汇总批注"))
                }

                error.isNotEmpty() -> Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    ErrorView(message = error, onRetry = { scope.launch { reload() } })
                }

                else -> {
                    SegBar(
                        options = listOf("${t("高亮")} ${ofTab(0).size}", "${t("书签")} ${ofTab(1).size}", "${t("笔记")} ${ofTab(2).size}"),
                        current = tab,
                        onPick = { tab = it }
                    )

                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        val list = ofTab(tab)
                        if (list.isEmpty()) {
                            EmptyView(
                                title = when (tab) {
                                    0 -> t("没有高亮")
                                    1 -> t("没有书签")
                                    else -> t("没有笔记")
                                },
                                hint = t("在阅读器里长按选段即可添加"),
                                glyph = if (tab == 1) "bookmark" else "marker"
                            )
                        } else {
                            list.forEach { entry ->
                                when (tab) {
                                    0 -> HighlightCard(entry)
                                    1 -> BookmarkRow(entry)
                                    else -> NoteCardRow(entry)
                                }
                            }
                        }
                    }

                    GroupHead(title = t("说明"))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dp2.rM.dp))
                            .background(C.bg1)
                            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                            .padding(13.dp)
                    ) {
                        Text(
                            t("批注接口按书查询，本页汇总「最近读过的 $scanned 本」中 $booksWithNotes 本的记录。定位用文本三分段，字号变化后仍能找回；marker 含 HIGHLIGHT / UNDERLINE / BOOKMARK / COMMENT。"),
                            fontSize = 9.5.sp,
                            color = C.tx4,
                            lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
