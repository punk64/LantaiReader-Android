package com.lantai.reader.net

import com.lantai.reader.model.SeriesPage

object SeriesCache {
    private const val LIMIT = 12

    private class Slot(val key: String, val page: SeriesPage)

    private val slots = ArrayList<Slot>()

    fun keyOf(page: Int, size: Int, sort: String): String = "$page|$size|$sort"

    fun get(key: String): SeriesPage? {
        val slot = slots.firstOrNull { it.key == key } ?: return null
        slots.remove(slot)
        slots.add(slot)
        return slot.page
    }

    fun put(key: String, page: SeriesPage) {
        slots.removeAll { it.key == key }
        slots.add(Slot(key, page))
        while (slots.size > LIMIT) {
            slots.removeAt(0)
        }
    }

    fun clear() {
        slots.clear()
    }
}
