package com.pulse.music.data.repository

import com.pulse.music.data.network.PulseApiService
import com.pulse.music.domain.JamRoomInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JamRepository @Inject constructor(
    private val apiService: PulseApiService
) {
    private val _myJams = MutableStateFlow<List<JamRoomInfo>>(emptyList())
    val myJams: StateFlow<List<JamRoomInfo>> = _myJams.asStateFlow()

    suspend fun fetchMyJams() {
        try {
            val jams = apiService.getMyJams()
            _myJams.value = jams
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createJam(name: String): JamRoomInfo {
        val newJam = apiService.createJam(name)
        fetchMyJams() // refresh
        return newJam
    }

    suspend fun deleteJam(jamId: String) {
        _myJams.value = _myJams.value.filter { it.jamId != jamId }
        try {
            apiService.deleteJam(jamId)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fetchMyJams() // always refresh list
        }
    }

    suspend fun leaveJam(jamId: String) {
        _myJams.value = _myJams.value.filter { it.jamId != jamId }
        try {
            apiService.leaveJam(jamId)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fetchMyJams() // always refresh list
        }
    }
}
