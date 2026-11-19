package com.lantai.reader.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lantai.reader.model.Prefs
import org.json.JSONObject

object ProgressMarks {
    data class Mark(val page: Int = 0, val total: Int = 0, val done: Boolean = false, val at: Long = 0L, val zero: Boolean = false)

    private const val KEY = "progressMarks"
    private val map = HashMap<String, Mark>()
    private val forced = HashMap<String, Boolean>()
    private var loaded = false

    var ver by mutableStateOf(0)
        private set

    private fun load() {
        if (loaded) return
        loaded = true
        try {
            val raw = Prefs.read(KEY)
            if (raw.isEmpty()) return
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = obj.optJSONObject(k) ?: continue
                map[k] = Mark(
                    page = o.optInt("p"),
                    total = o.optInt("t"),
                    done = o.optBoolean("d"),
                    at = o.optLong("a"),
                    zero = o.optBoolean("z")
                )
            }
        } catch (e: Throwable) {
        }
    }

    private fun save() {
        try {
            val obj = JSONObject()
            for ((k, v) in map) {
                obj.put(k, JSONObject().put("p", v.page).put("t", v.total).put("d", v.done).put("a", v.at).put("z", v.zero))
            }
            Prefs.write(KEY, obj.toString())
        } catch (e: Throwable) {
        }
    }

    fun get(id: String): Mark? {
        load()
        return map[id]
    }

    fun pageOf(id: String, fallback: Int): Int {
        val mark = get(id) ?: return fallback
        if (mark.zero) return 0
        return if (mark.page > 0) mark.page else fallback
    }

    fun doneOf(id: String, fallback: Boolean, page: Int, pages: Int): Boolean {
        val pinned = forced[id]
        if (pinned != null) return pinned
        val mark = get(id)
        if (mark != null && mark.total > 0 && pages > 0) {
            return page >= pages
        }
        if (mark != null && mark.done) return true
        return fallback
    }

    fun settle(id: String, completed: Boolean, pages: Int) {
        if (id.isEmpty()) return
        load()
        forced.remove(id)
        if (!completed) {
            map[id] = Mark(0, pages, false, System.currentTimeMillis(), true)
        } else if (pages > 0) {
            map[id] = Mark(pages, pages, true, System.currentTimeMillis(), false)
        } else {
            forced[id] = true
        }
        save()
        ver += 1
    }

    fun put(id: String, page: Int, total: Int, done: Boolean) {
        if (id.isEmpty()) return
        load()
        val prev = map[id]
        if (prev != null && prev.page == page && prev.total == total && prev.done == done && !prev.zero) return
        map[id] = Mark(page, total, done, System.currentTimeMillis(), false)
        if (forced.containsKey(id)) forced.remove(id)
        save()
        ver += 1
    }

    fun drop(id: String) {
        load()
        map.remove(id)
        forced.remove(id)
        save()
        ver += 1
    }

    fun clear() {
        map.clear()
        forced.clear()
        loaded = true
        save()
        ver += 1
    }
}
