package com.pulse.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayingAnimation() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp).padding(horizontal = 4.dp)
    ) {
        val transition = rememberInfiniteTransition(label = "eq")
        EqBar(transition, 0)
        EqBar(transition, 1)
        EqBar(transition, 2)
        EqBar(transition, 3)
    }
}

@Composable
private fun EqBar(transition: InfiniteTransition, index: Int) {
    val height by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400 + (index * 100), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_height_$index"
    )
    Box(
        modifier = Modifier
            .width(3.dp)
            .fillMaxHeight(height)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                ambientColor = Color(0xFFF92839),
                spotColor = Color(0xFFF92839)
            )
            .background(
                color = Color(0xFFF92839),
                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
            )
    )
}
