package com.lantai.reader.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.components.CoverCache
import com.lantai.reader.model.PrefKeys
import com.lantai.reader.model.Prefs
import com.lantai.reader.model.Store
import com.lantai.reader.net.TtsService
import com.lantai.reader.net.readingTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.max
import kotlin.math.roundToInt

private const val BAR_H = 56f
private const val W_OFF = 56f
private const val W_ON = 280f
private const val EDGE = 10f
private const val TOP_PAD = 44f
private const val BOT_PAD = 56f
private const val BALL_W = 40f
private const val BALL_CRIM = 40f
private const val BALL_CDARK = 38.6f
private const val BALL_CGOLD = 37.2f
private const val BALL_CGAP = 33f
private const val BALL_CPIC = 30.4f
private const val BALL_RIM = 56f
private const val BALL_DARK = 53.6f
private const val BALL_GOLD = 51.2f
private const val BALL_GAP = 46f
private const val BALL_PIC = 43f
private const val BALL_BADGE = 21f
private const val IDLE_MS = 2000L

private val RimLight = Color(0xB8FFFFFF)
private val RimDark = Color(0x66000000)
private val BallBg = Color(0xFF1C1C1E)
private val BarBg = Color(0xE61C1C1E)
private val BarEdge = Color(0x33FFFFFF)
private val KnobTx = Color(0xFFFFFFFF)
private val KnobMute = Color(0xFFB8B8B8)
private val GoldTx = Color(0xFFE8C46A)
private val SheetBg = Color(0xFF1C1C1E)
private val SheetTx = Color(0xFFFFFFFF)
private val SheetMute = Color(0xFF8A8A8E)
private val SheetDim = Color(0xFF7A7A7E)

private class VoiceRow(val tone: String, val label: String, val lang: String, val ready: Boolean)

private fun parseVoices(json: String): List<VoiceRow> {
    val out = ArrayList<VoiceRow>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                VoiceRow(
                    tone = o.optString("tone"),
                    label = o.optString("name"),
                    lang = o.optString("lang"),
                    ready = o.optBoolean("ready")
                )
            )
        }
    } catch (e: Throwable) {
    }
    return out
}

private fun ringColor(playing: Boolean): Color {
    if (playing) return if (Tm.mode == "light") Color(0xFF8A651F) else Color(0xFFE8C46A)
    return if (Tm.mode == "light") Color(0xFFB08A3A) else Color(0xFFE3C377)
}

