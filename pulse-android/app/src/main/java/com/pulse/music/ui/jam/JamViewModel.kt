package com.pulse.music.ui.jam

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class JamViewModel @Inject constructor(
    private val musicPlayerManager: com.pulse.music.player.MusicPlayerManager,
    private val authRepository: com.pulse.music.data.repository.AuthRepository,
    private val jamRepository: com.pulse.music.data.repository.JamRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val isConnected: StateFlow<Boolean> = JamSessionManager.isConnected
    val currentRoomId: StateFlow<String?> = JamSessionManager.currentRoomId
    val incomingSync: StateFlow<Triple<Boolean, Long, Long>?> = JamSessionManager.incomingSync
    val queue: StateFlow<List<JSONObject>> = JamSessionManager.queue
    val chatMessages: StateFlow<List<JSONObject>> = JamSessionManager.chatMessages
    val participants: StateFlow<List<JSONObject>> = JamSessionManager.participants
    val isPendingApproval: StateFlow<Boolean> = JamSessionManager.isPendingApproval
    val joinRequests: StateFlow<List<String>> = JamSessionManager.joinRequests
    val currentUserId: StateFlow<String?> = authRepository.userId.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val myJams: StateFlow<List<com.pulse.music.domain.JamRoomInfo>> = jamRepository.myJams

    fun fetchMyJams() {
        viewModelScope.launch {
            jamRepository.fetchMyJams()
        }
    }

    fun createJam(name: String) {
        viewModelScope.launch {
            try {
                val newJam = jamRepository.createJam(name)
                connectToJamSession(newJam.roomCode)
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = if (e is retrofit2.HttpException && e.code() == 400) {
                    "You can only create up to 5 jams."
                } else {
                    "Failed to create jam"
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteJam(jamId: String) {
        viewModelScope.launch {
            jamRepository.deleteJam(jamId)
            if (currentRoomId.value == jamId) {
                disconnect()
            }
        }
    }

    fun leaveJam(jamId: String) {
        viewModelScope.launch {
            jamRepository.leaveJam(jamId)
            if (currentRoomId.value == jamId) {
                disconnect()
            }
        }
    }

    fun connectToJamSession(roomId: String, isCreating: Boolean = false) {
        viewModelScope.launch {
            val uid = authRepository.userId.firstOrNull() ?: "Guest-${java.util.UUID.randomUUID().toString().substring(0, 4)}"
            val token = authRepository.authToken.firstOrNull()
            JamSessionManager.connectToJamSession(roomId, uid, isCreating, token)
        }
    }

    fun disconnect() {
        JamSessionManager.disconnect()
    }

    fun broadcastPlayPause(isPlaying: Boolean, currentPositionMs: Long) {
        JamSessionManager.broadcastPlayPause(isPlaying, currentPositionMs)
    }
    
    fun sendChatMessage(text: String) {
        JamSessionManager.sendChatMessage(text)
    }
    
    fun addSongToQueue(songId: String, title: String, addedBy: String) {
        JamSessionManager.addSongToQueue(songId, title, addedBy)
    }

    fun approveJoin(userId: String) {
        JamSessionManager.approveJoin(userId)
    }

    fun rejectJoin(userId: String) {
        JamSessionManager.rejectJoin(userId)
    }

    fun removeParticipant(userId: String) {
        JamSessionManager.removeParticipant(userId)
    }

    fun playJamSong(item: JSONObject) {
        val song = com.pulse.music.domain.Song(
            id = item.optString("id", ""),
            title = item.optString("title", "Unknown Title"),
            artist = item.optString("artist", "Unknown Artist"),
            albumArt = item.optString("image", ""),
            source = item.optString("streamUrl", "")
        )
        musicPlayerManager.playSong(song)
    }
}
