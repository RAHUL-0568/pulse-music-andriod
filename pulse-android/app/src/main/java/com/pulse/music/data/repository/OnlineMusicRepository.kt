package com.pulse.music.data.repository

import com.pulse.music.data.network.PulseApiService
import com.pulse.music.domain.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineMusicRepository @Inject constructor(
    private val apiService: PulseApiService
) {
    suspend fun getMusicCapsule(): Result<com.pulse.music.data.network.MusicCapsuleResponse> {
        return try {
            Result.success(apiService.getMusicCapsule())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchOnline(query: String): Result<SearchResponse> {
        return try {
            val response = apiService.search(query)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getLyrics(title: String, artist: String, songId: String? = null): Result<Lyrics> {
        return try {
            Result.success(apiService.getLyrics(title, artist, songId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getTrending(): Result<List<Song>> {
        return try {
            Result.success(apiService.getTrending())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getRecommendations(artist: String, track: String): Result<List<Song>> {
        return try {
            Result.success(apiService.getRecommendations(artist, track))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getHome(): Result<com.pulse.music.data.network.HomeResponse> {
        return try {
            Result.success(apiService.getHome())
        } catch (e: Exception) {
            android.util.Log.e("PulseAPI", "getHome failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun getArtistInfo(name: String): Result<Artist> {
        return try {
            Result.success(apiService.getArtist(name))
        } catch(e: Exception) {
            android.util.Log.e("PulseAPI", "getArtistInfo failed for name: $name", e)
            Result.failure(e)
        }
    }

    suspend fun getRecentSongs(): Result<List<Song>> {
        return try {
            Result.success(apiService.getRecentSongs())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addRecentSong(song: Song): Result<Map<String, String>> {
        return try {
            val req = mapOf<String, Any>(
                "id" to song.id,
                "title" to song.title,
                "artist" to song.artist,
                "album" to (song.album ?: ""),
                "albumArt" to (song.albumArt ?: ""),
                "source" to song.source,
                "timestamp" to System.currentTimeMillis().toDouble()
            )
            Result.success(apiService.addRecentSong(req))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeRecentSong(songId: String): Result<Map<String, String>> {
        return try {
            Result.success(apiService.removeRecentSong(songId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlbumInfo(id: String): Result<Album> {
        return try {
            Result.success(apiService.getAlbum(id))
        } catch(e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentSearches(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getRecentSearches())
        } catch(e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addRecentSearch(query: String): Result<Map<String, String>> {
        return try {
            val req = mapOf("query" to query, "timestamp" to System.currentTimeMillis().toString())
            Result.success(apiService.addRecentSearch(req))
        } catch(e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearRecentSearches(): Result<Map<String, String>> {
        return try {
            Result.success(apiService.clearRecentSearches())
        } catch(e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeRecentSearch(query: String): Result<Map<String, String>> {
        return try {
            Result.success(apiService.removeRecentSearch(query))
        } catch(e: Exception) {
            Result.failure(e)
        }
    }
}
