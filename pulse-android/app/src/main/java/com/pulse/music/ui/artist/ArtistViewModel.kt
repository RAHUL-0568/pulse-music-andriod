package com.pulse.music.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val followedArtistRepository: com.pulse.music.data.repository.FollowedArtistRepository,
    private val musicPlayerManager: com.pulse.music.player.MusicPlayerManager
) : ViewModel() {

    private val _artistInfo = MutableStateFlow<Any?>(null)
    val artistInfo: StateFlow<Any?> = _artistInfo.asStateFlow()

    private val _topTracks = MutableStateFlow<List<Any>>(emptyList())
    val topTracks: StateFlow<List<Any>> = _topTracks.asStateFlow()

    private val _isFollowed = MutableStateFlow(false)
    val isFollowed: StateFlow<Boolean> = _isFollowed.asStateFlow()

    val currentSong = musicPlayerManager.currentSong
    val isPlaying = musicPlayerManager.isPlaying

    fun loadArtist(name: String) {
        viewModelScope.launch {
            repository.getArtistInfo(name).collect { info ->
                _artistInfo.value = info
                if (info is com.pulse.music.domain.Artist) {
                    _topTracks.value = info.topTracks
                }
            }
        }
        viewModelScope.launch {
            followedArtistRepository.isArtistFollowed(name).collect { followed ->
                _isFollowed.value = followed
            }
        }
    }

    fun toggleFollow(artist: com.pulse.music.domain.Artist) {
        viewModelScope.launch {
            if (_isFollowed.value) {
                followedArtistRepository.removeFollowedArtist(artist.id)
                _isFollowed.value = false
            } else {
                followedArtistRepository.addFollowedArtist(artist)
                _isFollowed.value = true
            }
        }
    }

    fun playAll(contextSongs: List<com.pulse.music.domain.Song>) {
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
}
