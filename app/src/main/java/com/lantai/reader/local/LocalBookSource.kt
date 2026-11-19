package com.lantai.reader.local

import com.lantai.reader.model.LocalMeta

object LocalBookSource {
    private val metas = HashMap<String, LocalMeta>()
    private val opened = HashSet<String>()

    fun setMetas(list: List<LocalMeta>) {
        metas.clear()
        for (meta in list) metas[meta.id] = meta
    }

    fun metaOf(id: String): LocalMeta? = metas[id]

    fun preflight(id: String): String {
        val meta = metas[id] ?: return "这本书还没有完成解析"
        if (opened.contains(id)) return ""
        if (meta.kind == "comic") {
            val path = LocalLibrary.toPath(meta.uri)
            return if (LocalFiles.exists(path)) "" else "原文件已无法访问，请重新导入"
        }
        if (LocalMetas.ensureStaged(meta.uri, meta.name)) return ""
        return "原文件已无法访问，请重新导入"
    }

    fun forget(id: String) {
        metas.remove(id)
        opened.remove(id)
    }

    fun dropPages(id: String) {
        opened.remove(id)
    }

    fun releaseAll() {
        opened.clear()
    }
}
