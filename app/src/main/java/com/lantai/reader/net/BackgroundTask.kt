package com.lantai.reader.net

import android.os.PowerManager
import com.lantai.reader.LtApp

object BackgroundTask {
    private var lock: PowerManager.WakeLock? = null

    fun active(): Boolean = lock != null

    fun acquire() {
        if (lock != null) return
        try {
            val pm = LtApp.instance.getSystemService(PowerManager::class.java)
            val wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lantai:download")
            wake.setReferenceCounted(false)
            wake.acquire(30 * 60 * 1000L)
            lock = wake
        } catch (e: Throwable) {
        }
    }

    fun release() {
        val wake = lock ?: return
        lock = null
        try {
            if (wake.isHeld) wake.release()
        } catch (e: Throwable) {
        }
    }
}
