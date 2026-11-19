package com.lantai.reader.ui.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.t
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

data class GuideStep(
    val anchor: String = "",
    val zones: Boolean = false,
    val group: String = "",
    val need: String = "",
    val title: String = "",
    val body: String = ""
)

class GuideAnchors {
    private val map = HashMap<String, Rect>()

    fun put(id: String, rect: Rect) {
        if (id.isEmpty()) return
        map[id] = rect
    }

    fun get(id: String): Rect? {
        if (id.isEmpty()) return null
        val rect = map[id] ?: return null
        if (rect.width <= 0f || rect.height <= 0f) return null
        return rect
    }
}

fun Modifier.anchored(sink: GuideAnchors, id: String): Modifier =
    this.onGloballyPositioned { sink.put(id, it.boundsInRoot()) }

fun comicGuideSteps(): List<GuideStep> = listOf(
    GuideStep(
        anchor = "comic_top_back",
        group = t("顶栏"),
        title = t("返回"),
        body = t("左上角这个键退出阅读器，回到书架或详情页。刚读到的页码已经存下来了，下次打开直接续上。顶栏自动收起时，先点屏幕中间把它叫出来。")
    ),
    GuideStep(
        anchor = "comic_top_title",
        group = t("顶栏"),
        title = t("书名与页码"),
        body = t("上面一行是书名，太长会被省略号截断；下面一行是「第 x / y 页」。若末尾多了「· 隐身」，说明本次阅读不记进度、不上报时长。")
    ),
    GuideStep(
        anchor = "comic_prog_pos",
        group = t("进度"),
        title = t("页码计数"),
        body = t("当前页 / 总页数。拖动右边的进度条时，这个数字会临时变成目标页，松手才真正跳过去。")
    ),
    GuideStep(
        anchor = "comic_prog_slider",
        group = t("进度"),
        title = t("进度条"),
        body = t("拖动只预览页码、不跳页，松手才跳；直接点轨道任意位置也会立刻跳过去。几百页的长篇比一页页翻快得多。")
    ),
    GuideStep(
        anchor = "comic_prog_pct",
        group = t("进度"),
        title = t("全书百分比"),
        body = t("当前页在全书的位置。薄册子变化快，厚书里每 1% 都可能是几十页，用它判断还剩多少更直观。")
    ),
    GuideStep(
        anchor = "comic_prog_prev",
        group = t("进度"),
        title = t("上一页"),
        body = t("退一页，双页模式下退一整屏两页。也可以点屏幕左侧三分之一。")
    ),
    GuideStep(
        anchor = "comic_prog_next",
        group = t("进度"),
        title = t("下一页"),
        body = t("进一页，双页模式进一整屏两页。也可以点屏幕右侧三分之一。")
    ),
    GuideStep(
        anchor = "comic_tool_thumbs",
        group = t("工具"),
        need = "thumbs",
        title = t("缩略图"),
        body = t("点一下在底栏上方拉开缩略图条，再点一下收起。缩略图条打开期间这个图标是金色。")
    ),
    GuideStep(
        anchor = "comic_thumbs_strip",
        group = t("工具"),
        need = "thumbs",
        title = t("缩略图条"),
        body = t("横向滑动浏览全书每一页，当前页带金色边框，点任意一张直接跳过去。快速滑动时先转圈、停下再出图，避免一次解码上百张。")
    ),
    GuideStep(
        anchor = "comic_tool_settings",
        group = t("工具"),
        need = "panel",
        title = t("画质与版式"),
        body = t("点开底栏上方的设置面板：版式三选、裁白边、校色、三档画质。改动即时生效，退出阅读器后也会记住。")
    ),
    GuideStep(
        anchor = "comic_panel_mode",
        group = t("版式"),
        need = "panel",
        title = t("版式"),
        body = t("单页一次一张；双页一次两张，奇数页会自动归到偶数页起始；连续滚动上下滑到底自动接下一页。右侧数字是当前缩放倍数。")
    ),
    GuideStep(
        anchor = "comic_panel_crop",
        group = t("版式"),
        need = "panel",
        title = t("裁白边"),
        body = t("自动切掉扫描页四周的白边，画面更大更饱满，适合白边很宽的老扫描件。本身满幅的彩页打开反而会切到内容。")
    ),
    GuideStep(
        anchor = "comic_panel_enhance",
        group = t("版式"),
        need = "panel",
        title = t("校色"),
        body = t("提亮并压暗部，发灰的扫描件能看清细节。彩页打开可能偏色，看彩页时建议关掉。")
    ),
    GuideStep(
        anchor = "comic_panel_quality",
        group = t("版式"),
        need = "panel",
        title = t("画质"),
        body = t("省流最省流量也最模糊，标准日常够用，高清最清楚但吃流量与内存。切换后当前页会重新加载一次。")
    ),
    GuideStep(
        anchor = "comic_panel_guide",
        group = t("版式"),
        need = "panel",
        title = t("操作指引"),
        body = t("就是当前这个引导，随时可以重看一遍。它在面板最后一行，右侧标注「逐步演示」。")
    ),
    GuideStep(
        anchor = "comic_tool_bookmark",
        group = t("工具"),
        title = t("书签"),
        body = t("全书唯一的书签开关，金色表示当前页已标记，再点一次取消。放在底栏是为了单手够得到。")
    ),
    GuideStep(
        zones = true,
        group = t("交互"),
        title = t("触控有效区域"),
        body = t("屏幕横向分三块：左侧三分之一上一页，中间三分之一呼出或收起工具栏，右侧三分之一下一页。放大状态下三块都不响应。")
    ),
    GuideStep(
        group = t("交互"),
        title = t("手势"),
        body = t("双指捏合最大放大到 4 倍，放大后单指拖动查看局部；长按以触点为中心放大 1.5 倍，松开还原。放大时翻页请用进度条或缩略图。")
    ),
    GuideStep(
        anchor = "comic_guide_bottom",
        group = t("交互"),
        title = t("自动收起"),
        body = t("两条栏在 3.2 秒无操作后自动收起，把整页画面让出来。点屏幕中间唤出，再点一次立刻收起。翻页不会把工具栏叫出来。")
    )
)

