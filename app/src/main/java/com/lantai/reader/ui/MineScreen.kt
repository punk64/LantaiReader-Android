package com.lantai.reader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lantai.reader.BuildConfig
import com.lantai.reader.R
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Lang
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.data.offlineDb
import com.lantai.reader.model.BookQuery
import com.lantai.reader.model.PrefKeys
import com.lantai.reader.model.Prefs
import com.lantai.reader.model.Store
import com.lantai.reader.net.AutoSwitch
import com.lantai.reader.net.ProgressOutbox
import com.lantai.reader.net.UpdateChecker
import com.lantai.reader.net.UpdateResult
import com.lantai.reader.net.readingTracker
import com.lantai.reader.ui.comp.ActionBtn
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.common.Glyph
import com.lantai.reader.ui.comp.Mono
import com.lantai.reader.ui.comp.NavRow
import com.lantai.reader.ui.comp.ScreenHead
import com.lantai.reader.ui.comp.ToggleRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class MineMetric(val value: String, val label: String)

private fun emptyMetrics(): List<MineMetric> = listOf(
    MineMetric("—", t("藏书")),
    MineMetric("—", t("在读")),
    MineMetric("—", t("离线")),
    MineMetric("—", t("本月"))
)

private class MineCtl(val scope: CoroutineScope) {
    var autoSync by mutableStateOf(true)
    var incognito by mutableStateOf(false)
    var wifiOnly by mutableStateOf(true)
    var metrics by mutableStateOf(emptyMetrics())
    var queueLabel by mutableStateOf(t("读取中"))
    var outboxLabel by mutableStateOf(t("读取中"))
    var listLabel by mutableStateOf(t("读取中"))
    var collectionLabel by mutableStateOf(t("读取中"))
    var storageLabel by mutableStateOf(t("读取中"))
    var themeLabel by mutableStateOf("")
    var langLabel by mutableStateOf("")
    var updateLabel by mutableStateOf("")
    var repoDraft by mutableStateOf(UpdateChecker.source())

    suspend fun boot() {
        wifiOnly = Prefs.bool("wifiOnly", true)
        refreshLabels()
        loadMetrics()
    }

    fun refreshLabels() {
        themeLabel = if (Tm.mode == "light") t("浅色") else t("深色")
        langLabel = Lang.label()
        repoDraft = UpdateChecker.source()
    }

    suspend fun loadOfflineMetrics() {
        try {
            val rows = offlineDb.books()
            var bytes = 0L
            for (row in rows) bytes += row.bytes
            storageLabel = t("${rows.size} 本离线 · ${F.bytes(bytes)}")
            metrics = listOf(
                MineMetric("${Store.localMetas.size + rows.size}", t("藏书")),
                MineMetric("—", t("在读")),
                MineMetric("${rows.size}", t("离线")),
                MineMetric("—", t("本月"))
            )
            queueLabel = t("离线模式不可用")
            listLabel = t("离线模式不可用")
            collectionLabel = t("离线模式不可用")
        } catch (e: Throwable) {
            metrics = emptyMetrics()
            storageLabel = t("读取失败")
        }
        try {
            val pending = ProgressOutbox.count()
            val sessions = readingTracker.pendingCount()
            val total = pending + sessions
            outboxLabel = if (total > 0) t("$total 条待上报") else t("无积压")
        } catch (e: Throwable) {
            outboxLabel = t("读取失败")
        }
    }

