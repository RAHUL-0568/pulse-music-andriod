package com.pulse.music.data.local

import androidx.room.*

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String, 
    val title: String, 
    val artist: String, 
    val album: String?,
    val albumArt: String?,
    val source: String
)

@Entity(tableName = "albums")
data class AlbumEntity(@PrimaryKey val id: String, val title: String, val artist: String)

@Entity(tableName = "artists")
data class ArtistEntity(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs")
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArt: String?,
    val durationMs: Long?,
    val source: String,
    val addedAt: Long = System.currentTimeMillis()
)



@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>
    
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    suspend fun searchSongs(query: String): List<SongEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): kotlinx.coroutines.flow.Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Int, newName: String)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun getSongsForPlaylist(playlistId: Int): kotlinx.coroutines.flow.Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongToPlaylist(song: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String)
}



@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArt: String?,
    val durationMs: Long?,
    val source: String,
    val likedAt: Long = System.currentTimeMillis()
)

@Dao
interface LikedSongDao {
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    fun getAllLikedSongs(): kotlinx.coroutines.flow.Flow<List<LikedSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE id = :songId")
    suspend fun deleteLikedSong(songId: String)
    
    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE id = :songId)")
    fun isSongLiked(songId: String): kotlinx.coroutines.flow.Flow<Boolean>
}

@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val image: String?,
    val followedAt: Long = System.currentTimeMillis()
)

@Dao
interface FollowedArtistDao {
    @Query("SELECT * FROM followed_artists ORDER BY name ASC")
    fun getAllFollowedArtists(): kotlinx.coroutines.flow.Flow<List<FollowedArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowedArtist(artist: FollowedArtistEntity)

    @Query("DELETE FROM followed_artists WHERE id = :artistIdOrName OR name = :artistIdOrName")
    suspend fun deleteFollowedArtist(artistIdOrName: String)
    
    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE id = :artistIdOrName OR name = :artistIdOrName)")
    fun isArtistFollowed(artistIdOrName: String): kotlinx.coroutines.flow.Flow<Boolean>
}

@Database(
    entities = [
        SongEntity::class, 
        AlbumEntity::class, 
        ArtistEntity::class, 
        PlaylistEntity::class, 
        PlaylistSongEntity::class, 
        LikedSongEntity::class,
        FollowedArtistEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun likedSongDao(): LikedSongDao
    abstract fun followedArtistDao(): FollowedArtistDao
}
