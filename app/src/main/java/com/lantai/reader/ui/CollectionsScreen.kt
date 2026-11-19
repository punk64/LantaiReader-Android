package com.lantai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.BookQuery
import com.lantai.reader.model.CollectionCard
import com.lantai.reader.model.Store
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.EmptyView
import com.lantai.reader.ui.comp.ErrorView
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.ui.comp.LoadingView
import com.lantai.reader.ui.comp.ScreenHead
import kotlinx.coroutines.launch

private val COLLECTION_TINTS = listOf(
    Pair(0xFF2C3A3A, 0xFF101919),
    Pair(0xFF3A2C2C, 0xFF191313),
    Pair(0xFF2B3340, 0xFF121721),
    Pair(0xFF333C2C, 0xFF171B12),
    Pair(0xFF3A3340, 0xFF181420),
    Pair(0xFF2C3A35, 0xFF101915)
)

@Composable
private fun CollectionTile(item: CollectionCard, tint: Int, onTap: () -> Unit) {
    val C = Tm.pal
    val pair = COLLECTION_TINTS[tint % COLLECTION_TINTS.size]
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .clickable { onTap() }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(Brush.linearGradient(listOf(Color(pair.first), Color(pair.second))))
        ) {
            Text(
                if (item.name.isNotEmpty()) item.name.substring(0, 1) else t("集"),
                fontSize = 30.sp,
                color = Color(0xFFD6C9A8).copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                if (item.seriesCount > 0) t("${item.seriesCount} 个系列") else t("空系列合集"),
                fontSize = 9.sp,
                color = Color(0xFF918A76),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 9.dp)
            )
        }
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                item.name,
                fontSize = 13.5.sp,
                color = C.tx,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (item.at.isNotEmpty()) F.ago(item.at) else t("未记录时间"),
                fontSize = 9.5.sp,
                color = C.tx4,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RecentRow(book: BookItem, marks: Int, onTap: () -> Unit) {
    val C = Tm.pal
    val page = if (marks >= 0) ProgressMarks.pageOf(book.id, book.readPage) else book.readPage
    val done = ProgressMarks.doneOf(book.id, book.readCompleted, page, book.pages)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoverImage(bookId = book.id, title = book.title, coverWidth = 44.dp, coverHeight = 62.dp, radius = 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                book.title,
                fontSize = 12.5.sp,
                color = C.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${if (book.authors.isNotEmpty()) book.authors else book.profile} · ${F.ago(book.created)}",
                fontSize = 9.5.sp,
                color = C.tx4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                F.progressLabel(page, book.pages, done),
                fontSize = 9.5.sp,
                color = if (done) C.ok else C.tx4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text("›", fontSize = 14.sp, color = C.tx4)
    }
}

@Composable
fun CollectionsScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<CollectionCard>>(emptyList()) }
    var recent by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf(false) }
    val marks = ProgressMarks.ver

    suspend fun reload() {
        loading = true
        error = ""
        try {
            items = Store.api.collections()
        } catch (e: Throwable) {
            items = emptyList()
            error = e.message ?: t("读取失败")
        }
        loading = false
        try {
            recent = Store.api.books(BookQuery(size = 4, sort = "created,desc")).items
        } catch (e: Throwable) {
            recent = emptyList()
        }
    }

    LaunchedEffect(Unit) { reload() }

    fun open(item: CollectionCard) {
        if (opening) return
        opening = true
        scope.launch {
            try {
                val seriesId = Store.api.collectionFirstSeries(item.id)
                if (seriesId.isEmpty()) {
                    Tip.show(t("这个系列合集里还没有系列"))
                } else {
                    Store.selectedSeriesId = seriesId
                    Nav.push("series")
                }
            } catch (e: Throwable) {
                Tip.show(t("打开失败：${e.message ?: ""}"))
            }
            opening = false
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(
            title = t("系列合集"),
            sub = if (items.isNotEmpty()) {
                t("${items.size} 个系列合集 · 合计 ${items.sumOf { it.seriesCount }} 个系列")
            } else {
                t("按系列归集")
            },
            back = t("我的"),
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
                loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    LoadingView(label = t("读取系列合集"))
                }

                error.isNotEmpty() -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    ErrorView(message = error, onRetry = { scope.launch { reload() } })
                }

                items.isEmpty() -> EmptyView(
                    title = t("服务端还没有系列合集"),
                    hint = t("系列合集来自 Calibre 的 Collection 字段或服务端手动建立，建立后这里会自动出现"),
                    glyph = "grid",
                    modifier = Modifier.height(240.dp)
                )

                else -> {
                    items.chunked(2).forEachIndexed { rowIndex, rowItems ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEachIndexed { colIndex, item ->
                                Box(Modifier.weight(1f)) {
                                    CollectionTile(item, rowIndex * 2 + colIndex) { open(item) }
                                }
                            }
                            if (rowItems.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                GroupHead(title = t("最近加入"), meta = t("${recent.size} 册"))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dp2.rM.dp))
                        .background(C.bg1)
                        .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                        .padding(horizontal = 13.dp)
                ) {
                    recent.forEach { book ->
                        RecentRow(book, marks) {
                            Store.selected = book
                            Nav.push("book")
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
