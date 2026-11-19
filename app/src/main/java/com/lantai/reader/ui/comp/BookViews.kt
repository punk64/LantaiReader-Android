package com.lantai.reader.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.F
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.profileLabel
import kotlin.math.max
import kotlin.math.min

@Composable
fun CoverProgress(percent: Int, modifier: Modifier = Modifier, barHeight: Dp = 3.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(if (percent > 0) Color(0x4DFFFFFF) else Color(0x26FFFFFF))
    ) {
        if (percent > 0) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(min(100, max(1, percent)) / 100f)
                    .background(Tm.pal.acc)
            )
        }
    }
}

private fun metaOf(book: BookItem, showSeries: Boolean): String {
    val parts = ArrayList<String>()
    if (showSeries && book.seriesTitle.isNotEmpty()) parts.add(book.seriesTitle)
    parts.add(profileLabel(book.profile, book.mediaStatus))
    if (book.pages > 0) parts.add(t("${book.pages} 页"))
    return parts.joinToString(" · ")
}

private fun progressTextOf(page: Int, pages: Int, done: Boolean, seriesBooks: Int, seriesRead: Int): String {
    val own = F.progressLabel(page, pages, done)
    if (seriesBooks <= 0) return own
    return t("$own · 系列 $seriesRead/$seriesBooks")
}

@Composable
fun BookRow(
    book: BookItem,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    showSeries: Boolean = true,
    seriesBooks: Int = 0,
    seriesRead: Int = 0,
    onTap: () -> Unit
) {
    val C = Tm.pal
    ProgressMarks.ver
    val page = ProgressMarks.pageOf(book.id, book.readPage)
    val done = ProgressMarks.doneOf(book.id, book.readCompleted, page, book.pages)
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(58.dp, 82.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            CoverImage(book.id, book.title, 58.dp, 82.dp, 5.dp)
            if (showProgress) {
                CoverProgress(
                    percent = if (done) 100 else F.percent(page, book.pages),
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
        Column(Modifier.weight(1f).height(82.dp)) {
            Text(
                book.title,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = C.tx,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
            Text(
                metaOf(book, showSeries),
                fontSize = 10.5f.sp,
                color = C.tx3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                progressTextOf(page, book.pages, done, seriesBooks, seriesRead),
                fontSize = 10.5f.sp,
                color = if (done) C.ok else C.tx3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun BookGridCell(
    book: BookItem,
    cellWidth: Dp,
    modifier: Modifier = Modifier,
    seriesBooks: Int = 0,
    seriesRead: Int = 0,
    onTap: () -> Unit
) {
    val C = Tm.pal
    ProgressMarks.ver
    val page = ProgressMarks.pageOf(book.id, book.readPage)
    val done = ProgressMarks.doneOf(book.id, book.readCompleted, page, book.pages)
    Column(modifier.width(cellWidth).clickable { onTap() }) {
        Box(
            Modifier
                .size(cellWidth, cellWidth * 1.42f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            CoverImage(book.id, book.title, cellWidth, cellWidth * 1.42f, 6.dp)
            CoverProgress(
                percent = if (done) 100 else F.percent(page, book.pages),
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
        Text(
            book.title,
            fontSize = 11.5f.sp,
            lineHeight = 15.sp,
            color = C.tx,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(30.dp)
        )
        Text(
            if (book.seriesTitle.isNotEmpty()) book.seriesTitle else profileLabel(book.profile, book.mediaStatus),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = C.tx4,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(14.dp)
        )
        Text(
            progressTextOf(page, book.pages, done, seriesBooks, seriesRead),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = if (done) C.ok else C.tx4,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 1.dp).height(14.dp)
        )
    }
}

@Composable
fun FormatBadge(label: String, tone: String = "normal", modifier: Modifier = Modifier) {
    val C = Tm.pal
    Text(
        label,
        fontSize = 9.sp,
        color = if (tone == "done") C.ok else C.tx3,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, if (tone == "done") C.ok else C.line2, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
fun Chip(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    count: Int = -1,
    compact: Boolean = false,
    onTap: () -> Unit
) {
    val C = Tm.pal
    Row(
        modifier
            .height(if (compact) 24.dp else 28.dp)
            .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .background(if (active) C.accBg else C.bg2)
            .border(
                1.dp,
                if (active) C.accLine else C.line,
                RoundedCornerShape(if (compact) 12.dp else 14.dp)
            )
            .clickable { onTap() }
            .padding(horizontal = if (compact) 9.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            fontSize = if (compact) 10.5f.sp else 11.sp,
            color = if (active) C.acc2 else C.tx3,
            maxLines = 1
        )
        if (count >= 0) {
            Text("$count", fontSize = 9.sp, color = if (active) C.acc2 else C.tx4, maxLines = 1)
        }
    }
}
