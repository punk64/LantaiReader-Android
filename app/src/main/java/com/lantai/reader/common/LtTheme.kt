package com.lantai.reader.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class LtPalette(
    val bg: Color,
    val bg1: Color,
    val bg2: Color,
    val bg3: Color,
    val bg4: Color,
    val line: Color,
    val line2: Color,
    val line3: Color,
    val tx: Color,
    val tx2: Color,
    val tx3: Color,
    val tx4: Color,
    val acc: Color,
    val acc2: Color,
    val accBg: Color,
    val accLine: Color,
    val bad: Color,
    val ok: Color,
    val warn: Color,
    val info: Color,
    val paper: Color,
    val paperTx: Color,
    val glass: Color,
    val glassDeep: Color,
    val navSolid: Color,
    val navGlass: Color,
    val navEdge: Color,
    val hair: Color,
    val shadow: Color,
    val onAcc: Color
)

private fun c(v: Long): Color = Color(v)

val DarkPalette = LtPalette(
    bg = c(0xFF121313),
    bg1 = c(0xFF161818),
    bg2 = c(0xFF1B1E1E),
    bg3 = c(0xFF222727),
    bg4 = c(0xFF2B3130),
    line = c(0xFF2A2F2E),
    line2 = c(0xFF39403E),
    line3 = c(0xFF4A5350),
    tx = c(0xFFE9E5DB),
    tx2 = c(0xFFB3B6AE),
    tx3 = c(0xFF7F857E),
    tx4 = c(0xFF5A615A),
    acc = c(0xFFC9A24B),
    acc2 = c(0xFFE3C377),
    accBg = c(0xFF2A2517),
    accLine = c(0xFF5C4D29),
    bad = c(0xFFC8563C),
    ok = c(0xFF79A06A),
    warn = c(0xFFC99A3F),
    info = c(0xFF6D92A8),
    paper = c(0xFFF3EFE4),
    paperTx = c(0xFF2B2923),
    glass = c(0xD1161818),
    glassDeep = c(0xED0E100F),
    navSolid = c(0xFF1A1C1C),
    navGlass = c(0x801C201F),
    navEdge = c(0xFF3D4543),
    hair = c(0x26FFFFFF),
    shadow = c(0xFF000000),
    onAcc = c(0xFF17140C)
)

val LightPalette = LtPalette(
    bg = c(0xFFF2EEE3),
    bg1 = c(0xFFF7F4EC),
    bg2 = c(0xFFFFFDF6),
    bg3 = c(0xFFEFE9DA),
    bg4 = c(0xFFE5DDC9),
    line = c(0xFFDBD3BE),
    line2 = c(0xFFC9C0A6),
    line3 = c(0xFFB4AB90),
    tx = c(0xFF20201C),
    tx2 = c(0xFF545348),
    tx3 = c(0xFF87857A),
    tx4 = c(0xFFA8A494),
    acc = c(0xFF8A651F),
    acc2 = c(0xFFA87F2C),
    accBg = c(0xFFE9E0C9),
    accLine = c(0xFFC6B48A),
    bad = c(0xFFA63A24),
    ok = c(0xFF4D7A3F),
    warn = c(0xFF94701C),
    info = c(0xFF3F6A80),
    paper = c(0xFFFFFDF6),
    paperTx = c(0xFF2B2923),
    glass = c(0xDBFFFDF6),
    glassDeep = c(0xF2FFFDF6),
    navSolid = c(0xFFF7F4EC),
    navGlass = c(0x80FCFAF3),
    navEdge = c(0xFFC9C0A6),
    hair = c(0x1F000000),
    shadow = c(0xFF2A2420),
    onAcc = c(0xFFFFFDF6)
)

object Tm {
    var mode by mutableStateOf("dark")
    var pal by mutableStateOf(DarkPalette)

    fun apply(mode: String) {
        this.mode = if (mode == "light") "light" else "dark"
        this.pal = if (this.mode == "light") LightPalette else DarkPalette
    }

    fun toggle(): String {
        apply(if (mode == "light") "dark" else "light")
        return mode
    }
}

object Dp2 {
    const val rS = 8f
    const val rM = 12f
    const val rL = 16f
    const val rXl = 22f
    const val page = 16f
    const val gap = 12f
}
