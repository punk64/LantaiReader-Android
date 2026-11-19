package com.lantai.reader.common

import org.json.JSONArray
import org.json.JSONObject

object J {
    fun obj(o: JSONObject?, key: String): JSONObject {
        val v = o?.opt(key)
        return if (v is JSONObject) v else JSONObject()
    }

    fun arr(o: JSONObject?, key: String): JSONArray {
        val v = o?.opt(key)
        return if (v is JSONArray) v else JSONArray()
    }

    fun str(o: JSONObject?, key: String): String {
        val v = o?.opt(key) ?: return ""
        if (v is String) return v
        if (v is Boolean) return if (v) "true" else "false"
        if (v is Number) return trimNum(v)
        return v.toString()
    }

    fun num(o: JSONObject?, key: String): Int = lnum(o, key).toInt()

    fun lnum(o: JSONObject?, key: String): Long {
        val v = o?.opt(key) ?: return 0L
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toDoubleOrNull()?.toLong() ?: 0L
            is Boolean -> if (v) 1L else 0L
            else -> 0L
        }
    }

    fun dbl(o: JSONObject?, key: String): Double {
        val v = o?.opt(key) ?: return 0.0
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    fun bool(o: JSONObject?, key: String): Boolean {
        val v = o?.opt(key) ?: return false
        return when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v == "true" || v == "1"
            else -> false
        }
    }

    fun strList(o: JSONObject?, key: String): List<String> {
        val a = arr(o, key)
        val out = ArrayList<String>(a.length())
        for (i in 0 until a.length()) {
            val v = a.opt(i)
            if (v is String && v.isNotEmpty()) out.add(v)
        }
        return out
    }

    fun objList(o: JSONObject?, key: String): List<JSONObject> {
        val a = arr(o, key)
        val out = ArrayList<JSONObject>(a.length())
        for (i in 0 until a.length()) {
            val v = a.opt(i)
            if (v is JSONObject) out.add(v)
        }
        return out
    }

    fun rawList(o: JSONObject?, key: String): List<Any?> {
        val a = arr(o, key)
        val out = ArrayList<Any?>(a.length())
        for (i in 0 until a.length()) out.add(a.opt(i))
        return out
    }

    private fun trimNum(v: Number): String {
        val d = v.toDouble()
        return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
    }

    fun parse(text: String): JSONObject {
        if (text.isEmpty()) return JSONObject()
        return try {
            JSONObject(text)
        } catch (e: Throwable) {
            JSONObject()
        }
    }

    fun parseArray(text: String): JSONArray {
        if (text.isEmpty()) return JSONArray()
        return try {
            JSONArray(text)
        } catch (e: Throwable) {
            JSONArray()
        }
    }
}
