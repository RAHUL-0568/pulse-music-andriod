package com.pulse.music.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicServiceConnection @Inject constructor() {
    private val _currentSong = MutableStateFlow<Any?>(null)
    val currentSong: StateFlow<Any?> = _currentSong.asStateFlow()

    private val _playbackState = MutableStateFlow(0)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    fun play(song: Any) { }
    fun pause() { }
    fun seekTo(ms: Long) { }
    fun skipNext() { }
    fun skipPrev() { }
}
