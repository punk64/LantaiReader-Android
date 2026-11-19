package com.lantai.reader.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.R
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.Prefs
import com.lantai.reader.model.ServerProfile
import com.lantai.reader.model.ServerProfiles
import com.lantai.reader.model.Store
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.LtButton
import com.lantai.reader.ui.comp.LtField
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val C = Tm.pal
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<ServerProfile>>(emptyList()) }

    remember(Unit) {
        ServerProfiles.load()
        candidates = ServerProfiles.list()
        val current = ServerProfiles.current()
        val pending = Store.loginOrigin
        if (pending.isNotEmpty()) {
            server = pending
            Store.loginOrigin = ""
        } else if (current != null && current.origin.isNotEmpty()) {
            server = current.origin
        } else {
            server = Prefs.srv()
        }
        account = Prefs.account()
        true
    }

    fun submit() {
        if (busy) return
        error = ""
        if (server.trim().isEmpty()) {
            error = t("请填写服务器地址")
            return
        }
        if (account.trim().isEmpty()) {
            error = t("请填写用户名")
            return
        }
        busy = true
        scope.launch {
            try {
                Store.login(server, account.trim(), password)
                password = ""
                Store.phase = "home"
            } catch (e: Throwable) {
                val message = e.message ?: ""
                error = if (message.isNotEmpty()) message else t("连接失败")
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.logo_lantai),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .border(1.dp, C.accLine, RoundedCornerShape(Dp2.rM.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text(
                t("兰台阅读"),
                fontSize = 24.sp,
                color = C.tx,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Text(
                t("连接你自己的兰台服务器"),
                fontSize = 12.sp,
                color = C.tx3,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(34.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rL.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rL.dp))
                    .padding(18.dp)
            ) {
                LtField(
                    label = t("服务器地址"),
                    hint = t("http://主机或IP:25600"),
                    value = server,
                    onChange = { server = it }
                )
                Spacer(Modifier.height(10.dp))

                if (candidates.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidates.forEach { item ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(C.bg2)
                                    .clickable { server = item.origin }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    item.alias,
                                    fontSize = 10.sp,
                                    color = if (item.origin == server) C.acc else C.tx3,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                LtField(
                    label = t("用户名"),
                    hint = t("邮箱或登录名"),
                    value = account,
                    onChange = { account = it }
                )
                Spacer(Modifier.height(14.dp))
                LtField(
                    label = t("密码"),
                    hint = t("密码"),
                    value = password,
                    secret = true,
                    onChange = { password = it }
                )

                if (error.isNotEmpty()) {
                    Text(
                        error,
                        fontSize = 11.sp,
                        color = C.bad,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)
                    )
                } else {
                    Spacer(Modifier.height(18.dp))
                }

                LtButton(
                    label = if (busy) t("连接中…") else t("登录"),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = { submit() }
                )

                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(Dp2.rS.dp))
                        .background(C.bg2)
                        .border(1.dp, C.line, RoundedCornerShape(Dp2.rS.dp))
                        .clickable(enabled = !busy) { Store.enterOffline() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(t("离线使用"), fontSize = 12.5.sp, color = C.tx2)
                }
            }

            Text(
                t("离线模式只显示已下载的云库书与本机导入的本地书"),
                fontSize = 10.sp,
                color = C.tx4,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                t("数据只留在你的服务器上，客户端不落副本"),
                fontSize = 10.sp,
                color = C.tx4,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
