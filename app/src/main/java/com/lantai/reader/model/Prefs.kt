package com.lantai.reader.model

import android.content.Context
import android.content.SharedPreferences
import com.lantai.reader.LtApp

object PrefKeys {
    const val cmMode = "cmMode"
    const val cmFit = "cmFit"
    const val cmQuality = "cmQuality"
    const val cmMargin = "cmMargin"
    const val cmDir = "cmDir"
    const val fs = "ebFontSize"
    const val lh = "ebLineHeight"
    const val measure = "ebMeasure"
    const val ebFont = "ebFont"
    const val ebTheme = "ebTheme"
    const val keepScreen = "keepScreen"
    const val hideStatus = "hideStatus"
    const val autoHide = "autoHide"
    const val prefSync = "prefSync"
    const val cacheLimit = "cacheLimit"
    const val lang = "uiLang"
    const val uiTheme = "uiTheme"
    const val repo = "updateRepo"
    const val comicGuide = "comicGuideSeen"
    const val ebookGuide = "ebookGuideSeen"
    const val syncAt = "syncAt"
    const val sweepAt = "sweepAt"
    const val ttsTone = "ttsTone"
    const val ttsSpeed = "ttsSpeed"
    const val ttsBarSide = "ttsBarSide"
    const val ttsBarYr = "ttsBarYr"
    const val autoTone = "ttsAutoTone"
    const val autoToneMigrated = "ttsAutoTone_v2"
}

object Prefs {
    private const val STORE = "lantai_reader"
    private const val KEY_SERVER = "server"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_ACCOUNT = "account"

    private var store: SharedPreferences? = null

    fun init(ctx: Context = LtApp.instance) {
        if (store == null) {
            store = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        }
    }

    fun srv(): String = read(KEY_SERVER)

    fun cookie(): String = read(KEY_COOKIE)

    fun account(): String = read(KEY_ACCOUNT)

    fun read(key: String): String {
        val s = store ?: return ""
        return try {
            s.getString(key, "") ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun write(key: String, value: String) {
        val s = store ?: return
        try {
            s.edit().putString(key, value).apply()
        } catch (e: Throwable) {
        }
    }

    fun saveSession(server: String, cookie: String, account: String) {
        write(KEY_SERVER, server)
        write(KEY_COOKIE, cookie)
        write(KEY_ACCOUNT, account)
    }

    fun clearCookie() {
        write(KEY_COOKIE, "")
    }

    fun int(key: String, fallback: Int): Int {
        val raw = read(key)
        if (raw.isEmpty()) return fallback
        return raw.toIntOrNull() ?: fallback
    }

    fun bool(key: String, fallback: Boolean): Boolean {
        val raw = read(key)
        if (raw.isEmpty()) return fallback
        return raw == "true"
    }

    fun putInt(key: String, value: Int) {
        write(key, value.toString())
    }

    fun putBool(key: String, value: Boolean) {
        write(key, if (value) "true" else "false")
    }
}
