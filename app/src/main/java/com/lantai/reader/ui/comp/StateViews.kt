package com.lantai.reader.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t

@Composable
fun LoadingView(label: String = t("加载中"), modifier: Modifier = Modifier) {
    val C = Tm.pal
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(34.dp),
            color = C.acc,
            strokeWidth = 2.5.dp
        )
        Text(label, fontSize = 12.sp, color = C.tx3)
    }
}

@Composable
fun EmptyView(
    title: String = t("暂无内容"),
    hint: String = "",
    glyph: String = "book",
    modifier: Modifier = Modifier
) {
    val C = Tm.pal
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Glyph(glyph, 26.dp, C.tx4, 1.4f)
        Text(title, fontSize = 13.sp, color = C.tx2, textAlign = TextAlign.Center)
        if (hint.isNotEmpty()) {
            Text(hint, fontSize = 11.sp, color = C.tx4, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String = t("重试"),
    onRetry: () -> Unit
) {
    val C = Tm.pal
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, fontSize = 12.sp, color = C.bad, textAlign = TextAlign.Center)
        LtButton(
            label = actionLabel,
            primary = false,
            height = 32.dp,
            fontSize = 12f,
            onClick = onRetry
        )
    }
}

@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    meta: String = "",
    action: String = "",
    onAction: () -> Unit = {}
) {
    val C = Tm.pal
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontFamily = Disp,
            letterSpacing = 1.sp,
            color = C.tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (meta.isNotEmpty()) {
            Text(meta, fontSize = 10.sp, fontFamily = Mono, color = C.tx4, maxLines = 1)
        }
        Box(Modifier.weight(1f))
        if (action.isNotEmpty()) {
            Row(
                Modifier.clickable { onAction() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(action, fontSize = 11.sp, color = C.tx3, maxLines = 1)
                Glyph("next", 13.dp, C.tx3)
            }
        }
    }
}

@Composable
fun ProgressBar(percent: Int, modifier: Modifier = Modifier, barHeight: Dp = 3.dp) {
    val C = Tm.pal
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(barHeight))
                .background(C.bg4)
        )
        Box(
            Modifier
                .fillMaxWidth(maxOf(0, minOf(100, percent)) / 100f)
                .height(barHeight)
                .clip(RoundedCornerShape(barHeight))
                .background(C.acc)
        )
    }
}
