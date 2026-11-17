package com.pulse.music.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.pulse.music.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.pulse.music.player.MusicPlayerManager
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val musicRepository: com.pulse.music.data.repository.OnlineMusicRepository,
    private val musicPlayerManager: MusicPlayerManager
) : ViewModel() {

    private val _capsuleState = MutableStateFlow<com.pulse.music.data.network.MusicCapsuleResponse?>(null)
    val capsuleState: StateFlow<com.pulse.music.data.network.MusicCapsuleResponse?> = _capsuleState.asStateFlow()
    
    fun fetchMusicCapsule() {
        viewModelScope.launch {
            val result = musicRepository.getMusicCapsule()
            if (result.isSuccess) {
                _capsuleState.value = result.getOrNull()
            }
        }
    }

    val token: StateFlow<String?> = repository.authToken.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val username: StateFlow<String?> = repository.username.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val email: StateFlow<String?> = repository.email.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val profilePicUri: StateFlow<String?> = repository.profilePicUri.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val highQuality: StateFlow<Boolean> = repository.highQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val spatialAudio: StateFlow<Boolean> = repository.spatialAudio.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gapless: StateFlow<Boolean> = repository.gapless.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val crossfade: StateFlow<Int> = repository.crossfade.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun updateProfilePic(uri: String) {
        viewModelScope.launch {
            repository.updateProfilePic(uri)
        }
    }

    fun uploadProfilePic(context: Context, uri: String) {
        viewModelScope.launch {
            val result = repository.uploadProfilePic(context, uri)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                result.onSuccess {
                    android.widget.Toast.makeText(context, "Profile picture updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    android.widget.Toast.makeText(context, "Upload failed: ${error.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun updatePlaybackSettings(highQuality: Boolean, spatialAudio: Boolean, gapless: Boolean, crossfade: Int) {
        viewModelScope.launch {
            repository.updatePlaybackSettings(highQuality, spatialAudio, gapless, crossfade)
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.login(email, password)
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Error")
            }
        }
    }

    fun requestRegisterOtp(email: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.requestRegisterOtp(email)
            if (result.isSuccess) {
                onResult(result.getOrNull()?.message ?: "Code sent")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "Failed to send code")
            }
        }
    }

    fun register(username: String, email: String, password: String, code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.register(username, email, password, code)
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Error")
            }
        }
    }

    fun socialLogin(provider: String, token: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.socialLogin(provider, token)
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Social login failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            musicPlayerManager.stopAndClear()
            repository.logout()
        }
    }

    fun forgotPassword(email: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.forgotPassword(email)
            if (result.isSuccess) {
                onResult(result.getOrNull()?.message ?: "Code sent")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "Failed to send code")
            }
        }
    }

    fun verifyCode(email: String, code: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.verifyCode(email, code)
            if (result.isSuccess) {
                val response = result.getOrNull()
                onResult(response?.status == "success", response?.message ?: "Error")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Error")
            }
        }
    }

    fun resetPassword(email: String, code: String, newPassword: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.resetPassword(email, code, newPassword)
            if (result.isSuccess) {
                val response = result.getOrNull()
                onResult(response?.status == "success", response?.message ?: "Error")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Error")
            }
        }
    }
}
