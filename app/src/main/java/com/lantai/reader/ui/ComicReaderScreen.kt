package com.lantai.reader.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.ImageOps
import com.lantai.reader.model.PrefKeys
import com.lantai.reader.model.Prefs
import com.lantai.reader.common.ProgressMarks
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.common.tf
import com.lantai.reader.local.ArchiveBook
import com.lantai.reader.local.LocalStore
import com.lantai.reader.model.Store
import com.lantai.reader.net.offlinePage
import com.lantai.reader.net.readingTracker
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.GuideAnchors
import com.lantai.reader.ui.comp.GuideStep
import com.lantai.reader.ui.comp.Mono
import com.lantai.reader.ui.comp.ReaderGuide
import com.lantai.reader.ui.comp.anchored
import com.lantai.reader.ui.comp.comicGuideSteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val StageBg = Color(0xFF0A0B0B)
private val TopFrom = Color(0xF20E100F)
private val TopTo = Color(0xD90A0B0B)
private val BottomFrom = Color(0xD90A0B0B)
private val BottomTo = Color(0xF20E100F)
private val StripLine = Color(0xFF1D2221)
private val StripPaper = Color(0xFFCFC9B9)
private val ThumbErrBg = Color(0xFF2A1A1A)
private val VeilTrack = Color(0xFF1F2624)
private val ThumbPageTx = Color(0xFFE8E3D6)
private val ThumbShadow = Color(0x99000000)

private data class ComicTool(val label: String, val icon: String)

private fun comicTools(): List<ComicTool> = listOf(
    ComicTool(t("缩略图"), "thumbs"),
    ComicTool(t("画质与版式"), "settings"),
    ComicTool(t("书签"), "bookmark")
)

private class ComicCtl(val scope: CoroutineScope) {
    var bar by mutableStateOf(true)
    var page by mutableStateOf(1)
    var thumbs by mutableStateOf(false)
    var bookmarked by mutableStateOf(false)
    var total by mutableStateOf(0)
    var title by mutableStateOf("")
    var incognito by mutableStateOf(false)
    var pix by mutableStateOf<Bitmap?>(null)
    var pix2 by mutableStateOf<Bitmap?>(null)
    var loading by mutableStateOf(false)
    var failed by mutableStateOf("")
    var failed2 by mutableStateOf("")
    var mode by mutableStateOf("paged")
    var panel by mutableStateOf(false)
    var crop by mutableStateOf(false)
    var enhance by mutableStateOf(false)
    var qualityIndex by mutableStateOf(2)
    var zoom by mutableStateOf(1f)
    var stripVersion by mutableStateOf(0)
    var guideOpen by mutableStateOf(false)
    var guideStep by mutableStateOf(0)
    var guideNeedKey by mutableStateOf("")
    var sliderPage by mutableStateOf(0)
    var stageW by mutableStateOf(0f)
    var touchMove by mutableStateOf(0f)
    var safeTop by mutableStateOf(0f)
    var safeBottom by mutableStateOf(0f)
    var bookId = ""
    var localId = ""
    var archive: ArchiveBook? = null
    var swipeTurn = false
    var listState: androidx.compose.foundation.lazy.LazyListState? = null
    val anchors = GuideAnchors()
    val steps: List<GuideStep> = comicGuideSteps()
    private val cache = HashMap<Int, Bitmap>()
    private var guardJob: Job? = null
    private var barJob: Job? = null
    private var progressAt = 0L
    private var scrollTarget = 1

    suspend fun boot() {
        val modeIndex = Prefs.int(PrefKeys.cmMode, 0)
        mode = when (modeIndex) {
            2 -> "scroll"
            1 -> "spread"
            else -> "paged"
        }
        swipeTurn = modeIndex == 3
        qualityIndex = Prefs.int(PrefKeys.cmQuality, 2)
        scheduleHide()
        maybeGuide()
        val local = Store.localArchive
        if (local != null && local.pages.isNotEmpty()) {
            archive = local
            localId = Store.localArchiveId
            title = local.title
            total = local.pages.size
            page = min(max(1, Store.localArchivePage), max(1, total))
            if (mode == "spread" && page > 1 && page % 2 == 1) page -= 1
            scrollTarget = page
            load(page)
            load(page + 1)
            return
        }
        val book = Store.selected
        title = if (Store.readerTitle.isNotEmpty()) Store.readerTitle else t("漫画")
        total = if (book != null && book.pages > 0) book.pages else 0
        page = if (total > 0) min(max(1, Store.readerChapter), total) else 1
        if (mode == "spread" && page > 1 && page % 2 == 1) page -= 1
        incognito = Store.readerIncognito
        bookId = if (Store.readerBookId.isNotEmpty()) Store.readerBookId else (book?.id ?: "")
        scrollTarget = page
        load(page)
        load(page + 1)
        if (bookId.isNotEmpty()) readingTracker.start(bookId, page, incognito)
    }

