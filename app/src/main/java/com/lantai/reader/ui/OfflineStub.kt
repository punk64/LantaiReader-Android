package com.lantai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lantai.reader.common.Tm
import com.lantai.reader.common.t

@Composable
fun OfflineStub(label: String) {
    val C = Tm.pal
    Column(
        Modifier.fillMaxSize().background(C.bg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 12.sp, color = C.tx3)
        Text(
            t("到「我的」切换在线"),
            fontSize = 10.5.sp,
            color = C.tx4,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
