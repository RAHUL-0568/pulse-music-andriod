package com.pulse.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.data.repository.LocalMusicRepository
import com.pulse.music.domain.Song
import com.pulse.music.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val localMusicRepository: LocalMusicRepository,
    private val musicPlayerManager: MusicPlayerManager,
    private val likedSongsRepository: com.pulse.music.data.repository.LikedSongsRepository,
    private val playlistRepository: com.pulse.music.data.repository.PlaylistRepository,
    private val followedArtistRepository: com.pulse.music.data.repository.FollowedArtistRepository
) : ViewModel() {

    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()
    
    val likedSongs: StateFlow<List<Song>> = likedSongsRepository.likedSongs
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())
        
    val playlists = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    val followedArtists = followedArtistRepository.followedArtists
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _filter = MutableStateFlow("All")
    val filter: StateFlow<String> = _filter.asStateFlow()

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importResult = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val importResult = _importResult.asSharedFlow()

    init {
        viewModelScope.launch {
            launch {
                try { likedSongsRepository.syncLikedSongs() } catch (e: Exception) { e.printStackTrace() }
            }
            launch {
                try { playlistRepository.syncPlaylists() } catch (e: Exception) { e.printStackTrace() }
            }
            launch {
                try { followedArtistRepository.syncFollowedArtists() } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun loadLocalSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _localSongs.value = localMusicRepository.getLocalSongs()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFilter(newFilter: String) {
        _filter.value = newFilter
    }

    fun playSong(song: Song, contextSongs: List<Song>) {
        musicPlayerManager.playSongFromList(song, contextSongs)
    }

    fun importSpotifyPlaylist(url: String) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = playlistRepository.importSpotifyPlaylist(url)
            _isImporting.value = false
            if (result.isSuccess) {
                _importResult.emit("Import successful! Your songs are being processed in the cloud.")
            } else {
                _importResult.emit("Failed to import playlist. Please check the URL and try again.")
            }
        }
    }

    fun getSongsForPlaylist(playlistId: Int): kotlinx.coroutines.flow.Flow<List<Song>> {
        return playlistRepository.getSongsForPlaylist(playlistId)
    }
}
