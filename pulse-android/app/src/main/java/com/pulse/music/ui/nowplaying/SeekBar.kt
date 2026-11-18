package com.pulse.music.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Our own seek bar. The stock Slider was only 20 dp tall and sat inside a scrolling screen with a
 * swipe-to-change-song detector, so diagonal drags were taken by those instead. This one
 *  - has a 44 dp tall touch area,
 *  - takes the touch the moment the finger lands (and marks it used), so the scroll and the swipe detector
 *    ignore it,
 *  - reports tap and drag positions as a fraction 0..1.
 */
@Composable
fun SeekBar(
    value: Float,
    onChange: (Float) -> Unit,
    onEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(1f) }
    val shown = value.coerceIn(0f, 1f)
    val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { 7.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var fraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onChange(fraction)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onChange(fraction)
                            change.consume()
                        }
                    } while (change.pressed)
                    onEnd(fraction)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF3A3A3A))
        )
        // played part
        Box(
            modifier = Modifier
                .fillMaxWidth(shown)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
        // thumb
        Box(
            modifier = Modifier
                .offset { IntOffset((shown * widthPx - thumbPx).roundToInt(), 0) }
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
