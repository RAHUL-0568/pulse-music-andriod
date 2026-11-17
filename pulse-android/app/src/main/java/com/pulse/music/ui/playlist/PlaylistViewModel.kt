package com.pulse.music.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.data.local.PlaylistEntity
import com.pulse.music.data.repository.PlaylistRepository
import com.pulse.music.domain.Song
import com.pulse.music.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val likedSongsRepository: com.pulse.music.data.repository.LikedSongsRepository,
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    private val _playlistId = MutableStateFlow<Int?>(null)

    val playlistInfo: StateFlow<PlaylistEntity?> = _playlistId
        .filterNotNull()
        .flatMapLatest { id ->
            if (id == -1) {
                flowOf(PlaylistEntity(id = -1, name = "Liked Songs"))
            } else {
                playlistRepository.getAllPlaylists().map { playlists -> 
                    playlists.find { it.id == id } 
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playlistSongs: StateFlow<List<Song>> = _playlistId
        .filterNotNull()
        .flatMapLatest { id ->
            if (id == -1) {
                likedSongsRepository.likedSongs
            } else {
                playlistRepository.getSongsForPlaylist(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSong = musicPlayerManager.currentSong
    val isPlaying = musicPlayerManager.isPlaying
    
    // Mocking favorite functionality since DB doesn't support it directly on PlaylistEntity
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite = _isFavorite.asStateFlow()

    fun loadPlaylist(id: Int) {
        _playlistId.value = id
        // In a real app we'd load this from DB
        _isFavorite.value = false
    }

    fun playSong(song: Song, contextSongs: List<Song>) {
        musicPlayerManager.playSongFromList(song, contextSongs)
    }

    fun playAll(contextSongs: List<Song>) {
        if (contextSongs.isNotEmpty()) {
            musicPlayerManager.playSongFromList(contextSongs.first(), contextSongs)
        }
    }

    fun togglePlayPause() {
        musicPlayerManager.togglePlayPause()
    }

    val isShuffleEnabled = musicPlayerManager.shuffleEnabled

    fun toggleShuffle() {
        musicPlayerManager.toggleShuffle()
    }

    fun toggleFavorite() {
        _isFavorite.value = !_isFavorite.value
    }

    fun removeSong(songId: String) {
        val pId = _playlistId.value ?: return
        viewModelScope.launch {
            if (pId == -1) {
                likedSongsRepository.removeLikedSong(songId)
            } else {
                playlistRepository.removeSongFromPlaylist(pId, songId)
            }
        }
    }

    fun renamePlaylist(newName: String) {
        val pId = _playlistId.value ?: return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(pId, newName)
        }
    }

    fun deletePlaylist() {
        val pId = _playlistId.value ?: return
        viewModelScope.launch {
            playlistRepository.deletePlaylist(pId)
        }
    }
}
