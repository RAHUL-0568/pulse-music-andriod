package com.pulse.music.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.data.network.MusicCapsuleResponse
import com.pulse.music.data.network.MusicCapsuleSong
import com.pulse.music.data.repository.FollowedArtistRepository
import com.pulse.music.data.repository.LikedSongsRepository
import com.pulse.music.data.repository.OnlineMusicRepository
import com.pulse.music.data.repository.PlaylistRepository
import com.pulse.music.domain.Song
import com.pulse.music.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ListeningState {
    object Loading : ListeningState()
    object Empty : ListeningState()
    object Failed : ListeningState()
    data class Ready(val data: MusicCapsuleResponse) : ListeningState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    likedRepository: LikedSongsRepository,
    playlistRepository: PlaylistRepository,
    followedRepository: FollowedArtistRepository,
    private val onlineRepository: OnlineMusicRepository,
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    val likedCount: StateFlow<Int> = likedRepository.likedSongs.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val playlistCount: StateFlow<Int> = playlistRepository.getAllPlaylists().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val followingCount: StateFlow<Int> = followedRepository.followedArtists.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _listening = MutableStateFlow<ListeningState>(ListeningState.Loading)
    val listening: StateFlow<ListeningState> = _listening.asStateFlow()

    init {
        // The counts come from the phone's database; refresh them quietly in case Library was never opened.
        viewModelScope.launch {
            likedRepository.syncLikedSongs()
            playlistRepository.syncPlaylists()
            followedRepository.syncFollowedArtists()
        }
        loadListening()
    }

    fun loadListening() {
        viewModelScope.launch {
            _listening.value = ListeningState.Loading
            val result = onlineRepository.getMusicCapsule()
            _listening.value = result.fold(
                onSuccess = { if (it.totalPlays > 0) ListeningState.Ready(it) else ListeningState.Empty },
                onFailure = { ListeningState.Failed }
            )
        }
    }

    private fun toSong(s: MusicCapsuleSong) = Song(
        id = s.id,
        title = s.title,
        artist = s.artist,
        albumArt = s.albumArt.takeIf { it.isNotBlank() },
        source = "jiosaavn"
    )

    fun playTopSong(song: MusicCapsuleSong, all: List<MusicCapsuleSong>) {
        musicPlayerManager.playSongFromList(toSong(song), all.map { toSong(it) })
    }
}
