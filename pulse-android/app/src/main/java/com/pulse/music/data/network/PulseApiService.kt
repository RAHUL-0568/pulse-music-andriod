package com.pulse.music.data.network

import com.pulse.music.domain.*
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

data class AuthResponse(
    val token: String,
    val username: String,
    val email: String,
    val profilePic: String? = null
)


data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val username: String, val email: String, val password: String, val code: String)
data class ForgotPasswordRequest(val email: String)
data class ForgotPasswordResponse(val status: String, val message: String)
data class VerifyCodeRequest(val email: String, val code: String)
data class ResetPasswordRequest(val email: String, val code: String, val new_password: String)

data class SocialLoginRequest(val token: String)

data class HomeModule(
    val title: String,
    val items: List<Playlist>
)

data class HomeResponse(
    val modules: List<HomeModule>
)

interface PulseApiService {
    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "song"
    ): SearchResponse

    @GET("api/v1/lyrics")
    suspend fun getLyrics(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("songId") songId: String? = null
    ): Lyrics

    @GET("api/v1/artist/{name}")
    suspend fun getArtist(
        @Path("name") name: String
    ): Artist

    @GET("api/v1/recommendations")
    suspend fun getRecommendations(
        @Query("artist") artist: String,
        @Query("track") track: String
    ): List<Song>

    @GET("api/v1/trending")
    suspend fun getTrending(
        @Query("country") country: String = "IN"
    ): List<Song>

    @GET("api/v1/health")
    suspend fun getHealth(): Map<String, Any>

    @GET("api/v1/home")
    suspend fun getHome(): HomeResponse

    @GET("api/v1/album/{id}")
    suspend fun getAlbum(
        @Path("id") id: String
    ): Album

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/request-register-otp")
    suspend fun requestRegisterOtp(@Body request: ForgotPasswordRequest): ForgotPasswordResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body request: SocialLoginRequest): AuthResponse

    @POST("api/v1/auth/facebook")
    suspend fun facebookLogin(@Body request: SocialLoginRequest): AuthResponse

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ForgotPasswordResponse

    @POST("api/v1/auth/verify-code")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): ForgotPasswordResponse

    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): ForgotPasswordResponse

    @GET("api/v1/user/liked")
    suspend fun getLikedSongs(): List<Song>

    @POST("api/v1/user/liked")
    suspend fun addLikedSong(@Body song: Song): Map<String, String>

    @retrofit2.http.DELETE("api/v1/user/liked/{songId}")
    suspend fun removeLikedSong(@Path("songId") songId: String): Map<String, String>

    @GET("api/v1/user/searches")
    suspend fun getRecentSearches(): List<Map<String, Any>>

    @POST("api/v1/user/searches")
    suspend fun addRecentSearch(@Body request: Map<String, String>): Map<String, String>

    @retrofit2.http.DELETE("api/v1/user/searches")
    suspend fun clearRecentSearches(): Map<String, String>

    @retrofit2.http.DELETE("api/v1/user/searches/{query}")
    suspend fun removeRecentSearch(@retrofit2.http.Path("query") query: String): Map<String, String>

    @GET("api/v1/user/recent-songs")
    suspend fun getRecentSongs(): List<Song>

    @POST("api/v1/user/recent-songs")
    suspend fun addRecentSong(@Body song: Map<String, @JvmSuppressWildcards Any>): Map<String, String>

    @retrofit2.http.DELETE("api/v1/user/recent-songs/{songId}")
    suspend fun removeRecentSong(@Path("songId") songId: String): Map<String, String>

    @GET("api/v1/user/follows")
    suspend fun getFollowedArtists(): List<Map<String, String>>

    @POST("api/v1/user/follows")
    suspend fun followArtist(@Query("artist_id") artistId: String, @Query("name") name: String, @Query("image") image: String): Map<String, String>

    @GET("api/v1/user/playlists")
    suspend fun getPlaylists(): List<Map<String, Any>>

    @POST("api/v1/user/playlists")
    suspend fun createPlaylist(@Query("name") name: String): Map<String, String>

    @GET("api/v1/user/music-capsule")
    suspend fun getMusicCapsule(): MusicCapsuleResponse
    
    @POST("api/v1/user/playlists/spotify-import")
    suspend fun importSpotifyPlaylist(@Body request: Map<String, String>): Map<String, Any>

    @retrofit2.http.DELETE("api/v1/user/playlists/{playlistId}")
    suspend fun deletePlaylist(@Path("playlistId") playlistId: String): Map<String, String>

    @retrofit2.http.PUT("api/v1/user/playlists/{playlistId}")
    suspend fun renamePlaylist(@Path("playlistId") playlistId: String, @Query("name") name: String): Map<String, String>

    @POST("api/v1/user/playlists/{playlistId}/songs")
    suspend fun addSongToPlaylist(@Path("playlistId") playlistId: String, @Body song: Song): Map<String, String>

    @retrofit2.http.DELETE("api/v1/user/playlists/{playlistId}/songs/{songId}")
    suspend fun removeSongFromPlaylist(@Path("playlistId") playlistId: String, @Path("songId") songId: String): Map<String, String>

    @retrofit2.http.Multipart
    @POST("api/v1/user/profile-pic")
    suspend fun uploadProfilePic(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): Map<String, String>

    // Jam APIs
    @retrofit2.http.Headers("Cache-Control: no-cache, no-store")
    @GET("api/v1/jam/my-jams")
    suspend fun getMyJams(): List<com.pulse.music.domain.JamRoomInfo>

    @POST("api/v1/jam/create")
    suspend fun createJam(@Query("name") name: String): com.pulse.music.domain.JamRoomInfo

    @retrofit2.http.DELETE("api/v1/jam/{jam_id}")
    suspend fun deleteJam(@Path("jam_id") jamId: String): Map<String, String>

    @retrofit2.http.DELETE("api/v1/jam/{jam_id}/leave")
    suspend fun leaveJam(@Path("jam_id") jamId: String): Map<String, String>
}

data class MusicCapsuleSong(
    val id: String,
    val title: String,
    val artist: String,
    val albumArt: String,
    val playCount: Int
)

data class MusicCapsuleArtist(
    val name: String,
    val playCount: Int
)

data class MusicCapsuleResponse(
    val totalPlays: Int,
    val uniqueSongs: Int,
    val topSongs: List<MusicCapsuleSong>,
    val topArtists: List<MusicCapsuleArtist>
)
