package com.lantai.reader.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

object F {
    fun pad(value: Int): String = if (value < 10) "0$value" else "$value"

    fun moment(raw: String): Long {
        if (raw.isEmpty()) return 0L
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (p in formats) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                sdf.isLenient = true
                if (p.endsWith("'Z'")) sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val d = sdf.parse(raw)
                if (d != null) return d.time
            } catch (e: Throwable) {
            }
        }
        return 0L
    }

    fun ago(raw: String): String {
        val stamp = moment(raw)
        if (stamp == 0L) return ""
        val delta = System.currentTimeMillis() - stamp
        if (delta < 0) return t("刚刚")
        val minute = floor(delta / 60000.0).toLong()
        if (minute < 1) return t("刚刚")
        if (minute < 60) return t("$minute 分钟前")
        val hour = floor(minute / 60.0).toLong()
        if (hour < 24) return t("$hour 小时前")
        val day = floor(hour / 24.0).toLong()
        if (day == 1L) return t("昨天")
        if (day < 30) return t("$day 天前")
        val date = Date(stamp)
        val cal = Calendar.getInstance().apply { time = date }
        val month = pad(cal.get(Calendar.MONTH) + 1)
        val dayOfMonth = pad(cal.get(Calendar.DAY_OF_MONTH))
        val nowYear = Calendar.getInstance().get(Calendar.YEAR)
        if (cal.get(Calendar.YEAR) == nowYear) return "$month-$dayOfMonth"
        return "${cal.get(Calendar.YEAR)}-$month-$dayOfMonth"
    }

    fun bytes(size: Long): String {
        if (size <= 0L) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = size.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        val digits = if (value >= 100 || unit == 0) 0 else 1
        return String.format(Locale.US, "%.${digits}f %s", value, units[unit])
    }

    fun cap(size: Long): String {
        val units = arrayOf("B", "K", "M", "G", "T")
        var value = if (size > 0) size.toDouble() else 0.0
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        if (unit == 0) return "${Math.round(value)}B"
        val text = if (value >= 10) "${Math.round(value)}" else "${(value * 10).roundToInt() / 10.0}"
        return "$text${units[unit]}"
    }

    fun percent(page: Int, total: Int): Int {
        if (total <= 0 || page <= 0) return 0
        val ratio = page.toDouble() / total
        if (ratio >= 1) return 100
        return max(1, Math.round(ratio * 100).toInt())
    }

    fun duration(ms: Long): String {
        if (ms <= 0L) return t("0 分钟")
        val minute = floor(ms / 60000.0).toLong()
        if (minute < 60) return t("${max(1L, minute)} 分钟")
        val hour = floor(minute / 60.0).toLong()
        val rest = minute % 60
        return if (rest == 0L) t("$hour 小时") else t("$hour 小时 $rest 分")
    }

    fun progressLabel(page: Int, total: Int, completed: Boolean): String {
        if (completed) return t("已读完")
        if (page <= 0) return t("未开始")
        return t("读到 ${percent(page, total)}%")
    }

    fun shortDuration(ms: Long): String {
        if (ms <= 0L) return t("0 分")
        val minute = floor(ms / 60000.0).toLong()
        if (minute < 60) return t("$minute 分")
        val hour = minute / 60.0
        return if (hour >= 10) t("${Math.round(hour)} 时")
        else t("${String.format(Locale.US, "%.1f", hour)} 时")
    }

    fun day(iso: String): String {
        val stamp = moment(iso)
        if (stamp == 0L) return ""
        val cal = Calendar.getInstance().apply { time = Date(stamp) }
        return "${pad(cal.get(Calendar.MONTH) + 1)}-${pad(cal.get(Calendar.DAY_OF_MONTH))}"
    }

    fun stamp(iso: String): String {
        val stamp = moment(iso)
        if (stamp == 0L) return ""
        val cal = Calendar.getInstance().apply { time = Date(stamp) }
        return "${cal.get(Calendar.YEAR)}-${pad(cal.get(Calendar.MONTH) + 1)}-${pad(cal.get(Calendar.DAY_OF_MONTH))} " +
            "${pad(cal.get(Calendar.HOUR_OF_DAY))}:${pad(cal.get(Calendar.MINUTE))}"
    }

    fun year(iso: String): String {
        val stamp = moment(iso)
        if (stamp == 0L) return ""
        val cal = Calendar.getInstance().apply { time = Date(stamp) }
        return "${cal.get(Calendar.YEAR)}"
    }

    fun initial(title: String): String {
        val s = title.trim()
        if (s.isEmpty()) return "?"
        return s.substring(0, 1)
    }
}
