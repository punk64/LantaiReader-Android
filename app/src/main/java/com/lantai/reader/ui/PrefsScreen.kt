package com.lantai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.PrefKeys
import com.lantai.reader.model.Prefs
import com.lantai.reader.ui.comp.ActionBtn
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.ui.comp.ScreenHead
import com.lantai.reader.ui.comp.SegBar
import com.lantai.reader.ui.comp.ToggleRow
import java.util.Locale

@Composable
private fun FieldLabel(text: String, gap: Int) {
    val C = Tm.pal
    Text(
        text,
        fontSize = 10.5.sp,
        color = C.tx3,
        modifier = Modifier.fillMaxWidth().padding(top = gap.dp, bottom = 8.dp)
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    display: String,
    onChange: (Int) -> Unit
) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, fontSize = 11.sp, color = C.tx3, modifier = Modifier.width(36.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(Math.round(it)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = max - min - 1,
            colors = SliderDefaults.colors(
                thumbColor = C.acc2,
                activeTrackColor = C.acc,
                inactiveTrackColor = C.bg4
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            display,
            fontSize = 10.5.sp,
            color = C.tx2,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
fun PrefsScreen() {
    val C = Tm.pal
    var cmMode by remember { mutableStateOf(Prefs.int(PrefKeys.cmMode, 0)) }
    var cmFit by remember { mutableStateOf(Prefs.int(PrefKeys.cmFit, 0)) }
    var cmQuality by remember { mutableStateOf(Prefs.int(PrefKeys.cmQuality, 2)) }
    var cmMargin by remember { mutableStateOf(Prefs.int(PrefKeys.cmMargin, 2)) }
    var cmDir by remember { mutableStateOf(Prefs.int(PrefKeys.cmDir, 0)) }
    var fs by remember { mutableStateOf(Prefs.int(PrefKeys.fs, 18)) }
    var lh by remember { mutableStateOf(Prefs.int(PrefKeys.lh, 18)) }
    var measure by remember { mutableStateOf(Prefs.int(PrefKeys.measure, 34)) }
    var ebFont by remember { mutableStateOf(Prefs.int(PrefKeys.ebFont, 0)) }
    var ebTheme by remember { mutableStateOf(Prefs.int(PrefKeys.ebTheme, 0)) }
    var keepScreen by remember { mutableStateOf(Prefs.bool(PrefKeys.keepScreen, true)) }
    var hideStatus by remember { mutableStateOf(Prefs.bool(PrefKeys.hideStatus, false)) }
    var autoHide by remember { mutableStateOf(Prefs.bool(PrefKeys.autoHide, true)) }
    var prefSync by remember { mutableStateOf(Prefs.bool(PrefKeys.prefSync, false)) }
    var resetOpen by remember { mutableStateOf(false) }

    fun persist() {
        Prefs.putInt(PrefKeys.cmMode, cmMode)
        Prefs.putInt(PrefKeys.cmFit, cmFit)
        Prefs.putInt(PrefKeys.cmQuality, cmQuality)
        Prefs.putInt(PrefKeys.cmMargin, cmMargin)
        Prefs.putInt(PrefKeys.cmDir, cmDir)
        Prefs.putInt(PrefKeys.fs, fs)
        Prefs.putInt(PrefKeys.lh, lh)
        Prefs.putInt(PrefKeys.measure, measure)
        Prefs.putInt(PrefKeys.ebFont, ebFont)
        Prefs.putInt(PrefKeys.ebTheme, ebTheme)
        Prefs.putBool(PrefKeys.keepScreen, keepScreen)
        Prefs.putBool(PrefKeys.hideStatus, hideStatus)
        Prefs.putBool(PrefKeys.autoHide, autoHide)
        Prefs.putBool(PrefKeys.prefSync, prefSync)
    }

    fun save() {
        persist()
        Tip.show(t("偏好已保存，下次打开阅读器生效"))
        Nav.pop()
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(
            title = t("阅读偏好"),
            sub = t("本机即时生效 · 可跨端同步"),
            back = t("我的"),
            action = t("保存"),
            onBack = { Nav.pop() },
            onAction = { save() }
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Dp2.page.dp, end = Dp2.page.dp)
        ) {
            GroupHead(title = t("漫画"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(13.dp)
            ) {
                FieldLabel(t("阅读模式"), 2)
                SegBar(listOf(t("单页"), t("双页"), t("长条"), t("分页")), cmMode) { cmMode = it }
                FieldLabel(t("画面适应"), 14)
                SegBar(listOf(t("适应"), t("满宽"), t("满高"), t("原始")), cmFit) { cmFit = it }
                FieldLabel(t("画质"), 14)
                SegBar(listOf(t("省流"), t("标准"), t("高清")), cmQuality) { cmQuality = it }
                FieldLabel(t("页边距"), 14)
                SegBar(listOf(t("无"), t("窄"), t("标准"), t("宽")), cmMargin) { cmMargin = it }
                FieldLabel(t("翻页方向"), 14)
                SegBar(listOf(t("从左往右"), t("从右往左")), cmDir) { cmDir = it }
            }

            GroupHead(title = t("电子书"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(13.dp)
            ) {
                SliderRow(t("字号"), fs, 14, 26, "${fs}px") { fs = it }
                SliderRow(t("行距"), lh, 14, 24, String.format(Locale.US, "%.1f", lh / 10.0)) { lh = it }
                SliderRow(t("版心宽"), measure, 26, 44, "$measure") { measure = it }
                FieldLabel(t("字体"), 14)
                SegBar(listOf(t("宋体"), t("黑体"), t("楷体"), t("原书字体")), ebFont) { ebFont = it }
                FieldLabel(t("纸张主题"), 14)
                SegBar(listOf(t("纸白"), t("旧纸"), t("夜读")), ebTheme) { ebTheme = it }
            }

            GroupHead(title = t("通用"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(horizontal = 13.dp)
            ) {
                ToggleRow(
                    glyph = "sun",
                    title = t("保持屏幕常亮"),
                    sub = t("阅读期间不自动锁屏"),
                    isOn = keepScreen,
                    onSwitch = { keepScreen = it }
                )
                ToggleRow(
                    glyph = "eyeOff",
                    title = t("隐藏状态栏"),
                    sub = t("沉浸式全屏阅读"),
                    isOn = hideStatus,
                    onSwitch = { hideStatus = it }
                )
                ToggleRow(
                    glyph = "grid",
                    title = t("工具栏自动收起"),
                    sub = t("3.2 秒无操作后淡出，按住时暂停计时"),
                    isOn = autoHide,
                    onSwitch = { autoHide = it }
                )
                ToggleRow(
                    glyph = "sync",
                    title = t("跨端同步阅读偏好"),
                    sub = t("写入服务端 client-settings · 关闭时仅存本机"),
                    isOn = prefSync,
                    onSwitch = { prefSync = it }
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionBtn(label = t("恢复默认"), modifier = Modifier.weight(1f)) { resetOpen = true }
                ActionBtn(label = t("保存"), modifier = Modifier.weight(1f), primary = true) { save() }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (resetOpen) {
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            containerColor = C.bg1,
            title = { Text(t("恢复默认"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    t("所有阅读偏好回到初始值，本机已保存的设置会被覆盖。"),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    resetOpen = false
                    cmMode = 0
                    cmFit = 0
                    cmQuality = 2
                    cmMargin = 2
                    cmDir = 0
                    fs = 18
                    lh = 18
                    measure = 34
                    ebFont = 0
                    ebTheme = 0
                    keepScreen = true
                    hideStatus = false
                    autoHide = true
                    prefSync = false
                    save()
                }) {
                    Text(t("恢复"), fontSize = 12.sp, color = C.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetOpen = false }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }
}
