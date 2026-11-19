package com.lantai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.reader.ReaderHost
import com.lantai.reader.model.Store

private const val FLUSH_JS =
    "(function(){var e=window.EbookReader;if(e&&e.flush){e.flush();}})()"

private const val BACK_JS =
    "(function(){var e=window.EbookReader;if(e&&e.back){e.back();}})()"

@Composable
fun ReaderScreen() {
    val C = Tm.pal

    LaunchedEffect(Unit) {
        try {
            ReaderHost.visible = true
            val id = Store.readerBookId
            if (id.isEmpty()) {
                Nav.pop()
                return@LaunchedEffect
            }
            Store.backHook = {
                if (!ReaderHost.busy) {
                    ReaderHost.exec(FLUSH_JS)
                    ReaderHost.visible = false
                    false
                } else {
                    ReaderHost.exec(BACK_JS)
                    true
                }
            }
            Store.readerExitHook = {
                ReaderHost.exec(FLUSH_JS)
                ReaderHost.visible = false
                Nav.pop()
            }
            ReaderHost.open(id)
        } catch (e: Throwable) {
            ReaderHost.visible = false
            Nav.pop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Store.backHook = null
            Store.readerExitHook = null
            ReaderHost.visible = false
            Store.readerIncognito = false
        }
    }

    Box(Modifier.fillMaxSize().background(C.bg))
}
