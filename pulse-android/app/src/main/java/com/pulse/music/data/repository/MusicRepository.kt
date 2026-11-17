package com.pulse.music.data.repository

import com.pulse.music.data.local.SongDao
import com.pulse.music.data.local.SongEntity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.pulse.music.domain.*
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class MusicRepository @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val songDao: SongDao,
    @ApplicationContext private val context: Context
) {
    fun search(query: String): Flow<List<Any>> = flow {
        // Local fallback checked first
        val localEntities = songDao.searchSongs(query)
        val localSongs = localEntities.map { Song(it.id, it.title, it.artist, it.album, it.albumArt, source = it.source) }
        if (localSongs.isNotEmpty()) {
            emit(localSongs)
        }
        
        val online = onlineRepo.searchOnline(query)
        if (online.isSuccess) {
            val data = online.getOrNull()
            val apiSongs = data?.songs ?: emptyList()
            val apiArtists = data?.artists ?: emptyList()
            val apiAlbums = data?.albums ?: emptyList()
            
            // Deduplicate by title+artist
            val combined = (localSongs + apiSongs).distinctBy { "${it.title.lowercase()}_${it.artist.lowercase()}" }
            
            // Local songs always shown first
            val sortedCombined = combined.sortedBy { song -> if (localSongs.any { it.id == song.id }) 0 else 1 }
            
            // Save API results to room immediately
            if (apiSongs.isNotEmpty()) {
                songDao.insertSongs(apiSongs.map { SongEntity(it.id, it.title, it.artist, it.album, it.albumArt, it.source) })
            }
            
            val initialResults = buildList<Any> {
                addAll(apiArtists)
                addAll(apiAlbums)
                addAll(sortedCombined)
            }
            
            emit(initialResults)

            // Try to fetch artist info matching the query exactly to populate artist section
            if (apiArtists.isEmpty()) {
                val artistResult = onlineRepo.getArtistInfo(query).getOrNull()
                if (artistResult != null && artistResult.name.isNotEmpty()) {
                    val finalResults = buildList<Any> {
                        add(artistResult)
                        addAll(apiAlbums)
                        addAll(sortedCombined)
                    }
                    emit(finalResults)
                }
            }
        } else if (localSongs.isEmpty()) {
            // Only show error if Room also empty (Offline First rules)
            throw online.exceptionOrNull() ?: Exception("Offline and empty cache")
        }
    }
    
    fun getTrending(): Flow<List<Song>> = flow {
        val online = onlineRepo.getTrending()
        if (online.isSuccess) emit(online.getOrDefault(emptyList()))
    }
    
    fun getLyrics(title: String, artist: String, songId: String? = null): Flow<Lyrics?> = flow {
        val trackId = "${title}_${artist}".replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val cacheFile = File(context.cacheDir, "lyrics_$trackId.json")
        
        if (cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val lyrics = com.google.gson.Gson().fromJson(json, Lyrics::class.java)
                emit(lyrics)
                return@flow
            } catch (e: Exception) {
                cacheFile.delete()
            }
        }

        val online = onlineRepo.getLyrics(title, artist, songId)
        if (online.isSuccess) {
            val lyrics = online.getOrNull()
            if (lyrics != null) {
                try {
                    val json = com.google.gson.Gson().toJson(lyrics)
                    cacheFile.writeText(json)
                } catch (e: Exception) {
                    // Ignore write error
                }
            }
            emit(lyrics)
        } else {
            emit(null)
        }
    }
    
    fun getRecommendations(artist: String, track: String): Flow<List<Song>> = flow {
        val online = onlineRepo.getRecommendations(artist, track)
        if (online.isSuccess) emit(online.getOrDefault(emptyList())) else emit(emptyList())
    }
    
    fun getArtistInfo(name: String): Flow<Artist?> = flow {
        val online = onlineRepo.getArtistInfo(name)
        if (online.isSuccess) {
            emit(online.getOrNull())
        } else {
            // Emit a fallback so the UI stops spinning
            emit(Artist(id = name, name = name, bio = "Artist not found or offline", topTracks = emptyList(), albums = emptyList()))
        }
    }

    fun getAlbumInfo(id: String): Flow<Album?> = flow {
        val online = onlineRepo.getAlbumInfo(id)
        if (online.isSuccess) {
            emit(online.getOrNull())
        } else {
            // Emit a fallback so the UI stops spinning
            emit(Album(id = id, title = "Unknown Album", artist = "Unknown", coverArt = null, year = 0, tracks = emptyList()))
        }
    }
}
