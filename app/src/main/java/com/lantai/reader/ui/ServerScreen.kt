package com.lantai.reader.ui

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.LtApp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.common.tf
import com.lantai.reader.model.Prefs
import com.lantai.reader.model.ServerProfile
import com.lantai.reader.model.ServerProfiles
import com.lantai.reader.model.Store
import com.lantai.reader.net.LanHit
import com.lantai.reader.net.LanScan
import com.lantai.reader.ui.comp.ActionBtn
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.ui.comp.Mono
import com.lantai.reader.ui.comp.NoteCard
import com.lantai.reader.ui.comp.Pill
import com.lantai.reader.ui.comp.ScreenHead
import kotlinx.coroutines.launch

private const val KEY_ALIAS = "pendingAlias"
private const val KEY_LAN_PORT = "pendingLanPort"
private const val KEY_LAN_AUTO = "pendingLanAuto"
private const val KEY_LAN_HOST = "pendingLanHost"
private const val KEY_LAN_SCHEME = "pendingLanScheme"

private fun lanBody(value: String): String {
    var body = value.trim()
    if (body.startsWith("https://")) {
        body = body.substring(8)
    } else if (body.startsWith("http://")) {
        body = body.substring(7)
    }
    val slash = body.indexOf('/')
    if (slash > 0) body = body.substring(0, slash)
    return body
}

private fun lanSchemeOf(value: String, fallback: String): String {
    if (value.startsWith("https://")) return "https"
    if (value.startsWith("http://")) return "http"
    return fallback
}

private fun lanHostOf(value: String): String {
    val body = lanBody(value)
    val colon = body.lastIndexOf(':')
    if (colon <= 0) return body
    val tail = body.substring(colon + 1).toIntOrNull() ?: return body
    return body.substring(0, colon)
}

private fun lanPortOf(value: String): Int {
    val body = lanBody(value)
    val colon = body.lastIndexOf(':')
    if (colon <= 0) return 0
    val port = body.substring(colon + 1).toIntOrNull() ?: return 0
    if (port < 1 || port > 65535) return 0
    return port
}

private fun nameOf(item: ServerProfile): String {
    val name = item.alias
    val sep = " · "
    val index = name.indexOf(sep)
    if (index > 0 && item.account.isNotEmpty() && name.substring(index + sep.length) == item.account) {
        return name.substring(0, index)
    }
    return name
}

