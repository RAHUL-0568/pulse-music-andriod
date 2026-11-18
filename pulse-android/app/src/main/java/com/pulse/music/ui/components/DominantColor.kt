package com.pulse.music.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A rich, darkened colour taken from the artwork, so the mini player follows the song
 * that is playing. Falls back to [fallback] until the art loads (or if it can't).
 */
@Composable
fun rememberDominantColor(imageUrl: String?, fallback: Color = Color(0xFF1E1E27)): Color {
    val context = LocalContext.current
    var target by remember(imageUrl) { mutableStateOf(fallback) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrEmpty()) {
            target = fallback
            return@LaunchedEffect
        }
        val rgb: Int? = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(160)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let {
                    val palette = Palette.from(it).maximumColorCount(16).generate()
                    (palette.dominantSwatch ?: palette.mutedSwatch ?: palette.vibrantSwatch)?.rgb
                }
            } catch (e: Exception) {
                null
            }
        }
        target = rgb?.let { deepen(it) } ?: fallback
    }

    return animateColorAsState(targetValue = target, animationSpec = tween(500), label = "mini_player_color").value
}

/** Keeps the hue, but makes it dark enough for white text, like Spotify's mini player. */
private fun deepen(rgb: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rgb, hsv)
    hsv[1] = hsv[1].coerceAtMost(0.80f)
    hsv[2] = (hsv[2] * 0.55f).coerceIn(0.20f, 0.46f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
