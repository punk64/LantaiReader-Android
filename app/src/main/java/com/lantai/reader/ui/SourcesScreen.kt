package com.lantai.reader.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.common.tf
import com.lantai.reader.components.CoverCache
import com.lantai.reader.data.OfflineBookRow
import com.lantai.reader.data.offlineDb
import com.lantai.reader.local.CacheQuota
import com.lantai.reader.local.LocalStore
import com.lantai.reader.model.BookQuery
import com.lantai.reader.model.Store
import com.lantai.reader.net.CachePurge
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.ui.comp.Mono
import com.lantai.reader.ui.comp.NoteCard
import com.lantai.reader.ui.comp.Pill
import com.lantai.reader.ui.comp.ScreenHead
import kotlinx.coroutines.launch

private data class SourceRow(
    val glyph: String,
    val title: String,
    val sub: String,
    val count: String,
    val tone: String,
    val feed: String
)

private data class Legend(val color: Color, val label: String)

private fun capText(bytes: Long): String = F.cap(bytes)

@Composable
private fun QuotaRing(percent: Int) {
    val C = Tm.pal
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = C.bg4,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (percent > 0) {
                drawArc(
                    color = C.acc,
                    startAngle = -90f,
                    sweepAngle = 360f * percent / 100f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("$percent%", fontSize = 15.sp, color = C.acc2)
            Text(t("已用"), fontSize = 7.5.sp, color = C.tx4)
        }
    }
}

