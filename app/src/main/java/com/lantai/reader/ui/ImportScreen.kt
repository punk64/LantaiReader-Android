package com.lantai.reader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.LtApp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.F
import com.lantai.reader.common.Tip
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.common.tf
import com.lantai.reader.local.ComicArchive
import com.lantai.reader.local.LocalFiles
import com.lantai.reader.local.LocalLibrary
import com.lantai.reader.local.LocalMetas
import com.lantai.reader.local.LocalStore
import com.lantai.reader.local.LocalBookSource
import com.lantai.reader.local.localKindOf
import com.lantai.reader.model.LocalBook
import com.lantai.reader.model.LocalMeta
import com.lantai.reader.model.Store
import com.lantai.reader.ui.comp.ActionBtn
import com.lantai.reader.ui.comp.Chip
import com.lantai.reader.ui.comp.CoverImage
import com.lantai.reader.ui.comp.Disp
import com.lantai.reader.ui.comp.GroupHead
import com.lantai.reader.ui.comp.NoteCard
import com.lantai.reader.ui.comp.Pill
import com.lantai.reader.ui.comp.ScreenHead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IMPORT_FORMATS = listOf("EPUB", "TXT", "CBZ / CBR / CB7", "PDF", "KEPUB")

private data class ImportStep(val index: String, val title: String, val sub: String)

private fun importSteps(): List<ImportStep> = listOf(
    ImportStep("1", t("识别文件与编码"), t("只读文件头，不改原文件")),
    ImportStep("2", t("建立章节 / 页索引"), t("索引写入数据库，可随时重建")),
    ImportStep("3", t("生成封面与元数据"), t("失败不阻断，稍后可补"))
)

private fun extOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(dot + 1).uppercase() else "FILE"
}

private fun titleOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(0, dot) else name
}

