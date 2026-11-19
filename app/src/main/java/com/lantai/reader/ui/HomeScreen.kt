package com.lantai.reader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.BookQuery
import com.lantai.reader.model.HomePayload
import com.lantai.reader.model.Store
import com.lantai.reader.model.isEbook
import com.lantai.reader.model.profileLabel
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.CoverProgress
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.ErrorView
import com.lantai.reader.ui.comp.HomeSkeleton
import com.lantai.reader.ui.comp.Mono
import com.lantai.reader.ui.comp.ProgressBar
import com.lantai.reader.ui.comp.ScreenHead
import com.lantai.reader.ui.comp.SectionHead
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private fun labelOf(profile: String): String = if (profile.isNotEmpty()) profile else "—"

@Composable
private fun ShelfCard(book: BookItem, percent: Int, onTap: () -> Unit) {
    val C = Tm.pal
    Column(Modifier.width(82.dp).clickable { onTap() }) {
        Box(
            Modifier
                .size(82.dp, 115.dp)
                .clip(RoundedCornerShape(7.dp))
        ) {
            CoverImage(book.id, book.title, 82.dp, 115.dp, 7.dp)
            CoverProgress(percent = percent, modifier = Modifier.align(Alignment.BottomStart))
        }
        Text(
            book.title,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = C.tx,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(30.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).height(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(labelOf(book.profile), fontSize = 9.sp, fontFamily = Mono, color = C.tx4)
            Spacer(Modifier.weight(1f))
            Text("$percent%", fontSize = 9.sp, fontFamily = Mono, color = C.acc2)
        }
    }
}

@Composable
private fun HomeShelf(list: List<BookItem>, onTap: (BookItem) -> Unit) {
    val C = Tm.pal
    val scroll = rememberScrollState()
    var contentW by remember { mutableStateOf(0) }
    var viewW by remember { mutableStateOf(0) }
    var railOn by remember { mutableStateOf(true) }
    ProgressMarks.ver

    LaunchedEffect(scroll.value) {
        railOn = true
        kotlinx.coroutines.delay(900)
        railOn = false
    }

    val canScroll = viewW > 0 && contentW > viewW + 2
    val railAlpha by animateFloatAsState(if (railOn) 1f else 0.45f, tween(220))

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { viewW = it.width }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .onSizeChanged { contentW = it.width }
                .padding(start = Dp2.page.dp, end = Dp2.page.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                list.forEach { item ->
                    val page = ProgressMarks.pageOf(item.id, item.readPage)
                    val done = ProgressMarks.doneOf(item.id, item.readCompleted, page, item.pages)
                    ShelfCard(item, if (done) 100 else F.percent(page, item.pages)) { onTap(item) }
                }
            }
        }
        if (canScroll) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = Dp2.page.dp, end = Dp2.page.dp, bottom = 3.dp)
                    .height(2.dp)
                    .drawWithContent {
                        drawContent()
                        val h = size.height
                        val track = size.width
                        val span = contentW - viewW
                        val thumb = max(30.dp.toPx(), min(track, track * viewW / contentW))
                        val at = if (span > 0) max(0f, min(track - thumb, scroll.value.toFloat() / span * (track - thumb))) else 0f
                        drawRoundRect(
                            color = C.line,
                            size = androidx.compose.ui.geometry.Size(track, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
                        )
                        drawRoundRect(
                            color = C.acc,
                            topLeft = androidx.compose.ui.geometry.Offset(at, 0f),
                            size = androidx.compose.ui.geometry.Size(thumb, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
                            alpha = railAlpha
                        )
                    }
            )
        }
    }
}

