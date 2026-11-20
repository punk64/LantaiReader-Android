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
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.common.tf
import com.lantai.reader.data.JobRow
import com.lantai.reader.data.OfflineBookRow
import com.lantai.reader.data.offlineDb
import com.lantai.reader.model.Store
import com.lantai.reader.net.BackgroundTask
import com.lantai.reader.net.OfflineDownloader
import com.lantai.reader.net.ProgressOutbox
import com.lantai.reader.net.offlineCache
import com.lantai.reader.net.orphanSweep
import com.lantai.reader.net.readingTracker
import com.lantai.reader.ui.comp.ActionBtn
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.ui.comp.ProgressBar
import com.lantai.reader.ui.comp.ScreenHead
import com.lantai.reader.ui.comp.SegBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
private fun StatLine(glyph: String, label: String, value: String) {
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
            if (glyph.isNotEmpty() && glyph[0].code < 128) {
                Glyph(glyph, 15.dp, C.tx2)
            } else {
                Text(glyph, fontSize = 10.5.sp, color = C.tx2)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 12.5.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
            Text(value, fontSize = 10.sp, color = C.tx4, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DownloadRow(row: JobRow, orphan: Boolean, onPrimary: () -> Unit, onRemove: () -> Unit) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoverImage(
            bookId = row.bookId,
            title = if (row.title.isNotEmpty()) row.title.substring(0, 1) else "",
            coverWidth = 44.dp,
            coverHeight = 62.dp,
            radius = 4.dp
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                row.title,
                fontSize = 12.5.sp,
                color = C.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                subtitleOf(row),
                fontSize = 9.5.sp,
                color = if (row.state == "FAILED") C.acc else C.tx4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (orphan) {
                Text(
                    t("云端已移除"),
                    fontSize = 9.sp,
                    color = C.bad,
                    modifier = Modifier
                        .border(1.dp, C.bad, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (row.state != "DONE") {
                ProgressBar(percent = percentOf(row), modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (row.state != "DONE") {
            Box(
                Modifier
                    .width(28.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.bg2)
                    .border(1.dp, C.line, RoundedCornerShape(8.dp))
                    .clickable { onPrimary() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (row.state == "PAUSED" || row.state == "FAILED") t("续") else t("暂"),
                    fontSize = 11.sp,
                    color = C.tx2,
                    textAlign = TextAlign.Center
                )
            }
        }
        Box(
            Modifier
                .width(26.dp)
                .height(28.dp)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Glyph("trash", 15.dp, C.tx4)
        }
    }
}

private fun subtitleOf(row: JobRow): String {
    when (row.state) {
        "DONE" -> {
            val size = offlineCache.sizeOf(row.bookId)
            val at = try {
                F.ago(Instant.ofEpochMilli(row.at).toString())
            } catch (e: Throwable) {
                ""
            }
            return t("${row.total} 项 · ${F.bytes(if (size > 0) size else row.bytes)} · ${at}完成")
        }

        "FAILED" -> return if (row.msg.isNotEmpty()) row.msg else t("下载失败")
        "PAUSED" -> return t("${row.done} / ${row.total} 项 · ${F.bytes(row.bytes)} · ${row.msg}")
        "PENDING" -> return t("排队中 · 等待前一项完成")
    }
    val percent = F.percent(row.done, row.total)
    return t("${row.done} / ${row.total} 项 · ${F.bytes(row.bytes)} · $percent%")
}

private fun percentOf(row: JobRow): Int =
    if (row.state == "DONE") 100 else F.percent(row.done, row.total)

@Composable
fun DownloadsScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var jobs by remember { mutableStateOf<List<JobRow>>(emptyList()) }
    var books by remember { mutableStateOf<List<OfflineBookRow>>(emptyList()) }
    var queued by remember { mutableStateOf(0) }
    var sessions by remember { mutableStateOf(0) }
    var orphans by remember { mutableStateOf<List<String>>(emptyList()) }
    var sweeping by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            jobs = offlineDb.jobs()
            books = offlineDb.books()
            queued = ProgressOutbox.count()
            sessions = readingTracker.pendingCount()
        } catch (e: Throwable) {
        }
        busy = BackgroundTask.active()
    }

    suspend fun sweep() {
        if (sweeping) return
        sweeping = true
        val report = orphanSweep.run(Store.session, Store.api, false)
        orphans = report.offlineIds
        sweeping = false
        if (report.books > 0) {
            Tip.show(t("${report.books} 本书已从兰台移除，其会话与进度已丢弃"))
        }
    }

    LaunchedEffect(Unit) {
        reload()
        sweep()
        OfflineDownloader.pump()
        while (true) {
            reload()
            delay(1200)
        }
    }

    val running = jobs.filter { it.state == "RUNNING" || it.state == "PENDING" || it.state == "PAUSED" }
    val done = jobs.filter { it.state == "DONE" }
    val failed = jobs.filter { it.state == "FAILED" }
    val rows = when (tab) {
        0 -> running
        1 -> done
        else -> failed
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(
            title = t("下载队列"),
            sub = t("离线副本"),
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
            SegBar(
                options = listOf(
                    t("进行中 ${running.size}"),
                    t("已完成 ${done.size}"),
                    t("失败 ${failed.size}")
                ),
                current = tab,
                onPick = { tab = it }
            )

            if (rows.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(Dp2.rM.dp))
                        .background(C.bg1)
                        .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                        .padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Glyph("download", 22.dp, C.tx4)
                    Text(
                        when (tab) {
                            0 -> t("没有正在下载的任务")
                            1 -> t("还没有离线副本")
                            else -> t("没有失败的下载")
                        },
                        fontSize = 11.5.sp,
                        color = C.tx4
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(Dp2.rM.dp))
                        .background(C.bg1)
                        .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                        .padding(horizontal = 13.dp)
                ) {
                    rows.forEach { row ->
                        DownloadRow(
                            row = row,
                            orphan = orphans.contains(row.bookId),
                            onPrimary = {
                                scope.launch {
                                    if (row.state == "RUNNING" || row.state == "PENDING") {
                                        OfflineDownloader.pause(row.bookId)
                                    } else if (row.state == "PAUSED" || row.state == "FAILED") {
                                        OfflineDownloader.resume(row.bookId)
                                    }
                                    reload()
                                }
                            },
                            onRemove = {
                                scope.launch {
                                    OfflineDownloader.remove(row.bookId)
                                    reload()
                                }
                            }
                        )
                    }
                }
            }

            if (orphans.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(Dp2.rM.dp))
                        .background(C.bg1)
                        .border(1.dp, C.bad, RoundedCornerShape(Dp2.rM.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            t("${orphans.size} 本离线副本已从兰台移除"),
                            fontSize = 11.5.sp,
                            color = C.tx,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            t("清理只删本机副本与索引，不影响云端"),
                            fontSize = 9.5.sp,
                            color = C.tx4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        t("清理"),
                        fontSize = 10.5.sp,
                        color = C.bad,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(C.bg2)
                            .border(1.dp, C.bad, RoundedCornerShape(7.dp))
                            .clickable {
                                scope.launch {
                                    val ids = orphans.toList()
                                    for (id in ids) OfflineDownloader.remove(id)
                                    orphans = emptyList()
                                    reload()
                                    Tip.show(t("已清理 ${ids.size} 本失效副本"))
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            GroupHead(title = t("离线存储"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(horizontal = 13.dp)
            ) {
                StatLine(
                    glyph = "download",
                    label = t("离线占用"),
                    value = tf("{0} · {1} 本", listOf(F.bytes(books.sumOf { it.bytes }), books.size.toString()))
                )
                StatLine(
                    glyph = "upload",
                    label = t("长时任务"),
                    value = if (busy) t("下载中 · 后台保持") else t("空闲")
                )
                StatLine(
                    glyph = "clock",
                    label = t("待补报会话"),
                    value = if (sessions > 0) t("$sessions 条 · 联网后自动上报") else t("无积压")
                )
                StatLine(
                    glyph = "sync",
                    label = t("待补传进度"),
                    value = if (queued > 0) t("$queued 条 · 联网后自动上报") else t("无积压")
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionBtn(label = t("全部暂停"), glyph = "pause", modifier = Modifier.weight(1f)) {
                    scope.launch {
                        OfflineDownloader.pauseAll()
                        reload()
                    }
                }
                ActionBtn(
                    label = t("清除已完成"),
                    glyph = "trash",
                    danger = true,
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch {
                        OfflineDownloader.clearDone()
                        reload()
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