    suspend fun loadMetrics() {
        if (Store.offline) {
            loadOfflineMetrics()
            return
        }
        try {
            val page = Store.api.books(BookQuery(size = 1, sort = "lastModified,desc"))
            val stats = Store.api.readingStats()
            val offline = offlineDb.books()
            var offlineBytes = 0L
            for (row in offline) offlineBytes += row.bytes
            storageLabel = t("${offline.size} 本离线 · ${F.bytes(offlineBytes)}")
            metrics = listOf(
                MineMetric("${page.total}", t("藏书")),
                MineMetric("${stats.bookCount}", t("在读")),
                MineMetric("${offline.size}", t("离线")),
                MineMetric(F.shortDuration(stats.totalDurationMs), t("本月"))
            )
        } catch (e: Throwable) {
            metrics = emptyMetrics()
            storageLabel = t("读取失败")
        }
        try {
            val jobs = offlineDb.jobs()
            var running = 0
            var waiting = 0
            for (job in jobs) {
                if (job.state == "RUNNING") {
                    running += 1
                } else if (job.state == "PENDING" || job.state == "PAUSED") {
                    waiting += 1
                }
            }
            queueLabel = if (jobs.isEmpty()) t("空闲") else t("$running 项进行中 · $waiting 项等待中")
            val pending = ProgressOutbox.count()
            val sessions = readingTracker.pendingCount()
            val total = pending + sessions
            outboxLabel = if (total > 0) t("$total 条待上报") else t("无积压")
        } catch (e: Throwable) {
            queueLabel = t("读取失败")
            outboxLabel = t("读取失败")
        }
        try {
            val lists = Store.api.readlists()
            listLabel = t("${lists.size} 个")
        } catch (e: Throwable) {
            listLabel = t("读取失败")
        }
        try {
            val collections = Store.api.collections()
            collectionLabel = if (collections.isEmpty()) t("暂无") else t("${collections.size} 个")
        } catch (e: Throwable) {
            collectionLabel = t("读取失败")
        }
    }

    fun goOnline() {
        if (!Store.offline) return
        scope.launch {
            val restored = Store.exitOffline()
            if (!restored) {
                Tip.show(t("会话已失效，请从登录页重新登录"))
                return@launch
            }
            ProgressOutbox.flush(Store.session)
            readingTracker.flush(Store.session, null)
            AutoSwitch.start()
            Tip.show(t("已切回在线"))
            Store.notifyOffline()
            loadMetrics()
        }
    }

    fun goOffline() {
        if (Store.offline) return
        Store.enterOffline()
    }

    fun applyWifiOnly(value: Boolean) {
        wifiOnly = value
        Prefs.putBool("wifiOnly", value)
    }

    fun pickTheme(mode: String) {
        Tm.apply(mode)
        Prefs.write(PrefKeys.uiTheme, mode)
        refreshLabels()
        Store.uiTick += 1
    }

    fun pickLang(code: String) {
        Lang.apply(code)
        com.lantai.reader.reader.ReaderHost.invalidateLang()
        refreshLabels()
        scope.launch { loadMetrics() }
    }

    suspend fun checkUpdate(version: String): UpdateResult {
        updateLabel = t("检查中…")
        val result = UpdateChecker.check(version)
        updateLabel = when (result.state) {
            "new" -> t("发现新版本") + " · " + result.version
            "latest" -> t("已是最新版本")
            else -> if (result.message == "NO_RELEASE") {
                t("未找到 release")
            } else if (result.message == "INVALID_SOURCE") {
                t("更新源无效")
            } else {
                t("检查失败")
            }
        }
        return result
    }

    fun saveRepo() {
        val text = repoDraft.trim()
        if (text.isEmpty()) {
            Tip.show(t("更新源无效"))
            return
        }
        UpdateChecker.saveRepo(text)
        updateLabel = ""
        Tip.show(t("已保存"))
    }
}

@Composable
private fun MineGroup(content: @Composable () -> Unit) {
    val C = Tm.pal
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(horizontal = 13.dp)
    ) {
        content()
    }
}

