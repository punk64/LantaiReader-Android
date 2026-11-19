package com.lantai.reader.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.Tm

@Composable
fun LtField(
    label: String,
    hint: String,
    value: String,
    secret: Boolean = false,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    onChange: (String) -> Unit
) {
    val C = Tm.pal
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.sp,
            color = C.tx4,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = C.tx),
            cursorBrush = SolidColor(C.acc),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(Dp2.rS.dp))
                        .background(C.bg2)
                        .border(1.dp, C.line, RoundedCornerShape(Dp2.rS.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(hint, fontSize = 14.sp, color = C.tx4, maxLines = 1)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
fun LtButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 46.dp,
    radius: Dp = Dp2.rM.dp,
    fontSize: Float = 15f,
    glyph: String = "",
    onClick: () -> Unit
) {
    val C = Tm.pal
    val bg = when {
        !enabled -> C.bg3
        primary -> C.acc
        else -> C.bg2
    }
    val fg = when {
        !enabled -> C.tx3
        primary -> C.bg
        else -> C.tx2
    }
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .then(if (!primary) Modifier.border(1.dp, C.line, RoundedCornerShape(radius)) else Modifier)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (glyph.isNotEmpty()) {
            com.lantai.reader.common.Glyph(glyph, 14.dp, fg)
            Spacer(Modifier.padding(horizontal = 3.dp))
        }
        Text(
            label,
            fontSize = fontSize.sp,
            color = fg,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
    }
}