    fun scheduleHide() {
        barJob?.cancel()
        barJob = scope.launch {
            delay(3200)
            if (!bar || panel || thumbs || guideOpen) return@launch
            bar = false
        }
    }

    fun clearBarTimer() {
        barJob?.cancel()
        barJob = null
    }

    private fun maybeGuide() {
        if (Prefs.bool(PrefKeys.comicGuide, false)) return
        scope.launch {
            delay(900)
            if (guideOpen) return@launch
            bar = true
            guideStep = 0
            guideOpen = true
        }
    }

    fun onGuideChanged() {
        if (guideOpen) {
            bar = true
            clearBarTimer()
            guideNeedKey = ""
            return
        }
        guideNeedKey = ""
        panel = false
        thumbs = false
        Prefs.putBool(PrefKeys.comicGuide, true)
        scheduleHide()
    }

    fun guideNeed(need: String) {
        if (guideNeedKey == need) return
        guideNeedKey = need
        panel = need == "panel"
        thumbs = need == "thumbs"
    }

    fun openGuide() {
        panel = false
        thumbs = false
        guideNeedKey = ""
        guideStep = 0
        guideOpen = true
    }

    fun toggleBar() {
        bar = !bar
        if (bar) scheduleHide() else clearBarTimer()
    }

    fun onBack(): Boolean {
        if (guideOpen) {
            guideOpen = false
            return true
        }
        if (panel) {
            panel = false
            return true
        }
        if (thumbs) {
            thumbs = false
            return true
        }
        if (bar) {
            bar = false
            clearBarTimer()
            return true
        }
        return false
    }

    fun markLocal() {
        if (incognito) return
        if (localId.isNotEmpty()) {
            ProgressMarks.put(localId, page, total, ProgressMarks.get(localId)?.done ?: false)
            return
        }
        if (bookId.isNotEmpty()) {
            val selected = Store.selected
            val done = selected != null && selected.id == bookId && selected.readCompleted
            ProgressMarks.put(bookId, page, total, done)
        }
    }

    fun saveLocal() {
        if (localId.isEmpty()) return
        val ratio = if (total > 0) page.toDouble() / total else 0.0
        ProgressMarks.put(localId, page, total, ProgressMarks.get(localId)?.done ?: false)
        scope.launch {
            try {
                LocalStore.saveProgress(localId, page, ratio)
            } catch (e: Throwable) {
            }
        }
    }

    fun flushProgress() {
        if (incognito || bookId.isEmpty() || page <= 0) return
        progressAt = System.currentTimeMillis()
        val at = page
        scope.launch {
            try {
                Store.api.savePage(bookId, at)
            } catch (e: Throwable) {
            }
        }
    }

    fun pushProgress() {
        if (incognito || bookId.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - progressAt < 2000) return
        progressAt = now
        val at = page
        scope.launch {
            try {
                Store.api.savePage(bookId, at)
            } catch (e: Throwable) {
            }
        }
    }

    fun onPageSettled() {
        markLocal()
        readingTracker.track(page)
        pushProgress()
    }

    private fun qualityWidth(): Int = when (Prefs.int(PrefKeys.cmQuality, 2)) {
        0 -> 720
        1 -> 1080
        else -> 0
    }

    fun pageLabel(): String {
        if (mode == "spread" && page > 1 && page + 1 <= total) return "$page-${page + 1}"
        return "$page"
    }

    fun displayPage(): Int = if (sliderPage > 0) sliderPage else page

    fun displayLabel(): String {
        val at = displayPage()
        if (mode == "spread" && at > 1 && at + 1 <= total) return "$at-${at + 1}"
        return "$at"
    }

    fun percent(): Int {
        val at = displayPage()
        return if (total > 0) ((at * 100f / total) + 0.5f).toInt() else 0
    }

    private fun reasonOf(e: Throwable): String {
        val raw = e.message ?: ""
        if (raw.isEmpty()) return t("页图加载失败")
        return if (raw.length > 40) raw.substring(0, 40) + "…" else raw
    }

    private suspend fun decode(bytes: ByteArray): Bitmap = withContext(Dispatchers.IO) {
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException(t("页图解码失败"))
        ImageOps.tune(raw, crop, enhance)
    }

    private fun armGuard(n: Int) {
        disarmGuard()
        guardJob = scope.launch {
            delay(15000)
            if (page == n && loading) {
                loading = false
                pix = null
                failed = t("页图加载超时")
            }
        }
    }

    private fun disarmGuard() {
        guardJob?.cancel()
        guardJob = null
    }

    private fun evict(keep: Int) {
        if (cache.size <= 3) return
        val keys = cache.keys.toList()
        for (key in keys) {
            if (key == keep || abs(key - page) <= 1) continue
            cache.remove(key)?.recycle()
        }
    }

    fun loadAsync(n: Int) {
        scope.launch { load(n) }
    }