@Composable
private fun UserCard() {
    val C = Tm.pal
    val name = remember(Store.profileTick, Store.account, Store.nickname) { Store.displayName() }
    val conn = remember(Store.netTick, Store.offlineTick, Store.profileTick) { Store.connectionLabel() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val face = Store.avatar
        if (face != null) {
            Image(
                bitmap = face.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, C.accLine, RoundedCornerShape(14.dp))
            )
        } else {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.accBg)
                    .border(1.dp, C.accLine, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (name.isNotEmpty()) name.substring(0, 1).uppercase() else t("读"),
                    fontSize = 17.sp,
                    color = C.acc2
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                fontSize = 15.sp,
                color = C.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (Store.offline) {
                    t("离线模式 · 仅本机内容")
                } else {
                    "${t("已连接")} $conn · ${t("双来源可用")}"
                },
                fontSize = 10.sp,
                color = C.tx4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val tint = if (Store.offline) C.acc2 else C.ok
        Text(
            if (Store.offline) t("离线") else t("在线"),
            fontSize = 9.sp,
            color = tint,
            modifier = Modifier
                .border(1.dp, if (Store.offline) C.accLine else C.ok, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetricCard(metrics: List<MineMetric>) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(Dp2.rM.dp))
            .background(C.bg1)
            .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        metrics.forEachIndexed { index, item ->
            if (index > 0) {
                Box(Modifier.width(1.dp).height(30.dp).background(C.line))
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(item.value, fontSize = 16.5.sp, color = C.acc2)
                Text(t(item.label), fontSize = 9.5.sp, color = C.tx4)
            }
        }
    }
}

@Composable
private fun OnlineRow(ctl: MineCtl) {
    val C = Tm.pal
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (Store.offline) t("离线模式") else t("在线模式"),
                fontSize = 12.5.sp,
                color = C.tx,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (Store.offline) t("只显示已下载的云库书与本地书") else Store.connectionLabel(),
                fontSize = 9.5.sp,
                color = C.tx4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OnlineBtn(t("在线"), !Store.offline) { ctl.goOnline() }
        OnlineBtn(t("离线"), Store.offline) { ctl.goOffline() }
    }
}

@Composable
private fun OnlineBtn(label: String, on: Boolean, tap: () -> Unit) {
    val C = Tm.pal
    Box(
        Modifier
            .width(56.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) C.acc else C.bg2)
            .border(1.dp, if (on) C.acc else C.line, RoundedCornerShape(8.dp))
            .clickable { tap() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, color = if (on) C.onAcc else C.tx2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RepoEditor(ctl: MineCtl, onDone: () -> Unit) {
    val C = Tm.pal
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(C.bg2)
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = ctl.repoDraft,
                onValueChange = { ctl.repoDraft = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = C.tx),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(C.acc),
                modifier = Modifier.fillMaxWidth()
            )
            if (ctl.repoDraft.isEmpty()) {
                Text("owner/repo 或 https://…", fontSize = 12.sp, color = C.tx4)
            }
        }
        Text(
            t("保存"),
            fontSize = 12.sp,
            color = C.acc,
            modifier = Modifier.clickable {
                ctl.saveRepo()
                onDone()
            }
        )
        Text(
            t("取消"),
            fontSize = 12.sp,
            color = C.tx3,
            modifier = Modifier.clickable {
                ctl.repoDraft = UpdateChecker.source()
                onDone()
            }
        )
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    val C = Tm.pal
    val version = BuildConfig.VERSION_NAME
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dp2.rL.dp))
                .background(C.bg1)
                .border(1.dp, C.line, RoundedCornerShape(Dp2.rL.dp))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.logo_lantai),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, C.accLine, RoundedCornerShape(18.dp))
            )
            Text(
                t("兰台阅读"),
                fontSize = 19.sp,
                fontFamily = Disp,
                letterSpacing = 4.sp,
                color = C.acc2,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                if (version.isNotEmpty()) "LANTAI READER · $version" else "LANTAI READER",
                fontSize = 8.5.sp,
                fontFamily = Mono,
                letterSpacing = 1.6.sp,
                color = C.tx4,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                t("本地书库阅读客户端。仅面向个人与家庭自用，不提供公开注册与分享、不提供收费代下、不与任何预置内容源捆绑分发。"),
                fontSize = 10.5.sp,
                color = C.tx3,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            Text(
                t("不含任何在线书源、文本源客户端或书籍抓取引擎。"),
                fontSize = 10.5.sp,
                color = C.tx3,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.bg3)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(t("关闭"), fontSize = 12.sp, color = C.acc)
            }
        }
    }
}

@Composable
private fun PickDialog(title: String, options: List<String>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val C = Tm.pal
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.bg1,
        titleContentColor = C.tx,
        textContentColor = C.tx3,
        title = { Text(title, fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, label ->
                    Text(
                        label,
                        fontSize = 13.sp,
                        color = C.tx2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onPick(index) }
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(t("取消"), fontSize = 12.sp, color = C.tx3)
            }
        }
    )
}

