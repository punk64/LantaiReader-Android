package com.lantai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t
import com.lantai.reader.model.Store
import com.lantai.reader.ui.comp.BottomTabs

@Composable
private fun KeepAlive(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            if (visible) {
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            } else {
                layout(0, 0) { }
            }
        }
    ) {
        content()
    }
}

@Composable
fun MainShell() {
    val C = Tm.pal
    Box(Modifier.fillMaxSize().background(C.bg)) {
        KeepAlive(Store.mainTab == 0) {
            if (Store.offline) OfflineStub(t("离线模式下首页不可用")) else HomeScreen()
        }
        KeepAlive(Store.mainTab == 1) {
            LibraryScreen(embedded = true, focusTick = Store.searchTick)
        }
        KeepAlive(Store.mainTab == 2) {
            if (Store.offline) OfflineStub(t("离线模式下统计不可用")) else StatsScreen()
        }
        KeepAlive(Store.mainTab == 3) {
            MineScreen { Store.mainTab = it }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp)
        ) {
            BottomTabs(Store.mainTab) { Store.mainTab = it }
        }
    }
}
