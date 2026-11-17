package com.pulse.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.domain.Song
import com.pulse.music.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** Which parts of Home are still waiting for the server (the screen shows placeholders for them). */
data class HomeSectionsLoading(val suggested: Boolean = false, val modules: Boolean = false)

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

    private val _sectionsLoading = MutableStateFlow(HomeSectionsLoading())
    val sectionsLoading: StateFlow<HomeSectionsLoading> = _sectionsLoading.asStateFlow()

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

    private var loadJob: Job? = null

    init {
        // Memory first, then the disk copy, so Home is never empty when we already have something to show.
        if (cachedUiState == null) {
            try {
                val cacheFile = java.io.File(context.cacheDir, "home_cache.json")
                if (cacheFile.exists()) {
                    val parsed = com.google.gson.Gson().fromJson(cacheFile.readText(), HomeUiState.Success::class.java)
                    if (parsed != null && (parsed.suggested?.size ?: 0) > 0) cachedUiState = parsed
                }
            } catch (e: Exception) { /* a broken cache file is simply ignored */ }
        }

        val cached = cachedUiState
        if (cached != null) {
            _uiState.value = cached
            loadData(background = true)
        } else {
            loadData(background = false)
        }
    }

    private fun updateSuccess(transform: (HomeUiState.Success) -> HomeUiState.Success) {
        _uiState.update { s -> if (s is HomeUiState.Success) transform(s) else s }
    }

    private fun readLocalRecents(): List<Song> {
        return try {
            val prefs = context.getSharedPreferences("pulse_actual_recent_plays", android.content.Context.MODE_PRIVATE)
            val array = com.google.gson.Gson().fromJson(prefs.getString("plays", "[]"), Array<Song>::class.java)
            array?.toList()?.filter { !it.id.startsWith("local_") } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun saveLocalRecents(songs: List<Song>) {
        try {
            val prefs = context.getSharedPreferences("pulse_actual_recent_plays", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("plays", com.google.gson.Gson().toJson(songs)).apply()
        } catch (e: Exception) { }
    }

    fun loadData(background: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _sectionsLoading.value = HomeSectionsLoading(suggested = true, modules = true)

            // 1. What we already know on the phone appears instantly.
            val localSongs = readLocalRecents()
            if (!background || _uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Success(localSongs.take(5), localSongs, emptyList(), emptyList())
            }

            var gotModules = false
            var gotSuggested = false

            coroutineScope {
                // 2. Playlist rows: each appears the moment the server answers.
                launch {
                    try {
                        val modules = repository.getHome().getOrNull()?.modules
                        if (modules != null) {
                            gotModules = modules.isNotEmpty()
                            updateSuccess { it.copy(modules = modules) }
                        }
                    } finally {
                        _sectionsLoading.update { it.copy(modules = false) }
                    }
                }

                // 3. History (merged with the server's copy) and "picked for you".
                launch {
                    try {
                        val trendingDeferred = async { repository.getTrending() }
                        val remote = (repository.getRecentSongs().getOrNull() ?: emptyList())
                            .filter { !it.id.startsWith("local_") }
                        val merged = (localSongs + remote).distinctBy { it.id }.take(20)

                        if (merged.isNotEmpty()) {
                            updateSuccess { it.copy(recentPlays = merged.take(5), allRecentPlays = merged) }
                            saveLocalRecents(merged)
                            // push songs the server doesn't know yet
                            val remoteIds = remote.map { it.id }.toSet()
                            launch {
                                localSongs.filter { it.id !in remoteIds }.reversed().forEach { song ->
                                    try { repository.addRecentSong(song) } catch (e: Exception) { }
                                }
                            }
                        }

                        val recDeferreds = merged.take(3).map { s ->
                            async { repository.getRecommendations(s.artist, s.title) }
                        }
                        val trending = trendingDeferred.await().getOrNull() ?: emptyList()
                        val recs = recDeferreds.flatMap { it.await().getOrNull() ?: emptyList() }

                        // brand-new user with no history: fill the page from a few popular searches
                        val extra = if (merged.isEmpty()) {
                            listOf("top hits 2025", "best bollywood 2024", "popular hindi songs", "romantic songs")
                                .map { q -> async { try { repository.searchOnline(q).getOrNull()?.songs ?: emptyList() } catch (e: Exception) { emptyList() } } }
                                .flatMap { it.await() }
                        } else emptyList()

                        val suggested = (recs + trending + extra).distinctBy { it.id }.take(32)
                        if (suggested.isNotEmpty()) {
                            gotSuggested = true
                            updateSuccess { it.copy(suggested = suggested) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PulseAPI", "Home suggestions failed", e)
                    } finally {
                        _sectionsLoading.update { it.copy(suggested = false) }
                    }
                }
            }

            // 4. Nothing at all came back: offline. Use the saved copy, or show a friendly retry card.
            val finalState = _uiState.value
            if (finalState is HomeUiState.Success) {
                val empty = !gotModules && !gotSuggested && finalState.suggested.isEmpty() && finalState.modules.isEmpty()
                if (empty) {
                    val cached = cachedUiState
                    if (cached != null) {
                        _uiState.value = cached
                    } else if (finalState.allRecentPlays.isEmpty()) {
                        _uiState.value = HomeUiState.Error("We can't reach Pulse right now. Check your connection and try again.")
                    }
                } else {
                    cachedUiState = finalState
                    withContext(Dispatchers.IO) {
                        try {
                            java.io.File(context.cacheDir, "home_cache.json")
                                .writeText(com.google.gson.Gson().toJson(finalState))
                        } catch (e: Exception) { }
                    }
                }
            }
        }
    }

    fun removeRecentSong(song: Song) {
        viewModelScope.launch {
            // Local first, so it disappears immediately (and works offline).
            val newList = readLocalRecents().filter { it.id != song.id }
            saveLocalRecents(newList)

            updateSuccess { current ->
                val updatedAll = current.allRecentPlays.filter { it.id != song.id }
                current.copy(recentPlays = updatedAll.take(5), allRecentPlays = updatedAll)
            }
            (_uiState.value as? HomeUiState.Success)?.let { cachedUiState = it }

            try { repository.removeRecentSong(song.id) } catch (e: Exception) { }
        }
    }
}
