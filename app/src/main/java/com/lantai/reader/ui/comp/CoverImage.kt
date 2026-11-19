package com.lantai.reader.ui.comp

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.components.CoverCache
import com.lantai.reader.model.Store
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

private val TINTS = arrayOf(
    longArrayOf(0xFF2c3a3a, 0xFF101919),
    longArrayOf(0xFF3a2c2c, 0xFF191313),
    longArrayOf(0xFF2b3340, 0xFF121721),
    longArrayOf(0xFF333c2c, 0xFF171b12),
    longArrayOf(0xFF3a3428, 0xFF191710),
    longArrayOf(0xFF2f2b3a, 0xFF141220)
)

private fun tintOf(id: String): LongArray {
    var hash = 0
    for (ch in id) hash = (hash + ch.code) % 97
    return TINTS[hash % TINTS.size]
}

private fun initialOf(title: String): String = if (title.isNotEmpty()) title.substring(0, 1) else t("书")

@Composable
fun CoverImage(
    bookId: String,
    title: String,
    coverWidth: Dp,
    coverHeight: Dp,
    radius: Dp = 6.dp,
    series: Boolean = false,
    modifier: Modifier = Modifier
) {
    val C = Tm.pal
    var bmp by remember(bookId, series) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(bookId, series) { mutableStateOf(false) }
    val tick = Store.uiTick
    val request = max(160, (coverWidth.value * 2).roundToInt())

    LaunchedEffect(bookId, series, tick) {
        if (bookId.isEmpty()) return@LaunchedEffect
        val hit = if (series) CoverCache.cachedSeries(bookId) else CoverCache.cached(bookId)
        if (hit != null) {
            bmp = hit
            failed = false
            return@LaunchedEffect
        }
        if (bmp != null) return@LaunchedEffect
        var retried = false
        while (bmp == null) {
            try {
                bmp = CoverCache.load(bookId, request, series)
                failed = false
            } catch (e: Exception) {
                failed = true
                if (retried) break
                retried = true
                delay(2500)
            }
        }
    }

    Box(
        modifier
            .size(coverWidth, coverHeight)
            .clip(RoundedCornerShape(radius))
            .background(C.bg3)
    ) {
        val image = bmp
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val tint = tintOf(bookId)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(tint[0]), Color(tint[1])))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initialOf(title),
                    fontSize = max(16f, coverWidth.value * 0.42f).sp,
                    color = if (failed) C.tx4 else Color(0xFFc9c3b2),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
