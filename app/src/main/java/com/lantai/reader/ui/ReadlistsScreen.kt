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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.BookQuery
import com.lantai.reader.model.ReadListSummary
import com.lantai.reader.model.Store
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.EmptyView
import com.lantai.reader.ui.comp.ErrorView
import com.lantai.reader.ui.comp.LoadingView
import com.lantai.reader.ui.comp.Pill
import com.lantai.reader.ui.comp.ScreenHead
import kotlinx.coroutines.launch

@Composable
private fun ListBookRow(book: BookItem, marks: Int, onOpen: () -> Unit) {
    val C = Tm.pal
    val page = if (marks >= 0) ProgressMarks.pageOf(book.id, book.readPage) else book.readPage
    val done = ProgressMarks.doneOf(book.id, book.readCompleted, page, book.pages)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        CoverImage(bookId = book.id, title = book.title, coverWidth = 38.dp, coverHeight = 54.dp, radius = 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                book.title,
                fontSize = 12.sp,
                color = C.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (book.authors.isNotEmpty()) book.authors else book.profile,
                fontSize = 9.5.sp,
                color = C.tx4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            F.progressLabel(page, book.pages, done),
            fontSize = 9.5.sp,
            color = if (done) C.ok else C.tx4
        )
    }
}

@Composable
private fun ReadListCard(
    item: ReadListSummary,
    expanded: Boolean,
    opening: Boolean,
    books: List<BookItem>,
    marks: Int,
    onTap: () -> Unit,
    onOpenBook: (BookItem) -> Unit
) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, if (expanded) C.accLine else C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(13.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onTap() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.accBg)
                    .border(1.dp, C.accLine, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(t("单"), fontSize = 13.sp, color = C.acc2, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.name,
                    fontSize = 14.sp,
                    color = C.tx,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    t("${item.bookCount} 册") + " · " + (if (item.ordered) t("手动排序") else t("默认排序")) +
                        (if (item.lastModified.isNotEmpty()) " · " + F.ago(item.lastModified) else ""),
                    fontSize = 10.sp,
                    color = C.tx4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Pill(label = if (expanded) t("收起") else t("展开"), tone = if (expanded) "acc" else "mute")
        }

        if (item.summary.isNotEmpty()) {
            Text(
                item.summary,
                fontSize = 10.5.sp,
                color = C.tx3,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
            )
        }

        if (expanded) {
            Box(Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 4.dp).height(1.dp).background(C.line))
            when {
                opening -> Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                    LoadingView(label = t("读取书单"))
                }

                books.isEmpty() -> Text(
                    t("这个书单里还没有可读的书"),
                    fontSize = 10.5.sp,
                    color = C.tx4,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp)
                )

                else -> books.forEach { book ->
                    ListBookRow(book, marks) { onOpenBook(book) }
                }
            }
        }
    }
}

@Composable
fun ReadlistsScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf<List<ReadListSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf("") }
    var openBooks by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var opening by remember { mutableStateOf(false) }
    val marks = ProgressMarks.ver

    suspend fun reload() {
        loading = true
        error = ""
        try {
            lists = Store.api.readlists()
        } catch (e: Throwable) {
            error = e.message ?: t("读取失败")
            lists = emptyList()
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    fun toggle(item: ReadListSummary) {
        if (openId == item.id) {
            openId = ""
            openBooks = emptyList()
            return
        }
        openId = item.id
        openBooks = emptyList()
        opening = true
        scope.launch {
            try {
                val page = Store.api.books(BookQuery(readListId = item.id, size = 60, sort = "metadata.title,asc"))
                if (openId == item.id) openBooks = page.items
            } catch (e: Throwable) {
                if (openId == item.id) openBooks = emptyList()
            }
            opening = false
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(
            title = t("书单"),
            sub = if (lists.isNotEmpty()) {
                t("${lists.size} 个书单 · 合计 ${lists.sumOf { it.bookCount }} 册")
            } else {
                t("按阅读计划排序")
            },
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
                loading -> Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    LoadingView(label = t("读取书单"))
                }

                error.isNotEmpty() -> Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    ErrorView(message = error, onRetry = { scope.launch { reload() } })
                }

                lists.isEmpty() -> Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    EmptyView(
                        title = t("还没有书单"),
                        hint = t("在服务端网页版建立书单后，这里会自动出现"),
                        glyph = "list"
                    )
                }

                else -> lists.forEach { item ->
                    ReadListCard(
                        item = item,
                        expanded = openId == item.id,
                        opening = opening && openId == item.id,
                        books = if (openId == item.id) openBooks else emptyList(),
                        marks = marks,
                        onTap = { toggle(item) },
                        onOpenBook = { book ->
                            Store.selected = book
                            Nav.push("book")
                        }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
