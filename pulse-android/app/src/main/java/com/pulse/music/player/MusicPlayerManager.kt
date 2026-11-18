package com.pulse.music.player

import android.content.Context
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pulse.music.domain.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelChildren
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.pulse.music.data.repository.dataStore
import kotlinx.coroutines.flow.first

enum class RepeatMode { OFF, ONE, ALL }

enum class SleepTimerMode(val minutes: Int?, val label: String) {
    OFF(null, "Off"),
    MIN_5(5, "5 minutes"),
    MIN_10(10, "10 minutes"),
    MIN_15(15, "15 minutes"),
    MIN_30(30, "30 minutes"),
    MIN_45(45, "45 minutes"),
    MIN_60(60, "60 minutes"),
    END_OF_TRACK(null, "End of current track")
}

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onlineRepo: com.pulse.music.data.repository.OnlineMusicRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Played songs are kept on disk so replaying them costs no data.
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val audioCache by lazy {
        val cacheSize: Long = 1024L * 1024L * 1024L // 1 GB
        androidx.media3.datasource.cache.SimpleCache(
            java.io.File(context.cacheDir, "audio_cache"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(cacheSize),
            androidx.media3.database.StandaloneDatabaseProvider(context)
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val sharedDataSourceFactory by lazy {
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(audioCache)
            .setUpstreamDataSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context))
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        androidx.media3.datasource.ResolvingDataSource.Factory(
            cacheDataSourceFactory,
            object : androidx.media3.datasource.ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme == "pulse" && uri.host == "resolve") {
                        val songId = uri.lastPathSegment ?: return dataSpec
                        // One cache entry per song AND quality. Putting a 96 kbps and a 320 kbps copy of the
                        // same song under one key mixed two different files, which broke seeking.
                        val fullyCachedKey = listOf("320", "160", "96", "x")
                            .map { "song_${songId}_$it" }
                            .firstOrNull { audioCache.isCached(it, 0, androidx.media3.common.C.LENGTH_UNSET.toLong()) }

                        // Already fully on disk: skip the lookup (a network call) and the download.
                        if (fullyCachedKey != null) {
                            return dataSpec.buildUpon()
                                .setUri(android.net.Uri.parse("https://cached.invalid/$songId"))
                                .setKey(fullyCachedKey)
                                .build()
                        }

                        val title = uri.getQueryParameter("title") ?: ""
                        val artist = uri.getQueryParameter("artist") ?: ""

                        // This runs on ExoPlayer's loader thread, not the main thread.
                        val dummySong = Song(id = songId, title = title, artist = artist, album = "", albumArt = "", durationMs = null, source = "online")
                        // Skipping fast makes ExoPlayer cancel this load: the interrupt must surface as a
                        // normal IO cancel, not as a crash that leaves the player stuck in an error state.
                        val resolvedUrl = try {
                            kotlinx.coroutines.runBlocking { resolveSongSource(dummySong) }
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw java.io.InterruptedIOException("Song lookup cancelled")
                        }
                        // No stream found: fail cleanly (the player retries / skips on its own)
                        // instead of passing a pulse:// address that no data source understands.
                        if (resolvedUrl == "online" || resolvedUrl.startsWith("pulse://")) {
                            throw java.io.IOException("Could not find a stream for this song")
                        }
                        run {
                            return dataSpec.buildUpon()
                                .setUri(android.net.Uri.parse(resolvedUrl))
                                .setKey("song_${songId}_${bitrateTag(resolvedUrl)}")
                                .build()
                        }
                    }
                    return dataSpec
                }
            }
        )
    }

    /** "…_320.mp4" -> "320". Songs whose address doesn't show a bitrate get "x". */
    private fun bitrateTag(url: String): String {
        return Regex("_(320|160|96)(?:_p)?\\.").find(url)?.groupValues?.get(1) ?: "x"
    }

    /** The High quality switch decides, on Wi-Fi and mobile data alike. Off = data saver (low quality) everywhere. */
    private suspend fun wantHighQuality(): Boolean {
        return try {
            context.dataStore.data.first()[booleanPreferencesKey("high_quality")] ?: true
        } catch (e: Exception) { true }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createExoPlayer(): ExoPlayer {
        val audioAttributesBuilder = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            
        if (android.os.Build.VERSION.SDK_INT == 33) {
            audioAttributesBuilder.setSpatializationBehavior(androidx.media3.common.C.SPATIALIZATION_BEHAVIOR_NEVER)
        }
        val audioAttributes = audioAttributesBuilder.build()
            
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
        if (android.os.Build.VERSION.SDK_INT == 33) {
            renderersFactory.setEnableAudioFloatOutput(false)
            renderersFactory.setEnableAudioTrackPlaybackParams(false)
            renderersFactory.setEnableDecoderFallback(true)
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, false) // Manage audio focus manually to prevent ExoPlayers from pausing each other
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).setDataSourceFactory(sharedDataSourceFactory))
            .build().also { exo ->
            exo.addListener(createPlayerListener(exo))
        }
    }

    private val player1: ExoPlayer by lazy { createExoPlayer() }
    private val player2: ExoPlayer by lazy { createExoPlayer() }
    
    private val _activePlayerFlow = MutableStateFlow<ExoPlayer>(player1)
    val activePlayerFlow: StateFlow<ExoPlayer> = _activePlayerFlow.asStateFlow()
    
    val player: ExoPlayer get() = _activePlayerFlow.value
    private var mediaControllerFuture: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null
    private val crossfadeManager = CrossfadeManager()

    /** Gapless playback switch: songs of the same album flow straight into each other, with no crossfade. */
    @Volatile private var gaplessEnabled = false

    // Legacy bridge properties to avoid breaking other parts of the file temporarily if any remain
    private val crossfadeSecs: Int get() = crossfadeManager.crossfadeSecs
    private val isCrossfading: Boolean get() = crossfadeManager.isCrossfading

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS || focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            player.pause()
        } else if (focusChange == android.media.AudioManager.AUDIOFOCUS_GAIN) {
            player.play()
        }
    }

    private val focusRequest = if (android.os.Build.VERSION.SDK_INT >= 26) {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
    } else null

    init {
        val sessionToken = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))
        mediaControllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
        
        var savedCrossfadeSecs = 0
        
        scope.launch {
            val cfKey = intPreferencesKey("crossfade")
            val gaplessKey = booleanPreferencesKey("gapless")
            context.dataStore.data.collect { prefs ->
                savedCrossfadeSecs = prefs[cfKey] ?: 0
                // Only apply crossfade if NOT in a jam session
                if (!com.pulse.music.ui.jam.JamSessionManager.isConnected.value) {
                    crossfadeManager.crossfadeSecs = savedCrossfadeSecs
                }
                gaplessEnabled = prefs[gaplessKey] ?: false
                player1.skipSilenceEnabled = false
                player2.skipSilenceEnabled = false
                // Turning crossfade on while a song is already playing used to do nothing until the NEXT
                // song started, because the next track was only pre-loaded at the start of a song.
                if (crossfadeManager.crossfadeSecs > 0 && player.mediaItemCount > 0) preBufferAlternatePlayer()
            }
        }
        
        // Auto-disable crossfade during jam sessions to keep playback in sync
        scope.launch {
            com.pulse.music.ui.jam.JamSessionManager.isConnected.collect { inJam ->
                if (inJam) {
                    crossfadeManager.crossfadeSecs = 0
                } else {
                    crossfadeManager.crossfadeSecs = savedCrossfadeSecs
                }
            }
        }
    }

    // ── Queue ─────────────────────────────────────────────────────────────

    private var _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private var _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val resolvedUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    // ── Exposed state ────────────────────────────────────────────────────

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** True while a song is being looked up or buffered: the UI shows a small spinner instead of an error. */
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _sleepTimerMode = MutableStateFlow(SleepTimerMode.OFF)
    val sleepTimerMode: StateFlow<SleepTimerMode> = _sleepTimerMode.asStateFlow()

    private val _sleepTimerTimeLeft = MutableStateFlow(0L)
    val sleepTimerTimeLeft: StateFlow<Long> = _sleepTimerTimeLeft.asStateFlow()

    private var sleepTimerJob: Job? = null

    // ── Volume ───────────────────────────────────────────────────────────

    val maxVolume: Int get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun getVolumeFraction(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = maxVolume
        return if (max > 0) current.toFloat() / max.toFloat() else 0f
    }

    fun setVolumeFraction(fraction: Float) {
        val max = maxVolume
        val newVol = (fraction * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
    }

    // ── Position polling ─────────────────────────────────────────────────

    private var positionPollingJob: kotlinx.coroutines.Job? = null
    private var artworkJob: kotlinx.coroutines.Job? = null

    private fun updateArtworkData(song: com.pulse.music.domain.Song, index: Int, playerToUpdate: androidx.media3.exoplayer.ExoPlayer) {
        artworkJob?.cancel()
        artworkJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (song.albumArt == null) return@launch
                // Goes through Coil, so it shares the on-disk image cache with the UI
                // instead of downloading the same cover again.
                val bitmap = ArtworkLoader.load(context, song.albumArt, 400)

                if (bitmap != null) {
                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 400, 400, true)
                    val stream = java.io.ByteArrayOutputStream()
                    scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
                    val artworkData = stream.toByteArray()
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        try {
                            if (index < playerToUpdate.mediaItemCount) {
                                val currentItem = playerToUpdate.getMediaItemAt(index)
                                if (currentItem.mediaId == song.id) {
                                    val newMetadata = currentItem.mediaMetadata.buildUpon()
                                        .setArtworkData(artworkData, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                        .build()
                                    val newItem = currentItem.buildUpon().setMediaMetadata(newMetadata).build()
                                    playerToUpdate.replaceMediaItem(index, newItem)
                                }
                            }
                        } catch(e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerManager", "Artwork download failed", e)
            }
        }
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                _currentPosition.value = player.currentPosition

                // Some streams only report their true length after playback starts. A stale duration (0)
                // made the seek bar unusable on those songs, so keep it current.
                val liveDuration = player.duration
                if (liveDuration != androidx.media3.common.C.TIME_UNSET && liveDuration > 0 && liveDuration != _duration.value) {
                    _duration.value = liveDuration
                }


                // Crossfade Logic
                try {
                    if (crossfadeSecs > 0) {
                        val duration = player.duration
                        if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) {
                            val timeLeft = duration - player.currentPosition
                            var actualCrossfadeMs = crossfadeSecs * 1000L
                            
                            // Gapless ON: two songs from the same album (a live set, a continuous mix) play straight
                            // into each other instead of crossfading.
                            var skipForGapless = false
                            if (gaplessEnabled) {
                                val currentIdx = player.currentMediaItemIndex
                                if (currentIdx >= 0 && currentIdx < _queue.value.size - 1) {
                                    val currentSong = _queue.value[currentIdx]
                                    val nextSong = _queue.value[currentIdx + 1]
                                    if (!currentSong.album.isNullOrEmpty() && currentSong.album == nextSong.album) {
                                        skipForGapless = true
                                    }
                                }
                            }

                            // Debug log every 5 seconds left
                            if (timeLeft in 1..20000L && timeLeft % 1000L < 50L) {
                                android.util.Log.d("Crossfade", "Tick: timeLeft=$timeLeft, crossfadeMs=$actualCrossfadeMs, queueIdx=${player.currentMediaItemIndex}")
                            }

                            if (!skipForGapless && crossfadeManager.shouldStartCrossfade(timeLeft, player.currentPosition, player.currentMediaItemIndex >= _queue.value.size - 1, _repeatMode.value, _sleepTimerMode.value)) {
                                val nextPlayer = if (player === player1) player2 else player1
                                if (nextPlayer.mediaItemCount > 0) {
                                    if (nextPlayer.playbackState == androidx.media3.common.Player.STATE_READY) {
                                        android.util.Log.d("Crossfade", "Secondary player is READY. Starting crossfade.")
                                        crossfadeManager.startCrossfade(player, nextPlayer)
                                        
                                        // Spotify-style: Swap UI to the next track immediately
                                        _activePlayerFlow.value = nextPlayer
                                        val nextIdx = nextPlayer.currentMediaItemIndex
                                        if (nextIdx >= 0 && nextIdx < _queue.value.size) {
                                            _currentIndex.value = nextIdx
                                            val nextSong = _queue.value[nextIdx]
                                            _currentSong.value = nextSong
                                            updateArtworkData(nextSong, nextIdx, nextPlayer)
                                            if (!nextSong.id.startsWith("local_")) {
                                                saveRecentPlay(nextSong)
                                            }
                                        }
                                    } else {
                                        android.util.Log.w("Crossfade", "Secondary player NOT READY. Fallback: Continuing current track.")
                                    }
                                }
                            }
                        }
                    }
                } catch(e: Exception) {}
                
                if (crossfadeManager.isCrossfading) {
                    val completed = !crossfadeManager.updateCrossfade(player)
                    if (completed) {
                        preBufferAlternatePlayer()
                    }
                }
                
                delay(16L) // ~60Hz update rate for smooth volume ramping
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    private fun preBufferAlternatePlayer() {
        if (crossfadeSecs <= 0) return
        val currentIdx = player.currentMediaItemIndex
        val nextIdx = currentIdx + 1
        val alternatePlayer = if (player === player1) player2 else player1
        if (nextIdx >= 0 && nextIdx < _queue.value.size) {
            alternatePlayer.setMediaItems(_queue.value.map { buildMediaItem(it) })
            alternatePlayer.seekTo(nextIdx, 0)
            alternatePlayer.prepare()
            alternatePlayer.pause()
        } else {
            alternatePlayer.clearMediaItems()
        }
    }

    fun reset() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        
        player1.pause()
        player1.stop()
        player1.clearMediaItems()
        player2.pause()
        player2.stop()
        player2.clearMediaItems()
        crossfadeManager.abortCrossfade(player)
        stopPositionPolling()
    }

    /** The API lists streams from lowest to highest bitrate (12, 48, 96, 160, 320 kbps). */
    private fun pickDownloadUrl(urls: org.json.JSONArray, highQuality: Boolean): org.json.JSONObject {
        if (highQuality || urls.length() <= 2) return urls.getJSONObject(urls.length() - 1)
        return urls.getJSONObject((urls.length() - 3).coerceAtLeast(0)) // ~96 kbps
    }

    private suspend fun resolveSongSource(song: Song): String {
        if (song.id.startsWith("local_") || song.source.contains("saavncdn.com") || song.source.endsWith(".mp3") || song.source.endsWith(".mp4") || song.source.endsWith(".m4a")) {
            return song.source
        }
        // The cache is per quality, so flipping High quality takes effect on the next song.
        val cacheKey = "${song.id}|${if (wantHighQuality()) "hq" else "lq"}"
        val cached = resolvedUrlCache[cacheKey]
        if (cached != null) return cached

        val resolved = withContext(Dispatchers.IO) {
            val highQ = wantHighQuality()
            try {
                val url = "https://music-api.albatross0071.workers.dev/api/songs/${song.id}"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                
                val json = org.json.JSONObject(text)
                if (json.getBoolean("success")) {
                    val dataArray = json.getJSONArray("data")
                    if (dataArray.length() > 0) {
                        val downloadUrls = dataArray.getJSONObject(0).getJSONArray("downloadUrl")
                        val bestUrlObj = pickDownloadUrl(downloadUrls, highQ)
                        val u = bestUrlObj.getString("url")
                        if (u.isNotEmpty()) return@withContext u
                    }
                }
            } catch (e: Exception) {
                // Ignore and fallback
            }
            
            // Fallback 1: Direct JioSaavn API decryption
            try {
                val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=${song.id}&_format=json&_marker=0&ctx=web6dot0"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                
                val json = org.json.JSONObject(text)
                var songObj: org.json.JSONObject? = null
                if (json.has(song.id)) {
                    songObj = json.getJSONObject(song.id)
                } else if (json.has("songs")) {
                    val songsArr = json.getJSONArray("songs")
                    if (songsArr.length() > 0) {
                        songObj = songsArr.getJSONObject(0)
                    }
                }
                
                if (songObj != null) {
                    if (songObj.has("encrypted_media_url")) {
                        try {
                            val encUrl = songObj.getString("encrypted_media_url")
                            val key = "38346591".toByteArray()
                            val secretKeySpec = javax.crypto.spec.SecretKeySpec(key, "DES")
                            val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
                            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKeySpec)
                            val decoded = android.util.Base64.decode(encUrl.trim(), android.util.Base64.DEFAULT)
                            val decrypted = cipher.doFinal(decoded)
                            var decryptedUrl = String(decrypted)
                            
                            val kbps320 = songObj.optBoolean("320kbps", false) || songObj.optString("320kbps") == "true"
                            if (kbps320 && highQ) {
                                decryptedUrl = decryptedUrl.replace("_96.mp4", "_320.mp4")
                            } else {
                                decryptedUrl = decryptedUrl.replace("_96.mp4", "_160.mp4")
                            }
                            
                            if (decryptedUrl.isNotEmpty()) {
                                return@withContext decryptedUrl
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MusicPlayerManager", "Decryption failed", e)
                        }
                    }
                    
                    // Fallback to media_preview_url hack
                    if (songObj.has("media_preview_url")) {
                        val preview = songObj.getString("media_preview_url")
                        val kbps320 = songObj.optBoolean("320kbps", false) || songObj.optString("320kbps") == "true"
                        var full = preview.replace("preview.saavncdn.com", "aac.saavncdn.com")
                        if (kbps320 && highQ) {
                            full = full.replace("_96_p.mp4", "_320.mp4")
                        } else {
                            full = full.replace("_96_p.mp4", "_160.mp4")
                        }
                        if (full.isNotEmpty()) {
                            return@withContext full
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerManager", "JioSaavn decryption failed: ", e)
                e.printStackTrace()
            }
            
            // Fallback 2: saavn.dev API
            try {
                val url = "https://saavn.dev/api/songs?id=${song.id}"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                
                val json = org.json.JSONObject(text)
                if (json.getBoolean("success")) {
                    val dataArray = json.getJSONArray("data")
                    if (dataArray.length() > 0) {
                        val downloadUrls = dataArray.getJSONObject(0).getJSONArray("downloadUrl")
                        val bestUrlObj = pickDownloadUrl(downloadUrls, highQ)
                        val u = bestUrlObj.getString("url")
                        if (u.isNotEmpty()) return@withContext u
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerManager", "Saavn.dev failed: ", e)
                e.printStackTrace()
            }
            
            // Fallback 2: search
            try {
                val searchUrl = "${com.pulse.music.BuildConfig.API_BASE_URL}api/v1/search?q=${java.net.URLEncoder.encode(song.title + " " + song.artist, "UTF-8")}"
                val searchConn = java.net.URL(searchUrl).openConnection() as java.net.HttpURLConnection
                searchConn.connectTimeout = 5000
                searchConn.readTimeout = 5000
                val searchText = searchConn.inputStream.bufferedReader().use { it.readText() }
                searchConn.disconnect()
                val searchJson = org.json.JSONObject(searchText)
                val songsArray = searchJson.getJSONArray("songs")
                if (songsArray.length() > 0) {
                    val searchId = songsArray.getJSONObject(0).getString("id")
                    // Recursively resolve the real ID
                    if (searchId != song.id && searchId.isNotEmpty()) {
                        return@withContext resolveSongSource(song.copy(id = searchId))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerManager", "Search fallback failed: ", e)
                e.printStackTrace()
            }
            return@withContext song.source
        }
        resolvedUrlCache[cacheKey] = resolved
        return resolved
    }

    // ── Player.Listener ──────────────────────────────────────────────────

    private fun createPlayerListener(targetPlayer: ExoPlayer) = object : Player.Listener {

        override fun onIsPlayingChanged(playing: Boolean) {
            if (targetPlayer !== player) return
            _isPlaying.value = playing
            if (playing) startPositionPolling() else stopPositionPolling()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                if (android.os.Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
                    audioManager.requestAudioFocus(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(audioFocusChangeListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN)
                }
            }
        }
        
        // The song we already retried once after an error (so we never loop forever)
        private var errorRetrySongId: String? = null

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            if (targetPlayer !== player) return
            super.onPlayerError(error)
            android.util.Log.e("MusicPlayerManager", "Playback error: ${error.errorCodeName}", error)

            // After an error ExoPlayer goes idle and plays nothing until we prepare it again.
            // This is why one failed lookup while skipping fast stopped every following song.
            val songId = player.currentMediaItem?.mediaId
            if (songId != null) { resolvedUrlCache.remove("$songId|hq"); resolvedUrlCache.remove("$songId|lq") } // look the stream up fresh

            if (songId != null && errorRetrySongId != songId) {
                errorRetrySongId = songId
                player.prepare() // retry this song once
                return
            }
            errorRetrySongId = null
            // still failing: move on, in the direction the user was going
            if (lastNavDirection < 0 && player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                player.prepare()
                player.play()
                return
            }
            if (lastNavDirection >= 0 && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
                return
            }
            _isBuffering.value = false
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Couldn't play this song. Check your connection and try again.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (targetPlayer !== player) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) lastNavDirection = 1 // song ended by itself: we're moving forward
            // new song: forget the old length right away (the real one arrives via onTimelineChanged / polling)
            val newDuration = player.duration
            _duration.value = if (newDuration != androidx.media3.common.C.TIME_UNSET && newDuration > 0) newDuration else 0L
            if (isCrossfading) return // Ignore transitions during crossfade, we already swapped active player

            if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
                player.pause()
                _sleepTimerMode.value = SleepTimerMode.OFF
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Sleep timer ended. Playback paused.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            val idx = player.currentMediaItemIndex
            if (idx >= 0 && idx < _queue.value.size) {
                _currentIndex.value = idx
                val current = _queue.value[idx]
                _currentSong.value = current
                
                if (mediaItem?.mediaMetadata?.artworkData == null) {
                    updateArtworkData(current, idx, player)
                }
                
                // Track recent song
                if (!current.id.startsWith("local_")) {
                    saveRecentPlay(current)
                }
                
                // Pre-buffer the alternate player only when crossfade is on; otherwise it
                // downloads the next song for nothing (wasted data if you skip).
                val nextIdx = idx + 1
                if (crossfadeSecs > 0 && nextIdx < _queue.value.size) {
                    val alternatePlayer = if (player === player1) player2 else player1
                    alternatePlayer.setMediaItems(_queue.value.map { buildMediaItem(it) })
                    alternatePlayer.seekTo(nextIdx, 0)
                    alternatePlayer.prepare()
                    alternatePlayer.pause()
                }

                // Top the queue up with recommendations only when it is about to run out
                // (it used to fetch on every single track), and only if Repeat is OFF.
                val songsLeft = _queue.value.size - idx - 1
                if (songsLeft <= 2 && _repeatMode.value == RepeatMode.OFF && !current.id.startsWith("local_") && !com.pulse.music.ui.jam.JamSessionManager.isConnected.value) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val recs = onlineRepo.getRecommendations(current.artist, current.title).getOrNull()
                            if (!recs.isNullOrEmpty()) {
                                // Filter out songs already in the queue and take up to 5
                                val existingTitles = _queue.value.map { it.title.trim().lowercase() + it.artist.trim().lowercase() }.toSet()
                                val newSongs = recs.filter { it.title.trim().lowercase() + it.artist.trim().lowercase() !in existingTitles }
                                    .distinctBy { it.title.trim().lowercase() + it.artist.trim().lowercase() }
                                    .take(5)
                                
                                if (newSongs.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        val q = _queue.value.toMutableList()
                                        q.addAll(newSongs)
                                        _queue.value = q
                                        
                                        // Add to ExoPlayer
                                        for (song in newSongs) {
                                            player.addMediaItem(buildMediaItem(song))
                                        }
                                        preBufferAlternatePlayer()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        // A new song often starts WITHOUT a "ready" event, so the song length used to stay at the previous
        // song's value. Refresh it whenever the player learns the real length.
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (targetPlayer !== player) return
            val d = player.duration
            if (d != androidx.media3.common.C.TIME_UNSET && d > 0) _duration.value = d
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (targetPlayer === player) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
            }
            if (playbackState == Player.STATE_READY) {
                _duration.value = player.duration.coerceAtLeast(0L)
                errorRetrySongId = null // this song loaded fine, so a future error gets a fresh retry
            } else if (playbackState == Player.STATE_ENDED) {
                if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
                    _sleepTimerMode.value = SleepTimerMode.OFF
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Sleep timer ended. Playback paused.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────

    /** Play a song and set entire list as queue. */
    fun playSongFromList(song: Song, allSongs: List<Song>) {
        abortCrossfade()
        saveRecentPlay(song)
        playSongJob?.cancel()
        lastNavDirection = 1
        _isBuffering.value = true // shows the spinner while the stream address is looked up
        playSongJob = scope.launch {
            _queue.value = allSongs
            val idx = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            _currentIndex.value = idx
            
            val resolvedSource = resolveSongSource(song)
            if (!isActive) return@launch
            val resolvedSong = song.copy(source = resolvedSource)
            
            val updatedQueue = allSongs.toMutableList()
            updatedQueue[idx] = resolvedSong
            _queue.value = updatedQueue
            _currentSong.value = resolvedSong
            
            val mediaItems = updatedQueue.map { buildMediaItem(it) }
            player.setMediaItems(mediaItems, idx, 0)
            player.prepare()
            player.play()
            preBufferAlternatePlayer()
            com.pulse.music.ui.jam.JamSessionManager.broadcastQueue(_queue.value, idx, 0)
        }
    }

    /** Play a single song. */
    private var playSongJob: kotlinx.coroutines.Job? = null

    @OptIn(UnstableApi::class)
    fun playSong(song: Song, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        abortCrossfade()
        saveRecentPlay(song)
        playSongJob?.cancel()
        playSongJob = scope.launch {
            // Instant play/pause/seek sync if we are already playing this exact song!
            val currentIdx = _currentIndex.value
            if (currentIdx >= 0 && _currentSong.value?.id == song.id) {
                val diff = kotlin.math.abs(player.currentPosition - startPositionMs)
                if (diff > 1500) {
                    player.seekTo(currentIdx, startPositionMs)
                }
                if (player.playWhenReady != playWhenReady) {
                    player.playWhenReady = playWhenReady
                }
                return@launch
            }
            
            val resolvedSource = resolveSongSource(song)
            if (!isActive) return@launch
            val resolvedSong = song.copy(source = resolvedSource)
            
            val idx = _queue.value.indexOfFirst { it.id == song.id }
            if (idx >= 0) {
                val q = _queue.value.toMutableList()
                q[idx] = resolvedSong
                _queue.value = q
                _currentIndex.value = idx
                _currentSong.value = resolvedSong
                
                player.replaceMediaItem(idx, buildMediaItem(resolvedSong))
                player.seekTo(idx, startPositionMs)
                player.playWhenReady = playWhenReady
                player.prepare()
                if (playWhenReady) player.play()
                preBufferAlternatePlayer()
            } else {
                val q = _queue.value.toMutableList()
                q.add(resolvedSong)
                _queue.value = q
                
                val newIdx = q.size - 1
                _currentIndex.value = newIdx
                _currentSong.value = resolvedSong
                
                player.addMediaItem(buildMediaItem(resolvedSong))
                player.seekTo(newIdx, startPositionMs)
                player.playWhenReady = playWhenReady
                player.prepare()
                if (playWhenReady) player.play()
                preBufferAlternatePlayer()
                com.pulse.music.ui.jam.JamSessionManager.broadcastQueue(_queue.value, newIdx, startPositionMs)
            }
        }
    }

    fun setQueueSync(songs: List<Song>, syncIndex: Int, syncPositionMs: Long) {
        _queue.value = songs
        val mediaItems = songs.map { buildMediaItem(it) }
        
        // Coerce index to be safe
        val safeIndex = syncIndex.coerceIn(0, maxOf(0, mediaItems.size - 1))
        val currentPlayWhenReady = player.playWhenReady
        
        player.setMediaItems(mediaItems, safeIndex, syncPositionMs)
        player.playWhenReady = currentPlayWhenReady
        player.prepare()
        preBufferAlternatePlayer()
        
        // Ensure UI state matches the new queue immediately
        if (safeIndex >= 0 && safeIndex < songs.size) {
            _currentIndex.value = safeIndex
            _currentSong.value = songs[safeIndex]
        }
    }

    /**
     * A Jam started or joined: drop the queue you had before it, keep only the song that is playing.
     * Without this the old list leaked into the Jam the next time anything was played or added.
     * The current song keeps playing, uninterrupted.
     */
    suspend fun trimQueueToCurrent() = withContext(Dispatchers.Main) {
        val idx = player.currentMediaItemIndex
        val size = player.mediaItemCount
        if (idx < 0 || size == 0) return@withContext
        if (idx + 1 < size) player.removeMediaItems(idx + 1, size)
        if (idx > 0) player.removeMediaItems(0, idx)
        val current = _currentSong.value
        _queue.value = if (current != null) listOf(current) else emptyList()
        _currentIndex.value = 0
        preBufferAlternatePlayer()
    }

    /**
     * Songs added to a Jam by anyone: add the ones we don't have to the END of the queue.
     * The playing song is not touched, so it neither restarts nor jumps.
     */
    fun appendQueueSync(songs: List<Song>) {
        scope.launch(Dispatchers.Main) {
            val known = _queue.value.map { it.id }.toMutableSet()
            val fresh = songs.filter { it.id.isNotEmpty() && known.add(it.id) }
            if (fresh.isEmpty()) return@launch
            _queue.value = _queue.value + fresh
            player.addMediaItems(fresh.map { buildMediaItem(it) })
            preBufferAlternatePlayer()
        }
    }

    fun enqueue(song: Song) {
        scope.launch {
            val resolvedSong = song.copy(source = resolveSongSource(song))
            withContext(Dispatchers.Main) {
                val q = _queue.value.toMutableList()
                q.add(resolvedSong)
                _queue.value = q
                player.addMediaItem(buildMediaItem(resolvedSong))
                preBufferAlternatePlayer()
                if (!player.isPlaying && _queue.value.size == 1) {
                    _currentIndex.value = 0
                    _currentSong.value = resolvedSong
                    player.prepare()
                    player.play()
                }
                com.pulse.music.ui.jam.JamSessionManager.broadcastQueue(_queue.value, _currentIndex.value.coerceAtLeast(0), player.currentPosition)
            }
        }
    }

    private fun buildMediaItem(song: Song): MediaItem {
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(if (song.albumArt != null) android.net.Uri.parse(song.albumArt) else null)
            .build()
            
        val uriStr = if (!song.id.startsWith("local_") && !song.source.contains("saavncdn.com") && !song.source.contains("youtube") && !song.source.endsWith(".mp3") && !song.source.endsWith(".m4a")) {
            "pulse://resolve/${song.id}?title=${java.net.URLEncoder.encode(song.title, "UTF-8")}&artist=${java.net.URLEncoder.encode(song.artist, "UTF-8")}"
        } else {
            song.source
        }
            
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(android.net.Uri.parse(uriStr))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun abortCrossfade() {
        if (crossfadeManager.isCrossfading) {
            crossfadeManager.abortCrossfade(player)
        }
    }

    /** Toggle between play and pause. */
    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            if (isCrossfading) {
                val alternate = if (player === player1) player2 else player1
                alternate.pause()
            }
        } else {
            player.play()
            if (isCrossfading) {
                val alternate = if (player === player1) player2 else player1
                alternate.play()
            }
        }
    }

    /** Seek to [positionMs] within the current track. */
    fun seekTo(positionMs: Long) {
        abortCrossfade()
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    /** Seek by 0..1 using the player's LIVE song length (the stored one can lag behind after a song change). */
    fun seekToFraction(fraction: Float) {
        val live = player.duration
        val liveOk = live != androidx.media3.common.C.TIME_UNSET && live > 0
        val total = if (liveOk) live else _duration.value
        if (total <= 0) return
        if (liveOk) _duration.value = live
        seekTo((total * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Skip to the next track in the queue. */
    // +1 = the user last moved forward, -1 = backward. If a song then fails to load, we keep
    // going the same way (it used to always jump forward, even after you swiped back).
    private var lastNavDirection = 1

    fun skipNext() {
        abortCrossfade()
        lastNavDirection = 1
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    /** Skip to the previous track, or restart if > 3s in. */
    fun skipPrevious() {
        abortCrossfade()
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            _currentPosition.value = 0
            return
        }
        lastNavDirection = -1
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    /** For swipe gestures: always go to the previous song (never just restart the current one). */
    fun skipToPreviousTrack() {
        abortCrossfade()
        lastNavDirection = -1
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
            _currentPosition.value = 0
        }
    }

    /** Toggle shuffle on/off. */
    fun toggleShuffle() {
        val newState = !_shuffleEnabled.value
        _shuffleEnabled.value = newState
        
        if (com.pulse.music.ui.jam.JamSessionManager.isConnected.value) {
            player.shuffleModeEnabled = false
            if (newState) {
                // Physically shuffle the queue for Jam session
                val q = _queue.value.toMutableList()
                if (q.size > 1 && _currentIndex.value >= 0) {
                    val current = q.removeAt(_currentIndex.value)
                    q.shuffle()
                    q.add(0, current)
                    _queue.value = q
                    _currentIndex.value = 0
                    
                    val mediaItems = q.map { buildMediaItem(it) }
                    player.setMediaItems(mediaItems, 0, player.currentPosition)
                    com.pulse.music.ui.jam.JamSessionManager.broadcastQueue(q, 0, player.currentPosition)
                }
            }
            com.pulse.music.ui.jam.JamSessionManager.broadcastShuffle(newState)
        } else {
            player.shuffleModeEnabled = newState
        }
    }
    
    fun setShuffleEnabled(enabled: Boolean) {
        if (_shuffleEnabled.value != enabled) {
            _shuffleEnabled.value = enabled
            if (com.pulse.music.ui.jam.JamSessionManager.isConnected.value) {
                player.shuffleModeEnabled = false
                com.pulse.music.ui.jam.JamSessionManager.broadcastShuffle(enabled)
            } else {
                player.shuffleModeEnabled = enabled
            }
        }
    }
    
    fun setShuffleSync(enabled: Boolean) {
        if (_shuffleEnabled.value != enabled) {
            _shuffleEnabled.value = enabled
            player.shuffleModeEnabled = false // Always false in jam sessions
        }
    }

    private fun syncRepeatMode() {
        val mode = when (_repeatMode.value) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        player1.repeatMode = mode
        player2.repeatMode = mode
    }

    /** Cycle repeat mode: OFF → ALL → ONE → OFF. */
    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        syncRepeatMode()
        com.pulse.music.ui.jam.JamSessionManager.broadcastRepeat(_repeatMode.value.name)
    }

    fun setRepeatSync(modeName: String) {
        val mode = try { RepeatMode.valueOf(modeName) } catch (e: Exception) { RepeatMode.OFF }
        if (_repeatMode.value != mode) {
            _repeatMode.value = mode
            syncRepeatMode()
        }
    }

    /** Remove a song from queue by index. */
    fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index < 0 || index >= q.size) return
        q.removeAt(index)
        _queue.value = q
        player.removeMediaItem(index)
        
        if (q.isEmpty()) {
            _currentSong.value = null
            _currentIndex.value = -1
        }
        com.pulse.music.ui.jam.JamSessionManager.broadcastQueue(_queue.value, _currentIndex.value.coerceAtLeast(0), player.currentPosition)
    }

    fun setSleepTimer(mode: SleepTimerMode) {
        sleepTimerJob?.cancel()
        _sleepTimerMode.value = mode
        if (mode.minutes != null) {
            _sleepTimerTimeLeft.value = mode.minutes * 60 * 1000L
            sleepTimerJob = scope.launch {
                while (_sleepTimerTimeLeft.value > 0) {
                    delay(1000L)
                    _sleepTimerTimeLeft.value -= 1000L
                }
                player.pause()
                _sleepTimerMode.value = SleepTimerMode.OFF
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Sleep timer ended. Playback paused.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Sleep timer set for ${mode.minutes} minutes.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else if (mode == SleepTimerMode.END_OF_TRACK) {
            _sleepTimerTimeLeft.value = 0L
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Sleep timer set for end of current track.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            _sleepTimerTimeLeft.value = 0L
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Sleep timer cancelled.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopAndClear() {
        scope.coroutineContext.cancelChildren()
        player1.pause()
        player1.stop()
        player1.clearMediaItems()
        player2.pause()
        player2.stop()
        player2.clearMediaItems()
        _queue.value = emptyList()
        _currentSong.value = null
        _currentIndex.value = -1
        _isPlaying.value = false
        _currentPosition.value = 0L
    }

    /** Release the underlying [ExoPlayer] and cancel coroutines. */
    fun release() {
        stopPositionPolling()
        player1.release()
        player2.release()
    }

    private fun saveRecentPlay(song: Song) {
        if (song.id.startsWith("local_")) return
        scope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("pulse_actual_recent_plays", Context.MODE_PRIVATE)
                val json = prefs.getString("plays", "[]")
                val type = object : com.google.gson.reflect.TypeToken<List<Song>>() {}.type
                val currentPlays: MutableList<Song> = com.google.gson.Gson().fromJson(json, type) ?: mutableListOf()
                
                // Remove if already exists to move it to top
                currentPlays.removeAll { it.id == song.id }
                currentPlays.add(0, song) // Add to top
                
                // Keep only top 20
                val limited = currentPlays.take(20)
                prefs.edit().putString("plays", com.google.gson.Gson().toJson(limited)).apply()
                
                // Also update the backend if possible
                try { onlineRepo.addRecentSong(song) } catch(e: Exception) {}
            } catch (e: Exception) { }
        }
    }
}