@Composable
private fun PlayMark(on: Boolean) {
    if (on) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.2.dp).height(11.dp).background(Color.White, RoundedCornerShape(1.2.dp)))
            Box(Modifier.width(3.2.dp).height(11.dp).background(Color.White, RoundedCornerShape(1.2.dp)))
        }
    } else {
        Canvas(Modifier.size(10.dp, 12.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height / 2f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

@Composable
private fun BallRing(idx: Int, total: Int, playing: Boolean, size: Float) {
    Canvas(Modifier.size(size.dp)) {
        val stroke = 2.4.dp.toPx()
        val radius = (this.size.width - stroke) / 2f
        drawCircle(color = Color(0x33FFFFFF), radius = radius, style = Stroke(stroke))
        val value = max(1, idx + 1).toFloat()
        val count = max(1, total).toFloat()
        drawArc(
            color = if (playing) Color(0xFFE8C46A) else Color(0xFF9A9A9E),
            startAngle = -90f,
            sweepAngle = 360f * (value / count).coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun VoiceSheet(
    voices: List<VoiceRow>,
    state: String,
    toneNow: String,
    onPick: (Int) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() })
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(SheetBg)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        t("朗读音色"),
                        fontSize = 15.sp,
                        color = SheetTx,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "✕",
                        fontSize = 16.sp,
                        color = KnobMute,
                        modifier = Modifier.clickable { onClose() }.padding(6.dp)
                    )
                }
                if (voices.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().height(150.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (state == "loading") {
                            CircularProgressIndicator(color = SheetMute, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                            Text(
                                t("正在读取系统音色"),
                                fontSize = 12.sp,
                                color = SheetMute,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        } else {
                            Text(t("没有读到系统音色"), fontSize = 12.sp, color = SheetMute)
                            Text(
                                t("重试"),
                                fontSize = 12.sp,
                                color = GoldTx,
                                modifier = Modifier.padding(top = 12.dp).clickable { onRetry() }
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        voices.forEachIndexed { index, v ->
                            val current = v.tone == toneNow
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (current) Color(0x26E8C46A) else Color.Transparent)
                                    .clickable { onPick(index) }
                                    .padding(horizontal = 18.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                    Text(
                                        v.label,
                                        fontSize = 14.sp,
                                        color = if (v.ready) SheetTx else SheetDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!v.ready) {
                                        Text(
                                            "${v.lang} · ${t("需下载")}",
                                            fontSize = 10.5f.sp,
                                            color = SheetMute,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                if (current) {
                                    Text("✓", fontSize = 15.sp, color = GoldTx)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TtsBar(onOpenReader: () -> Unit) {
    if (!Store.ttsOn) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var open by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    var pxTarget by remember { mutableStateOf(0f) }
    var py by remember { mutableStateOf(0f) }
    var boxW by remember { mutableStateOf(0f) }
    var boxH by remember { mutableStateOf(0f) }
    var side by remember { mutableStateOf(Prefs.read(PrefKeys.ttsBarSide).ifEmpty { "R" }) }
    var ratio by remember { mutableStateOf(Prefs.read(PrefKeys.ttsBarYr).toFloatOrNull() ?: 0.42f) }
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var voiceOpen by remember { mutableStateOf(false) }
    var voices by remember { mutableStateOf<List<VoiceRow>>(emptyList()) }
    var voiceState by remember { mutableStateOf("") }
    var toneNow by remember { mutableStateOf("") }
    var idleJob by remember { mutableStateOf<Job?>(null) }

    val barW by animateFloatAsState(
        targetValue = if (open) W_ON else W_OFF,
        animationSpec = tween(200),
        label = "ttsW"
    )
    val barX by animateFloatAsState(
        targetValue = pxTarget,
        animationSpec = if (dragging) snap() else tween(220),
        label = "ttsX"
    )

    fun maxY(): Float = max(TOP_PAD, boxH - BAR_H - BOT_PAD)

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (hi < lo) lo else v.coerceIn(lo, hi)

    fun anchorX(w: Float): Float =
        if (boxW <= 0f) pxTarget
        else if (side == "R") max(EDGE, boxW - w - EDGE) else EDGE

    fun place() {
        if (boxW <= 0f || boxH <= 0f) return
        val room = maxY() - TOP_PAD
        py = clamp(TOP_PAD + ratio * (if (room > 0f) room else 0f), TOP_PAD, maxY())
        pxTarget = anchorX(if (open) W_ON else W_OFF)
    }

    fun savePlace() {
        Prefs.write(PrefKeys.ttsBarSide, side)
        Prefs.write(PrefKeys.ttsBarYr, "$ratio")
    }

    fun expand() {
        if (open) return
        side = if (pxTarget + W_OFF / 2 < boxW / 2) "L" else "R"
        open = true
        pxTarget = anchorX(W_ON)
        savePlace()
    }

    fun collapse() {
        if (!open) return
        open = false
        pxTarget = anchorX(W_OFF)
        savePlace()
    }

    fun recycle() {
        if (!open) return
        val left = pxTarget + W_ON / 2 < boxW / 2
        side = if (left) "L" else "R"
        open = false
        pxTarget = if (left) EDGE else max(EDGE, boxW - W_OFF - EDGE)
        val room = maxY() - TOP_PAD
        ratio = if (room > 0f) clamp((py - TOP_PAD) / room, 0f, 1f) else 0.42f
        savePlace()
    }

    fun snap() {
        val w = if (open) W_ON else W_OFF
        val left = pxTarget + w / 2 < boxW / 2
        side = if (left) "L" else "R"
        pxTarget = if (left) EDGE else max(EDGE, boxW - w - EDGE)
        val room = maxY() - TOP_PAD
        ratio = if (room > 0f) clamp((py - TOP_PAD) / room, 0f, 1f) else 0.42f
        savePlace()
    }

    fun armIdle() {
        idleJob?.cancel()
        if (!open || voiceOpen) return
        idleJob = scope.launch {
            delay(IDLE_MS)
            if (!open) return@launch
            if (voiceOpen) return@launch
            recycle()
        }
    }

    fun openVoices() {
        toneNow = TtsService.toneNow()
        TtsService.reloadVoices()
        voices = parseVoices(TtsService.voicesJson())
        voiceState = if (voices.isNotEmpty()) "done" else "loading"
        voiceOpen = true
        if (voices.isEmpty()) {
            scope.launch {
                delay(600)
                voices = parseVoices(TtsService.voicesJson())
                voiceState = if (voices.isNotEmpty()) "done" else "empty"
            }
        }
    }

    LaunchedEffect(Store.readerBookId, Store.ttsOn) {
        val id = Store.readerBookId
        if (id.isEmpty()) return@LaunchedEffect
        cover?.recycle()
        cover = null
        cover = try {
            CoverCache.own(id, 240)
        } catch (e: Throwable) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            idleJob?.cancel()
            cover?.recycle()
            cover = null
        }
    }

    LaunchedEffect(placed, boxW, boxH, configuration.orientation) {
        if (!placed && boxW > 0f && boxH > 0f) {
            placed = true
            place()
            armIdle()
        } else if (placed) {
            pxTarget = clamp(pxTarget, 0f, max(0f, boxW - barW))
            py = clamp(py, TOP_PAD, maxY())
        }
    }

    LaunchedEffect(open, voiceOpen) {
        if (open && !voiceOpen) armIdle() else idleJob?.cancel()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                boxW = it.width.toFloat()
                boxH = it.height.toFloat()
            }
    ) {
        if (placed) {
            val ring = ringColor(Store.ttsPlay)
            Box(
                Modifier
                    .offset { IntOffset(barX.roundToInt(), py.roundToInt()) }
                    .width(barW.dp)
                    .height(BAR_H.dp)
                    .scale(if (dragging) 1.04f else 1f)
                    .shadow(
                        elevation = if (Store.ttsPlay && !open) 22.dp else if (dragging) 26.dp else 18.dp,
                        shape = RoundedCornerShape((BAR_H / 2).dp),
                        spotColor = if (Store.ttsPlay && !open) Color(0x4DE8C46A) else if (dragging) Color(0x66000000) else Color(0x40000000)
                    )
                    .clip(RoundedCornerShape((BAR_H / 2).dp))
                    .background(if (cover != null && !open) Color.Transparent else BarBg)
                    .then(if (open) Modifier.border(1.dp, BarEdge, RoundedCornerShape((BAR_H / 2).dp)) else Modifier)
                    .pointerInput(open) {
                        detectDragGestures(
                            onDragStart = {
                                idleJob?.cancel()
                                dragging = true
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val w = if (open) W_ON else W_OFF
                                pxTarget = clamp(pxTarget + amount.x, 0f, max(0f, boxW - w))
                                py = clamp(py + amount.y, TOP_PAD, maxY())
                            },
                            onDragEnd = {
                                dragging = false
                                if (open) armIdle() else snap()
                            },
                            onDragCancel = {
                                dragging = false
                                if (open) armIdle() else snap()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (open) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(BALL_W.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { collapse() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(BALL_CRIM.dp).background(RimLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(BALL_CDARK.dp).background(RimDark, CircleShape), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(BALL_CGOLD.dp).background(ring, CircleShape), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(BALL_CGAP.dp).background(BallBg, CircleShape), contentAlignment = Alignment.Center) {
                                            val shown = cover
                                            if (shown != null) {
                                                Box(Modifier.size(BALL_CPIC.dp).clip(CircleShape)) {
                                                    Image(
                                                        shown.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    Box(Modifier.fillMaxSize().background(Color(0x5C000000)))
                                                }
                                            } else {
                                                Text("♪", fontSize = 13.sp, color = Color(0xFFD8D8DC))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "‹",
                            fontSize = 22.sp,
                            color = KnobTx,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .width(30.dp)
                                .height(BAR_H.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    armIdle()
                                    TtsService.seek(Store.ttsIdx - 1)
                                }
                        )
                        Box(
                            Modifier.size(42.dp, BAR_H.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                armIdle()
                                if (Store.ttsPlay) TtsService.pause() else TtsService.resume()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            PlayMark(Store.ttsPlay)
                        }
                        Text(
                            "›",
                            fontSize = 22.sp,
                            color = KnobTx,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .width(30.dp)
                                .height(BAR_H.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    armIdle()
                                    TtsService.seek(Store.ttsIdx + 1)
                                }
                        )
                        Text(
                            "${max(1, Store.ttsIdx + 1)}/${max(1, Store.ttsTotal)}",
                            fontSize = 11.sp,
                            color = KnobMute,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .width(54.dp)
                                .height(BAR_H.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    armIdle()
                                    onOpenReader()
                                }
                        )
                        Text(
                            t("音色"),
                            fontSize = 11.sp,
                            color = GoldTx,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .width(40.dp)
                                .height(BAR_H.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    armIdle()
                                    openVoices()
                                }
                        )
                        Text(
                            "×",
                            fontSize = 17.sp,
                            color = KnobMute,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .width(30.dp)
                                .height(BAR_H.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    idleJob?.cancel()
                                    TtsService.stop()
                                    scope.launch { readingTracker.stop() }
                                    open = false
                                }
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .size(W_OFF.dp, BAR_H.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                armIdle()
                                if (Store.ttsPlay) TtsService.pause() else TtsService.resume()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(BALL_RIM.dp).background(RimLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(BALL_DARK.dp).background(RimDark, CircleShape), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(BALL_GOLD.dp).background(ring, CircleShape), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(BALL_GAP.dp).background(BallBg, CircleShape), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(BALL_PIC.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                            val shown = cover
                                            if (shown != null) {
                                                Image(
                                                    shown.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Box(Modifier.fillMaxSize().background(Color(0x73000000)))
                                            } else {
                                                Text("♪", fontSize = 16.sp, color = Color(0xFFD8D8DC))
                                            }
                                            BallRing(Store.ttsIdx, Store.ttsTotal, Store.ttsPlay, BALL_PIC - 10f)
                                        }
                                    }
                                }
                            }
                        }
                        Box(
                            Modifier
                                .offset(x = 20.5.dp, y = 20.5.dp)
                                .size(BALL_BADGE.dp)
                                .shadow(4.dp, CircleShape)
                                .background(if (Store.ttsPlay) Color(0xFFE8C46A) else Color(0xFFF4F1EA), CircleShape)
                                .border(1.8.dp, Color(0xFF1C1C1E), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (Store.ttsPlay) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.4.dp)) {
                                    Box(Modifier.width(2.6.dp).height(9.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(1.dp)))
                                    Box(Modifier.width(2.6.dp).height(9.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(1.dp)))
                                }
                            } else {
                                Canvas(Modifier.size(8.dp, 10.dp)) {
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(0f, size.height)
                                        lineTo(size.width, size.height / 2f)
                                        close()
                                    }
                                    drawPath(path, Color(0xFF1C1C1E))
                                }
                            }
                        }
                    }
                    Box(
                        Modifier
                            .offset(x = 30.dp, y = 30.dp)
                            .size(26.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { expand() }
                    )
                }
            }
        }
    }

    if (voiceOpen) {
        VoiceSheet(
            voices = voices,
            state = voiceState,
            toneNow = toneNow,
            onPick = pick@{ index ->
                val v = voices.getOrNull(index)
                if (v == null) {
                    voiceOpen = false
                    return@pick
                }
                if (!v.ready) {
                    Tip.show(t("该音色需先在系统语音设置中下载"))
                    return@pick
                }
                toneNow = v.tone
                TtsService.setTone(index)
                Tip.show("${t("已切换音色")} · ${v.label}")
                voiceOpen = false
            },
            onRetry = {
                voiceState = "loading"
                TtsService.reloadVoices()
                voices = parseVoices(TtsService.voicesJson())
                voiceState = if (voices.isNotEmpty()) "done" else "empty"
            },
            onClose = { voiceOpen = false }
        )
    }
}