@Composable
fun MineScreen(onOpenTab: (Int) -> Unit = {}) {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    val ctl = remember { MineCtl(scope) }
    var editingRepo by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var themeOpen by remember { mutableStateOf(false) }
    var langOpen by remember { mutableStateOf(false) }
    var logoutOpen by remember { mutableStateOf(false) }
    var updateOpen by remember { mutableStateOf<UpdateResult?>(null) }
    val version = BuildConfig.VERSION_NAME

    val themeKey = Tm.mode
    val offlineKey = Store.offline
    val offlineTick = Store.offlineTick
    val uiKey = Store.uiTick
    val langText = remember(uiKey) { Lang.label() }

    LaunchedEffect(Unit) { ctl.boot() }
    LaunchedEffect(offlineTick) { ctl.loadMetrics() }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(brand = true, title = t("我的"))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Dp2.page.dp, end = Dp2.page.dp, bottom = 100.dp)
        ) {
            UserCard()
            MetricCard(ctl.metrics)

            GroupHead(title = t("书库与存储"))
            MineGroup {
                NavRow(
                    glyph = "folder",
                    title = t("来源与存储"),
                    sub = t("${ctl.storageLabel} · 本地库 / 云库"),
                    onTap = { Nav.push("sources") }
                )
                NavRow(
                    glyph = "upload",
                    title = t("导入本地文件"),
                    sub = t("不上传、不复制两份"),
                    onTap = { Nav.push("import") }
                )
                NavRow(
                    glyph = "download",
                    title = t("下载队列"),
                    sub = ctl.queueLabel,
                    onTap = { Nav.push("downloads") }
                )
            }

            GroupHead(title = t("连接与同步"))
            MineGroup {
                OnlineRow(ctl)
                if (!offlineKey) {
                    NavRow(
                        glyph = "server",
                        title = t("连接服务器"),
                        sub = t("${com.lantai.reader.model.ServerProfiles.count()} 个服务器 · 当前 ${Store.connectionLabel()}"),
                        onTap = { Nav.push("server") }
                    )
                }
                ToggleRow(
                    glyph = "sync",
                    title = t("进度同步"),
                    sub = if (ctl.autoSync) t("自动 · 读完一章即上报") else t("已暂停 · 进度只记在本机"),
                    isOn = ctl.autoSync,
                    onSwitch = { ctl.autoSync = it }
                )
                NavRow(
                    glyph = "clock",
                    title = t("离线会话补报"),
                    sub = ctl.outboxLabel,
                    onTap = { if (!Store.offline) onOpenTab(2) }
                )
            }

            GroupHead(title = t("内容"))
            MineGroup {
                NavRow(
                    glyph = "bookmark",
                    title = t("批注与书签"),
                    sub = if (offlineKey) t("离线模式不可用") else t("按书归集 · 进页汇总"),
                    onTap = { if (!Store.offline) Nav.push("annotations") }
                )
                NavRow(
                    glyph = "collection",
                    title = t("系列合集"),
                    sub = ctl.collectionLabel,
                    onTap = { if (!Store.offline) Nav.push("collections") }
                )
                NavRow(
                    glyph = "readlist",
                    title = t("书单"),
                    sub = ctl.listLabel,
                    onTap = { if (!Store.offline) Nav.push("readlists") }
                )
            }

            GroupHead(title = t("偏好"))
            MineGroup {
                NavRow(
                    glyph = "type",
                    title = t("阅读偏好"),
                    sub = t("漫画四模式 / 电子书排版"),
                    onTap = { Nav.push("prefs") }
                )
                NavRow(
                    glyph = "sun",
                    title = t("外观主题"),
                    sub = t("深色与浅色，与网页版一致"),
                    right = if (themeKey == "light") t("浅色") else t("深色"),
                    onTap = { themeOpen = true }
                )
                NavRow(
                    glyph = "type",
                    title = t("界面语言"),
                    sub = t("简体中文 / English / 日本語"),
                    right = langText,
                    onTap = { langOpen = true }
                )
                ToggleRow(
                    glyph = "wifiOff",
                    title = t("仅 WiFi 下载"),
                    sub = t("公网下自动暂停离线下载"),
                    isOn = ctl.wifiOnly,
                    onSwitch = { ctl.applyWifiOnly(it) }
                )
                ToggleRow(
                    glyph = "eyeOff",
                    title = t("隐身阅读"),
                    sub = t("不写进度、不计时长、不落统计"),
                    isOn = ctl.incognito,
                    onSwitch = { ctl.incognito = it }
                )
            }

            GroupHead(title = t("关于"))
            MineGroup {
                NavRow(
                    glyph = "info",
                    title = t("关于兰台阅读"),
                    sub = t("版本信息与分发说明"),
                    right = version,
                    onTap = { aboutOpen = true }
                )
                if (!offlineKey) {
                    NavRow(
                        glyph = "sync",
                        title = t("检查更新"),
                        sub = if (ctl.updateLabel.isNotEmpty()) ctl.updateLabel else t("读取 Git 仓库的最新 release"),
                        onTap = {
                            scope.launch {
                                val result = ctl.checkUpdate(version)
                                if (result.state == "new") {
                                    updateOpen = result
                                } else if (result.state == "latest") {
                                    Tip.show(t("已是最新版本") + " · " + version)
                                } else {
                                    Tip.show(ctl.updateLabel + " · " + UpdateChecker.source())
                                }
                            }
                        }
                    )
                    NavRow(
                        glyph = "server",
                        title = t("更新源"),
                        sub = ctl.repoDraft,
                        right = t("编辑"),
                        onTap = { editingRepo = !editingRepo }
                    )
                    if (editingRepo) {
                        RepoEditor(ctl) { editingRepo = false }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (offlineKey) {
                    ActionBtn(label = t("返回登录页"), glyph = "back", modifier = Modifier.weight(1f)) {
                        Store.loginTick += 1
                    }
                } else {
                    ActionBtn(label = t("退出登录"), danger = true, glyph = "close", modifier = Modifier.weight(1f)) {
                        logoutOpen = true
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (aboutOpen) AboutDialog { aboutOpen = false }

    if (themeOpen) {
        PickDialog(
            title = t("外观主题"),
            options = listOf(t("深色"), t("浅色")),
            onPick = {
                ctl.pickTheme(if (it == 1) "light" else "dark")
                themeOpen = false
            },
            onDismiss = { themeOpen = false }
        )
    }

    if (langOpen) {
        PickDialog(
            title = t("界面语言"),
            options = listOf("简体中文", "English", "日本語"),
            onPick = {
                ctl.pickLang(if (it == 1) "en" else if (it == 2) "ja" else "zh")
                langOpen = false
            },
            onDismiss = { langOpen = false }
        )
    }

    if (logoutOpen) {
        AlertDialog(
            onDismissRequest = { logoutOpen = false },
            containerColor = C.bg1,
            title = { Text(t("退出登录"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    t("将清除本机登录凭据。离线副本与阅读进度保留在本机，重新登录后可继续使用。"),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    logoutOpen = false
                    scope.launch {
                        Store.logout()
                        Store.logoutTick += 1
                    }
                }) {
                    Text(t("退出"), fontSize = 12.sp, color = C.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutOpen = false }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }

    val update = updateOpen
    if (update != null) {
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { updateOpen = null },
            containerColor = C.bg1,
            title = {
                Text(
                    t("发现新版本") + " · " + update.version,
                    fontSize = 15.sp,
                    fontFamily = Disp,
                    color = C.tx
                )
            },
            text = {
                Text(
                    if (update.notes.isNotEmpty()) update.notes else t("当前版本") + " " + version,
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )
            },
            dismissButton = {
                TextButton(onClick = { updateOpen = null }) {
                    Text(t("关闭"), fontSize = 12.sp, color = C.tx3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateOpen = null
                    if (update.url.isNotEmpty()) {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.url)))
                    }
                }) {
                    Text(t("下载"), fontSize = 12.sp, color = C.acc)
                }
            }
        )
    }
}
