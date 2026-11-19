package com.lantai.reader.common

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Crash {
    private var home: File? = null

    fun install(ctx: Context) {
        val ext = try {
            ctx.getExternalFilesDir(null)
        } catch (e: Throwable) {
            null
        }
        val base = File(ext ?: ctx.filesDir, "log")
        try {
            base.mkdirs()
        } catch (e: Throwable) {
        }
        home = base
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(base, thread, error)
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(base: File, thread: Thread, error: Throwable) {
        val buffer = StringWriter()
        error.printStackTrace(PrintWriter(buffer))
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val tail = buildString {
            append("time=").append(Date()).append('\n')
            append("thread=").append(thread.name).append('\n')
            append("device=").append(android.os.Build.MANUFACTURER).append(' ')
            append(android.os.Build.MODEL).append(" / API ").append(android.os.Build.VERSION.SDK_INT).append('\n')
            append("app=").append(appVersion()).append('\n')
            try {
                append("phase=").append(com.lantai.reader.model.Store.phase).append('\n')
                append("route=").append(com.lantai.reader.ui.Nav.names()).append('\n')
                append("book=").append(com.lantai.reader.model.Store.readerBookId).append('\n')
                append("tab=").append(com.lantai.reader.model.Store.mainTab).append('\n')
                append("offline=").append(com.lantai.reader.model.Store.offline).append('\n')
            } catch (e: Throwable) {
            }
        }
        val body = tail + "\n" + buffer.toString()
        try {
            File(base, "crash-$stamp.txt").writeText(body)
        } catch (e: Throwable) {
        }
        try {
            File(base, "crash-last.txt").writeText(body)
        } catch (e: Throwable) {
        }
    }

    private fun appVersion(): String {
        val app = try {
            com.lantai.reader.LtApp.instance
        } catch (e: Throwable) {
            return "?"
        }
        return try {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (e: Throwable) {
            "?"
        }
    }

    fun last(): String {
        val dir = home ?: return ""
        return try {
            val file = File(dir, "crash-last.txt")
            if (file.isFile) file.readText() else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun folder(): String = home?.absolutePath ?: ""
}
