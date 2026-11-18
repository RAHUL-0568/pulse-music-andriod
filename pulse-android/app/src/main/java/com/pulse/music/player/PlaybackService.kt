package com.pulse.music.player

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.core.app.NotificationCompat
import android.widget.RemoteViews
import com.pulse.music.R
import android.app.PendingIntent
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    
    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    // Observe shared JamSessionManager for network playback sync
    private val jamSessionManager = com.pulse.music.ui.jam.JamSessionManager

    private var mediaSession: MediaSession? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    
    

    // Always the CURRENT player: crossfade swaps between two ExoPlayers, so capturing
    // one at start-up made Jam sync talk to the wrong one after the first crossfade.
    private val player: androidx.media3.exoplayer.ExoPlayer
        get() = musicPlayerManager.player

    override fun onCreate() {
        super.onCreate()
        // Observe Jam incoming sync events and apply to local player
        var ignoreNextSeek = false
        var ignoreNextPlayChange = false

        scope.launch {
            jamSessionManager.incomingSync.collect { sync ->
                sync?.let { (isPlaying, positionMs, _) ->
                    if (player.playWhenReady != isPlaying) {
                        ignoreNextPlayChange = true
                        player.playWhenReady = isPlaying
                    }
                    val diff = player.currentPosition - positionMs
                    if (Math.abs(diff) > 2000) {
                        // Hard seek if diff > 2 seconds
                        ignoreNextSeek = true
                        player.seekTo(positionMs)
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                    } else if (Math.abs(diff) > 100) {
                        // Drift correction: Speed up or slow down slightly
                        val speed = if (diff < 0) 1.02f else 0.98f
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                    } else {
                        // In sync, restore normal speed
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                    }
                }
            }
        }
        scope.launch {
            jamSessionManager.incomingSongWithSync.collect { data ->
                data?.let { (song, syncInfo) ->
                    val (isPlaying, positionMs) = syncInfo
                    if (jamSessionManager.isConnected.value) {
                        if (player.playWhenReady != isPlaying) {
                            ignoreNextPlayChange = true
                        }
                        if (Math.abs(player.currentPosition - positionMs) > 1000) {
                            ignoreNextSeek = true
                        }
                        musicPlayerManager.playSong(song, positionMs, isPlaying)
                    }
                }
            }
        }
        scope.launch {
            jamSessionManager.incomingSong.collect { song ->
                song?.let { 
                    if (musicPlayerManager.currentSong.value?.id != it.id) {
                        musicPlayerManager.playSong(it)
                    }
                }
            }
        }
        scope.launch {
            jamSessionManager.incomingQueueSync.collect { syncData ->
                if (syncData != null && jamSessionManager.isConnected.value) {
                    val queueJson = syncData.first
                    val syncIndex = syncData.second
                    val syncPos = syncData.third
                    
                    if (queueJson.isNotEmpty()) {
                        val songs = queueJson.map { item ->
                            com.pulse.music.domain.Song(
                                id = item.optString("id", item.optString("song_id", "")),
                                title = item.optString("title", "Unknown Title"),
                                artist = item.optString("artist", "Unknown Artist"),
                                albumArt = item.optString("albumArt", ""),
                                durationMs = item.optLong("durationMs", 0L),
                                source = item.optString("source", "")
                            )
                        }
                        musicPlayerManager.setQueueSync(songs, syncIndex, syncPos)
                    }
                }
            }
        }
        scope.launch {
            jamSessionManager.incomingQueueAppend.collect { list ->
                if (list != null && jamSessionManager.isConnected.value) {
                    val songs = list.map { item ->
                        com.pulse.music.domain.Song(
                            id = item.optString("id", item.optString("song_id", "")),
                            title = item.optString("title", "Unknown Title"),
                            artist = item.optString("artist", "Unknown Artist"),
                            albumArt = item.optString("albumArt", ""),
                            durationMs = item.optLong("durationMs", 0L),
                            source = item.optString("source", "")
                        )
                    }
                    musicPlayerManager.appendQueueSync(songs)
                }
            }
        }
        scope.launch {
            jamSessionManager.isConnected.collect { connected ->
                if (connected) {
                    // The queue you had before the Jam must not leak into it.
                    musicPlayerManager.trimQueueToCurrent()
                    // The creator starts the Jam from the song they are playing.
                    if (jamSessionManager.takeSeedFlag()) {
                        val current = musicPlayerManager.currentSong.value
                        if (current != null) {
                            jamSessionManager.broadcastQueue(listOf(current), 0, musicPlayerManager.currentPosition.value)
                            jamSessionManager.broadcastPlaySong(current)
                        }
                        jamSessionManager.broadcastShuffle(musicPlayerManager.shuffleEnabled.value)
                        jamSessionManager.broadcastRepeat(musicPlayerManager.repeatMode.value.name)
                    }
                }
            }
        }
        
        scope.launch {
            jamSessionManager.incomingShuffle.collect { enabled ->
                enabled?.let {
                    if (musicPlayerManager.shuffleEnabled.value != it) {
                        musicPlayerManager.setShuffleSync(it)
                    }
                }
            }
        }
        scope.launch {
            jamSessionManager.incomingRepeat.collect { mode ->
                mode?.let {
                    if (musicPlayerManager.repeatMode.value.name != it) {
                        musicPlayerManager.setRepeatSync(it)
                    }
                }
            }
        }
        // Listen to local playback changes and broadcast to Jam participants
        val jamListener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (ignoreNextPlayChange) {
                    ignoreNextPlayChange = false
                    return
                }
                val position = player.currentPosition
                jamSessionManager.broadcastPlayPause(playWhenReady, position)
            }
            
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    if (ignoreNextSeek) {
                        ignoreNextSeek = false
                        return
                    }
                    val position = newPosition.positionMs
                    jamSessionManager.broadcastPlayPause(player.playWhenReady, position)
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // If it transitioned locally (by user explicitly), broadcast it.
                // If it was an AUTO transition (track finished naturally):
                // - Only the HOST should broadcast the next song to keep everyone in sync.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Let both devices seamlessly auto-transition using their synced ExoPlayer queues.
                    // Do not broadcast play_song here, as that causes the guest to re-buffer and lag.
                    return
                }
                
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    // Ignore transitions caused by the network explicitly syncing the queue
                    return
                }
                
                // Fetch the actual song that the player just transitioned to
                val idx = player.currentMediaItemIndex
                val queue = musicPlayerManager.queue.value
                val newSong = if (idx >= 0 && idx < queue.size) queue[idx] else musicPlayerManager.currentSong.value
                
                if (newSong != null && jamSessionManager.isConnected.value) {
                    jamSessionManager.broadcastPlaySong(newSong)
                }
            }
        }
        var listenedPlayer: Player = player
        listenedPlayer.addListener(jamListener)
        val intent = Intent(this, com.pulse.music.ui.lockscreen.LockScreenActivity::class.java)
        val options = android.app.ActivityOptions.makeBasic()
        if (android.os.Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
            options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE, options.toBundle())

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(object : androidx.media3.common.util.BitmapLoader {
                override fun supportsMimeType(mimeType: String): Boolean = true
                override fun decodeBitmap(data: ByteArray): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> {
                    return com.google.common.util.concurrent.Futures.immediateFuture(android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size))
                }
                override fun loadBitmapFromMetadata(metadata: androidx.media3.common.MediaMetadata): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap>? {
                    val data = metadata.artworkData
                    if (data != null) {
                        return decodeBitmap(data)
                    }
                    return null
                }
                override fun loadBitmap(uri: android.net.Uri): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> {
                    val future = com.google.common.util.concurrent.SettableFuture.create<android.graphics.Bitmap>()
                    // Through Coil: shares the disk cache, so no second download of the cover.
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val bitmap = ArtworkLoader.load(this@PlaybackService, uri.toString(), 512)
                        if (bitmap != null) future.set(bitmap)
                        else future.setException(Exception("Could not load artwork"))
                    }
                    return future
                }
            })
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: androidx.media3.session.SessionCommand,
                    args: android.os.Bundle
                ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
                    )
                }
            })
            .build()
            
        scope.launch {
            musicPlayerManager.activePlayerFlow.collect { newPlayer ->
                if (newPlayer !== listenedPlayer) {
                    listenedPlayer.removeListener(jamListener)
                    newPlayer.addListener(jamListener)
                    listenedPlayer = newPlayer
                }
                mediaSession?.player = newPlayer
            }
        }
        
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Hard reset the player manager so the UI state matches the destroyed player
        musicPlayerManager.reset()
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
        
        super.onTaskRemoved(rootIntent)
        
        // Completely kill the process so the next launch is 100% fresh
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }
}