@Composable
private fun HeroCard(book: BookItem, sub: String, line: String, percent: Int, moment: String, onTap: () -> Unit) {
    val C = Tm.pal
    val shape = RoundedCornerShape(Dp2.rL.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(C.bg2, C.bg1)), shape)
            .border(1.dp, C.accLine, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
            .padding(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(C.ok))
            Text(
                t("继续阅读 · $moment"),
                fontSize = 10.sp,
                color = C.acc2,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CoverImage(book.id, book.title, 88.dp, 124.dp, 6.dp)
            Column(Modifier.weight(1f).height(124.dp)) {
                Text(
                    book.title,
                    fontSize = 19.sp,
                    fontFamily = Disp,
                    letterSpacing = 1.sp,
                    lineHeight = 25.sp,
                    color = C.tx,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    sub,
                    fontSize = 11.5f.sp,
                    color = C.tx3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    line,
                    fontSize = 11.sp,
                    color = C.tx3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
                )
                ProgressBar(percent = percent, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(C.acc),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Glyph("book", 14.dp, C.onAcc)
                        Text(t("继续阅读"), fontSize = 12.5f.sp, color = C.onAcc, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLogout: () -> Unit = {}) {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var home by remember { mutableStateOf(HomePayload()) }
    var unread by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var updated by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var heroBook by remember { mutableStateOf<BookItem?>(null) }
    ProgressMarks.ver

    fun pct(book: BookItem): Int {
        val page = ProgressMarks.pageOf(book.id, book.readPage)
        if (ProgressMarks.doneOf(book.id, book.readCompleted, page, book.pages)) return 100
        return F.percent(page, book.pages)
    }

    suspend fun loadUnread() {
        unread = try {
            Store.api.wantToRead(0, 12).items
        } catch (e: Throwable) {
            emptyList()
        }
    }

    suspend fun loadUpdated() {
        updated = try {
            Store.api.books(BookQuery(size = 12, sort = "lastModified,desc")).items
        } catch (e: Throwable) {
            emptyList()
        }
    }

    suspend fun reload() {
        error = ""
        val first = home.added.isEmpty()
        if (first) loading = true
        try {
            val payload = Store.api.home(12)
            home = payload
            heroBook = payload.continueReading.firstOrNull() ?: payload.recentRead.firstOrNull()
            loading = false
            if (!first) {
                loadUnread()
                loadUpdated()
            }
        } catch (e: Throwable) {
            loading = false
            val message = e.message ?: ""
            if (message.contains(t("会话"))) {
                onLogout()
                return
            }
            error = message
        } finally {
            refreshing = false
        }
    }

    fun openDetail(book: BookItem) {
        Store.selected = book
        Nav.push("book")
    }

    fun openReader(book: BookItem) {
        Store.selected = book
        Store.readerIncognito = false
        Store.localArchive = null
        Store.localArchiveId = ""
        Store.localArchivePage = 1
        Store.readerBookId = book.id
        Store.readerTitle = book.title
        Store.readerChapter = max(1, ProgressMarks.pageOf(book.id, book.readPage))
        if (!isEbook(book.profile)) {
            Nav.push("comic")
            return
        }
        Nav.push("reader")
    }

    fun heroSub(book: BookItem): String {
        val parts = ArrayList<String>()
        if (book.authors.isNotEmpty()) parts.add(book.authors)
        if (book.volume.isNotEmpty()) parts.add(book.volume)
        if (parts.isEmpty()) parts.add(if (book.seriesTitle.isNotEmpty()) book.seriesTitle else profileLabel(book.profile))
        return parts.joinToString(" · ")
    }

    fun heroLine(book: BookItem): String {
        val page = ProgressMarks.pageOf(book.id, book.readPage)
        val at = when {
            page > 0 -> t("第 $page 页")
            book.seriesTitle.isNotEmpty() -> book.seriesTitle
            else -> t("未开始")
        }
        return t("$at · 已读 ${pct(book)}%")
    }

    LaunchedEffect(Unit) {
        scope.launch {
            loadUnread()
            loadUpdated()
            reload()
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            ScreenHead(brand = true)
        }

        if (loading) {
            HomeSkeleton()
        } else if (error.isNotEmpty()) {
            ErrorView(message = error, onRetry = { scope.launch { reload() } })
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch { reload() }
                },
                modifier = Modifier.fillMaxSize()
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(bottom = 100.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dp2.page.dp)
                        .padding(top = 4.dp)
                ) {
                    val hero = heroBook
                    if (hero != null) {
                        HeroCard(
                            book = hero,
                            sub = heroSub(hero),
                            line = heroLine(hero),
                            percent = pct(hero),
                            moment = F.ago(hero.readDate)
                        ) { openReader(hero) }
                    }
                    if (home.recentRead.isNotEmpty()) {
                        SectionHead(title = t("最近在读"), meta = t("${home.recentRead.size} 册"))
                    }
                }

                if (home.recentRead.isNotEmpty()) {
                    HomeShelf(home.recentRead) { openDetail(it) }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = Dp2.page.dp)) {
                    SectionHead(
                        title = t("待读"),
                        meta = if (unread.isNotEmpty()) t("${unread.size} 册") else ""
                    )
                }

                if (unread.isNotEmpty()) {
                    HomeShelf(unread) { openDetail(it) }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dp2.page.dp)
                            .clip(RoundedCornerShape(Dp2.rM.dp))
                            .background(C.bg1)
                            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Glyph("book", 22.dp, C.tx4, 1.4f)
                        Text(
                            t("暂时没有待读的书"),
                            fontSize = 12.sp,
                            color = C.tx2,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            t("在书籍详情点「加入待读」"),
                            fontSize = 10.5f.sp,
                            color = C.tx4,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = Dp2.page.dp)) {
                    if (home.added.isNotEmpty()) {
                        SectionHead(title = t("最近添加"), meta = t("${home.added.size} 册"))
                    }
                }
                if (home.added.isNotEmpty()) {
                    HomeShelf(home.added) { openDetail(it) }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = Dp2.page.dp)) {
                    if (updated.isNotEmpty()) {
                        SectionHead(title = t("最近更新"), meta = t("${updated.size} 册"))
                    }
                }
                if (updated.isNotEmpty()) {
                    HomeShelf(updated) { openDetail(it) }
                }
            }
            }
        }
    }
}