@Composable
fun ImportScreen() {
    val C = Tm.pal
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf(Store.localBooks) }
    var missing by remember { mutableStateOf(Store.localMissing) }
    var busy by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf(false) }
    var purging by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<LocalBook?>(null) }

    fun usedBytes(): Long = books.sumOf { it.size }

    fun isMissing(book: LocalBook): Boolean = missing.contains(LocalMetas.idOf(book.uri))

    fun subOf(book: LocalBook): String {
        val kind = when {
            ComicArchive.isReadable(book.name) -> t("漫画")
            localKindOf(book.name).isNotEmpty() -> t("文档")
            else -> t("待接入阅读器")
        }
        val size = if (book.size > 0) " · ${F.bytes(book.size)}" else ""
        return "${extOf(book.name)} $kind$size"
    }

    suspend fun enrichAll(incoming: List<LocalBook>) {
        for (book in incoming) {
            try {
                val meta = LocalMetas.enrich(book)
                val prev = Store.localMetas.firstOrNull { it.id == meta.id }
                if (prev != null) {
                    meta.chapter = prev.chapter
                    meta.progress = prev.progress
                }
                Store.localMetas = Store.localMetas.filter { it.id != meta.id } + meta
                LocalStore.save(meta, Store.localRoot)
            } catch (e: Throwable) {
            }
        }
    }

    fun merge(incoming: List<LocalBook>) {
        val merged = ArrayList<LocalBook>(incoming)
        for (current in Store.localBooks) {
            if (merged.none { it.uri == current.uri }) merged.add(current)
        }
        Store.localBooks = merged
        books = merged
        if (Store.localRoot.isEmpty()) {
            Store.localRoot = "picked"
            LocalStore.persistRoot("picked")
        }
        scope.launch {
            LocalStore.persistBooks(incoming, Store.localRoot)
            enrichAll(incoming)
        }
    }

    fun metaOf(uri: String): LocalMeta? = Store.localMetas.firstOrNull { it.uri == uri }

    fun open(book: LocalBook) {
        if (archiving) return
        if (isMissing(book)) {
            Tip.show(t("文件已缺失，请重新导入"))
            return
        }
        if (ComicArchive.isReadable(book.name)) {
            archiving = true
            scope.launch {
                try {
                    val opened = withContext(Dispatchers.IO) {
                        ComicArchive.open(LocalLibrary.toPath(book.uri), book.name, book.size)
                    }
                    val meta = metaOf(book.uri)
                    Store.localArchive = opened
                    Store.localArchiveId = LocalMetas.idOf(book.uri)
                    Store.localArchivePage = if (meta != null && meta.chapter > 0) meta.chapter else 1
                    Store.readerBookId = ""
                    Nav.push("comic")
                } catch (e: Throwable) {
                    Tip.show(t("打开失败：${e.message ?: ""}"))
                }
                archiving = false
            }
            return
        }
        val meta = metaOf(book.uri)
        if (meta == null) {
            Tip.show(t("这本书还没有完成解析"))
            return
        }
        val blocked = LocalBookSource.preflight(meta.id)
        if (blocked.isNotEmpty()) {
            Tip.show(blocked)
            return
        }
        Store.readerBookId = meta.id
        Store.readerTitle = if (meta.title.isNotEmpty()) meta.title else meta.name
        Store.readerChapter = if (meta.chapter > 0) meta.chapter else 1
        Store.readerIncognito = false
        Nav.push("reader")
    }

    fun removeBook(book: LocalBook) {
        scope.launch {
            val meta = metaOf(book.uri)
            if (meta != null) LocalStore.remove(meta)
            val rest = books.filter { it.uri != book.uri }
            books = rest
            Store.localBooks = rest
            Store.localMetas = Store.localMetas.filter { it.uri != book.uri }
            Store.localMissing = Store.localMissing.filter { it != LocalMetas.idOf(book.uri) }
            missing = Store.localMissing
            if (rest.isEmpty()) {
                Store.localRoot = ""
                LocalStore.persistRoot("")
            }
            Tip.show(t("已移除"))
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val picked = LocalLibrary.importPicked(ctx, uris)
                if (picked.isEmpty()) {
                    Tip.show(t("所选文件中没有可识别的书籍格式"))
                } else {
                    merge(picked)
                    val failed = uris.size - picked.size
                    Tip.show(if (failed > 0) tf("已导入 {0} 本，失败 {1} 本", listOf(picked.size.toString(), failed.toString())) else tf("已导入 {0} 本", listOf(picked.size.toString())))
                }
            } catch (e: Throwable) {
                Tip.show(t("导入失败：${e.message ?: ""}"))
            }
            busy = false
        }
    }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                LocalLibrary.persist(ctx, uri)
                val scanned = LocalLibrary.scanTree(ctx, uri)
                val found = LocalLibrary.adopt(ctx, scanned)
                if (found.isEmpty()) {
                    Tip.show(t("这个文件夹里没有可识别的书籍"))
                } else {
                    Store.localRoot = uri.toString()
                    merge(found)
                    Tip.show(t("扫描到 ${found.size} 本"))
                }
            } catch (e: Throwable) {
                Tip.show(t("扫描失败：${e.message ?: ""}"))
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        ScreenHead(
            title = t("导入本地文件"),
            sub = if (books.isNotEmpty()) {
                tf("已导入 {0} 本 · 占用 {1}", listOf(books.size.toString(), F.bytes(usedBytes())))
            } else {
                t("导入后复制到应用内，不上传")
            },
            back = t("我的"),
            onBack = { Nav.pop() }
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Dp2.page.dp, end = Dp2.page.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.accLine, RoundedCornerShape(Dp2.rM.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.accBg)
                        .border(1.dp, C.accLine, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(t("导"), fontSize = 20.sp, color = C.acc2, textAlign = TextAlign.Center)
                }
                Text(
                    if (busy) t("选择文件中…") else t("从手机文件里选书"),
                    fontSize = 15.5.sp,
                    color = C.tx,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 9.dp)
                )
                Text(
                    t("文件可多选，也可整个文件夹导入"),
                    fontSize = 9.5.sp,
                    color = C.tx4,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    t("选中的文件会复制一份到应用内，原文件删除后仍可阅读"),
                    fontSize = 9.5.sp,
                    color = C.tx4
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionBtn(
                        label = t("选择文件 · 多选"),
                        primary = true,
                        glyph = "book",
                        modifier = Modifier.weight(1f)
                    ) {
                        filePicker.launch(arrayOf("*/*"))
                    }
                    ActionBtn(label = t("选择文件夹"), glyph = "folder", modifier = Modifier.weight(1f)) {
                        dirPicker.launch(null)
                    }
                }
                Text(
                    t("选文件夹需要系统文件选择器授权，授权后可递归扫描子目录"),
                    fontSize = 9.sp,
                    color = C.tx4,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 9.dp)
                )
            }

            GroupHead(title = t("支持的格式"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IMPORT_FORMATS.forEach { label ->
                    Chip(label = label, active = false, compact = true) { }
                }
            }

            Box(Modifier.padding(top = 12.dp)) {
                NoteCard(text = t("MOBI / AZW3 / FB2 等格式需服务端安装可选转换组件；未安装时书籍仍可见、可下载、可记进度。"))
            }

            GroupHead(title = t("导入流程"), meta = t("3 步"))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(horizontal = 13.dp)
            ) {
                importSteps().forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(C.accBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item.index, fontSize = 10.5.sp, color = C.acc2, textAlign = TextAlign.Center)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, fontSize = 12.5.sp, color = C.tx, modifier = Modifier.fillMaxWidth())
                            Text(
                                item.sub,
                                fontSize = 10.sp,
                                color = C.tx4,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Pill(label = t("完成"), tone = "done")
                    }
                }
            }

            GroupHead(
                title = t("已导入"),
                meta = if (books.isNotEmpty()) "${books.size} 册 · ${F.bytes(usedBytes())}" else t("暂无"),
                action = if (books.isNotEmpty()) t("清理全部") else "",
                onAction = { if (books.isNotEmpty()) purging = true }
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dp2.rM.dp))
                    .background(C.bg1)
                    .border(1.dp, C.line, RoundedCornerShape(Dp2.rM.dp))
                    .padding(horizontal = 13.dp)
            ) {
                if (books.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(t("还没有导入任何文件"), fontSize = 12.sp, color = C.tx2)
                        Text(
                            t("点上方「选择文件 · 多选」开始，导入的记录会保存在本机"),
                            fontSize = 10.sp,
                            color = C.tx4,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    books.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { open(item) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CoverImage(
                                bookId = "",
                                title = titleOf(item.name).take(1),
                                coverWidth = 42.dp,
                                coverHeight = 60.dp,
                                radius = 4.dp
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    titleOf(item.name),
                                    fontSize = 12.5.sp,
                                    color = C.tx,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    item.name,
                                    fontSize = 10.sp,
                                    color = C.tx4,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    subOf(item),
                                    fontSize = 9.5.sp,
                                    color = C.tx4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (isMissing(item)) {
                                Text(
                                    t("缺失"),
                                    fontSize = 9.sp,
                                    color = C.bad,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(C.bg2)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                t("移除"),
                                fontSize = 10.5.sp,
                                color = C.tx3,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(C.bg2)
                                    .clickable { removeTarget = item }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    val target = removeTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = C.bg1,
            title = { Text(t("移除本地书"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    tf("确定移除《{0}》？应用内副本与封面会一并删除。", listOf(titleOf(target.name))),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeBook(target)
                    removeTarget = null
                }) {
                    Text(t("移除"), fontSize = 12.sp, color = C.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }

    if (purging) {
        AlertDialog(
            onDismissRequest = { purging = false },
            containerColor = C.bg1,
            title = { Text(t("清理全部本地书"), fontSize = 15.sp, fontFamily = Disp, color = C.tx) },
            text = {
                Text(
                    tf("将删除 {0} 条记录、应用内副本与封面，共 {1}。授权目录中的原文件不会被删除。", listOf(books.size.toString(), F.bytes(usedBytes()))),
                    fontSize = 11.5.sp,
                    color = C.tx3,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    purging = false
                    scope.launch {
                        LocalStore.purgeAll(Store.localMetas)
                        Store.localBooks = emptyList()
                        Store.localMetas = emptyList()
                        Store.localMissing = emptyList()
                        Store.localRoot = ""
                        Store.localRootStale = false
                        books = emptyList()
                        missing = emptyList()
                        Tip.show(t("已清理全部本地书"))
                    }
                }) {
                    Text(t("清理"), fontSize = 12.sp, color = C.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { purging = false }) {
                    Text(t("取消"), fontSize = 12.sp, color = C.tx3)
                }
            }
        )
    }
}
