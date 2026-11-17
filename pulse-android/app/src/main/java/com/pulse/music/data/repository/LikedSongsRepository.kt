package com.pulse.music.data.repository

import com.pulse.music.data.local.LikedSongDao
import com.pulse.music.data.local.LikedSongEntity
import com.pulse.music.domain.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

import com.pulse.music.data.network.PulseApiService

class LikedSongsRepository @Inject constructor(
    private val likedSongDao: LikedSongDao,
    private val apiService: PulseApiService
) {
    val likedSongs: Flow<List<Song>> = likedSongDao.getAllLikedSongs().map { entities ->
        entities.map { entity ->
            Song(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                albumArt = entity.albumArt,
                durationMs = entity.durationMs,
                source = entity.source
            )
        }
    }

    suspend fun toggleLiked(song: Song) {
        // Toggle based on current state (we can check via DAO, or just handle in VM, but for simplicity, we provide add/remove)
        // Wait, we need a way to check if liked.
    }
    
    suspend fun syncLikedSongs() {
        try {
            val remoteSongs = apiService.getLikedSongs()
            remoteSongs.forEach { song ->
                likedSongDao.insertLikedSong(
                    LikedSongEntity(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        albumArt = song.albumArt,
                        durationMs = song.durationMs,
                        source = song.source
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addLikedSong(song: Song) {
        likedSongDao.insertLikedSong(
            LikedSongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                albumArt = song.albumArt,
                durationMs = song.durationMs,
                source = song.source
            )
        )
        try {
            apiService.addLikedSong(song)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun removeLikedSong(songId: String) {
        likedSongDao.deleteLikedSong(songId)
        try {
            apiService.removeLikedSong(songId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun isSongLiked(songId: String): Flow<Boolean> {
        return likedSongDao.isSongLiked(songId)
    }
}
