package com.pulse.music.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Raised by the network layer when the server says this account was blocked or removed
 * (header X-Account-Blocked / X-Account-Removed). MainActivity listens, signs the user
 * out and shows [message].
 */
object AccountEvents {
    private val _forcedLogout = MutableStateFlow<String?>(null)
    val forcedLogout: StateFlow<String?> = _forcedLogout.asStateFlow()

    fun report(message: String) {
        _forcedLogout.value = message
    }

    fun consume() {
        _forcedLogout.value = null
    }
}
