package com.pulse.music.domain

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArt: String? = null,
    val durationMs: Long? = null,
    val source: String
)

data class Artist(
    val id: String,
    val name: String,
    val bio: String? = null,
    val image: String? = null,
    val genres: List<String> = emptyList(),
    val similar: List<Artist> = emptyList(),
    val topTracks: List<Song> = emptyList(),
    val albums: List<Album>? = emptyList(),
    val score: Int? = null
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverArt: String? = null,
    val year: Int? = null,
    val tracks: List<Song> = emptyList()
)

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class Lyrics(
    val synced: List<LyricLine>? = null,
    val plain: String? = null,
    val source: String,
    val cached: Boolean = false
)

data class Playlist(
    val id: String,
    val title: String,
    val image: String? = null,
    val songCount: Int? = null,
    val source: String
)

data class SearchResponse(
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

data class JamRoomInfo(
    val jamId: String,
    val roomCode: String,
    val name: String,
    val hostId: String,
    val memberCount: Int = 0,
    val isActive: Boolean = false,
    val currentSongTitle: String? = null
)