    suspend fun load(n: Int) {
        val arc = archive
        if (arc == null && bookId.isEmpty()) {
            failed = t("缺少书籍标识，无法定位页图")
            return
        }
        if (n < 1) return
        if (total > 0 && n > total) return
        val hit = cache[n]
        if (hit != null) {
            if (n == page) {
                pix = hit
                failed = ""
            } else if (mode == "spread" && page > 1 && n == page + 1) {
                pix2 = hit
                failed2 = ""
            }
            return
        }
        if (n == page) {
            loading = true
            armGuard(n)
        }
        try {
            val bytes = if (arc != null) {
                withContext(Dispatchers.IO) { arc.readPage(n) }
            } else {
                val local = withContext(Dispatchers.IO) { offlinePage(bookId, n, "jpeg") }
                local ?: Store.api.page(bookId, n, "jpeg", qualityWidth())
            }
            val bmp = decode(bytes)
            cache[n] = bmp
            evict(n)
            if (n == page) {
                pix = bmp
                failed = ""
            } else if (mode == "spread" && page > 1 && n == page + 1) {
                pix2 = bmp
                failed2 = ""
            }
        } catch (e: Throwable) {
            val msg = reasonOf(e)
            if (n == page) {
                pix = null
                failed = msg
            } else if (mode == "spread" && page > 1 && n == page + 1) {
                pix2 = null
                failed2 = msg
            }
        }
        if (n == page) {
            loading = false
            disarmGuard()
        }
    }

    fun reload() {
        for (bmp in cache.values) bmp.recycle()
        cache.clear()
        pix = null
        pix2 = null
        failed = ""
        failed2 = ""
        stripVersion += 1
        loadAsync(page)
        if (mode == "spread") loadAsync(page + 1)
    }

    fun jumpTo(target: Int) {
        if (total <= 0) return
        val n = min(total, max(1, target))
        if (mode == "scroll") {
            page = n
            scrollTarget = n
            scrollToPage()
            return
        }
        var next = n
        if (mode == "spread" && next > 1 && next % 2 == 1) next -= 1
        page = next
        pix2 = null
        pix = cache[next]
        failed = ""
        failed2 = ""
        loadAsync(next)
        loadAsync(next + 1)
        if (bar) scheduleHide()
    }

    fun turn(delta: Int) {
        var next = page + if (mode == "spread") delta * 2 else delta
        if (mode == "spread") {
            if (next <= 1) next = 1
            else if (next % 2 == 1) next -= 1
        }
        if (next < 1 || (total > 0 && next > total)) return
        page = next
        pix2 = null
        val hit = cache[next]
        if (hit != null) {
            pix = hit
        } else if (mode == "spread") {
            pix = null
        }
        failed = ""
        failed2 = ""
        loadAsync(next)
        loadAsync(next + 1)
        if (bar) scheduleHide()
    }

    fun zoneTapPaged(delta: Int) {
        if (guideOpen || zoom > 1f || touchMove > 10f) return
        if (delta == 0) {
            toggleBar()
            return
        }
        turn(delta)
    }

    fun switchMode() {
        zoom = 1f
        pix2 = null
        failed = ""
        failed2 = ""
        if (mode == "spread" && page > 1 && page % 2 == 1) page -= 1
        loadAsync(page)
        if (mode == "spread") loadAsync(page + 1)
        scrollTarget = page
        scrollToPage()
    }

    fun scrollToPage() {
        if (mode != "scroll") return
        val target = max(0, scrollTarget - 1)
        scope.launch {
            delay(400)
            try {
                listState?.scrollToItem(target)
            } catch (e: Throwable) {
            }
        }
    }

    fun scrollTurn(delta: Int) {
        val next = page + delta
        if (next < 1 || (total > 0 && next > total)) return
        page = next
        scrollTarget = next
        scrollToPage()
    }

    fun zoneTap(x: Float, w: Float) {
        if (touchMove > 10f || zoom > 1.01f) return
        if (w <= 0f) {
            toggleBar()
            return
        }
        if (x < w * 0.28f) {
            scrollTurn(-1)
            return
        }
        if (x > w * 0.72f) {
            scrollTurn(1)
            return
        }
        toggleBar()
    }

    fun pickQuality(index: Int) {
        if (qualityIndex == index) return
        qualityIndex = index
        Prefs.putInt(PrefKeys.cmQuality, index)
        reload()
    }

    fun applyCrop(on: Boolean) {
        if (crop == on) return
        crop = on
        reload()
    }

    fun applyEnhance(on: Boolean) {
        if (enhance == on) return
        enhance = on
        reload()
    }

    fun release() {
        disarmGuard()
        clearBarTimer()
        if (archive != null) {
            Store.localArchivePage = page
            saveLocal()
        } else {
            flushProgress()
        }
        Store.localArchive = null
        Store.localArchiveId = ""
        for (bmp in cache.values) bmp.recycle()
        cache.clear()
        pix = null
        pix2 = null
        Store.readerIncognito = false
    }
}

