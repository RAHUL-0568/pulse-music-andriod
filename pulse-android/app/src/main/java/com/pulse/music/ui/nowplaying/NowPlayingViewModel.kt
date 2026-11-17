package com.pulse.music.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.data.repository.MusicRepository
import com.pulse.music.domain.LyricLine
import com.pulse.music.domain.Song
import com.pulse.music.player.MusicPlayerManager
import com.pulse.music.player.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LyricsState {
    object Loading : LyricsState()
    data class Synced(val lines: List<LyricLine>) : LyricsState()
    data class Plain(val text: String) : LyricsState()
    object Unavailable : LyricsState()
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicPlayerManager: MusicPlayerManager,
    private val likedSongsRepository: com.pulse.music.data.repository.LikedSongsRepository,
    private val playlistRepository: com.pulse.music.data.repository.PlaylistRepository
) : ViewModel() {

    val playlists = playlistRepository.getAllPlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSong: StateFlow<Song?> = musicPlayerManager.currentSong
    val isPlaying: StateFlow<Boolean> = musicPlayerManager.isPlaying
    val currentPosition: StateFlow<Long> = musicPlayerManager.currentPosition
    val duration: StateFlow<Long> = musicPlayerManager.duration
    val shuffleEnabled: StateFlow<Boolean> = musicPlayerManager.shuffleEnabled
    val repeatMode: StateFlow<RepeatMode> = musicPlayerManager.repeatMode
    val queue: StateFlow<List<Song>> = musicPlayerManager.queue
    val currentIndex: StateFlow<Int> = musicPlayerManager.currentIndex
    
    fun createPlaylistAndAddSong(name: String, song: Song) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(name)
            playlistRepository.addSongToPlaylist(id, song)
        }
    }
    
    fun addSongToPlaylist(playlistId: Int, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }

    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Unavailable)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private var currentFetchingSongId: String? = null

    init {
        viewModelScope.launch {
            currentSong.collect { song ->
                if (song != null) {
                    if (song.id != currentFetchingSongId) {
                        currentFetchingSongId = song.id
                        fetchLyricsAndRecommendations(song.title, song.artist, song.id)
                    }
                } else {
                    currentFetchingSongId = null
                    _lyricsState.value = LyricsState.Unavailable
                }
            }
        }
    }

    val isCurrentSongLiked = currentSong.flatMapLatest { song ->
        if (song != null) {
            likedSongsRepository.isSongLiked(song.id)
        } else {
            flowOf(false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val highlightedLine = combine(currentPosition, _lyricsState) { currentProgress, state ->
        if (state is LyricsState.Synced) {
            val lines = state.lines
            val matchIndex = lines.indexOfLast { it.timeMs <= currentProgress }
            if (matchIndex >= 0) matchIndex else 0
        } else {
            0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun togglePlayPause() = musicPlayerManager.togglePlayPause()
    fun seekTo(positionMs: Long) = musicPlayerManager.seekTo(positionMs)
    fun skipNext() = musicPlayerManager.skipNext()
    fun skipPrevious() = musicPlayerManager.skipPrevious()
    fun toggleShuffle() = musicPlayerManager.toggleShuffle()
    fun cycleRepeatMode() = musicPlayerManager.cycleRepeatMode()

    val sleepTimerMode: StateFlow<com.pulse.music.player.SleepTimerMode> = musicPlayerManager.sleepTimerMode
    val sleepTimerTimeLeft: StateFlow<Long> = musicPlayerManager.sleepTimerTimeLeft

    fun setSleepTimer(mode: com.pulse.music.player.SleepTimerMode) = musicPlayerManager.setSleepTimer(mode)

    fun getVolumeFraction(): Float = musicPlayerManager.getVolumeFraction()
    fun setVolume(fraction: Float) = musicPlayerManager.setVolumeFraction(fraction)

    fun playFromQueue(index: Int) {
        val q = queue.value
        if (index in q.indices) {
            musicPlayerManager.playSongFromList(q[index], q)
        }
    }

    fun removeFromQueue(index: Int) = musicPlayerManager.removeFromQueue(index)
    
    fun addToQueue(song: Song) {
        if (com.pulse.music.ui.jam.JamSessionManager.isConnected.value) {
            // If in a jam session, add it to the shared queue instead!
            com.pulse.music.ui.jam.JamSessionManager.addSongToQueue(song, "You")
        } else {
            musicPlayerManager.enqueue(song)
        }
    }

    fun toggleLike() {
        viewModelScope.launch {
            val song = currentSong.value ?: return@launch
            if (isCurrentSongLiked.value) {
                likedSongsRepository.removeLikedSong(song.id)
            } else {
                likedSongsRepository.addLikedSong(song)
            }
        }
    }

    private val _rawRecommendations = MutableStateFlow<List<Song>>(emptyList())
    
    val recommendations = combine(_rawRecommendations, queue) { recs, q ->
        val queueTitles = q.map { it.title.trim().lowercase() + it.artist.trim().lowercase() }.toSet()
        recs.filter { it.title.trim().lowercase() + it.artist.trim().lowercase() !in queueTitles }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playSong(song: Song) {
        musicPlayerManager.playSong(song)
    }

    private var lyricsJob: kotlinx.coroutines.Job? = null
    private var recsJob: kotlinx.coroutines.Job? = null

    fun fetchLyricsAndRecommendations(title: String, artist: String, songId: String? = null) {
        // Cancel the previous song's requests so a slow reply can't overwrite this song's lyrics.
        lyricsJob?.cancel()
        recsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _lyricsState.value = LyricsState.Loading
            repository.getLyrics(title, artist, songId).collect { lyrics ->
                if (lyrics != null) {
                    if (!lyrics.synced.isNullOrEmpty()) {
                        _lyricsState.value = LyricsState.Synced(lyrics.synced)
                    } else if (!lyrics.plain.isNullOrEmpty()) {
                        _lyricsState.value = LyricsState.Plain(lyrics.plain)
                    } else {
                        _lyricsState.value = LyricsState.Unavailable
                    }
                } else {
                    _lyricsState.value = LyricsState.Unavailable
                }
            }
        }
        recsJob = viewModelScope.launch {
            repository.getRecommendations(artist, title).collect { list ->
                val filteredList = list.filter { it.title != title || it.artist != artist }
                val uniqueList = filteredList.distinctBy { it.title.trim().lowercase() + it.artist.trim().lowercase() }
                _rawRecommendations.value = uniqueList
            }
        }
    }
}
