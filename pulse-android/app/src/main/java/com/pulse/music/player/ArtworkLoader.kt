package com.pulse.music.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/** Loads cover art through Coil so every screen, the notification and the lock screen share one disk cache. */
object ArtworkLoader {
    suspend fun load(context: Context, url: String, sizePx: Int): Bitmap? {
        return try {
            val request = ImageRequest.Builder(context)
                .data(url.replace("http://", "https://"))
                .size(sizePx)
                .allowHardware(false) // we re-encode the pixels, so they must be readable
                .build()
            val result = context.imageLoader.execute(request)
            ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }
}
