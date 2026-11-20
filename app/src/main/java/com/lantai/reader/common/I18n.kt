package com.lantai.reader.common

object Lang {
    var code: String = "zh"
    val en: String = "en"
    val zh: String = "zh"
    val ja: String = "ja"

    fun set(v: String) {
        code = when (v) {
            en -> en
            ja -> ja
            else -> zh
        }
    }

    fun isEn(): Boolean = code == en

    fun isJa(): Boolean = code == ja

    fun label(): String = when (code) {
        en -> "English"
        ja -> "日本語"
        else -> "简体中文"
    }

    fun tag(): String = when (code) {
        en -> "en-US"
        ja -> "ja-JP"
        else -> "zh-CN"
    }

    fun apply(v: String) {
        set(v)
        com.lantai.reader.model.Prefs.write(com.lantai.reader.model.PrefKeys.lang, code)
        com.lantai.reader.model.Store.uiTick += 1
    }
}

object StrTpl {
    private val num = Regex("\\d+(?:\\.\\d+)?")

    fun apply(zh: String, table: Map<String, String>): String {
        if (table.isEmpty()) return zh
        val nums = ArrayList<String>(4)
        val normalized = num.replace(zh) { m ->
            nums.add(m.value)
            "{n}"
        }
        val pattern = table[normalized] ?: return zh
        var i = 0
        val sb = StringBuilder()
        var p = 0
        while (p < pattern.length) {
            val at = pattern.indexOf("{n}", p)
            if (at < 0) {
                sb.append(pattern, p, pattern.length)
                break
            }
            sb.append(pattern, p, at)
            sb.append(nums.getOrNull(i) ?: "")
            i++
            p = at + 3
        }
        return sb.toString()
    }
}

fun t(zh: String): String {
    if (Lang.code == Lang.zh) return zh
    val map = if (Lang.isJa()) StrJa.map else StrEn.map
    val templates = if (Lang.isJa()) StrJa.templates else StrEn.templates
    val hit = map[zh]
    if (hit != null) return hit
    return StrTpl.apply(zh, templates)
}

fun tf(zh: String, args: List<String>): String {
    var out = t(zh)
    args.forEachIndexed { i, v -> out = out.replace("{$i}", v) }
    return out
}
