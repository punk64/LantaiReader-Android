package com.lantai.reader

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.lantai.reader.common.Lang
import com.lantai.reader.components.CoverCache
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.PrefKeys
import com.lantai.reader.model.Prefs
import com.lantai.reader.model.ServerProfiles
import com.lantai.reader.model.Store
import com.lantai.reader.ui.LoginScreen
import com.lantai.reader.ui.MainShell
import com.lantai.reader.ui.Nav
import com.lantai.reader.ui.NavLayer
import com.lantai.reader.net.SeriesCache
import com.lantai.reader.net.TtsService
import com.lantai.reader.reader.ReadingWebHost
import com.lantai.reader.ui.Route
import com.lantai.reader.ui.TtsBar
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prefs.init(this)
        ServerProfiles.load()
        Tm.apply(Prefs.read(PrefKeys.uiTheme).ifEmpty { "dark" })
        Lang.set(Prefs.read(PrefKeys.lang))
        setContent {
            LtTheme {
                LtRoot()
            }
        }
        lifecycleScope.launch {
            boot()
        }
    }

    private suspend fun boot() {
        val restored = try {
            Store.restore()
        } catch (e: Throwable) {
            false
        }
        Store.phase = if (restored) "home" else "login"
    }
}

@Composable
fun LtTheme(content: @Composable () -> Unit) {
    val C = Tm.pal
    val scheme = if (Tm.mode == "light") {
        lightColorScheme(
            background = C.bg,
            surface = C.bg1,
            primary = C.acc,
            onPrimary = C.onAcc,
            onBackground = C.tx,
            onSurface = C.tx,
            surfaceVariant = C.bg2,
            onSurfaceVariant = C.tx2
        )
    } else {
        darkColorScheme(
            background = C.bg,
            surface = C.bg1,
            primary = C.acc,
            onPrimary = C.onAcc,
            onBackground = C.tx,
            onSurface = C.tx,
            surfaceVariant = C.bg2,
            onSurfaceVariant = C.tx2
        )
    }
    val base = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalTextStyle provides base) {
            content()
        }
    }
}

private val handler = Handler(Looper.getMainLooper())

@Composable
fun LtRoot() {
    val C = Tm.pal
    val activity = LocalContext.current as? ComponentActivity

    LaunchedEffect(Unit) {
        TtsService.onBar = { idx, total, playing, on ->
            handler.post { Store.applyTts(idx, total, playing, on) }
        }
    }

    LaunchedEffect(Store.offlineTick) {
        if (Store.offlineTick == 0) return@LaunchedEffect
        Nav.clear()
        CoverCache.clear()
        SeriesCache.clear()
        Store.selected = null
        if (Store.offline) Store.mainTab = 1
        Store.phase = "home"
    }

    BackHandler {
        val hook = Store.backHook
        if (hook != null && hook()) return@BackHandler
        if (Nav.pop()) return@BackHandler
        if (Store.phase == "home" || Store.phase == "login") {
            activity?.finish()
        }
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (Store.phase) {
            "home" -> MainShell()
            "login" -> LoginScreen()
            else -> BootScreen()
        }

        val depth = Nav.depth()
        Nav.names().forEachIndexed { index, name ->
            if (index >= depth) return@forEachIndexed
            key(index, name) {
                NavLayer(enter = index > 0) {
                    Route(name)
                }
            }
        }

        ReadingWebHost()

        TtsBar(
            onOpenReader = {
                if (Store.readerBookId.isNotEmpty() && Nav.top() != "reader") Nav.push("reader")
            }
        )
    }
}

@Composable
private fun BootScreen() {
    val C = Tm.pal
    Column(
        Modifier.fillMaxSize().background(C.bg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(30.dp), color = C.acc, strokeWidth = 2.dp)
        Text(
            t("正在恢复会话…"),
            color = C.tx4,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
