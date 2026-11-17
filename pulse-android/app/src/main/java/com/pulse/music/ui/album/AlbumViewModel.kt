package com.pulse.music.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.data.repository.MusicRepository
import com.pulse.music.domain.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicPlayerManager: com.pulse.music.player.MusicPlayerManager
) : ViewModel() {

    private val _albumInfo = MutableStateFlow<Album?>(null)
    val albumInfo: StateFlow<Album?> = _albumInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val currentSong = musicPlayerManager.currentSong
    val isPlaying = musicPlayerManager.isPlaying

    fun loadAlbum(id: String) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getAlbumInfo(id).collect { info ->
                _albumInfo.value = info
                _isLoading.value = false
            }
        }
    }

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    fun toggleFavorite() {
        _isFavorite.value = !_isFavorite.value
    }

    val isShuffleEnabled = musicPlayerManager.shuffleEnabled

    fun toggleShuffle() {
        musicPlayerManager.toggleShuffle()
    }

    fun togglePlayPause() {
        musicPlayerManager.togglePlayPause()
    }
}