@Composable
private fun Field(
    value: String,
    placeholder: String,
    mono: Boolean = false,
    numeric: Boolean = false,
    maxLength: Int = 200,
    center: Boolean = false,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    val C = Tm.pal
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(C.bg2)
            .padding(horizontal = 9.dp),
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onChange(it) },
            singleLine = true,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            textStyle = TextStyle(
                fontSize = if (numeric) 11.5.sp else 12.sp,
                fontFamily = if (mono) Mono else null,
                color = C.tx,
                textAlign = if (center) TextAlign.Center else TextAlign.Start
            ),
            cursorBrush = SolidColor(C.acc),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) {
            Text(
                placeholder,
                fontSize = if (numeric) 11.5.sp else 12.sp,
                fontFamily = if (mono) Mono else null,
                color = C.tx4,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SchemeBtn(label: String, on: Boolean, onTap: () -> Unit) {
    val C = Tm.pal
    Text(
        label,
        fontSize = 9.5.sp,
        color = if (on) C.acc else C.tx4,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (on) C.bg3 else C.bg2)
            .clickable { onTap() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun SmallAction(label: String, tone: String = "acc", onTap: () -> Unit) {
    val C = Tm.pal
    Text(
        label,
        fontSize = 10.5.sp,
        color = if (tone == "acc") C.acc else C.tx3,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(C.bg3)
            .clickable { onTap() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
fun ServerScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(Store.displayName()) }
    var connText by remember { mutableStateOf(Store.connectionLabel()) }
    var connected by remember { mutableStateOf(true) }
    var testing by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<List<ServerProfile>>(emptyList()) }
    var activeKey by remember { mutableStateOf("") }
    var busyKey by remember { mutableStateOf("") }
    var editingKey by remember { mutableStateOf("") }
    var editAlias by remember { mutableStateOf("") }
    var editOrigin by remember { mutableStateOf("") }
    var editPort by remember { mutableStateOf(LanScan.defaultPort().toString()) }
    var editAuto by remember { mutableStateOf(true) }
    var editLan by remember { mutableStateOf("") }
    var editScheme by remember { mutableStateOf("http") }
    var port by remember { mutableStateOf(LanScan.defaultPort().toString()) }
    var addScheme by remember { mutableStateOf("http") }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<LanHit>>(emptyList()) }
    var addAuto by remember { mutableStateOf(true) }
    var addLanHost by remember { mutableStateOf("") }
    var netLabel by remember { mutableStateOf(t("读取中")) }
    var addAlias by remember { mutableStateOf("") }
    var addOrigin by remember { mutableStateOf("") }
    var dropTarget by remember { mutableStateOf<ServerProfile?>(null) }
    var pendingConnect by remember { mutableStateOf("") }
    var unbinding by remember { mutableStateOf(false) }

    fun reloadProfiles() {
        profiles = ServerProfiles.list()
        activeKey = ServerProfiles.activeKey()
    }

    suspend fun loadNet() {
        var wifi = false
        var cellular = false
        try {
            val cm = LtApp.instance.getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            if (caps != null) {
                wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
        } catch (e: Throwable) {
        }
        if (wifi) {
            val ip = LanScan.localAddress()
            netLabel = if (ip.isNotEmpty()) "Wi-Fi · $ip" else "Wi-Fi"
        } else if (cellular) {
            netLabel = t("公网")
        } else {
            netLabel = t("未联网")
        }
    }

    suspend fun test(silent: Boolean) {
        if (testing) return
        testing = true
        try {
            val who = Store.api.me()
            if (who.isEmpty()) {
                connected = false
                if (!silent) Tip.show(t("连接失败：会话已失效"))
            } else {
                connected = true
                account = Store.displayName()
                if (!silent) Tip.show(t("连接正常 · ${Store.displayName()}"))
            }
        } catch (e: Throwable) {
            connected = false
            if (!silent) Tip.show(t("连接失败：${e.message ?: ""}"))
        }
        testing = false
    }

    suspend fun use(key: String) {
        if (busyKey.isNotEmpty()) return
        if (key == activeKey) {
            Tip.show(t("已是当前服务器"))
            return
        }
        busyKey = key
        try {
            Store.switchTo(key)
            reloadProfiles()
            Tip.show(t("已切换服务器"))
            Store.serverTick += 1
        } catch (e: Throwable) {
            Tip.show(e.message ?: t("切换失败"))
        }
        busyKey = ""
    }

    suspend fun drop(item: ServerProfile) {
        val done = Store.removeProfile(item.key)
        if (!done) {
            Tip.show(t("当前服务器不能删除"))
            return
        }
        reloadProfiles()
    }

    fun startEdit(item: ServerProfile) {
        editingKey = item.key
        editAlias = nameOf(item)
        editOrigin = item.origin
        editPort = "${if (item.lanPort > 0) item.lanPort else LanScan.defaultPort()}"
        editAuto = item.lanAuto
        editLan = item.lanHost
        editScheme = item.lanScheme
    }

    suspend fun applyEdit() {
        val alias = editAlias.trim()
        if (alias.isEmpty()) {
            Tip.show(t("名称不能为空"))
            return
        }
        val raw = editOrigin.trim()
        if (raw.isEmpty()) {
            Tip.show(t("请填写服务器地址"))
            return
        }
        val origin = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        val parsedPort = editPort.toIntOrNull() ?: 0
        if (parsedPort < 1 || parsedPort > 65535) {
            Tip.show(t("端口不合法"))
            return
        }
        val lanRaw = editLan.trim()
        val lanScheme = lanSchemeOf(lanRaw, editScheme)
        val lanHost = if (lanRaw.isNotEmpty()) lanHostOf(lanRaw) else ""
        if (lanHost.isNotEmpty() && lanHost.contains(' ')) {
            Tip.show(t("局域网地址不合法"))
            return
        }
        val typedPort = if (lanRaw.isNotEmpty()) lanPortOf(lanRaw) else 0
        val lanPort = if (typedPort > 0) typedPort else parsedPort
        val other = ServerProfiles.findByOrigin(origin)
        if (other != null && other.key != editingKey) {
            Tip.show(t("该地址已被其它服务器档案使用"))
            return
        }
        val isActive = ServerProfiles.isActive(editingKey)
        Store.editProfile(editingKey, alias, origin, lanPort, editAuto, lanHost, lanScheme)
        editingKey = ""
        reloadProfiles()
        if (isActive) {
            test(true)
            Store.serverTick += 1
        }
        Tip.show(t("已保存"))
    }

    suspend fun saveLanPending() {
        val fallback = port.toIntOrNull() ?: LanScan.defaultPort()
        val raw = addLanHost.trim()
        val host = if (raw.isNotEmpty()) lanHostOf(raw) else ""
        val typed = if (raw.isNotEmpty()) lanPortOf(raw) else 0
        val scheme = lanSchemeOf(raw, addScheme)
        val chosen = if (typed > 0) typed else fallback
        Prefs.putInt(KEY_LAN_PORT, chosen)
        Prefs.write(KEY_LAN_AUTO, if (addAuto) "true" else "false")
        Prefs.write(KEY_LAN_HOST, host)
        Prefs.write(KEY_LAN_SCHEME, scheme)
    }

    suspend fun goLogin(origin: String, alias: String) {
        Prefs.write(KEY_ALIAS, alias.trim())
        saveLanPending()
        Store.loginOrigin = origin
        Store.logout()
        Store.logoutTick += 1
    }

    fun discover() {
        if (scanning) return
        val value = port.toIntOrNull() ?: 0
        if (value < 1 || value > 65535) {
            Tip.show(t("端口不合法"))
            return
        }
        port = "$value"
        scanning = true
        scanned = false
        hits = emptyList()
        val first = if (addScheme == "https") "https" else "http"
        val second = if (first == "https") "http" else "https"
        scope.launch {
            try {
                hits = LanScan.scan(listOf(value), listOf(first, second))
                scanned = true
                if (hits.isEmpty()) Tip.show(t("同网段未发现兰台服务器"))
            } catch (e: Throwable) {
                Tip.show(t("扫描失败：${e.message ?: ""}"))
            }
            scanning = false
        }
    }

    suspend fun pick(hit: LanHit) {
        val existed = ServerProfiles.findByOrigin(hit.origin)
        if (existed != null && existed.cookie.isNotEmpty()) {
            use(existed.key)
            return
        }
        addLanHost = "${hit.host}:${hit.port}"
        addScheme = hit.scheme
        saveLanPending()
        Store.loginOrigin = hit.origin
        Store.logout()
        Store.logoutTick += 1
    }

    fun activeAlias(): String {
        val item = ServerProfiles.current()
        if (item != null && item.alias.isNotEmpty()) return nameOf(item)
        return Store.serverLabel()
    }

    fun activePlace(): String = if (Store.isLanActive()) t("局域网") else t("公网")

    fun lanOf(item: ServerProfile, isActive: Boolean): Boolean =
        if (isActive) Store.isLanActive() else LanScan.isPrivate(item.origin)

    LaunchedEffect(Unit) {
        val savedPort = Prefs.int(KEY_LAN_PORT, 0)
        port = "${if (savedPort > 0) savedPort else LanScan.defaultPort()}"
        addAuto = Prefs.read(KEY_LAN_AUTO) != "false"
        val savedHost = Prefs.read(KEY_LAN_HOST)
        addScheme = if (Prefs.read(KEY_LAN_SCHEME) == "https") "https" else "http"
        addLanHost = if (savedHost.isNotEmpty()) "$savedHost:$port" else ""
        reloadProfiles()
        loadNet()
        test(true)
    }
    LaunchedEffect(Store.netTick) {
        account = Store.displayName()
        connText = Store.connectionLabel()
        loadNet()
    }
    LaunchedEffect(Store.profileTick) { account = Store.displayName() }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(title = t("连接服务器"), back = t("我的"), onBack = { Nav.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Dp2.page.dp, end = Dp2.page.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (connected) androidx.compose.ui.graphics.Color(0xFF1C2A1C) else C.bg3),
                    contentAlignment = Alignment.Center
                ) {
                    Glyph("server", 22.dp, if (connected) C.ok else C.tx4)
                }
                Text(
                    if (connected) t("已连接 · ${activeAlias()}") else t("未连接"),
                    fontSize = 16.sp,
                    color = C.tx,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 11.dp)
                )
                Text(
                    if (connected) {
                        "${if (account.isNotEmpty()) account else t("已登录")} · ${activePlace()}"
                    } else {
                        t("连接后可用服务器书库与进度同步")
                    },
                    fontSize = 9.5.sp,
                    color = C.tx4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (connected) {
                    Text(
                        connText,
                        fontSize = 9.sp,
                        color = C.tx3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            GroupHead(
                title = t("我的服务器"),
                meta = if (profiles.isNotEmpty()) t("${profiles.size} 台") else ""
            )

            if (editingKey.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(Dp2.rM.dp))
                        .background(C.bg1)
                        .border(1.dp, C.accLine, RoundedCornerShape(Dp2.rM.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t("编辑服务器"),
                            fontSize = 12.5.sp,
                            color = C.tx,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            t("取消"),
                            fontSize = 11.5.sp,
                            color = C.tx3,
                            modifier = Modifier.clickable { editingKey = "" }
                        )
                    }
                    Field(editAlias, t("名称"), modifier = Modifier.fillMaxWidth().height(34.dp)) { editAlias = it }
                    Field(editOrigin, t("http(s)://主机或IP:端口"), mono = true, modifier = Modifier.fillMaxWidth().height(34.dp)) {
                        editOrigin = it
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("局域网地址"), fontSize = 9.5.sp, color = C.tx4)
                        Spacer(Modifier.weight(1f))
                        SchemeBtn("HTTP", editScheme == "http") { editScheme = "http" }
                        Spacer(Modifier.width(6.dp))
                        SchemeBtn("HTTPS", editScheme == "https") { editScheme = "https" }
                    }
                    Field(
                        editLan,
                        t("可选，如 192.168.1.10:33100"),
                        mono = true,
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        editLan = it
                        val typed = lanPortOf(it)
                        if (typed > 0) editPort = "$typed"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t("局域网自动发现"), fontSize = 11.5.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
                            Text(t("仅对这台服务器生效"), fontSize = 9.5.sp, color = C.tx4, modifier = Modifier.fillMaxWidth())
                        }
                        Switch(
                            checked = editAuto,
                            onCheckedChange = { editAuto = it },
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("局域网端口"), fontSize = 10.sp, color = C.tx4)
                        Spacer(Modifier.width(8.dp))
                        Field(
                            editPort,
                            "25600",
                            mono = true,
                            numeric = true,
                            maxLength = 5,
                            center = true,
                            modifier = Modifier.width(70.dp).height(30.dp)
                        ) { editPort = it }
                        Spacer(Modifier.weight(1f))
                        SmallAction(t("保存")) { scope.launch { applyEdit() } }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(vertical = 4.dp)
            ) {
                if (profiles.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(t("尚无保存的服务器"), fontSize = 11.sp, color = C.tx4)
                    }
                } else {
                    profiles.forEachIndexed { index, item ->
                        val isActive = item.key == activeKey
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isActive) androidx.compose.ui.graphics.Color(0xFF1C2A1C) else C.bg3
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Glyph(
                                    if (lanOf(item, isActive)) "local" else "server",
                                    15.dp,
                                    if (isActive) C.ok else C.tx3
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    nameOf(item),
                                    fontSize = 12.5.sp,
                                    color = C.tx,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "${if (item.account.isNotEmpty()) item.account else t("未登录")} · " +
                                        if (lanOf(item, isActive)) t("局域网") else t("公网"),
                                    fontSize = 9.5.sp,
                                    color = C.tx4,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (busyKey == item.key) {
                                CircularProgressIndicator(color = C.acc, modifier = Modifier.size(16.dp))
                            } else if (isActive) {
                                Pill(label = t("已连接"), tone = if (connected) "done" else "warn")
                            } else {
                                Text(
                                    t("切换"),
                                    fontSize = 10.5.sp,
                                    color = C.acc,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(C.bg3)
                                        .clickable { scope.launch { use(item.key) } }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clickable {
                                        if (editingKey == item.key) {
                                            editingKey = ""
                                        } else {
                                            startEdit(item)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⋯", fontSize = 14.sp, color = C.tx3, textAlign = TextAlign.Center)
                            }
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clickable { dropTarget = item },
                                contentAlignment = Alignment.Center
                            ) {
                                Glyph("trash", 13.dp, C.tx4)
                            }
                        }
                        if (index < profiles.size - 1) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(C.line))
                        }
                    }
                }
            }

            GroupHead(title = t("添加服务器"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(t("名称"), fontSize = 9.5.sp, color = C.tx4, modifier = Modifier.fillMaxWidth())
                Field(addAlias, t("例如：书房 NAS"), modifier = Modifier.fillMaxWidth().height(34.dp)) { addAlias = it }
                Text(
                    t("服务器地址"),
                    fontSize = 9.5.sp,
                    color = C.tx4,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
                Field(addOrigin, t("http(s)://主机或IP:端口"), modifier = Modifier.fillMaxWidth().height(34.dp)) { addOrigin = it }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp).height(1.dp).background(C.line))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t("局域网自动发现"), fontSize = 11.5.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
                        Text(
                            if (addLanHost.isNotEmpty()) t("已记录 $addLanHost") else t("仅对这台服务器生效"),
                            fontSize = 9.5.sp,
                            color = C.tx4,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Switch(
                        checked = addAuto,
                        onCheckedChange = { addAuto = it },
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

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("端口"), fontSize = 10.sp, color = C.tx4)
                    Spacer(Modifier.width(8.dp))
                    Field(
                        port,
                        "25600",
                        mono = true,
                        numeric = true,
                        maxLength = 5,
                        center = true,
                        modifier = Modifier.width(70.dp).height(30.dp)
                    ) { port = it }
                    Spacer(Modifier.weight(1f))
                    SmallAction(if (scanning) t("扫描中…") else t("扫描本机网段"), if (scanning) "mute" else "acc") {
                        discover()
                    }
                }

                when {
                    scanning -> Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = C.acc, modifier = Modifier.size(13.dp))
                        Text(t("正在遍历本机网段，约需数秒"), fontSize = 9.5.sp, color = C.tx4)
                    }

                    hits.isNotEmpty() -> Column(Modifier.fillMaxWidth()) {
                        hits.forEachIndexed { index, hit ->
                            Row(
                                Modifier.fillMaxWidth().height(34.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "${hit.host}:${hit.port}",
                                    fontSize = 11.5.sp,
                                    color = C.tx,
                                    fontFamily = Mono,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(hit.scheme, fontSize = 8.5.sp, color = C.tx4, fontFamily = Mono)
                                Text(
                                    t("用这台"),
                                    fontSize = 10.5.sp,
                                    color = C.acc,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(C.bg3)
                                        .clickable { scope.launch { pick(hit) } }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            if (index < hits.size - 1) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                            }
                        }
                    }

                    scanned -> Text(
                        t("未发现。确认服务端已启动、手机与服务器在同一网段。"),
                        fontSize = 9.5.sp,
                        color = C.tx4,
                        lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Spacer(Modifier.weight(1f))
                    SmallAction(t("添加并连接")) {
                        val raw = addOrigin.trim()
                        if (raw.isEmpty()) {
                            Tip.show(t("请填写服务器地址"))
                            return@SmallAction
                        }
                        val origin = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
                        pendingConnect = origin
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionBtn(
                    label = if (testing) t("测试中…") else t("测试连接"),
                    primary = true,
                    glyph = "sync",
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch { test(false) }
                }
                ActionBtn(
                    label = t("解绑此设备"),
                    danger = true,
                    glyph = "trash",
                    modifier = Modifier.weight(1f)
                ) { unbinding = true }
            }

            Box(Modifier.padding(top = 14.dp)) {
                NoteCard(text = t("扫描只在点按后进行，不常驻后台，也不会上传任何网络信息。"))
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    val target = dropTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { dropTarget = null },
            containerColor = C.bg1,
            title = { Text(t("删除服务器档案"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    t("仅移除本机保存的地址与会话，服务器上的数据与已下载副本不受影响。"),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dropTarget = null
                    scope.launch { drop(target) }
                }) {
                    Text(t("删除"), fontSize = 12.sp, color = C.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { dropTarget = null }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }

    if (pendingConnect.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingConnect = "" },
            containerColor = C.bg1,
            title = { Text(t("添加服务器"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    tf(
                        "将断开当前连接并回到登录页，地址预填为 {0}。已下载副本与阅读进度保留在本机。",
                        listOf(pendingConnect)
                    ),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val origin = pendingConnect
                    pendingConnect = ""
                    scope.launch {
                        val existed = ServerProfiles.findByOrigin(origin)
                        if (existed != null && existed.cookie.isNotEmpty()) {
                            use(existed.key)
                        } else {
                            goLogin(origin, addAlias)
                        }
                    }
                }) {
                    Text(t("去登录"), fontSize = 12.sp, color = C.acc)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConnect = "" }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }

    if (unbinding) {
        AlertDialog(
            onDismissRequest = { unbinding = false },
            containerColor = C.bg1,
            title = { Text(t("解绑并更换服务器"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    t("将清除本机保存的会话凭据并回到登录页。离线副本与阅读进度保留在本机。"),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    unbinding = false
                    scope.launch {
                        Store.logout()
                        Store.logoutTick += 1
                    }
                }) {
                    Text(t("解绑"), fontSize = 12.sp, color = C.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { unbinding = false }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }
}
