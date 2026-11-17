package com.pulse.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.data.local.PlaylistDao
import com.pulse.music.data.local.PlaylistEntity
import com.pulse.music.data.local.SongDao
import com.pulse.music.data.local.SongEntity
import com.pulse.music.data.repository.OnlineMusicRepository
import com.pulse.music.domain.Album
import com.pulse.music.domain.Artist
import com.pulse.music.domain.Song
import com.pulse.music.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchSectionState<T>(
    val data: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isError: Boolean = false
)

data class SearchUiState(
    val query: String = "",
    val activeCategory: String = "All",
    val recentSearches: List<String> = emptyList(),
    val songs: SearchSectionState<Song> = SearchSectionState(),
    val albums: SearchSectionState<Album> = SearchSectionState(),
    val artists: SearchSectionState<Artist> = SearchSectionState(),
    val playlists: SearchSectionState<PlaylistEntity> = SearchSectionState(),
    val onlinePlaylists: SearchSectionState<com.pulse.music.domain.Playlist> = SearchSectionState(),
    val exactArtist: Artist? = null,
    val isExactArtistLoading: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val musicPlayerManager: MusicPlayerManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("search_prefs", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        fetchRecentSearches()
        viewModelScope.launch {
            _uiState.map { it.query }
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }
    
    private fun fetchRecentSearches() {
        val historyStr = sharedPrefs.getString("recent_searches", "") ?: ""
        val searches = if (historyStr.isNotBlank()) historyStr.split("|||") else emptyList()
        _uiState.update { it.copy(recentSearches = searches) }
    }
    
    companion object {
        val cachedCategoryPlaylists = androidx.compose.runtime.mutableStateMapOf<String, com.pulse.music.domain.Playlist?>()
        val cachedCategoryLoading = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    }

    val categoryPlaylists = cachedCategoryPlaylists
    val categoryLoading = cachedCategoryLoading
    
    // Category tiles rarely change, so keep them on disk for a week instead of
    // running ~80 searches after every app start.
    private val categoryPrefs = context.getSharedPreferences("category_cache", android.content.Context.MODE_PRIVATE)
    private val categoryTtlMs = 7L * 24 * 60 * 60 * 1000

    fun fetchCategoryPlaylist(categoryQuery: String) {
        if (categoryPlaylists.containsKey(categoryQuery) || categoryLoading[categoryQuery] == true) return

        val saved = categoryPrefs.getString("c_$categoryQuery", null)
        val savedAt = categoryPrefs.getLong("t_$categoryQuery", 0L)
        if (saved != null && System.currentTimeMillis() - savedAt < categoryTtlMs) {
            try {
                val cached = com.google.gson.Gson().fromJson(saved, com.pulse.music.domain.Playlist::class.java)
                if (cached != null) {
                    categoryPlaylists[categoryQuery] = cached
                    categoryLoading[categoryQuery] = false
                    return
                }
            } catch (e: Exception) {
                // fall through and refetch
            }
        }

        categoryLoading[categoryQuery] = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val response = onlineRepo.searchOnline(categoryQuery).getOrNull()
                val playlist = response?.playlists?.firstOrNull()
                if (playlist != null) {
                    categoryPrefs.edit()
                        .putString("c_$categoryQuery", com.google.gson.Gson().toJson(playlist))
                        .putLong("t_$categoryQuery", System.currentTimeMillis())
                        .apply()
                }
                categoryPlaylists[categoryQuery] = playlist
            } catch (e: Exception) {
                categoryPlaylists[categoryQuery] = null
            } finally {
                categoryLoading[categoryQuery] = false
            }
        }
    }

    fun saveRecentSearch(query: String) {
        if (query.isBlank()) return
        
        val currentSearches = _uiState.value.recentSearches.toMutableList()
        currentSearches.remove(query) // Remove if exists to put it at the top
        currentSearches.add(0, query)
        
        // Keep only top 20 recent searches
        if (currentSearches.size > 20) {
            currentSearches.removeAt(currentSearches.size - 1)
        }
        
        _uiState.update { it.copy(recentSearches = currentSearches) }
        sharedPrefs.edit().putString("recent_searches", currentSearches.joinToString("|||")).apply()

        // Still send to backend silently for cross-device sync, but don't wait/fetch from it
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            onlineRepo.addRecentSearch(query)
        }
    }

    fun removeRecentSearch(query: String) {
        val currentSearches = _uiState.value.recentSearches.toMutableList()
        currentSearches.remove(query)
        _uiState.update { it.copy(recentSearches = currentSearches) }
        sharedPrefs.edit().putString("recent_searches", currentSearches.joinToString("|||")).apply()
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            onlineRepo.removeRecentSearch(query)
        }
    }

    fun setCategory(category: String) {
        _uiState.update { it.copy(activeCategory = category) }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    songs = SearchSectionState(),
                    albums = SearchSectionState(),
                    artists = SearchSectionState(),
                    playlists = SearchSectionState(),
                    exactArtist = null,
                    isExactArtistLoading = false
                )
            }
            return
        }

        searchJob?.cancel()

        _uiState.update { state ->
            state.copy(
                songs = state.songs.copy(isLoading = true, isError = false),
                albums = state.albums.copy(isLoading = true, isError = false),
                artists = state.artists.copy(isLoading = true, isError = false),
                playlists = state.playlists.copy(isLoading = true, isError = false),
                onlinePlaylists = state.onlinePlaylists.copy(isLoading = true, isError = false),
                isExactArtistLoading = true,
                exactArtist = null
            )
        }

        searchJob = viewModelScope.launch {
            // Fetch Playlists locally
            launch {
                try {
                    val p = playlistDao.getAllPlaylists().first()
                    val filteredPlaylists = p.filter { it.name.contains(query, ignoreCase = true) }
                    _uiState.update { it.copy(playlists = SearchSectionState(data = filteredPlaylists, isLoading = false)) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(playlists = it.playlists.copy(isLoading = false, isError = true)) }
                }
            }

            // Fetch Exact Artist match independently
            launch {
                try {
                    val artistRes = onlineRepo.getArtistInfo(query)
                    val artist = artistRes.getOrNull()
                    if (artist != null && artist.name.isNotEmpty()) {
                        _uiState.update { it.copy(exactArtist = artist, isExactArtistLoading = false) }
                    } else {
                        _uiState.update { it.copy(isExactArtistLoading = false) }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isExactArtistLoading = false) }
                }
            }

            // Fetch Online Search
            launch {
                try {
                    val localSongs = songDao.searchSongs(query).map { Song(it.id, it.title, it.artist, it.album, it.albumArt, source = it.source) }
                    if (localSongs.isNotEmpty()) {
                        _uiState.update { it.copy(songs = SearchSectionState(data = localSongs, isLoading = true)) }
                    }

                    val res = onlineRepo.searchOnline(query)
                    res.onSuccess { response ->
                        val combinedSongs = (response.songs + localSongs).distinctBy { "${it.title.lowercase()}_${it.artist.lowercase()}" }
                        if (response.songs.isNotEmpty()) {
                            songDao.insertSongs(response.songs.map { SongEntity(it.id, it.title, it.artist, it.album, it.albumArt, it.source) })
                        }
                        _uiState.update {
                            it.copy(
                                songs = SearchSectionState(data = combinedSongs, isLoading = false),
                                albums = SearchSectionState(data = response.albums, isLoading = false),
                                artists = SearchSectionState(data = response.artists, isLoading = false),
                                onlinePlaylists = SearchSectionState(data = response.playlists, isLoading = false)
                            )
                        }
                    }.onFailure {
                        _uiState.update {
                            it.copy(
                                songs = it.songs.copy(isLoading = false, isError = localSongs.isEmpty()),
                                albums = it.albums.copy(isLoading = false, isError = true),
                                artists = it.artists.copy(isLoading = false, isError = true),
                                onlinePlaylists = it.onlinePlaylists.copy(isLoading = false, isError = true)
                            )
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            songs = it.songs.copy(isLoading = false, isError = true),
                            albums = it.albums.copy(isLoading = false, isError = true),
                            artists = it.artists.copy(isLoading = false, isError = true),
                            onlinePlaylists = it.onlinePlaylists.copy(isLoading = false, isError = true)
                        )
                    }
                }
            }
        }
    }

    fun playSong(song: Song, contextList: List<Song>) {
        saveRecentSearch(_uiState.value.query)
        musicPlayerManager.playSongFromList(song, contextList)
    }

    fun addToQueue(song: Song) {
        if (com.pulse.music.ui.jam.JamSessionManager.isConnected.value) {
            com.pulse.music.ui.jam.JamSessionManager.addSongToQueue(song, "You")
        } else {
            musicPlayerManager.enqueue(song)
        }
    }
}
