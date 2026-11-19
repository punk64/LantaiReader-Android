package com.lantai.reader.ui.comp

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.LtApp
import com.lantai.reader.common.Glyph
import com.lantai.reader.common.Tip
import com.lantai.reader.common.t
import com.lantai.reader.components.CoverCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private fun saveToGallery(bitmap: Bitmap): Boolean {
    val name = "lantai-cover-${System.currentTimeMillis()}"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lantai")
    }
    return try {
        val resolver = LtApp.instance.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            false
        } else {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
            } ?: return false
            true
        }
    } catch (e: Throwable) {
        try {
            val dir = File(LtApp.instance.getExternalFilesDir(null), "covers")
            dir.mkdirs()
            val target = File(dir, "$name.jpg")
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
            }
            true
        } catch (e2: Throwable) {
            false
        }
    }
}

@Composable
fun CoverViewer(bookId: String, title: String, onClose: () -> Unit) {
    var bitmap by remember(bookId) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(bookId) { mutableStateOf(true) }
    var error by remember(bookId) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bookId) {
        loading = true
        error = ""
        try {
            bitmap = CoverCache.loadFull(bookId)
        } catch (e: Throwable) {
            error = e.message ?: ""
        }
        loading = false
        if (bitmap == null && error.isEmpty()) error = t("封面读取失败")
    }

    androidx.compose.runtime.DisposableEffect(bookId) {
        onDispose { CoverCache.releaseFull(bookId) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val face = bitmap
        if (face != null) {
            Image(
                bitmap = face.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(34.dp))
            }
        } else if (error.isNotEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Glyph("cover", 30.dp, Color(0x66FFFFFF))
                Text(error, fontSize = 12.sp, color = Color(0x99FFFFFF), modifier = Modifier.padding(top = 12.dp))
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x24FFFFFF))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Glyph("close", 17.dp, Color.White)
            }
            Text(
                title,
                fontSize = 12.sp,
                color = Color(0xCCFFFFFF),
                fontFamily = FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x24FFFFFF))
                    .clickable(enabled = !saving && face != null) {
                        val target = face ?: return@clickable
                        saving = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { saveToGallery(target) }
                            saving = false
                            Tip.show(if (ok) t("已保存到相册") else t("保存失败"))
                        }
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(t("保存"), fontSize = 11.sp, color = Color.White)
            }
        }
    }
}
