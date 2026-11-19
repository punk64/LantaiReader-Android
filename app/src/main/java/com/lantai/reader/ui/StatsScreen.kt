package com.lantai.reader.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.BookItem
import com.lantai.reader.model.PrefKeys
import com.lantai.reader.model.Prefs
import com.lantai.reader.model.ReadingStats
import com.lantai.reader.model.Store
import com.lantai.reader.net.orphanSweep
import com.lantai.reader.net.ProgressOutbox
import com.lantai.reader.net.readingTracker
import com.lantai.reader.ui.comp.ActionBtn
import com.lantai.reader.ui.comp.EmptyView
import com.lantai.reader.ui.comp.ErrorView
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.ui.comp.LoadingView
import com.lantai.reader.ui.comp.NoteCard
import com.lantai.reader.ui.comp.Pill
import com.lantai.reader.ui.comp.ProgressBar
import com.lantai.reader.ui.comp.ScreenHead
import com.lantai.reader.ui.comp.ToggleRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class Metric(val value: String, val unit: String, val label: String)

private data class BarItem(val date: String, val label: String, val ratio: Float, val today: Boolean)

private data class RankRow(
    val bookId: String,
    val title: String,
    val meta: String,
    val percent: Int,
    val book: BookItem?
)

private val WEEK_LABEL = listOf("日", "一", "二", "三", "四", "五", "六")

private fun emptyMetrics(): List<Metric> = listOf(
    Metric("—", "", t("总时长")),
    Metric("—", "", t("翻页数")),
    Metric("—", "", t("读过的书"))
)

@Composable
private fun MetricsCard(metrics: List<Metric>) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        metrics.forEachIndexed { index, item ->
            if (index > 0) {
                Box(Modifier.width(1.dp).height(38.dp).background(C.line))
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(item.value, fontSize = 19.sp, color = C.acc2)
                    if (item.unit.isNotEmpty()) {
                        Text(item.unit, fontSize = 10.sp, color = C.acc2, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                Text(item.label, fontSize = 10.sp, color = C.tx4)
            }
        }
    }
}