private val ShadeColor = Color(0xCC030405)
private val ZoneLeft = Color(0x57789EC4)
private val ZoneMid = Color(0x42E3C377)
private val ZoneRight = Color(0x57C48E68)
private val ZoneLine = Color(0xEBE3C377)
private val RingSoft = Color(0x4DE3C377)
private val RingHard = Color(0xF2F0D392)
private val BubbleBg = Color(0xFF15171A)
private val BubbleEdge = Color(0xFF6D5B2C)
private val BubbleGroup = Color(0xFFBFA163)
private val BubbleTitle = Color(0xFFF0EBDF)
private val BubbleBody = Color(0xFFBAB5A9)
private val BubbleSkip = Color(0xFF8D887C)
private val BubbleOn = Color(0xFFD8D2C4)
private val BubbleOff = Color(0xFF5B574E)
private val BubbleOnEdge = Color(0xFF41453F)
private val BubbleOffEdge = Color(0xFF2B2E2C)
private val BubbleAccent = Color(0xFFE3C377)
private val BubbleAccentTx = Color(0xFF1A1C1A)

@Composable
fun ReaderGuide(
    visible: Boolean,
    index: Int,
    steps: List<GuideStep>,
    anchors: GuideAnchors,
    safeTop: Float,
    safeBottom: Float,
    onIndex: (Int) -> Unit,
    onNeed: (String) -> Unit,
    onClose: () -> Unit
) {
    if (!visible || steps.isEmpty()) return
    val density = LocalDensity.current
    val step = steps[index.coerceIn(0, steps.size - 1)]
    var hole by remember { mutableStateOf<FloatArray?>(null) }
    var bubbleHeight by remember { mutableStateOf(196f) }

    LaunchedEffect(index, step.anchor) {
        onNeed(step.need)
        hole = null
        delay(60)
        hole = holeOf(anchors, step.anchor, density)
        delay(400)
        hole = holeOf(anchors, step.anchor, density)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cw = maxWidth.value
        val ch = maxHeight.value
        val box = hole
        val padded = if (box != null && box[2] > 0f && box[3] > 0f) {
            val p = 7f
            val x = max(0f, box[0] - p)
            val y = max(0f, box[1] - p)
            floatArrayOf(x, y, min(cw - x, box[2] + p * 2), min(ch - y, box[3] + p * 2))
        } else null

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            val px = { v: Float -> v.dp.toPx() }
            if (step.zones) {
                val top = safeTop + 52f
                val bottom = safeBottom + 120f
                val height = max(0f, ch - top - bottom)
                val w = cw
                drawRect(ShadeColor)
                if (height > 0f) {
                    drawRect(ZoneLeft, Offset(0f, px(top)), Size(px(w * 0.30f), px(height)))
                    drawRect(ZoneMid, Offset(px(w * 0.30f), px(top)), Size(px(w * 0.40f), px(height)))
                    drawRect(ZoneRight, Offset(px(w * 0.70f), px(top)), Size(px(w * 0.30f), px(height)))
                    val line = 1.5.dp.toPx()
                    drawLine(ZoneLine, Offset(px(w * 0.30f), px(top)), Offset(px(w * 0.30f), px(top + height)), line)
                    drawLine(ZoneLine, Offset(px(w * 0.70f), px(top)), Offset(px(w * 0.70f), px(top + height)), line)
                    drawLine(ZoneLine, Offset(0f, px(top)), Offset(px(w), px(top)), line)
                    drawLine(ZoneLine, Offset(0f, px(top + height)), Offset(px(w), px(top + height)), line)
                }
                return@Canvas
            }
            val canvas = drawContext.canvas
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawRect(ShadeColor)
            if (padded != null) {
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(px(padded[0]), px(padded[1])),
                    size = Size(px(padded[2]), px(padded[3])),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }
            canvas.restore()
            if (padded != null) {
                drawRoundRect(
                    color = RingSoft,
                    topLeft = Offset(px(padded[0]), px(padded[1])),
                    size = Size(px(padded[2]), px(padded[3])),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(6.dp.toPx())
                )
                drawRoundRect(
                    color = RingHard,
                    topLeft = Offset(px(padded[0]), px(padded[1])),
                    size = Size(px(padded[2]), px(padded[3])),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(1.6.dp.toPx())
                )
            }
        }

        val bubbleW = max(210f, min(340f, cw - 28f))
        val bubbleX = if (step.zones || padded == null) {
            max(14f, (cw - bubbleW) / 2f)
        } else {
            min(
                max(14f, padded[0] + padded[2] / 2f - bubbleW / 2f),
                max(14f, cw - bubbleW - 14f)
            )
        }
        val bubbleY = if (step.zones || padded == null) {
            max(safeTop + 14f, (ch - bubbleHeight) / 2f - 40f)
        } else {
            val below = padded[1] + padded[3] + 18f
            val above = padded[1] - bubbleHeight - 18f
            val roomBelow = ch - safeBottom - 14f - below
            val roomAbove = above - safeTop - 14f
            when {
                roomBelow >= bubbleHeight -> below
                roomAbove >= 0f -> above
                else -> max(safeTop + 14f, min(below, ch - bubbleHeight - 14f))
            }
        }

        Column(
            Modifier
                .offset { IntOffset(with(density) { bubbleX.dp.roundToPx() }, with(density) { bubbleY.dp.roundToPx() }) }
                .width(bubbleW.dp)
                .onSizeChanged { bubbleHeight = with(density) { it.height.toDp() }.value }
                .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color(0xA6000000))
                .background(BubbleBg, RoundedCornerShape(16.dp))
                .border(1.dp, BubbleEdge, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${step.group} · ${index + 1} / ${steps.size}",
                    fontSize = 9.5f.sp,
                    fontFamily = Mono,
                    letterSpacing = 1.sp,
                    color = BubbleGroup
                )
                Spacer(Modifier.weight(1f))
                Text(
                    t("跳过"),
                    fontSize = 10.5f.sp,
                    color = BubbleSkip,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                step.title,
                fontSize = 16.sp,
                fontFamily = Disp,
                color = BubbleTitle,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                step.body,
                fontSize = 12.sp,
                lineHeight = 21.sp,
                color = BubbleBody,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t("上一步"),
                    fontSize = 11.5f.sp,
                    color = if (index > 0) BubbleOn else BubbleOff,
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (index > 0) BubbleOnEdge else BubbleOffEdge,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable(enabled = index > 0) { if (index > 0) onIndex(index - 1) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(BubbleAccent, RoundedCornerShape(9.dp))
                        .clickable {
                            if (index >= steps.size - 1) onClose() else onIndex(index + 1)
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (index >= steps.size - 1) t("开始阅读") else t("下一步"),
                        fontSize = 11.5f.sp,
                        color = BubbleAccentTx
                    )
                }
            }
        }
    }
}

private fun holeOf(
    anchors: GuideAnchors,
    id: String,
    density: androidx.compose.ui.unit.Density
): FloatArray? {
    val rect = anchors.get(id) ?: return null
    return with(density) {
        floatArrayOf(
            rect.left.toDp().value,
            rect.top.toDp().value,
            rect.width.toDp().value,
            rect.height.toDp().value
        )
    }
}