@Composable
private fun SourceLine(row: SourceRow, connText: String) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(C.bg3),
            contentAlignment = Alignment.Center
        ) {
            if (row.glyph.isNotEmpty() && row.glyph[0].code < 128) {
                Glyph(row.glyph, 15.dp, C.tx2)
            } else {
                Text(row.glyph, fontSize = 10.5.sp, color = C.tx2, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, fontSize = 12.5.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
            Text(
                if (row.feed == "cloud") "$connText · ${t("已连接")}" else row.sub,
                fontSize = 10.sp,
                color = C.tx4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Pill(label = row.count, tone = row.tone)
    }
}

@Composable
fun SourcesScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    var connText by remember { mutableStateOf(Store.connectionLabel()) }
    var rows by remember { mutableStateOf<List<SourceRow>>(emptyList()) }
    var legends by remember { mutableStateOf<List<Legend>>(emptyList()) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var limitBytes by remember { mutableStateOf(CacheQuota.limit()) }
    var limitInput by remember { mutableStateOf(capText(CacheQuota.limit())) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var purgingLocal by remember { mutableStateOf(false) }
    var purgingCache by remember { mutableStateOf(false) }

    fun usagePercent(): Int {
        if (limitBytes <= 0) return 0
        val ratio = cacheBytes.toDouble() / limitBytes * 100
        if (ratio >= 100) return 100
        return maxOf(if (cacheBytes > 0) 1 else 0, Math.round(ratio).toInt())
    }

    fun rootLabel(): String {
        val root = Store.localRoot
        if (root.isEmpty()) return t("未记录")
        if (root == "picked") return t("逐个文件选择导入")
        return root.replace("file://", "")
    }

    suspend fun loadCache() {
        cacheBytes = try {
            CacheQuota.measure()
        } catch (e: Throwable) {
            0L
        }
    }

    suspend fun reload() {
        loading = true
        connText = Store.connectionLabel()
        val localCount = Store.localMetas.size
        var localBytes = 0L
        for (meta in Store.localMetas) localBytes += meta.sizeBytes
        val cloudTotal = try {
            Store.api.books(BookQuery(size = 1)).total
        } catch (e: Throwable) {
            0
        }
        var offlineCount = 0
        var offlineBytes = 0L
        try {
            val books: List<OfflineBookRow> = offlineDb.books()
            offlineCount = books.size
            for (row in books) offlineBytes += row.bytes
        } catch (e: Throwable) {
            offlineCount = 0
        }
        rows = listOf(
            SourceRow(
                glyph = "book",
                title = t("本地库"),
                sub = t("应用内副本 · 可移除"),
                count = t("$localCount 册"),
                tone = if (localCount > 0) "acc" else "mute",
                feed = "local"
            ),
            SourceRow(
                glyph = "server",
                title = t("云库"),
                sub = t("$connText · 已连接"),
                count = t("$cloudTotal 册"),
                tone = "info",
                feed = "cloud"
            ),
            SourceRow(
                glyph = "download",
                title = t("离线副本"),
                sub = t("云库下载到本机 · 断网可读"),
                count = t("$offlineCount 册"),
                tone = if (offlineCount > 0) "done" else "mute",
                feed = "offline"
            )
        )
        legends = listOf(
            Legend(C.acc, t("离线副本 ${F.bytes(offlineBytes)}")),
            Legend(C.bad, t("本机导入 ${F.bytes(localBytes)}"))
        )
        loading = false
    }

    LaunchedEffect(Unit) {
        reload()
        loadCache()
    }
    LaunchedEffect(Store.netTick) { connText = Store.connectionLabel() }

    fun applyLimit() {
        val bytes = CacheQuota.parse(limitInput)
        if (bytes <= 0) {
            Tip.show(t("请输入 256M ~ 64G 的容量，如 4G、512M、1.5G"))
            return
        }
        if (bytes == limitBytes) {
            Tip.show(t("上限未变化"))
            return
        }
        busy = true
        scope.launch {
            CacheQuota.apply(bytes)
            limitBytes = CacheQuota.limit()
            limitInput = capText(limitBytes)
            loadCache()
            busy = false
            Tip.show(t("缓存上限已设为 ${capText(limitBytes)}"))
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(
            title = t("来源与存储"),
            sub = t("本地库 / 云库 / 离线副本"),
            back = t("我的"),
            onBack = { Nav.pop() }
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Dp2.page.dp, end = Dp2.page.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuotaRing(usagePercent())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(t("缓存"), fontSize = 14.5.sp, color = C.tx, letterSpacing = 0.5.sp)
                    Text(
                        "${capText(cacheBytes)} / ${capText(limitBytes)}",
                        fontSize = 16.sp,
                        fontFamily = Mono,
                        color = C.tx
                    )
                    Text(
                        t("解压页图与文档副本 · 超出上限清理最久未用"),
                        fontSize = 9.5.sp,
                        color = C.tx4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            Modifier
                                .width(70.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(C.bg2)
                                .border(1.dp, C.line2, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = limitInput,
                                onValueChange = { if (it.length <= 7) limitInput = it },
                                singleLine = true,
                                enabled = !busy,
                                textStyle = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                    color = C.tx,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.acc),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            )
                        }
                        Text(
                            t("应用"),
                            fontSize = 11.sp,
                            color = C.acc2,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(C.bg2)
                                .border(1.dp, C.line2, RoundedCornerShape(7.dp))
                                .clickable(enabled = !busy && limitInput.isNotEmpty()) { applyLimit() }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                        Text(
                            t("清理"),
                            fontSize = 11.sp,
                            color = C.tx2,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(C.bg2)
                                .border(1.dp, C.line, RoundedCornerShape(7.dp))
                                .clickable(enabled = !busy) { purgingCache = true }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                legends.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(item.color))
                        Text(
                            item.label,
                            fontSize = 9.sp,
                            color = C.tx4,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (Store.localMetas.isNotEmpty() || Store.localBooks.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(Dp2.rM.dp))
                        .background(C.bg1)
                        .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (Store.localRootStale) t("本地库需要重新授权") else t("本地库目录"),
                            fontSize = 11.sp,
                            color = if (Store.localRootStale) C.warn else C.tx
                        )
                        Text(
                            rootLabel(),
                            fontSize = 9.5.sp,
                            color = C.tx4,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        t("移除全部"),
                        fontSize = 10.5.sp,
                        color = C.tx3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(C.bg2)
                            .clickable { purgingLocal = true }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }

            GroupHead(title = t("书库来源"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(horizontal = 13.dp)
            ) {
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        Text(t("读取中…"), fontSize = 11.sp, color = C.tx4)
                    }
                } else {
                    rows.forEach { row -> SourceLine(row, connText) }
                }
            }

            Box(Modifier.padding(top = 14.dp)) {
                NoteCard(text = t("原始文件始终只保留一份；缓存可随时清理重建，超出上限时自动覆盖最久未用。"))
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (purgingLocal) {
        AlertDialog(
            onDismissRequest = { purgingLocal = false },
            containerColor = C.bg1,
            title = { Text(t("移除全部本地书"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    t("将删除本地书索引、阅读进度与应用内副本，并释放占用的空间。授权目录中的原文件不会被删除。"),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    purgingLocal = false
                    scope.launch {
                        val freed = LocalStore.purgeAll(Store.localMetas)
                        Store.localBooks = emptyList()
                        Store.localMetas = emptyList()
                        Store.localMissing = emptyList()
                        Store.localRoot = ""
                        Store.localRootStale = false
                        reload()
                        Tip.show(tf("已移除全部本地书，释放 {0}", listOf(F.bytes(freed))))
                    }
                }) {
                    Text(t("移除"), fontSize = 12.sp, color = C.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { purgingLocal = false }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }

    if (purgingCache) {
        AlertDialog(
            onDismissRequest = { purgingCache = false },
            containerColor = C.bg1,
            title = { Text(t("清理缓存"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    t("将清空页图与文档副本缓存，原始文件与离线副本不受影响。正在阅读的书会在下次打开时重新解压。"),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    purgingCache = false
                    scope.launch {
                        val freed = CachePurge.purge()
                        CoverCache.clear()
                        loadCache()
                        Tip.show(if (freed > 0) t("已释放 ${F.bytes(freed)}") else t("没有可释放的缓存"))
                    }
                }) {
                    Text(t("清理"), fontSize = 12.sp, color = C.acc)
                }
            },
            dismissButton = {
                TextButton(onClick = { purgingCache = false }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }
}