@Composable
private fun PageFailBox(pageNo: Int, reason: String, compact: Boolean, onRetry: () -> Unit) {
    val C = Tm.pal
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp)
    ) {
        Glyph("warn", if (compact) 16.dp else 21.dp, C.bad)
        Text(
            tf("第 {0} 页加载失败", listOf("$pageNo")),
            fontSize = (if (compact) 10.5f else 12.5f).sp,
            color = C.tx2,
            textAlign = TextAlign.Center
        )
        if (reason.isNotEmpty()) {
            Text(
                reason,
                fontSize = (if (compact) 8.5f else 9.5f).sp,
                color = C.tx4,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
        Row(
            Modifier
                .clip(CircleShape)
                .border(1.dp, C.accLine, CircleShape)
                .clickable { onRetry() }
                .padding(start = 11.dp, end = 13.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Glyph("sync", if (compact) 10.dp else 12.dp, C.acc)
            Text(t("重试"), fontSize = (if (compact) 10f else 11f).sp, color = C.acc)
        }
    }
}

@Composable
private fun ZoomLayer(
    bmp: Bitmap,
    pageKey: Int,
    onZoom: (Float) -> Unit,
    onSwipe: (Int) -> Unit,
    onTapZone: (Int) -> Unit,
    swipeEnabled: Boolean,
    guideOpen: Boolean
) {
    var zoom by remember { mutableStateOf(1f) }
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }
    var base by remember { mutableStateOf(1f) }
    var pivotX by remember { mutableStateOf(0.5f) }
    var pivotY by remember { mutableStateOf(0.5f) }
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }

    fun clampX(v: Float): Float {
        if (zoom <= 1.01f || w <= 0f) return 0f
        val limit = w * (zoom - 1f) / 2f
        return max(-limit, min(limit, v))
    }

    fun clampY(v: Float): Float {
        if (zoom <= 1.01f || h <= 0f) return 0f
        val limit = h * (zoom - 1f) / 2f
        return max(-limit, min(limit, v))
    }

    fun restore() {
        zoom = 1f
        offX = 0f
        offY = 0f
        pivotX = 0.5f
        pivotY = 0.5f
        base = 1f
        onZoom(1f)
    }

    LaunchedEffect(pageKey) { restore() }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                w = it.width.toFloat()
                h = it.height.toFloat()
            }
            .pointerInput(pageKey) {
                detectTapGestures(
                    onLongPress = { offset ->
                        if (zoom > 1f) {
                            restore()
                        } else {
                            pivotX = if (w > 0f) offset.x / w else 0.5f
                            pivotY = if (h > 0f) offset.y / h else 0.5f
                            zoom = 1.5f
                            base = 1.5f
                            onZoom(1.5f)
                        }
                    },
                    onTap = { offset ->
                        if (zoom > 1f) return@detectTapGestures
                        if (guideOpen) return@detectTapGestures
                        val x = offset.x
                        if (x < w * 0.294f) onTapZone(-1)
                        else if (x > w * 0.706f) onTapZone(1)
                        else onTapZone(0)
                    }
                )
            }
            .pointerInput(pageKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var travelled = 0f
                    var totalX = 0f
                    var totalY = 0f
                    var gestureZoom = 1f
                    val startZoom = zoom
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        if (changes.all { !it.pressed }) break
                        if (changes.size >= 2) {
                            gestureZoom *= event.calculateZoom()
                            val pan = event.calculatePan()
                            zoom = (startZoom * gestureZoom).coerceIn(1f, 4f)
                            offX = clampX(offX + pan.x * min(3.4f, 1f + (zoom - 1f) * 1.05f))
                            offY = clampY(offY + pan.y * min(2.2f, 1f + (zoom - 1f) * 0.45f))
                            changes.forEach { it.consume() }
                        } else {
                            val change = changes.first()
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalX += dx
                            totalY += dy
                            travelled = max(travelled, hypot(totalX, totalY))
                            if (zoom > 1.01f) {
                                offX = clampX(offX + dx * min(3.4f, 1f + (zoom - 1f) * 1.05f))
                                offY = clampY(offY + dy * min(2.2f, 1f + (zoom - 1f) * 0.45f))
                                change.consume()
                            }
                        }
                    }
                    if (zoom < 1.05f) {
                        restore()
                    } else {
                        base = zoom
                        onZoom(zoom)
                    }
                    if (swipeEnabled && zoom <= 1.01f && travelled > 14f && abs(totalX) > 88f && abs(totalX) > abs(totalY) * 1.6f) {
                        onSwipe(if (totalX < 0f) 1 else -1)
                    }
                }
            }
    ) {
        Image(
            bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offX
                    translationY = offY
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                }
        )
    }
}

