package com.pulse.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.data.repository.MusicRepository
import com.pulse.music.domain.Song
import com.pulse.music.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import javax.inject.Inject

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val recentPlays: List<Song>,
        val allRecentPlays: List<Song>,
        val suggested: List<Song>,
        val modules: List<com.pulse.music.data.network.HomeModule>
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: com.pulse.music.data.repository.OnlineMusicRepository,
    private val songDao: com.pulse.music.data.local.SongDao,
    private val musicPlayerManager: MusicPlayerManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val authRepository: com.pulse.music.data.repository.AuthRepository
) : ViewModel() {

    fun playSong(song: Song, contextSongs: List<Song>) {
        musicPlayerManager.playSongFromList(song, contextSongs)
    }

    companion object {
        var cachedUiState: HomeUiState.Success? = null
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val username: StateFlow<String?> = authRepository.username.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val profilePicUri: StateFlow<String?> = authRepository.profilePicUri.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        // Check in-memory cache first
        val memCacheValid = (cachedUiState?.suggested?.size ?: 0) >= 8
        
        if (!memCacheValid) {
            cachedUiState = null
            // Try disk cache
            try {
                val cacheFile = java.io.File(context.cacheDir, "home_cache.json")
                if (cacheFile.exists()) {
                    val cachedJson = cacheFile.readText()
                    val parsed = com.google.gson.Gson().fromJson(cachedJson, HomeUiState.Success::class.java)
                    if (parsed != null && (parsed.suggested?.size ?: 0) >= 8) {
                        cachedUiState = parsed
                    } else {
                        try { cacheFile.delete() } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) { /* ignore */ }
        }

        if (cachedUiState != null) {
            // Show cache immediately, then refresh in background
            _uiState.value = cachedUiState!!
            loadData(background = true)
        } else {
            loadData(background = false)
        }
    }

    fun loadData(background: Boolean = false) {
        viewModelScope.launch {
            if (!background) {
                _uiState.value = HomeUiState.Loading
            }
            try {
                // Kick off independent parallel fetches immediately
                val trendingDeferred = async { repository.getTrending() }
                val homeDeferred = async { repository.getHome() }
                val recentSongsDeferred = async { repository.getRecentSongs() }
                
                // Fetch actual recently played songs from local SharedPreferences
                var localSongs: List<Song> = emptyList()
                try {
                    val prefs = context.getSharedPreferences("pulse_actual_recent_plays", android.content.Context.MODE_PRIVATE)
                    val json = prefs.getString("plays", "[]")
                    val array = com.google.gson.Gson().fromJson(json, Array<Song>::class.java)
                    localSongs = array?.toList()?.filter { !it.id.startsWith("local_") } ?: emptyList()
                } catch (e: Exception) {}

                val actualRecentSongs = (recentSongsDeferred.await().getOrNull() ?: emptyList()).filter { !it.id.startsWith("local_") }
                
                // Merge local and backend songs, keeping the most recent first
                val mergedList = (localSongs + actualRecentSongs).distinctBy { it.id }.take(20)
                val recentPlaysList = mergedList
                
                // Sync any local songs to the backend if they are missing
                val backendIds = actualRecentSongs.map { it.id }.toSet()
                val missingInBackend = localSongs.filter { it.id !in backendIds }
                if (missingInBackend.isNotEmpty()) {
                    launch {
                        missingInBackend.reversed().forEach { song ->
                            try { repository.addRecentSong(song) } catch(e: Exception) {}
                        }
                    }
                }

                // Update local cache so it reflects the backend
                try {
                    val prefs = context.getSharedPreferences("pulse_actual_recent_plays", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString("plays", com.google.gson.Gson().toJson(recentPlaysList)).apply()
                } catch (e: Exception) {}

                val suggestedDeferred1 = if (recentPlaysList.size > 0) {
                    async { repository.getRecommendations(recentPlaysList[0].artist, recentPlaysList[0].title) }
                } else null
                val suggestedDeferred2 = if (recentPlaysList.size > 1) {
                    async { repository.getRecommendations(recentPlaysList[1].artist, recentPlaysList[1].title) }
                } else null
                val suggestedDeferred3 = if (recentPlaysList.size > 2) {
                    async { repository.getRecommendations(recentPlaysList[2].artist, recentPlaysList[2].title) }
                } else null
                
                val limitedRecentPlays = recentPlaysList.take(5)
                
                val rawSuggested1 = suggestedDeferred1?.await()?.getOrNull() ?: emptyList()
                val rawSuggested2 = suggestedDeferred2?.await()?.getOrNull() ?: emptyList()
                val rawSuggested3 = suggestedDeferred3?.await()?.getOrNull() ?: emptyList()
                val rawTrending = trendingDeferred.await().getOrNull() ?: emptyList()

                // If no recent songs (not logged in), bulk-fill with parallel genre searches
                val extraSongs = if (recentPlaysList.isEmpty()) {
                    val genres = listOf("top hits 2025", "best bollywood 2024", "popular hindi songs", "romantic songs")
                    val deferreds = genres.map { q -> async { 
                        try { repository.searchOnline(q).getOrNull()?.songs ?: emptyList() } catch(e: Exception) { emptyList() }
                    }}
                    deferreds.flatMap { it.await() }
                } else emptyList()

                val suggested = (rawSuggested1 + rawSuggested2 + rawSuggested3 + rawTrending + extraSongs)
                    .distinctBy { it.id }
                    .take(32)
                
                val homeData = homeDeferred.await().getOrNull()
                
                val successState = HomeUiState.Success(
                    recentPlays = limitedRecentPlays,
                    allRecentPlays = recentPlaysList,
                    suggested = suggested,
                    modules = homeData?.modules ?: emptyList()
                )
                cachedUiState = successState
                _uiState.value = successState
                
                // Save to cache directory so it clears on Clear Cache
                try {
                    val cacheFile = java.io.File(context.cacheDir, "home_cache.json")
                    cacheFile.writeText(com.google.gson.Gson().toJson(successState))
                } catch (e: Exception) {
                    // Ignore save error
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PulseAPI", "HomeViewModel error: ", e)
                if (cachedUiState != null) {
                    _uiState.value = cachedUiState!! // Seamless offline fallback
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Offline Mode: Showing cached songs", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    _uiState.value = HomeUiState.Error(e.message ?: "Network error and no cache available")
                }
            }
        }
    }
    fun removeRecentSong(song: Song) {
        viewModelScope.launch {
            try {
                repository.removeRecentSong(song.id)
                
                val prefs = context.getSharedPreferences("pulse_actual_recent_plays", android.content.Context.MODE_PRIVATE)
                val json = prefs.getString("plays", "[]")
                val array = com.google.gson.Gson().fromJson(json, Array<Song>::class.java)
                val localSongs = array?.toList() ?: emptyList()
                
                val newList = localSongs.filter { it.id != song.id }
                prefs.edit().putString("plays", com.google.gson.Gson().toJson(newList)).apply()
                
                val currentState = _uiState.value
                if (currentState is HomeUiState.Success) {
                    val updatedAll = currentState.allRecentPlays.filter { it.id != song.id }
                    val successState = currentState.copy(
                        recentPlays = updatedAll.take(5),
                        allRecentPlays = updatedAll
                    )
                    cachedUiState = successState
                    _uiState.value = successState
                    
                    val cacheFile = java.io.File(context.cacheDir, "home_cache.json")
                    cacheFile.writeText(com.google.gson.Gson().toJson(successState))
                }
            } catch (e: Exception) {}
        }
    }
}