@Composable
private fun BarsCard(bars: List<BarItem>, peakText: String) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { item ->
                Column(
                    Modifier.weight(1f).height(106.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(max(4f, item.ratio * 84f).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (item.today) C.acc else C.bg4)
                    )
                    Text(
                        item.label,
                        fontSize = 9.sp,
                        color = if (item.today) C.acc2 else C.tx4,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        Text(
            peakText,
            fontSize = 9.5f.sp,
            color = C.tx4,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun RankCard(rank: List<RankRow>, onOpen: (RankRow) -> Unit) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(horizontal = 13.dp)
    ) {
        rank.forEachIndexed { index, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text("${index + 1}", fontSize = 11.sp, color = C.acc2, modifier = Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (item.title.isNotEmpty()) item.title else t("读取中…"),
                        fontSize = 12.5f.sp,
                        color = if (item.title.isNotEmpty()) C.tx else C.tx4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        item.meta,
                        fontSize = 10.sp,
                        color = C.tx4,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    Modifier
                        .width(58.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(C.bg4)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.percent / 100f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(C.acc)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncIconBox(glyph: String) {
    val C = Tm.pal
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.bg3),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 10.5f.sp, color = C.tx2, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatsScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var incognito by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var metrics by remember { mutableStateOf(emptyMetrics()) }
    var rangeText by remember { mutableStateOf("") }
    var bars by remember { mutableStateOf<List<BarItem>>(emptyList()) }
    var peakText by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf<List<RankRow>>(emptyList()) }
    var sessions by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0) }
    var lastSyncAt by remember { mutableStateOf(0L) }
    var syncPhase by remember { mutableStateOf("idle") }
    var syncDone by remember { mutableStateOf(0) }
    var syncTotal by remember { mutableStateOf(0) }
    var syncSent by remember { mutableStateOf(0) }
    var syncDropped by remember { mutableStateOf(0) }
    var syncFailed by remember { mutableStateOf(0) }
    var syncRemain by remember { mutableStateOf(0) }
    var sweepNote by remember { mutableStateOf("") }
    var ranksLoading by remember { mutableStateOf(false) }

    fun weekOf(date: String): String {
        val parts = date.split("-")
        if (parts.size != 3) return ""
        val year = parts[0].toIntOrNull() ?: return ""
        val month = parts[1].toIntOrNull() ?: return ""
        val day = parts[2].toIntOrNull() ?: return ""
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day)
        return t(WEEK_LABEL[cal.get(Calendar.DAY_OF_WEEK) - 1])
    }

    fun thousands(value: Int): String {
        val text = "$value"
        val sb = StringBuilder()
        for (index in text.indices) {
            val rest = text.length - index
            sb.append(text[index])
            if (rest > 1 && rest % 3 == 1) sb.append(',')
        }
        return sb.toString()
    }

    fun applyStats(stats: ReadingStats) {
        val hours = floor(stats.totalDurationMs / 3600000.0).toInt()
        val minutes = (floor(stats.totalDurationMs / 60000.0).toInt()) % 60
        metrics = listOf(
            Metric(
                if (hours > 0) "$hours" else "${floor(stats.totalDurationMs / 60000.0).toInt()}",
                if (hours > 0) "h ${minutes}m" else "m",
                t("总时长")
            ),
            Metric(thousands(stats.totalPagesRead), "", t("翻页数")),
            Metric("${stats.bookCount}", "", t("读过的书"))
        )
        rangeText = if (stats.from.isNotEmpty() && stats.to.isNotEmpty()) {
            t("${stats.from.take(10)} ~ ${stats.to.take(10)} · ${stats.sessionCount} 次会话")
        } else {
            t("${stats.sessionCount} 次会话")
        }
        val days = stats.days
        var peak = 0L
        var total = 0L
        for (day in days) {
            peak = max(peak, day.durationMs)
            total += day.durationMs
        }
        val items = ArrayList<BarItem>()
        val lastDate = if (days.isNotEmpty()) days.last().date.take(10) else ""
        for (day in days) {
            val date = day.date.take(10)
            items.add(
                BarItem(
                    date = date,
                    label = weekOf(date),
                    ratio = if (peak > 0) (day.durationMs.toFloat() / peak) else 0f,
                    today = date == lastDate
                )
            )
        }
        bars = items
        peakText = if (days.isNotEmpty()) {
            t("最高 ${Math.round(peak / 60000.0)} 分钟 · 日均 ${Math.round(total.toDouble() / days.size / 60000.0)} 分钟")
        } else {
            t("近 7 天没有阅读记录")
        }
    }

    suspend fun loadRank(stats: ReadingStats) {
        val sorted = stats.books.sortedByDescending { it.durationMs }
        val head = sorted.take(5)
        val peak = if (head.isNotEmpty()) head[0].durationMs else 0L
        val rows = ArrayList<RankRow>()
        for (stat in head) {
            rows.add(
                RankRow(
                    bookId = stat.bookId,
                    title = "",
                    meta = t("${F.shortDuration(stat.durationMs)} · ${stat.pagesRead} 页 · ${stat.sessionCount} 次"),
                    percent = if (peak > 0) (stat.durationMs.toFloat() / peak * 100).roundToInt() else 0,
                    book = null
                )
            )
        }
        rank = rows
        if (head.isEmpty()) return
        ranksLoading = true
        val filled = ArrayList<RankRow>()
        for ((index, stat) in head.withIndex()) {
            val row = rows[index]
            try {
                val detail = Store.api.book(stat.bookId)
                filled.add(row.copy(title = detail.title, book = detail))
            } catch (e: Throwable) {
                continue
            }
        }
        rank = filled
        ranksLoading = false
    }

    suspend fun refreshCounts() {
        sessions = readingTracker.pendingCount()
        progress = ProgressOutbox.count()
    }

    suspend fun reload() {
        loading = true
        error = ""
        try {
            val stats = Store.api.readingStats()
            applyStats(stats)
            refreshCounts()
            loading = false
            loadRank(stats)
        } catch (e: Throwable) {
            error = e.message ?: ""
            metrics = emptyMetrics()
            bars = emptyList()
            rank = emptyList()
            loading = false
        }
    }

    suspend fun refreshStats() {
        try {
            val stats = Store.api.readingStats()
            applyStats(stats)
            loadRank(stats)
        } catch (e: Throwable) {
        }
    }

    fun busy(): Boolean = syncPhase == "detect" || syncPhase == "upload"

    fun syncLabel(): String = when (syncPhase) {
        "detect" -> t("检测中…")
        "upload" -> t("上报中…")
        "done" -> t("已同步")
        "fail" -> t("重试同步")
        else -> t("立即同步")
    }

    fun syncPercent(): Int {
        if (syncPhase == "done") return 100
        if (syncTotal <= 0) return if (syncPhase == "upload") 40 else 0
        return min(100, (syncDone.toFloat() / syncTotal * 100).roundToInt())
    }

    fun syncText(): String {
        if (syncPhase == "detect") return t("核对书架中已移除的书籍…")
        if (syncPhase == "upload") {
            return if (syncTotal > 0) t("上报 $syncDone / $syncTotal 条") else t("正在上报…")
        }
        if (syncPhase == "done") {
            var text = t("成功 $syncSent 条")
            if (syncDropped > 0) text += t(" · 丢弃 $syncDropped 条")
            return text
        }
        if (syncPhase == "fail") return t("未完成 · 仍积压 $syncRemain 条")
        return ""
    }

    fun lastSyncText(): String {
        if (lastSyncAt <= 0) return t("尚未同步")
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(lastSyncAt))
        return "${t("上次同步")} · ${F.ago(iso)}"
    }

    suspend fun syncNow() {
        if (busy()) return
        refreshCounts()
        syncTotal = sessions + progress
        syncDone = 0
        syncSent = 0
        syncDropped = 0
        syncFailed = 0
        syncRemain = syncTotal
        sweepNote = ""
        val session = Store.session
        if (!session.ready()) {
            syncPhase = "fail"
            return
        }
        syncPhase = "detect"
        val sweep = orphanSweep.run(session, Store.api)
        if (sweep.failed) {
            sweepNote = t("失效检测未完成，本轮只上报未失效的数据")
        } else if (sweep.books > 0) {
            sweepNote = t("${sweep.books} 本书已从兰台移除：丢弃会话 ${sweep.sessions} 条、进度 ${sweep.progress} 条、离线副本 ${sweep.offline} 本待清理")
            syncDropped += sweep.sessions + sweep.progress
        }
        refreshCounts()
        syncTotal = sessions + progress
        syncPhase = "upload"
        val sessionReport = readingTracker.flush(session) { done, _ -> syncDone = done }
        syncSent += sessionReport.sent
        syncDropped += sessionReport.dropped
        syncFailed += sessionReport.failed
        val base = sessionReport.sent + sessionReport.dropped
        val progressReport = ProgressOutbox.flush(session) { done, _ -> syncDone = base + done }
        syncSent += progressReport.sent
        syncDropped += progressReport.dropped
        syncFailed += progressReport.failed
        refreshCounts()
        syncRemain = sessions + progress
        syncDone = syncSent + syncDropped
        if (syncRemain > 0) {
            syncPhase = "fail"
            return
        }
        syncPhase = "done"
        lastSyncAt = System.currentTimeMillis()
        Prefs.write(PrefKeys.syncAt, "$lastSyncAt")
        refreshStats()
        delay(4000)
        if (syncPhase == "done") syncPhase = "idle"
    }

    suspend fun detectNow() {
        if (busy()) return
        syncPhase = "detect"
        syncSent = 0
        syncDropped = 0
        syncDone = 0
        syncTotal = 0
        sweepNote = ""
        val sweep = orphanSweep.run(Store.session, Store.api)
        refreshCounts()
        syncRemain = sessions + progress
        if (sweep.failed) {
            syncPhase = "fail"
            sweepNote = t("检测失败，请检查网络后重试")
            return
        }
        if (sweep.books > 0) {
            syncDropped = sweep.sessions + sweep.progress
            sweepNote = t("${sweep.books} 本书已从兰台移除：丢弃会话 ${sweep.sessions} 条、进度 ${sweep.progress} 条、离线副本 ${sweep.offline} 本待清理")
        } else {
            sweepNote = t("没有发现失效书籍")
        }
        syncPhase = "done"
        refreshStats()
        delay(4000)
        if (syncPhase == "done") syncPhase = "idle"
    }

    fun openBook(row: RankRow) {
        val book = row.book ?: return
        Store.selected = book
        Nav.push("book")
    }

    LaunchedEffect(Unit) {
        val stamp = Prefs.read(PrefKeys.syncAt)
        lastSyncAt = stamp.toLongOrNull() ?: 0L
        scope.launch { reload() }
    }

    Column(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            ScreenHead(
                title = t("阅读统计"),
                sub = t("时长只来自心跳 · 页数只来自翻页")
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Dp2.page.dp)
                .padding(bottom = 100.dp)
        ) {
            when {
                loading -> Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    LoadingView(t("汇总阅读统计"))
                }

                error.isNotEmpty() -> Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    ErrorView(message = error, onRetry = { scope.launch { reload() } })
                }

                else -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Pill(t("近 30 天"), "acc")
                        Text(
                            rangeText,
                            fontSize = 10.sp,
                            color = C.tx4,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Box(Modifier.padding(top = 12.dp)) { MetricsCard(metrics) }
                    GroupHead(title = t("每日时长"), meta = t("分钟"))
                    if (bars.isNotEmpty()) {
                        BarsCard(bars, peakText)
                    } else {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            EmptyView(
                                title = t("还没有阅读记录"),
                                hint = t("读几页书，这里会出现每日时长"),
                                glyph = "clock"
                            )
                        }
                    }
                    GroupHead(title = t("按书排行"), meta = if (ranksLoading) t("加载书名") else "")
                    if (rank.isNotEmpty()) {
                        RankCard(rank) { openBook(it) }
                    } else {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            EmptyView(
                                title = t("近 30 天没有读完的会话"),
                                hint = t("翻页与时长都记在服务端"),
                                glyph = "book"
                            )
                        }
                    }
                    GroupHead(title = t("同步与离线"))

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dp2.rM.dp))
                            .background(C.bg1)
                            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                            .padding(horizontal = 13.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            SyncIconBox("会")
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t("离线会话待补报"), fontSize = 12.5f.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
                                Text(
                                    if (sessions > 0) t("$sessions 条 · 联网后自动上报") else t("无积压"),
                                    fontSize = 10.sp,
                                    color = C.tx4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Pill(if (sessions > 0) t("待同步") else t("已同步"), if (sessions > 0) "warn" else "done")
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            SyncIconBox("进")
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t("进度补报"), fontSize = 12.5f.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
                                Text(
                                    if (progress > 0) t("$progress 条 · 断网时暂存本机") else t("无积压"),
                                    fontSize = 10.sp,
                                    color = C.tx4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Pill(if (progress > 0) t("待同步") else t("已同步"), if (progress > 0) "warn" else "done")
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            SyncIconBox("幂")
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t("幂等去重"), fontSize = 12.5f.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
                                Text(
                                    t("同一会话重发不会重复计时"),
                                    fontSize = 10.sp,
                                    color = C.tx4,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Pill(t("已启用"), "done")
                        }
                        Text(
                            lastSyncText(),
                            fontSize = 9.5f.sp,
                            color = C.tx4,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
                        )
                        ToggleRow(
                            glyph = "eyeOff",
                            title = t("隐身阅读"),
                            sub = t("该时段不写进度、不计时长"),
                            isOn = incognito,
                            onSwitch = { incognito = it }
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            ActionBtn(label = syncLabel(), glyph = "sync", onTap = { scope.launch { syncNow() } })
                        }
                        Box(Modifier.weight(1f)) {
                            ActionBtn(label = t("检测失效"), glyph = "search", onTap = { scope.launch { detectNow() } })
                        }
                    }

                    if (syncPhase != "idle") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(Dp2.rM.dp))
                                .background(C.bg1)
                                .border(1.dp, if (syncPhase == "fail") C.bad else C.line, RoundedCornerShape(Dp2.rM.dp))
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    syncLabel(),
                                    fontSize = 10.5f.sp,
                                    color = when (syncPhase) {
                                        "fail" -> C.bad
                                        "done" -> C.ok
                                        else -> C.acc2
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (syncPhase == "upload") {
                                    Text("${syncPercent()}%", fontSize = 10.sp, color = C.acc2)
                                }
                            }
                            if (syncPhase == "upload" || syncPhase == "done") {
                                ProgressBar(percent = syncPercent())
                            }
                            Text(
                                syncText(),
                                fontSize = 10.sp,
                                color = C.tx3,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (syncPhase == "fail") {
                                Text(
                                    t("检查网络或登录状态后重试"),
                                    fontSize = 9.5f.sp,
                                    color = C.bad,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (sweepNote.isNotEmpty()) {
                                Text(
                                    sweepNote,
                                    fontSize = 9.5f.sp,
                                    color = C.tx4,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Box(Modifier.padding(top = 12.dp)) {
                        NoteCard(
                            t("统计只认服务端 reading/stats 的聚合结果；该接口固定返回近 30 天，页面不再提供其它区间。离线产生的会话先落本机队列，联网后按幂等键补报，不会重复计时。同步前会先核对书籍是否仍在兰台，已移除书籍的会话、进度与离线副本直接丢弃。")
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}
