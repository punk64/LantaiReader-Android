package com.lantai.reader.net

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.lantai.reader.LtApp
import com.lantai.reader.common.t
import com.lantai.reader.common.tf
import com.lantai.reader.model.Prefs
import com.lantai.reader.model.ServerProfile
import com.lantai.reader.model.ServerProfiles
import com.lantai.reader.model.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val KEY_NOTE = "autoSwitchNote"
private const val SCAN_GAP = 60000L
private const val AUTO_DEFAULT_PORT = 25600

object AutoSwitch {
    private var watching = false
    private var busy = false
    private var lastScan = 0L
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun lanOriginOf(profile: ServerProfile): String {
        if (profile.lanHost.isEmpty() || profile.lanPort <= 0) return ""
        return LanScan.originOf(profile.lanScheme, profile.lanHost, profile.lanPort)
    }

    fun note(): String {
        val raw = Prefs.read(KEY_NOTE)
        if (raw.isEmpty()) return ""
        val parts = raw.split("|")
        if (parts.size < 2) return ""
        val at = parts[1].toLongOrNull() ?: 0L
        if (at <= 0L) return parts[0]
        val minutes = (System.currentTimeMillis() - at) / 60000L
        val ago = when {
            minutes < 1 -> t("刚刚")
            minutes < 60 -> t("${minutes} 分钟前")
            else -> t("${minutes / 60} 小时前")
        }
        return "${parts[0]} · $ago"
    }

    fun start() {
        if (!watching) {
            watching = true
            try {
                val cm = LtApp.instance.getSystemService(ConnectivityManager::class.java)
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        check()
                    }

                    override fun onLost(network: Network) {
                        check()
                    }

                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        check()
                    }
                }
                cm.registerDefaultNetworkCallback(cb)
                callback = cb
            } catch (e: Throwable) {
            }
        }
        check()
    }

    fun check() {
        if (busy) return
        val profile = ServerProfiles.current() ?: return
        if (profile.cookie.isEmpty()) return
        val lan = lanOriginOf(profile)
        if (lan.isEmpty() && !profile.lanAuto) return
        busy = true
        scope.launch {
            try {
                run(profile, lan)
            } catch (e: Throwable) {
            }
            busy = false
            bumpTick()
        }
    }

    private suspend fun run(profile: ServerProfile, lan: String) {
        val current = Store.session.serverOrigin()
        if (!onLan()) {
            if (current != profile.origin) {
                Store.session.setOrigin(profile.origin)
                mark(t("已切回公网"))
            }
            return
        }
        if (lan.isNotEmpty() && LanScan.ping(lan, profile.cookie)) {
            if (current != lan) {
                Store.session.setOrigin(lan)
                mark(tf("已连接局域网 {0}", listOf(profile.lanHost)))
            }
            return
        }
        if (lan.isNotEmpty() && current == lan) {
            Store.session.setOrigin(profile.origin)
            mark(t("局域网不可达，已切回公网"))
        }
        if (!profile.lanAuto) return
        val now = System.currentTimeMillis()
        if (now - lastScan < SCAN_GAP) return
        lastScan = now
        val hit = discover(profile)
        if (hit == null) {
            if (current != profile.origin) {
                Store.session.setOrigin(profile.origin)
                mark(t("局域网未发现，已切回公网"))
            }
            return
        }
        Store.session.setOrigin(hit.origin)
        mark(tf("已发现局域网 {0}", listOf(hit.host)))
        remember(profile, hit)
    }

    private suspend fun discover(profile: ServerProfile): LanHit? {
        val ports = ArrayList<Int>(3)
        if (profile.lanPort > 0) ports.add(profile.lanPort)
        val mainPort = portOf(profile.origin)
        if (mainPort > 0 && !ports.contains(mainPort)) ports.add(mainPort)
        if (!ports.contains(AUTO_DEFAULT_PORT)) ports.add(AUTO_DEFAULT_PORT)
        val schemes = ArrayList<String>(2)
        schemes.add(profile.lanScheme)
        schemes.add(if (profile.lanScheme == "https") "http" else "https")
        val hits = LanScan.scan(ports, schemes)
        for (hit in hits) {
            if (LanScan.ping(hit.origin, profile.cookie)) return hit
        }
        return null
    }

    private fun remember(profile: ServerProfile, hit: LanHit) {
        if (profile.lanHost == hit.host && profile.lanPort == hit.port && profile.lanScheme == hit.scheme) return
        profile.lanHost = hit.host
        profile.lanPort = hit.port
        profile.lanScheme = hit.scheme
        ServerProfiles.upsert(profile)
        ServerProfiles.persist()
    }

    private fun mark(text: String) {
        Prefs.write(KEY_NOTE, "$text|${System.currentTimeMillis()}")
        bumpTick()
    }

    fun bumpTick() {
        Store.netTick += 1
    }

    private fun onLan(): Boolean {
        return try {
            val cm = LtApp.instance.getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Throwable) {
            false
        }
    }

    private fun portOf(origin: String): Int {
        val body = origin.replace("http://", "").replace("https://", "")
        val colon = body.indexOf(':')
        if (colon <= 0) return 0
        return body.substring(colon + 1).substringBefore('/').toIntOrNull() ?: 0
    }
}