@Composable
private fun StripItem(ctl: ComicCtl, pageNo: Int) {
    var bmp by remember(pageNo) { mutableStateOf<Bitmap?>(null) }
    var ratio by remember(pageNo) { mutableStateOf(1.4f) }
    var err by remember(pageNo) { mutableStateOf("") }

    LaunchedEffect(pageNo, ctl.stripVersion) {
        if (pageNo < 1) return@LaunchedEffect
        val arc = ctl.archive
        if (arc == null && ctl.bookId.isEmpty()) return@LaunchedEffect
        err = ""
        try {
            val bytes = if (arc != null) {
                withContext(Dispatchers.IO) { arc.readPage(pageNo) }
            } else {
                val local = withContext(Dispatchers.IO) { offlinePage(ctl.bookId, pageNo, "jpeg") }
                local ?: Store.api.page(ctl.bookId, pageNo, "jpeg", Prefs.int(PrefKeys.cmQuality, 2).let { q -> if (q == 0) 720 else if (q == 1) 1080 else 0 })
            }
            val decoded = withContext(Dispatchers.IO) {
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException(t("页图解码失败"))
                ImageOps.tune(raw, ctl.crop, ctl.enhance)
            }
            if (decoded.height > 0) ratio = decoded.width.toFloat() / decoded.height
            bmp?.recycle()
            bmp = decoded
        } catch (e: Throwable) {
            val raw = e.message ?: ""
            err = when {
                raw.isEmpty() -> t("页图加载失败")
                raw.length > 40 -> raw.substring(0, 40) + "…"
                else -> raw
            }
            bmp = null
        }
    }

    DisposableEffect(pageNo) {
        onDispose { bmp?.recycle() }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            val shown = bmp
            if (shown != null) {
                Image(
                    shown.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (err.isNotEmpty()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF141414))) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PageFailBox(pageNo, err, false) {
                            ctl.stripVersion += 1
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF141414)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF6F6A56), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(StripLine))
    }
}

