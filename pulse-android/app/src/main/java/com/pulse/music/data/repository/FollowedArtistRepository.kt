package com.pulse.music.data.repository

import com.pulse.music.data.local.FollowedArtistDao
import com.pulse.music.data.local.FollowedArtistEntity
import com.pulse.music.domain.Artist
import com.pulse.music.data.network.PulseApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FollowedArtistRepository @Inject constructor(
    private val followedArtistDao: FollowedArtistDao,
    private val apiService: PulseApiService
) {
    val followedArtists: Flow<List<Artist>> = followedArtistDao.getAllFollowedArtists().map { entities ->
        entities.map { entity ->
            Artist(
                id = entity.id,
                name = entity.name,
                image = entity.image ?: "",
                bio = null,
                similar = emptyList(),
                genres = emptyList(),
                albums = emptyList()
            )
        }
    }

    suspend fun syncFollowedArtists() {
        try {
            val remoteArtists = apiService.getFollowedArtists()
            remoteArtists.forEach { artistMap ->
                val id = artistMap["id"] ?: return@forEach
                val name = artistMap["name"] ?: ""
                val image = artistMap["image"] ?: ""
                followedArtistDao.insertFollowedArtist(FollowedArtistEntity(id, name, image))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addFollowedArtist(artist: Artist) {
        followedArtistDao.insertFollowedArtist(
            FollowedArtistEntity(
                id = artist.id,
                name = artist.name,
                image = artist.image ?: ""
            )
        )
        try {
            apiService.followArtist(artist.id, artist.name, artist.image ?: "")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun removeFollowedArtist(artistId: String) {
        followedArtistDao.deleteFollowedArtist(artistId)
    }

    fun isArtistFollowed(artistId: String): Flow<Boolean> {
        return followedArtistDao.isArtistFollowed(artistId)
    }
}
