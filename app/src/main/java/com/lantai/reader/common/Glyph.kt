package com.lantai.reader.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlyphDef {
    fun path(name: String): String = when (name) {
        "home" -> "M3 10.5 12 3l9 7.5V21H3zM9 21v-7h6v7"
        "library" -> "M4 4h4v16H4zM10 4h4v16h-4zM16.5 5l3.5 1-3 14-3.5-1z"
        "discover" -> "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0M16.5 16.5l4 4"
        "local" -> "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 10h18M8 5v5M12 14h5"
        "chart" -> "M4 20V10M10 20V4M16 20v-7M22 20H2"
        "user" -> "M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0M4 21c0-4.4 3.6-7 8-7c4.4 0 8 2.6 8 7"
        "back" -> "m15 18-6-6 6-6"
        "next" -> "m9 18 6-6-6-6"
        "down" -> "m6 9 6 6 6-6"
        "up" -> "m6 15 6-6 6 6"
        "search" -> "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0M16.5 16.5l4 4"
        "filter" -> "M3 5h18M6 12h12M10 19h4"
        "grid" -> "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1zM15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z"
        "list" -> "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"
        "download" -> "M12 3v12m0 0 4-4m-4 4-4-4M4 18v3h16v-3"
        "upload" -> "M12 17V5m0 0 4 4m-4-4L8 9M4 18v3h16v-3"
        "book" -> "M4 4h7a3 3 0 0 1 3 3v13a3 3 0 0 0-3-3H4zM20 4h-3a3 3 0 0 0-3 3v13a3 3 0 0 1 3-3h3z"
        "bookmark" -> "M6 3h12v18l-6-4-6 4z"
        "marker" -> "m4 20 4-1 11-11-3-3L5 16zM14 6l3 3"
        "toc" -> "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"
        "settings" -> "M10.53 2.72L13.47 2.72L13.62 5.24L15.63 6.07L17.53 4.4L19.6 6.47L17.93 8.37L18.76 10.38L21.28 10.53L21.28 13.47L18.76 13.62L17.93 15.63L19.6 17.53L17.53 19.6L15.63 17.93L13.62 18.76L13.47 21.28L10.53 21.28L10.38 18.76L8.37 17.93L6.47 19.6L4.4 17.53L6.07 15.63L5.24 13.62L2.72 13.47L2.72 10.53L5.24 10.38L6.07 8.37L4.4 6.47L6.47 4.4L8.37 6.07L10.38 5.24ZM8.8 12a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0-6.4 0"
        "more" -> "M3.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0-2.8 0M10.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0-2.8 0M17.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0-2.8 0"
        "edit" -> "M4 20h4L20 8l-4-4L4 16zM14 6l4 4"
        "cover" -> "M4 4h16v16H4zM4 15l4-4 3 3 3-3 6 5"
        "ext" -> "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"
        "close" -> "M6 6l12 12M18 6 6 18"
        "check" -> "m4 12 5 5L20 6"
        "sync" -> "M20 11a8 8 0 0 0-14-5L4 8m0 5a8 8 0 0 0 14 5l2-2M4 4v4h4M20 20v-4h-4"
        "server" -> "M5 3h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM5 14h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2zM7 6.5h.01M7 17.5h.01M11 6.5h6M11 17.5h6"
        "shield" -> "M12 2 4 5v7c0 5 3.5 8.5 8 10 4.5-1.5 8-5 8-10V5zm-3 10 2 2 4-4"
        "eyeOff" -> "M3 3l18 18M10.6 6.2A9 9 0 0 1 12 6c5 0 9 6 9 6a15 15 0 0 1-2.2 2.8M6.5 7.6C4.3 9 3 12 3 12c0 0 4 6 9 6a8 8 0 0 0 3-.6M9.9 9.9a3 3 0 0 0 4.2 4.2"
        "eye" -> "M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7zM9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0"
        "play" -> "M7 4l13 8-13 8z"
        "pause" -> "M7 4v16M17 4v16"
        "type" -> "M4 7V4h16v3M12 4v16M9 20h6"
        "sun" -> "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0M12 2v2m0 16v2M2 12h2m16 0h2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4"
        "moon" -> "M20 14A8 8 0 0 1 10 4a8 8 0 1 0 10 10z"
        "trash" -> "M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14M10 11v6M14 11v6"
        "info" -> "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M12 11v6M12 7h.01"
        "clock" -> "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M12 7v5l3 2"
        "folder" -> "M3 6h6l2 2h10v11H3z"
        "collection" -> "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1zM15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z"
        "readlist" -> "M4 5h16M4 10h16M4 15h10M4 20h7"
        "export" -> "M12 16V3m0 0L8 7m4-4 4 4M4 17v4h16v-4"
        "warn" -> "M12 3 2 21h20zM12 9v5M12 17h.01"
        "zoom" -> "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0M16.5 16.5l4 4M8 11h6M11 8v6"
        "thumbs" -> "M3 7h3a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1zM10.5 7h3a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1zM18 7h3a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z"
        "tts" -> "M4 9v6h4l5 4V5L8 9zM17 9a4 4 0 0 1 0 6M19.5 6a8 8 0 0 1 0 12"
        "wifiOff" -> "M3 3l18 18M8.5 15.5a5 5 0 0 1 7 0M5 12a10 10 0 0 1 3-2M2 8.5A15 15 0 0 1 6 6.3M12 19h.01"
        else -> ""
    }
}

private val pathCache = HashMap<String, Path>()

fun glyphPath(name: String): Path {
    val d = GlyphDef.path(name)
    if (d.isEmpty()) return Path()
    return pathCache.getOrPut(name) {
        try {
            PathParser().parsePathString(d).toPath()
        } catch (e: Throwable) {
            Path()
        }
    }
}

@Composable
fun Glyph(
    name: String,
    size: Dp = 18.dp,
    color: Color = Tm.pal.tx2,
    weight: Float = 1.7f,
    modifier: Modifier = Modifier
) {
    val path = remember(name) { glyphPath(name) }
    Canvas(modifier = modifier.then(Modifier.size(size))) {
        if (path.isEmpty) return@Canvas
        val k = this.size.width / 24f
        withTransform({ scale(k, k, Offset.Zero) }) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = weight, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