@Composable
private fun ThumbItem(ctl: ComicCtl, pageNo: Int, busy: Boolean, current: Int) {
    var bmp by remember(pageNo) { mutableStateOf<Bitmap?>(null) }
    var err by remember(pageNo) { mutableStateOf(false) }

    LaunchedEffect(pageNo, busy) {
        if (busy || bmp != null || err) return@LaunchedEffect
        if (pageNo < 1) return@LaunchedEffect
        try {
            val arc = ctl.archive
            val bytes = if (arc != null) {
                withContext(Dispatchers.IO) { arc.readPage(pageNo) }
            } else {
                try {
                    Store.api.page(ctl.bookId, pageNo, "jpeg", 300)
                } catch (e: Throwable) {
                    Store.api.pageThumb(ctl.bookId, pageNo)
                }
            }
            val decoded = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            if (decoded != null) {
                bmp = decoded
                err = false
            } else {
                err = true
            }
        } catch (e: Throwable) {
            bmp = null
            err = true
        }
    }

    DisposableEffect(pageNo) {
        onDispose { bmp?.recycle() }
    }

    Box(
        Modifier
            .size(58.dp, 82.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(StripPaper)
            .border(
                2.dp,
                if (err) Tm.pal.bad else if (pageNo == current) Tm.pal.acc else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { ctl.jumpTo(pageNo) }
    ) {
        val shown = bmp
        if (shown != null) {
            Image(
                shown.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (err) {
            Column(
                Modifier.fillMaxSize().background(ThumbErrBg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Glyph("warn", 15.dp, Tm.pal.bad)
                Text(t("重试"), fontSize = 8.5f.sp, color = Tm.pal.acc, modifier = Modifier.padding(top = 3.dp))
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF6F6A56), strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            }
        }
        Box(
            Modifier.fillMaxSize().padding(3.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                "$pageNo",
                fontSize = 9.sp,
                fontFamily = Mono,
                color = ThumbPageTx,
                modifier = Modifier
                    .background(ThumbShadow, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun RowScope.ModeChip(label: String, active: Boolean, onTap: () -> Unit) {
    val C = Tm.pal
    Text(
        label,
        fontSize = 11.sp,
        color = if (active) C.acc2 else C.tx3,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) C.accBg else C.bg2)
            .border(1.dp, if (active) C.accLine else C.line, CircleShape)
            .clickable { onTap() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun RowScope.QualityChip(label: String, active: Boolean, onTap: () -> Unit) {
    val C = Tm.pal
    Text(
        label,
        fontSize = 11.sp,
        color = if (active) C.acc2 else C.tx3,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) C.accBg else C.bg2)
            .border(1.dp, if (active) C.accLine else C.line, CircleShape)
            .clickable { onTap() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun SwitchRow(sink: GuideAnchors, id: String, label: String, width: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    val C = Tm.pal
    Row(
        Modifier.anchored(sink, id),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = C.tx3, modifier = Modifier.width(width.dp))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.onAcc,
                checkedTrackColor = C.acc,
                uncheckedThumbColor = C.tx3,
                uncheckedTrackColor = C.bg3,
                uncheckedBorderColor = C.line2
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun LayoutPanel(ctl: ComicCtl) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xF20E100F))
            .border(width = 1.dp, color = C.line)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().anchored(ctl.anchors, "comic_panel_mode"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip(t("单页"), ctl.mode == "paged") {
                ctl.mode = "paged"
                ctl.switchMode()
            }
            ModeChip(t("双页"), ctl.mode == "spread") {
                ctl.mode = "spread"
                ctl.switchMode()
            }
            ModeChip(t("连续滚动"), ctl.mode == "scroll") {
                ctl.mode = "scroll"
                ctl.switchMode()
            }
            Spacer(Modifier.weight(1f))
            Text(
                String.format(java.util.Locale.US, "%.1fx", ctl.zoom),
                fontSize = 10.sp,
                fontFamily = Mono,
                color = C.tx4
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SwitchRow(ctl.anchors, "comic_panel_crop", t("裁白边"), 46, ctl.crop) { ctl.applyCrop(it) }
            SwitchRow(ctl.anchors, "comic_panel_enhance", t("校色"), 34, ctl.enhance) { ctl.applyEnhance(it) }
            Spacer(Modifier.weight(1f))
        }

        Row(
            Modifier.fillMaxWidth().anchored(ctl.anchors, "comic_panel_quality"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(t("画质"), fontSize = 11.sp, color = C.tx3, modifier = Modifier.width(34.dp))
            QualityChip(t("省流"), ctl.qualityIndex == 0) { ctl.pickQuality(0) }
            QualityChip(t("标准"), ctl.qualityIndex == 1) { ctl.pickQuality(1) }
            QualityChip(t("高清"), ctl.qualityIndex == 2) { ctl.pickQuality(2) }
            Spacer(Modifier.weight(1f))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .anchored(ctl.anchors, "comic_panel_guide")
                .clickable { ctl.openGuide() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Glyph("info", 13.dp, C.tx3)
            Text(t("操作指引"), fontSize = 11.sp, color = C.tx2)
            Spacer(Modifier.weight(1f))
            Text(t("逐步演示"), fontSize = 10.sp, color = C.tx4)
        }
    }
}

@Composable
private fun TopBar(ctl: ComicCtl) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .anchored(ctl.anchors, "comic_guide_top")
            .background(Brush.verticalGradient(listOf(TopFrom, TopTo)))
            .padding(start = 12.dp, end = 12.dp, top = (ctl.safeTop + 10f).dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(31.dp)
                .anchored(ctl.anchors, "comic_top_back")
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, C.hair, RoundedCornerShape(9.dp))
                .clickable { ctl.flushProgress(); Nav.pop() },
            contentAlignment = Alignment.Center
        ) {
            Glyph("back", 17.dp, C.tx2)
        }
        Column(
            Modifier.weight(1f).anchored(ctl.anchors, "comic_top_title"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                ctl.title,
                fontSize = 13.sp,
                fontFamily = Disp,
                letterSpacing = 0.6.sp,
                color = C.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (ctl.total > 0) {
                    tf(
                        "第 {0} / {1} 页{2}",
                        listOf(ctl.pageLabel(), "${ctl.total}", if (ctl.incognito) t(" · 隐身") else "")
                    )
                } else {
                    if (ctl.incognito) t("隐身阅读") else t("阅读中")
                },
                fontSize = 9.5f.sp,
                color = if (ctl.incognito) C.acc2 else C.tx3,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Stage(ctl: ComicCtl) {
    val C = Tm.pal
    Box(Modifier.fillMaxSize().background(StageBg)) {
        if (ctl.mode == "spread") {
            Row(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { ctl.stageW = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (ctl.zoom > 1f || ctl.guideOpen) return@detectTapGestures
                            val w = size.width.toFloat()
                            when {
                                offset.x < w * 0.28f -> ctl.zoneTapPaged(-1)
                                offset.x > w * 0.72f -> ctl.zoneTapPaged(1)
                                else -> ctl.zoneTapPaged(0)
                            }
                        }
                    }
            ) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val bmp = ctl.pix
                    if (bmp != null) {
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (ctl.failed.isNotEmpty()) {
                        PageFailBox(ctl.page, ctl.failed, true) { ctl.loadAsync(ctl.page) }
                    } else {
                        CircularProgressIndicator(color = C.acc, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
                if (ctl.page + 1 <= ctl.total) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val bmp2 = ctl.pix2
                        if (bmp2 != null) {
                            Image(
                                bmp2.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (ctl.failed2.isNotEmpty()) {
                            PageFailBox(ctl.page + 1, ctl.failed2, true) { ctl.loadAsync(ctl.page + 1) }
                        } else {
                            CircularProgressIndicator(color = C.acc, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        } else {
            val bmp = ctl.pix
            if (bmp != null) {
                Box(Modifier.fillMaxSize().onSizeChanged { ctl.stageW = it.width.toFloat() }) {
                    ZoomLayer(
                        bmp = bmp,
                        pageKey = ctl.page,
                        onZoom = { ctl.zoom = it },
                        onSwipe = { delta -> if (ctl.swipeTurn && !ctl.guideOpen) ctl.turn(delta) },
                        onTapZone = { delta -> ctl.zoneTapPaged(delta) },
                        swipeEnabled = ctl.swipeTurn,
                        guideOpen = ctl.guideOpen
                    )
                    if (ctl.loading) TurningVeil(ctl)
                }
            } else if (ctl.failed.isNotEmpty()) {
                Box(Modifier.fillMaxSize().onSizeChanged { ctl.stageW = it.width.toFloat() }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PageFailBox(ctl.page, ctl.failed, false) { ctl.loadAsync(ctl.page) }
                    }
                }
            } else {
                PageSkeleton(ctl)
                ZoneRow(ctl)
            }
        }
    }
}

@Composable
private fun ZoneRow(ctl: ComicCtl) {
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxHeight().clickable(
                interactionSource = null,
                indication = null
            ) { ctl.zoneTapPaged(-1) }
        )
        Box(
            Modifier.weight(1.4f).fillMaxHeight().clickable(
                interactionSource = null,
                indication = null
            ) { ctl.zoneTapPaged(0) }
        )
        Box(
            Modifier.weight(1f).fillMaxHeight().clickable(
                interactionSource = null,
                indication = null
            ) { ctl.zoneTapPaged(1) }
        )
    }
}

@Composable
private fun PageSkeleton(ctl: ComicCtl) {
    val C = Tm.pal
    var breath by remember { mutableStateOf(0.32f) }
    LaunchedEffect(Unit) {
        var up = true
        while (true) {
            delay(1000)
            up = !up
            breath = if (up) 0.82f else 0.32f
        }
    }
    Box(Modifier.fillMaxSize().background(C.bg1.copy(alpha = breath)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = C.acc, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            Text(
                if (ctl.total > 0) tf("第 {0} / {1} 页", listOf("${ctl.page}", "${ctl.total}")) else t("正在载入页面"),
                fontSize = 11.sp,
                fontFamily = Mono,
                color = C.tx3
            )
        }
    }
}

@Composable
private fun TurningVeil(ctl: ComicCtl) {
    val C = Tm.pal
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(VeilTrack)) {
            Box(
                Modifier
                    .fillMaxWidth(max(4, ctl.percent()) / 100f)
                    .height(2.dp)
                    .background(C.acc)
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(CircleShape)
                .background(C.glassDeep)
                .border(1.dp, C.line, CircleShape)
                .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(color = C.acc, strokeWidth = 1.5.dp, modifier = Modifier.size(13.dp))
            Text(
                if (ctl.total > 0) tf("{0} / {1}", listOf("${ctl.page}", "${ctl.total}")) else "${ctl.page}",
                fontSize = 10.sp,
                fontFamily = Mono,
                color = C.tx2
            )
        }
        Spacer(Modifier.height((ctl.safeBottom + 24f).dp))
    }
}

@Composable
private fun BottomBar(ctl: ComicCtl) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .anchored(ctl.anchors, "comic_guide_bottom")
            .background(Brush.verticalGradient(listOf(BottomFrom, BottomTo)))
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = (ctl.safeBottom + 12f).dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().anchored(ctl.anchors, "comic_guide_progress"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (ctl.total > 0) "${ctl.displayLabel()} / ${ctl.total}" else "${ctl.page}",
                fontSize = 10.sp,
                fontFamily = Mono,
                color = if (ctl.sliderPage > 0) C.acc2 else C.tx3,
                maxLines = 1,
                modifier = Modifier.width(52.dp).anchored(ctl.anchors, "comic_prog_pos")
            )
            Slider(
                value = ctl.displayPage().toFloat(),
                onValueChange = { ctl.sliderPage = it.roundToInt().coerceIn(1, max(1, ctl.total)) },
                onValueChangeFinished = {
                    val target = ctl.sliderPage
                    ctl.sliderPage = 0
                    if (target > 0) ctl.jumpTo(target)
                },
                valueRange = 1f..max(1, ctl.total).toFloat(),
                steps = 0,
                enabled = ctl.total > 1,
                colors = SliderDefaults.colors(
                    thumbColor = C.acc,
                    activeTrackColor = C.acc,
                    inactiveTrackColor = C.line2
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .anchored(ctl.anchors, "comic_prog_slider")
            )
            Text(
                "${ctl.percent()}%",
                fontSize = 10.sp,
                fontFamily = Mono,
                color = if (ctl.sliderPage > 0) C.acc2 else C.tx3,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp).anchored(ctl.anchors, "comic_prog_pct")
            )
            Box(
                Modifier
                    .size(31.dp)
                    .anchored(ctl.anchors, "comic_prog_prev")
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, C.hair, RoundedCornerShape(8.dp))
                    .clickable { ctl.turn(-1) },
                contentAlignment = Alignment.Center
            ) {
                Glyph("back", 15.dp, C.tx2)
            }
            Box(
                Modifier
                    .size(31.dp)
                    .anchored(ctl.anchors, "comic_prog_next")
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, C.hair, RoundedCornerShape(8.dp))
                    .clickable { ctl.turn(1) },
                contentAlignment = Alignment.Center
            ) {
                Glyph("next", 15.dp, C.tx2)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            comicTools().forEach { tool ->
                val active = tool.icon == "thumbs" && ctl.thumbs
                Column(
                    Modifier
                        .weight(1f)
                        .anchored(ctl.anchors, "comic_tool_${tool.icon}")
                        .clickable {
                            when (tool.icon) {
                                "thumbs" -> ctl.thumbs = !ctl.thumbs
                                "settings" -> ctl.panel = !ctl.panel
                                "bookmark" -> ctl.bookmarked = !ctl.bookmarked
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Glyph(tool.icon, 19.dp, if (active) C.acc2 else C.tx3)
                    Text(
                        tool.label,
                        fontSize = 9.sp,
                        color = if (active) C.acc2 else C.tx3,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbsStrip(ctl: ComicCtl, state: androidx.compose.foundation.lazy.LazyListState) {
    val C = Tm.pal
    LazyRow(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .anchored(ctl.anchors, "comic_thumbs_strip")
            .background(C.glassDeep)
            .border(width = 1.dp, color = C.line)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(ctl.total) { index ->
            ThumbItem(ctl, index + 1, state.isScrollInProgress, ctl.page)
        }
    }
}

@Composable
fun ComicReaderScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    val ctl = remember { ComicCtl(scope) }
    val density = LocalDensity.current
    val view = LocalView.current
    val listState = rememberLazyListState()
    val thumbState = rememberLazyListState()
    val configuration = LocalConfiguration.current

    val safeTop = with(density) { WindowInsets.systemBars.getTop(density).toDp().value }
    val safeBottom = with(density) { WindowInsets.systemBars.getBottom(density).toDp().value }
    ctl.safeTop = safeTop
    ctl.safeBottom = safeBottom
    ctl.listState = listState

    LaunchedEffect(Unit) {
        Store.backHook = { ctl.onBack() }
        try {
            ctl.boot()
        } catch (e: Throwable) {
            ctl.failed = tf("打开失败：{0}", listOf(e.message ?: ""))
        }
    }

    LaunchedEffect(ctl.page) {
        try {
            ctl.onPageSettled()
        } catch (e: Throwable) {
        }
    }

    LaunchedEffect(ctl.guideOpen) { ctl.onGuideChanged() }

    LaunchedEffect(ctl.mode) {
        if (ctl.mode == "scroll") ctl.scrollToPage()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            val n = index + 1
            if (ctl.mode == "scroll" && n != ctl.page && n <= ctl.total) ctl.page = n
        }
    }

    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        val keep = Prefs.bool(PrefKeys.keepScreen, true)
        if (keep) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            Store.backHook = null
            ctl.release()
            scope.launch { readingTracker.stop() }
            if (keep) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(configuration.orientation) {
        ctl.stripVersion += 1
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(StageBg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var downX = 0f
                    var downY = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Press -> {
                                downX = change.position.x
                                downY = change.position.y
                                ctl.touchMove = 0f
                            }
                            PointerEventType.Move -> {
                                val dx = abs(change.position.x - downX)
                                val dy = abs(change.position.y - downY)
                                ctl.touchMove = max(ctl.touchMove, max(dx, dy))
                            }
                        }
                    }
                }
            }
    ) {
        if (ctl.mode == "scroll") {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(StageBg)
                    .onSizeChanged { ctl.stageW = it.width.toFloat() }
                    .clickable(interactionSource = null, indication = null) {
                        ctl.zoneTap(0f, 0f)
                    }
            ) {
                items(ctl.total) { index ->
                    StripItem(ctl, index + 1)
                }
            }
        } else {
            Stage(ctl)
        }

        AnimatedVisibility(
            visible = ctl.bar,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160))
        ) {
            Column(Modifier.fillMaxSize()) {
                TopBar(ctl)
                Spacer(Modifier.weight(1f))
                if (ctl.panel) LayoutPanel(ctl)
                if (ctl.thumbs) ThumbsStrip(ctl, thumbState)
                BottomBar(ctl)
            }
        }

        if (ctl.guideOpen) {
            ReaderGuide(
                visible = ctl.guideOpen,
                index = ctl.guideStep,
                steps = ctl.steps,
                anchors = ctl.anchors,
                safeTop = ctl.safeTop,
                safeBottom = ctl.safeBottom,
                onIndex = { ctl.guideStep = it },
                onNeed = { ctl.guideNeed(it) },
                onClose = { ctl.guideOpen = false }
            )
        }
    }
}
