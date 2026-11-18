package com.pulse.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.music.ui.theme.PulseCoral
import com.pulse.music.ui.theme.PulseRed

/**
 * Pulse's signature element: a heartbeat line with a soft travelling glow.
 * Used under hero headings so the brand shows up as design, not just as a logo.
 */
@Composable
fun PulseLine(
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "pulse_line")
    val sweep by transition.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val mid = h / 2f

            // Flat baseline with one heartbeat complex, then flat again
            val path = Path().apply {
                moveTo(0f, mid)
                lineTo(w * 0.34f, mid)
                lineTo(w * 0.39f, mid - h * 0.12f)
                lineTo(w * 0.43f, mid + h * 0.10f)
                lineTo(w * 0.48f, mid - h * 0.46f)
                lineTo(w * 0.54f, mid + h * 0.46f)
                lineTo(w * 0.59f, mid - h * 0.16f)
                lineTo(w * 0.63f, mid)
                lineTo(w, mid)
            }

            // faint static trace
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.10f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // glowing highlight that travels along the trace
            val center = if (animated) sweep * w else w * 0.5f
            val brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, PulseCoral, PulseRed, Color.Transparent),
                startX = center - w * 0.22f,
                endX = center + w * 0.22f
            )
            drawPath(
                path = path,
                brush = brush,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
