package com.lantai.reader.ui.comp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Switch
import androidx.compose.foundation.Image
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.R
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t

val Disp = FontFamily.Serif
val Mono = FontFamily.Monospace

fun isGlyphIcon(glyph: String): Boolean = glyph.isNotEmpty() && glyph[0].code < 128

data class TabItem(val label: String, val icon: String)

fun tabs(): List<TabItem> = listOf(
    TabItem(t("首页"), "home"),
    TabItem(t("书库"), "library"),
    TabItem(t("统计"), "chart"),
    TabItem(t("我的"), "user")
)

@Composable
fun ScreenHead(
    title: String = "",
    sub: String = "",
    back: String = "",
    action: String = "",
    actionIcon: String = "",
    actionOn: Boolean = false,
    actionIcon2: String = "",
    actionOn2: Boolean = false,
    actionIcon3: String = "",
    actionOn3: Boolean = false,
    brand: Boolean = false,
    titleSize: Float = 18f,
    onBack: () -> Unit = {},
    onAction: () -> Unit = {},
    onAction2: () -> Unit = {},
    onAction3: () -> Unit = {}
) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.glass)
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(C.line, topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
            }
            .padding(start = Dp2.page.dp, end = Dp2.page.dp, top = 12.dp, bottom = 11.dp)
    ) {
        if (back.isNotEmpty()) {
            Row(
                Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(start = 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Glyph("back", 16.dp, C.tx2)
                Text(back, fontSize = 12.5.sp, color = C.tx2)
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 33.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (brand) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_lantai),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(31.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .border(1.dp, C.accLine, RoundedCornerShape(9.dp))
                    )
                    Column {
                        Text(
                            t("兰台阅读"),
                            fontSize = 17.sp,
                            fontFamily = Disp,
                            letterSpacing = 3.sp,
                            color = C.acc2
                        )
                        Text(
                            "LANTAI READER",
                            fontSize = 8.sp,
                            fontFamily = Mono,
                            letterSpacing = 2.sp,
                            color = C.tx4,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            } else {
                Text(
                    title,
                    fontSize = titleSize.sp,
                    fontFamily = Disp,
                    letterSpacing = 1.sp,
                    color = C.tx,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.weight(1f))

            if (actionIcon3.isNotEmpty()) {
                HeadBtn(actionIcon3, actionOn3, onAction3)
            }
            if (actionIcon2.isNotEmpty()) {
                HeadBtn(actionIcon2, actionOn2, onAction2)
            }
            if (actionIcon.isNotEmpty()) {
                HeadBtn(actionIcon, actionOn, onAction, bordered = true)
            }
            if (action.isNotEmpty()) {
                Text(
                    action,
                    fontSize = 11.5.sp,
                    color = C.acc,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dp2.rS.dp))
                        .background(C.accBg)
                        .clickable { onAction() }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }

        if (brand && title.isNotEmpty()) {
            Text(
                title,
                fontSize = 21.sp,
                fontFamily = Disp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium,
                color = C.tx,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        if (sub.isNotEmpty()) {
            Text(
                sub,
                fontSize = 11.5.sp,
                color = C.tx3,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun HeadBtn(icon: String, on: Boolean, tap: () -> Unit, bordered: Boolean = false) {
    val C = Tm.pal
    Box(
        Modifier
            .size(33.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) C.accBg else Color.Transparent)
            .then(if (bordered) Modifier.border(1.dp, if (on) C.accLine else Color.Transparent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable { tap() },
        contentAlignment = Alignment.Center
    ) {
        Glyph(icon, 17.dp, if (on) C.acc2 else C.tx2)
    }
}

@Composable
fun BottomTabs(current: Int, onPick: (Int) -> Unit) {
    val C = Tm.pal
    var gx by remember { mutableStateOf(0f) }
    var gy by remember { mutableStateOf(0f) }
    var gi by remember { mutableStateOf(0f) }
    var press by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val pressAnim by animateFloatAsState(press, tween(180), label = "tabPress")

    val glow = Brush.radialGradient(
        colors = listOf(
            C.acc.copy(alpha = 0.94f),
            C.acc.copy(alpha = 0.44f),
            C.acc.copy(alpha = 0.12f),
            C.acc.copy(alpha = 0f)
        ),
        radius = with(density) { 76.dp.toPx() }
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(
                    26.dp,
                    RoundedCornerShape(26.dp),
                    ambientColor = C.shadow.copy(alpha = 0.4f),
                    spotColor = C.shadow.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(C.bg.copy(alpha = 0.88f))
                .background(C.navGlass)
                .border(1.dp, C.navEdge, RoundedCornerShape(26.dp))
                .graphicsLayer {
                    scaleX = 1f - pressAnim * 0.012f
                    scaleY = 1f - pressAnim * 0.02f
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            gi = 1f
                            press = 1f
                            tryAwaitRelease()
                            gi = 0f
                            press = 0f
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            val p = e.changes.firstOrNull()?.position
                            if (p != null) {
                                gx = p.x
                                gy = p.y
                            }
                        }
                    }
                }
        ) {
            if (gi > 0f) {
                Box(
                    Modifier
                        .offset(
                            x = with(density) { gx.toDp() } - 76.dp,
                            y = with(density) { gy.toDp() } - 76.dp
                        )
                        .size(152.dp)
                        .clip(CircleShape)
                        .background(glow)
                )
            }
            Row(
                Modifier.fillMaxSize().padding(top = 8.dp, bottom = 9.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs().forEachIndexed { index, item ->
                    val on = index == current
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { onPick(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            Modifier
                                .width(18.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (on) C.acc2 else Color.Transparent)
                        )
                        Glyph(item.icon, 21.dp, if (on) C.acc2 else C.tx)
                        Text(
                            item.label,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            color = if (on) C.acc2 else C.tx,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavRow(
    glyph: String = "",
    title: String,
    sub: String = "",
    right: String = "",
    danger: Boolean = false,
    onTap: () -> Unit = {}
) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        if (glyph.isNotEmpty()) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.bg3),
                contentAlignment = Alignment.Center
            ) {
                if (isGlyphIcon(glyph)) {
                    Glyph(glyph, 15.dp, if (danger) C.bad else C.tx2)
                } else {
                    Text(glyph, fontSize = 10.5.sp, color = if (danger) C.bad else C.tx2)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontSize = 12.5.sp,
                color = if (danger) C.bad else C.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    fontSize = 10.sp,
                    color = C.tx4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (right.isNotEmpty()) {
            Text(right, fontSize = 10.sp, color = C.tx4)
        }
        Text("›", fontSize = 14.sp, color = C.tx4)
    }
}

@Composable
fun ToggleRow(
    glyph: String = "",
    title: String,
    sub: String = "",
    isOn: Boolean,
    onSwitch: (Boolean) -> Unit
) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        if (glyph.isNotEmpty()) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.bg3),
                contentAlignment = Alignment.Center
            ) {
                if (isGlyphIcon(glyph)) {
                    Glyph(glyph, 15.dp, C.tx2)
                } else {
                    Text(glyph, fontSize = 10.5.sp, color = C.tx2)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 12.5.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 10.sp, color = C.tx4, maxLines = 2, modifier = Modifier.fillMaxWidth())
            }
        }
        Switch(
            checked = isOn,
            onCheckedChange = onSwitch,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.bg,
                checkedTrackColor = C.acc,
                checkedBorderColor = C.acc,
                uncheckedThumbColor = C.tx3,
                uncheckedTrackColor = C.bg3,
                uncheckedBorderColor = C.line2
            ),
            modifier = Modifier.width(38.dp).height(22.dp)
        )
    }
}

@Composable
fun Pill(label: String, tone: String = "mute") {
    val C = Tm.pal
    val tint = when (tone) {
        "done" -> C.ok
        "acc" -> C.acc2
        "warn" -> C.warn
        "bad" -> C.bad
        "info" -> C.info
        else -> C.tx3
    }
    Text(
        label,
        fontSize = 8.5.sp,
        color = tint,
        modifier = Modifier
            .border(1.dp, tint, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
fun SegBar(options: List<String>, current: Int, onPick: (Int) -> Unit) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(C.bg2)
            .border(1.dp, C.line, RoundedCornerShape(9.dp))
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val on = index == current
            Box(
                Modifier
                    .weight(1f)
                    .height(27.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) C.accBg else Color.Transparent)
                    .clickable { onPick(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (on) C.acc2 else C.tx3,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ActionBtn(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    glyph: String = "",
    enabled: Boolean = true,
    onTap: () -> Unit
) {
    val C = Tm.pal
    val fg = when {
        !enabled -> C.tx4
        primary -> C.onAcc
        danger -> C.bad
        else -> C.tx2
    }
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) (if (enabled) C.acc else C.bg3) else C.bg2)
            .border(
                1.dp,
                when {
                    primary -> if (enabled) C.acc else C.line
                    danger -> C.bad
                    else -> C.line2
                },
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) { onTap() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (glyph.isNotEmpty()) {
            if (isGlyphIcon(glyph)) {
                Glyph(glyph, 14.dp, fg)
                Spacer(Modifier.width(5.dp))
            } else {
                Text(glyph, fontSize = 10.5.sp, color = fg)
                Spacer(Modifier.width(5.dp))
            }
        }
        Text(label, fontSize = 12.sp, color = fg, maxLines = 1)
    }
}

@Composable
fun CardBox(
    padH: Float = 13f,
    padV: Float = 2f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val C = Tm.pal
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(horizontal = padH.dp, vertical = padV.dp)
    ) {
        content()
    }
}

@Composable
fun GroupHead(title: String, meta: String = "", action: String = "", onAction: () -> Unit = {}) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 14.5.sp,
            color = C.tx,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        if (meta.isNotEmpty()) {
            Text(meta, fontSize = 9.5.sp, color = C.tx4, modifier = Modifier.padding(start = 7.dp))
        }
        Spacer(Modifier.weight(1f))
        if (action.isNotEmpty()) {
            Text(
                "$action ›",
                fontSize = 10.5.sp,
                color = C.tx3,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

@Composable
fun NoteCard(text: String) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(15.dp)
                .clip(CircleShape)
                .border(1.dp, C.line2, CircleShape)
                .padding(top = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("i", fontSize = 9.sp, color = C.tx4, textAlign = TextAlign.Center)
        }
        Text(
            text,
            fontSize = 10.sp,
            color = C.tx4,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
