package com.pulse.music.player

import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class CrossfadeManager {
    var crossfadeSecs: Int = 0
    var isCrossfading: Boolean = false
        private set
    var crossfadeStartTime: Long = 0L
        private set
    private var fadingPlayer: ExoPlayer? = null

    fun getCrossfadeMs(): Long = crossfadeSecs * 1000L

    fun shouldStartCrossfade(
        timeLeftMs: Long,
        currentPositionMs: Long,
        isLastTrack: Boolean,
        repeatMode: RepeatMode,
        sleepTimerMode: SleepTimerMode
    ): Boolean {
        if (crossfadeSecs <= 0) return false
        val ms = getCrossfadeMs()
        return ms > 0L &&
               timeLeftMs in 1..ms && 
               currentPositionMs > 15000L && 
               !isLastTrack && 
               !isCrossfading && 
               repeatMode != RepeatMode.ONE && 
               sleepTimerMode != SleepTimerMode.END_OF_TRACK
    }

    fun startCrossfade(current: ExoPlayer, next: ExoPlayer) {
        isCrossfading = true
        crossfadeStartTime = System.currentTimeMillis()
        fadingPlayer = current
        
        val fadingIdx = current.currentMediaItemIndex
        if (fadingIdx + 1 < current.mediaItemCount) {
            current.removeMediaItems(fadingIdx + 1, current.mediaItemCount)
        }
        
        // Dynamic setAudioAttributes causes the AudioSink to recreate and breaks timestamps (4x speed bug).
        // Let them steal focus or rely on MediaSession focus.
        
        next.volume = 0f
        next.play()
        android.util.Log.d("CrossfadeManager", "Secondary player play() called.")
    }

    fun updateCrossfade(activePlayer: ExoPlayer): Boolean {
        if (!isCrossfading || fadingPlayer == null) return false
        
        val elapsed = System.currentTimeMillis() - crossfadeStartTime
        val ms = getCrossfadeMs()
        
        if (elapsed < ms) {
            val progress = elapsed.toFloat() / ms.toFloat()
            // Equal power crossfade (cosine/sine curve)
            fadingPlayer!!.volume = cos(progress * PI / 2).toFloat()
            activePlayer.volume = sin(progress * PI / 2).toFloat()
            return true
        } else {
            endCrossfade(activePlayer)
            return false
        }
    }

    fun endCrossfade(activePlayer: ExoPlayer) {
        if (isCrossfading || fadingPlayer != null) {
            isCrossfading = false
            fadingPlayer?.stop()
            fadingPlayer?.clearMediaItems()
            fadingPlayer = null
            activePlayer.volume = 1f
            
            // Audio focus is restored naturally by ExoPlayer when the old player stops.
            
            android.util.Log.d("CrossfadeManager", "Player swap completed.")
        }
    }

    fun abortCrossfade(activePlayer: ExoPlayer) {
        endCrossfade(activePlayer)
    }
}
