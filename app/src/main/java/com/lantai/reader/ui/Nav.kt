package com.lantai.reader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Tm
import com.lantai.reader.model.Store
import com.lantai.reader.common.t
import com.lantai.reader.ui.comp.ScreenHead

object Nav {
    val stack = mutableStateListOf<String>()

    fun push(name: String) {
        stack.add(name)
    }

    fun replace(name: String) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(name)
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popTo(name: String): Boolean {
        val i = stack.lastIndexOf(name)
        if (i < 0) return false
        while (stack.size > i + 1) stack.removeAt(stack.lastIndex)
        return true
    }

    fun clear() {
        stack.clear()
    }

    fun top(): String? = stack.lastOrNull()

    fun depth(): Int = stack.size

    fun has(name: String): Boolean = stack.contains(name)

    fun names(): List<String> = stack.toList()
}

@Composable
fun NavLayer(enter: Boolean, content: @Composable () -> Unit) {
    val shift = remember { Animatable(if (enter) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enter) shift.animateTo(0f, tween(280))
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = shift.value * size.width }
            .background(Tm.pal.bg)
    ) {
        content()
    }
}

@Composable
fun Route(name: String) {
    when (name) {
        "library" -> LibraryScreen(embedded = false, focusTick = Store.searchTick)
        "book" -> BookDetailScreen()
        "reader" -> ReaderScreen()
        "comic" -> ComicReaderScreen()
        "series" -> SeriesDetailScreen()
        "collections" -> CollectionsScreen()
        "readlists" -> ReadlistsScreen()
        "annotations" -> AnnotationsScreen()
        "sources" -> SourcesScreen()
        "import" -> ImportScreen()
        "downloads" -> DownloadsScreen()
        "server" -> ServerScreen()
        "prefs" -> PrefsScreen()
        else -> PlaceholderScreen(name)
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    val C = Tm.pal
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(title = title, back = t("返回"), onBack = { Nav.pop() })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t("建设中"), fontSize = 13.sp, color = C.tx3)
                Text(
                    t("该页面在后续阶段接入"),
                    fontSize = 10.5.sp,
                    color = C.tx4,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun BlankScreen(title: String, sub: String = t("该页面在后续阶段接入")) {
    val C = Tm.pal
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(title = title)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(sub, fontSize = 11.sp, color = C.tx4)
        }
    }
}
