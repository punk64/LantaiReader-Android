package com.lantai.reader.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

data class LanHit(
    val origin: String = "",
    val host: String = "",
    val scheme: String = "",
    val port: Int = 0
)

private const val PROBE_PATH = "/api/v1/oauth2/providers"
private const val ME_PATH = "/api/v2/users/me"
private const val SCAN_CONCURRENCY = 32
private const val PLAIN_TIMEOUT = 900
private const val TLS_TIMEOUT = 1800
private const val SCAN_DEFAULT_PORT = 25600

object LanScan {
    fun defaultPort(): Int = SCAN_DEFAULT_PORT

    fun schemeOf(origin: String): String = if (origin.startsWith("https://")) "https" else "http"

    fun originOf(scheme: String, host: String, port: Int): String = "$scheme://$host:$port"

    fun isPrivate(origin: String): Boolean {
        if (origin.isEmpty()) return false
        return isPrivateHost(hostOf(origin))
    }

    fun isPrivateHost(host: String): Boolean {
        if (host.isEmpty()) return false
        if (host == "localhost") return true
        if (host.contains(':')) {
            val h = host.lowercase()
            return h.startsWith("fe80") || h.startsWith("fd") || h.startsWith("fc") || h == "::1"
        }
        val parts = host.split(".")
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        if (parts.any { (it.toIntOrNull() ?: -1) !in 0..255 }) return false
        if (a == 10) return true
        if (a == 192 && b == 168) return true
        if (a == 172 && b in 16..31) return true
        if (a == 127) return true
        if (a == 169 && b == 254) return true
        return false
    }

    fun hostOf(origin: String): String {
        if (origin.isEmpty()) return ""
        return try {
            URL(origin).host
        } catch (e: Throwable) {
            origin.replace("http://", "").replace("https://", "").substringBefore('/').substringBefore(':')
        }
    }

    fun probeSuffix(origin: String): String {
        val host = hostOf(origin)
        return if (host.isEmpty()) "" else " · $host"
    }

    fun probe(origin: String): Boolean {
        if (origin.isEmpty()) return false
        val timeout = if (origin.startsWith("https://")) TLS_TIMEOUT else PLAIN_TIMEOUT
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("$origin$PROBE_PATH").openConnection() as HttpURLConnection
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.requestMethod = "GET"
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            code == 200 || code == 401 || code == 403
        } catch (e: Throwable) {
            false
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Throwable) {
            }
        }
    }

    fun ping(origin: String, cookie: String): Boolean {
        if (origin.isEmpty()) return false
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("$origin$ME_PATH").openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.requestMethod = "GET"
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            if (cookie.isNotEmpty()) conn.setRequestProperty("Cookie", cookie)
            conn.responseCode == 200
        } catch (e: Throwable) {
            false
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Throwable) {
            }
        }
    }

    fun lanIp(): String {
        val fallback = ArrayList<String>(4)
        try {
            val all = NetworkInterface.getNetworkInterfaces() ?: return ""
            for (nic in all) {
                if (!nic.isUp || nic.isLoopback) continue
                for (addr in nic.inetAddresses) {
                    if (addr !is Inet4Address) continue
                    if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                    val ip = addr.hostAddress ?: continue
                    if (ip.startsWith("127.") || ip.startsWith("169.254.")) continue
                    if (ip.split(".").size != 4) continue
                    val name = nic.name.lowercase()
                    if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap")) return ip
                    fallback.add(ip)
                }
            }
        } catch (e: Throwable) {
        }
        return fallback.firstOrNull() ?: ""
    }

    fun localAddress(): String = lanIp()

    private suspend fun sweep(hosts: List<String>, scheme: String, port: Int): List<LanHit> = coroutineScope {
        val hits = ArrayList<LanHit>(4)
        var cursor = 0
        while (cursor < hosts.size) {
            val batch = hosts.subList(cursor, minOf(cursor + SCAN_CONCURRENCY, hosts.size))
            cursor += SCAN_CONCURRENCY
            val results = batch.map { host ->
                async(Dispatchers.IO) {
                    val origin = originOf(scheme, host, port)
                    if (probe(origin)) LanHit(origin, host, scheme, port) else null
                }
            }.awaitAll()
            for (hit in results) {
                if (hit != null) hits.add(hit)
            }
            if (hits.isNotEmpty()) return@coroutineScope hits
        }
        hits
    }

    suspend fun scan(ports: List<Int>, schemes: List<String>): List<LanHit> = withContext(Dispatchers.IO) {
        val ip = lanIp()
        if (ip.isEmpty()) return@withContext emptyList()
        val parts = ip.split(".")
        if (parts.size != 4) return@withContext emptyList()
        val head = "${parts[0]}.${parts[1]}.${parts[2]}"
        val hosts = (1..254).map { "$head.$it" }
        for (scheme in schemes) {
            for (port in ports) {
                val hits = sweep(hosts, scheme, port)
                if (hits.isNotEmpty()) return@withContext hits
            }
        }
        emptyList()
    }
}
