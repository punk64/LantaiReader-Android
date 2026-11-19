package com.lantai.reader.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lantai.reader.common.t
import com.lantai.reader.local.ArchiveBook
import com.lantai.reader.net.LanScan
import com.lantai.reader.net.LantaiApi
import com.lantai.reader.net.LtSession

data class LocalBook(
    val name: String = "",
    val uri: String = "",
    val size: Long = 0L
)

data class LocalMeta(
    var id: String = "",
    var uri: String = "",
    var name: String = "",
    var title: String = "",
    var author: String = "",
    var series: String = "",
    var publisher: String = "",
    var date: String = "",
    var profile: String = "",
    var pages: Int = 0,
    var sizeBytes: Long = 0L,
    var cover: String = "",
    var kind: String = "",
    var chapter: Int = 0,
    var progress: Double = 0.0
)

object Store {
    val session: LtSession = LtSession()
    val api: LantaiApi = LantaiApi(session)

    var phase by mutableStateOf("boot")
    var mainTab by mutableStateOf(0)
    var uiTick by mutableStateOf(0)
    var profileTick by mutableStateOf(0)
    var offlineTick by mutableStateOf(0)
    var loginTick by mutableStateOf(0)
    var serverTick by mutableStateOf(0)
    var logoutTick by mutableStateOf(0)
    var searchTick by mutableStateOf(0)
    var netTick by mutableStateOf(0)
    var loginOrigin by mutableStateOf("")
    var ttsOn by mutableStateOf(false)
    var ttsIdx by mutableStateOf(0)
    var ttsTotal by mutableStateOf(0)
    var ttsPlay by mutableStateOf(false)
    var ttsKey by mutableStateOf(0)

    var account by mutableStateOf("")
    var nickname by mutableStateOf("")
    var avatar by mutableStateOf<Bitmap?>(null)
    var avatarKey by mutableStateOf("")
    var avatarUserId by mutableStateOf("")

    var selected by mutableStateOf<BookItem?>(null)
    var selectedSeriesId by mutableStateOf("")
    var readerBookId by mutableStateOf("")
    var readerTitle by mutableStateOf("")
    var readerChapter by mutableStateOf(1)
    var readerIncognito by mutableStateOf(false)

    var localRoot by mutableStateOf("")
    var localRootStale by mutableStateOf(false)
    var localBooks by mutableStateOf<List<LocalBook>>(emptyList())
    var localMetas by mutableStateOf<List<LocalMeta>>(emptyList())
    var localMissing by mutableStateOf<List<String>>(emptyList())
    var localVersion by mutableStateOf(0)

    var librarySource by mutableStateOf("")
    var localArchive by mutableStateOf<ArchiveBook?>(null)
    var localArchivePage by mutableStateOf(1)
    var localArchiveId by mutableStateOf("")

    var backHook: (() -> Boolean)? = null
    var readerExitHook: (() -> Unit)? = null
    var offline by mutableStateOf(false)

    fun bumpUi() {
        uiTick += 1
    }

    fun applyTts(idx: Int, total: Int, playing: Boolean, on: Boolean) {
        ttsIdx = idx
        ttsTotal = total
        ttsPlay = playing
        ttsOn = on
        ttsKey += 1
    }

    suspend fun restore(): Boolean {
        ServerProfiles.load()
        val profile = ServerProfiles.current()
        if (profile == null || profile.origin.isEmpty() || profile.cookie.isEmpty()) {
            return false
        }
        session.restore(profile.origin, profile.cookie)
        account = profile.account
        return try {
            val who = api.me()
            if (who.isEmpty()) {
                session.reset()
                false
            } else {
                if (account.isEmpty()) account = who
                loadProfile()
                true
            }
        } catch (e: Throwable) {
            session.reset()
            false
        }
    }

    suspend fun login(server: String, user: String, password: String) {
        session.login(server, user, password)
        var who = ""
        try {
            who = api.me()
        } catch (e: Throwable) {
            who = ""
        }
        if (who.isEmpty()) who = user
        account = who
        val origin = session.serverOrigin()
        val cookie = session.cookieHeader()
        val fingerprint = fingerprint()
        val merged = ServerProfiles.match(fingerprint, who)
        val key = if (merged != null) {
            merged.key
        } else {
            ServerProfiles.findByOrigin(origin)?.key ?: ServerProfiles.makeKey()
        }
        val existed = ServerProfiles.find(key)
        val pendingAlias = Prefs.read("pendingAlias")
        val pendingPort = Prefs.int("pendingLanPort", 0)
        val pendingAuto = Prefs.read("pendingLanAuto")
        val pendingHost = Prefs.read("pendingLanHost")
        val pendingScheme = Prefs.read("pendingLanScheme")
        var alias = ServerProfiles.labelOf(origin, who)
        if (existed != null && existed.alias.isNotEmpty()) {
            alias = existed.alias
        } else if (pendingAlias.isNotEmpty()) {
            alias = pendingAlias
        }
        ServerProfiles.upsert(
            ServerProfile(
                key = key,
                origin = origin,
                cookie = cookie,
                account = who,
                alias = alias,
                fingerprint = fingerprint,
                at = System.currentTimeMillis(),
                lanPort = if (pendingPort > 0) pendingPort else (existed?.lanPort?.takeIf { it > 0 } ?: 25600),
                lanAuto = if (pendingAuto.isNotEmpty()) pendingAuto == "true" else (existed?.lanAuto ?: true),
                lanHost = if (pendingHost.isNotEmpty()) pendingHost else (existed?.lanHost ?: ""),
                lanScheme = if (pendingScheme.isNotEmpty()) pendingScheme else (existed?.lanScheme ?: "http")
            )
        )
        ServerProfiles.setActive(key)
        ServerProfiles.persist()
        if (ServerProfiles.ownerKey().isEmpty()) ServerProfiles.persistOwner(key)
        Prefs.saveSession(origin, cookie, who)
        if (pendingAlias.isNotEmpty()) Prefs.write("pendingAlias", "")
        if (pendingHost.isNotEmpty()) Prefs.write("pendingLanHost", "")
        if (pendingScheme.isNotEmpty()) Prefs.write("pendingLanScheme", "")
        if (pendingAuto.isNotEmpty()) Prefs.write("pendingLanAuto", "")
        if (pendingPort > 0) Prefs.write("pendingLanPort", "")
        loadProfile()
    }

