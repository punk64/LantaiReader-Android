package com.lantai.reader.model

import com.lantai.reader.common.J
import org.json.JSONArray
import org.json.JSONObject

data class ServerProfile(
    var key: String = "",
    var origin: String = "",
    var cookie: String = "",
    var account: String = "",
    var alias: String = "",
    var fingerprint: String = "",
    var at: Long = 0L,
    var lanPort: Int = 25600,
    var lanAuto: Boolean = true,
    var lanHost: String = "",
    var lanScheme: String = "http"
)

object ServerProfiles {
    private const val KEY_PROFILES = "servers"
    private const val KEY_ACTIVE = "activeServer"
    private const val KEY_OWNER = "offlineOwner"
    private const val DEFAULT_KEY = "sk-default"

    private var items: MutableList<ServerProfile> = ArrayList()
    private var active: String = ""
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        items = decode(Prefs.read(KEY_PROFILES))
        active = Prefs.read(KEY_ACTIVE)
        if (items.isEmpty()) {
            val origin = Prefs.srv()
            val cookie = Prefs.cookie()
            if (origin.isNotEmpty() && cookie.isNotEmpty()) {
                val key = makeKey()
                items.add(
                    ServerProfile(
                        key = key,
                        origin = origin,
                        cookie = cookie,
                        account = Prefs.account(),
                        alias = labelOf(origin, Prefs.account()),
                        fingerprint = "",
                        at = System.currentTimeMillis(),
                        lanPort = 25600,
                        lanAuto = true,
                        lanHost = "",
                        lanScheme = "http"
                    )
                )
                active = key
                persist()
            }
        }
        if (active.isEmpty() && items.isNotEmpty()) {
            active = items[0].key
        }
        if (Prefs.read(KEY_OWNER).isEmpty()) {
            Prefs.write(KEY_OWNER, activeKey())
        }
    }

    fun list(): List<ServerProfile> = items.toList()

    fun count(): Int = items.size

    fun find(key: String): ServerProfile? = items.firstOrNull { it.key == key }

    fun findByOrigin(origin: String): ServerProfile? = items.firstOrNull { it.origin == origin }

    fun match(fingerprint: String, account: String): ServerProfile? {
        if (fingerprint.isEmpty() || account.isEmpty()) return null
        return items.firstOrNull { it.fingerprint == fingerprint && it.account == account }
    }

    fun current(): ServerProfile? = find(active)

    fun activeKey(): String = if (active.isNotEmpty()) active else DEFAULT_KEY

    fun isActive(key: String): Boolean = active == key

    fun setActive(key: String) {
        active = key
    }

    fun upsert(profile: ServerProfile) {
        val i = items.indexOfFirst { it.key == profile.key }
        if (i >= 0) items[i] = profile else items.add(profile)
    }

    fun remove(key: String) {
        items.removeAll { it.key == key }
        if (active == key) {
            active = if (items.isNotEmpty()) items[0].key else ""
        }
    }

    fun makeKey(): String {
        val stamp = java.lang.Long.toString(System.currentTimeMillis(), 36)
        val noise = java.lang.Long.toString((Math.random() * 1679616).toLong(), 36)
        return "sk-$stamp$noise"
    }

    fun labelOf(origin: String, account: String): String {
        val host = origin.replace("http://", "").replace("https://", "")
        return if (account.isNotEmpty()) "$host · $account" else host
    }

    fun ownerKey(): String = Prefs.read(KEY_OWNER)

    fun persistOwner(key: String) {
        Prefs.write(KEY_OWNER, key)
    }

    fun persist() {
        val arr = JSONArray()
        for (p in items) {
            val o = JSONObject()
            o.put("key", p.key)
            o.put("origin", p.origin)
            o.put("cookie", p.cookie)
            o.put("account", p.account)
            o.put("alias", p.alias)
            o.put("fingerprint", p.fingerprint)
            o.put("at", p.at)
            o.put("lanPort", p.lanPort)
            o.put("lanAuto", p.lanAuto)
            o.put("lanHost", p.lanHost)
            o.put("lanScheme", p.lanScheme)
            arr.put(o)
        }
        Prefs.write(KEY_PROFILES, arr.toString())
        Prefs.write(KEY_ACTIVE, active)
    }

    private fun decode(raw: String): MutableList<ServerProfile> {
        val out = ArrayList<ServerProfile>()
        if (raw.isEmpty()) return out
        val rows = try {
            JSONArray(raw)
        } catch (e: Throwable) {
            return out
        }
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val key = J.str(row, "key")
            if (key.isEmpty()) continue
            val origin = J.str(row, "origin")
            val lanPort = J.num(row, "lanPort")
            val alias = J.str(row, "alias")
            out.add(
                ServerProfile(
                    key = key,
                    origin = origin,
                    cookie = J.str(row, "cookie"),
                    account = J.str(row, "account"),
                    alias = if (alias.isNotEmpty()) alias else labelOf(origin, J.str(row, "account")),
                    fingerprint = J.str(row, "fingerprint"),
                    at = J.lnum(row, "at"),
                    lanPort = if (lanPort > 0) lanPort else 25600,
                    lanAuto = if (row.opt("lanAuto") is Boolean) row.optBoolean("lanAuto") else true,
                    lanHost = J.str(row, "lanHost"),
                    lanScheme = if (J.str(row, "lanScheme") == "https") "https" else "http"
                )
            )
        }
        return out
    }
}
