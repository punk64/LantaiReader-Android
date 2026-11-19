package com.lantai.reader.ui.comp

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lantai.reader.common.Dp2
import com.lantai.reader.common.Tm

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    blockWidth: Dp = 0.dp,
    blockHeight: Dp = 12.dp,
    blockRadius: Dp = 5.dp
) {
    val C = Tm.pal
    val transition = rememberInfiniteTransition(label = "skeleton")
    val lit by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lit"
    )
    Row(
        Modifier
            .then(if (blockWidth.value > 0f) Modifier.width(blockWidth) else Modifier.fillMaxWidth())
            .height(blockHeight)
            .clip(RoundedCornerShape(blockRadius))
            .background(C.bg3)
            .alpha(lit)
    ) {}
}

@Composable
fun LibrarySkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Dp2.page.dp, vertical = 0.dp).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(blockWidth = 52.dp, blockHeight = 26.dp, blockRadius = 13.dp)
            SkeletonBlock(blockWidth = 64.dp, blockHeight = 26.dp, blockRadius = 13.dp)
            SkeletonBlock(blockWidth = 46.dp, blockHeight = 26.dp, blockRadius = 13.dp)
            SkeletonBlock(blockWidth = 58.dp, blockHeight = 26.dp, blockRadius = 13.dp)
        }
        SkeletonBlock(Modifier.padding(top = 22.dp).padding(end = 220.dp), blockHeight = 13.dp)
        Column(Modifier.fillMaxWidth().height(420.dp).padding(top = 12.dp)) {
            repeat(2) {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) {
                        Column(Modifier.width(104.dp)) {
                            SkeletonBlock(blockWidth = 104.dp, blockHeight = 148.dp, blockRadius = 8.dp)
                            SkeletonBlock(Modifier.padding(top = 9.dp), blockHeight = 10.dp, blockRadius = 4.dp)
                            SkeletonBlock(Modifier.padding(top = 6.dp), blockWidth = 58.dp, blockHeight = 8.dp, blockRadius = 4.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dp2.page.dp)
            .padding(top = 6.dp)
    ) {
        SkeletonBlock(blockHeight = 192.dp, blockRadius = Dp2.rL.dp)
        SkeletonBlock(Modifier.padding(top = 26.dp), blockWidth = 92.dp, blockHeight = 14.dp)
        SkeletonShelf()
        SkeletonBlock(Modifier.padding(top = 24.dp), blockWidth = 78.dp, blockHeight = 14.dp)
        SkeletonShelf()
    }
}

@Composable
private fun SkeletonShelf() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) {
            Column(Modifier.width(82.dp)) {
                SkeletonBlock(blockWidth = 82.dp, blockHeight = 115.dp, blockRadius = 7.dp)
                SkeletonBlock(Modifier.padding(top = 7.dp), blockHeight = 9.dp, blockRadius = 4.dp)
                SkeletonBlock(Modifier.padding(top = 6.dp), blockWidth = 48.dp, blockHeight = 8.dp, blockRadius = 4.dp)
            }
        }
    }
}