    suspend fun switchTo(key: String) {
        val target = ServerProfiles.find(key) ?: throw IllegalStateException(t("服务器档案不存在"))
        if (ServerProfiles.isActive(key)) return
        val previous = ServerProfiles.current()
        session.restore(target.origin, target.cookie)
        var valid = false
        try {
            valid = api.me().isNotEmpty()
        } catch (e: Throwable) {
            valid = false
        }
        if (!valid) {
            if (previous != null) {
                session.restore(previous.origin, previous.cookie)
                account = previous.account
            } else {
                session.reset()
                account = ""
            }
            throw IllegalStateException(t("该服务器会话已失效，请重新登录"))
        }
        account = target.account
        selected = null
        target.at = System.currentTimeMillis()
        ServerProfiles.upsert(target)
        ServerProfiles.setActive(key)
        ServerProfiles.persist()
        ServerProfiles.persistOwner(key)
        Prefs.saveSession(target.origin, target.cookie, target.account)
        loadProfile()
    }

    fun rename(key: String, alias: String) {
        val target = ServerProfiles.find(key) ?: return
        target.alias = alias
        ServerProfiles.upsert(target)
        ServerProfiles.persist()
    }

    fun editProfile(
        key: String,
        alias: String,
        origin: String,
        lanPort: Int,
        lanAuto: Boolean,
        lanHost: String,
        lanScheme: String
    ) {
        val target = ServerProfiles.find(key) ?: return
        val changed = origin != target.origin
        target.origin = origin
        target.alias = alias
        target.lanPort = lanPort
        target.lanAuto = lanAuto
        target.lanHost = lanHost
        target.lanScheme = lanScheme
        ServerProfiles.upsert(target)
        ServerProfiles.persist()
        if (ServerProfiles.isActive(key) && changed) {
            session.restore(origin, target.cookie)
            Prefs.saveSession(origin, target.cookie, target.account)
        }
    }

    fun removeProfile(key: String): Boolean {
        if (ServerProfiles.isActive(key)) return false
        ServerProfiles.remove(key)
        ServerProfiles.persist()
        return true
    }

    suspend fun fingerprint(): String {
        return try {
            val home = api.home(1)
            home.libraries.map { it.id }.sorted().joinToString(",")
        } catch (e: Throwable) {
            ""
        }
    }

    fun logout() {
        session.reset()
        selected = null
        val profile = ServerProfiles.current()
        if (profile != null) {
            profile.cookie = ""
            ServerProfiles.upsert(profile)
            ServerProfiles.persist()
        }
        Prefs.clearCookie()
        clearProfile()
    }

    fun serverLabel(): String {
        val origin = session.serverOrigin()
        if (origin.isEmpty()) return t("未连接")
        return origin.replace("http://", "").replace("https://", "")
    }

    fun enterOffline() {
        offline = true
        session.reset()
        selected = null
        clearProfile()
        offlineTick += 1
    }

    suspend fun exitOffline(): Boolean {
        val restored = restore()
        if (!restored) return false
        offline = false
        return true
    }

    fun notifyOffline() {
        offlineTick += 1
    }

    fun isLanActive(): Boolean = LanScan.isPrivate(session.serverOrigin())

    fun connectionLabel(): String {
        if (offline) return t("离线模式")
        if (session.serverOrigin().isEmpty()) return t("未连接")
        val kind = if (isLanActive()) t("局域网") else t("公网")
        return "$kind · ${serverLabel()}"
    }

    fun displayName(): String {
        if (nickname.isNotEmpty()) return nickname
        if (account.isNotEmpty()) return account
        return t("本机读者")
    }

    suspend fun loadProfile() {
        val profile = try {
            api.userProfile()
        } catch (e: Throwable) {
            return
        }
        val name = if (profile.nickname.isNotEmpty()) profile.nickname else profile.username
        if (name.isNotEmpty()) nickname = name
        val key = "${profile.userId}|${profile.avatarUpdated}|${profile.hasAvatar}"
        if (key != avatarKey) {
            avatarKey = key
            avatarUserId = profile.userId
            avatar = null
            if (profile.hasAvatar && profile.userId.isNotEmpty()) {
                try {
                    val bytes = api.userAvatar(profile.userId)
                    avatar = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Throwable) {
                    avatar = null
                }
            }
        }
        profileTick += 1
    }

    fun clearProfile() {
        nickname = ""
        avatar = null
        avatarKey = ""
        avatarUserId = ""
        profileTick += 1
    }
}
