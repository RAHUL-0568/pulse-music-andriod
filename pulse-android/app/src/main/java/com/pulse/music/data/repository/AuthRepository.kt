package com.pulse.music.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pulse.music.data.network.AuthResponse
import com.pulse.music.data.network.LoginRequest
import com.pulse.music.data.network.PulseApiService
import com.pulse.music.data.network.RegisterRequest
import com.pulse.music.data.network.ForgotPasswordRequest
import com.pulse.music.data.network.ForgotPasswordResponse
import com.pulse.music.data.network.VerifyCodeRequest
import com.pulse.music.data.network.ResetPasswordRequest
import com.pulse.music.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

val Context.dataStore by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthRepository @Inject constructor(
    private val api: PulseApiService,
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    private val TOKEN_KEY = stringPreferencesKey("jwt_token")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val EMAIL_KEY = stringPreferencesKey("email")
    private val PROFILE_PIC_KEY = stringPreferencesKey("profile_pic_uri")
    
    // Playback Settings
    private val HIGH_QUALITY_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("high_quality")
    private val SPATIAL_AUDIO_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("spatial_audio")
    private val GAPLESS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("gapless")
    private val CROSSFADE_KEY = androidx.datastore.preferences.core.intPreferencesKey("crossfade")

    val authToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_KEY]
    }
    
    val email: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[EMAIL_KEY]
    }

    val userId: Flow<String?> = context.dataStore.data.map { prefs ->
        val token = prefs[TOKEN_KEY]
        if (token != null) {
            try {
                val parts = token.split(".")
                if (parts.size == 3) {
                    val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
                    val json = org.json.JSONObject(payload)
                    json.optString("sub")
                } else null
            } catch (e: Exception) { null }
        } else null
    }

    val username: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USERNAME_KEY]
    }

    val profilePicUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PROFILE_PIC_KEY]
    }

    val highQuality: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[HIGH_QUALITY_KEY] ?: true }
    val spatialAudio: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[SPATIAL_AUDIO_KEY] ?: true }
    val gapless: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[GAPLESS_KEY] ?: false }
    val crossfade: Flow<Int> = context.dataStore.data.map { prefs -> prefs[CROSSFADE_KEY] ?: 0 }

    private fun parseError(e: Exception): Exception {
        if (e is java.io.IOException) {
            return Exception("Can't reach the server. Check your internet connection.")
        }
        if (e is retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                if (errorBody != null) {
                    val json = org.json.JSONObject(errorBody)
                    if (json.has("detail")) {
                        return Exception(json.getString("detail"))
                    } else if (json.has("message")) {
                        return Exception(json.getString("message"))
                    }
                }
            } catch (ex: Exception) {
                // Ignore parse errors
            }
        }
        return e
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            saveAuth(response)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun requestRegisterOtp(email: String): Result<ForgotPasswordResponse> {
        return try {
            val response = api.requestRegisterOtp(ForgotPasswordRequest(email))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun register(username: String, email: String, password: String, code: String): Result<AuthResponse> {
        return try {
            val response = api.register(RegisterRequest(username, email, password, code))
            saveAuth(response)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun socialLogin(provider: String, token: String): Result<AuthResponse> {
        return try {
            val response = if (provider == "google") {
                api.googleLogin(com.pulse.music.data.network.SocialLoginRequest(token))
            } else {
                api.facebookLogin(com.pulse.music.data.network.SocialLoginRequest(token))
            }
            saveAuth(response)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun forgotPassword(email: String): Result<ForgotPasswordResponse> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(email))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun verifyCode(email: String, code: String): Result<ForgotPasswordResponse> {
        return try {
            val response = api.verifyCode(VerifyCodeRequest(email, code))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<ForgotPasswordResponse> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(email, code, newPassword))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(parseError(e))
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(PROFILE_PIC_KEY)
        }
        
        // Clear all persistent storage to prevent data leaking to new users
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.clearAllTables()
            
            try {
                // Clear SharedPreferences caches
                context.getSharedPreferences("pulse_actual_recent_plays", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("pulse_library_cache", Context.MODE_PRIVATE).edit().clear().apply()
                
                // Clear JSON file caches
                val homeCache = java.io.File(context.cacheDir, "home_cache.json")
                if (homeCache.exists()) homeCache.delete()
                
                // Clear static memory states
                com.pulse.music.ui.home.HomeViewModel.cachedUiState = null
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    suspend fun updateProfilePic(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[PROFILE_PIC_KEY] = uri
        }
    }

    suspend fun uploadProfilePic(context: Context, uriString: String): Result<String> {
        return try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            
            if (bytes == null) {
                return Result.failure(Exception("Failed to read image data"))
            }
            
            val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
            val body = okhttp3.MultipartBody.Part.createFormData("file", "profile.jpg", requestFile)
            
            val result = api.uploadProfilePic(body)
            val serverUrl = result["profilePic"]
            
            if (!serverUrl.isNullOrEmpty()) {
                context.dataStore.edit { prefs ->
                    prefs[PROFILE_PIC_KEY] = serverUrl
                }
                Result.success(serverUrl)
            } else {
                Result.failure(Exception("Failed to retrieve profile image URL from server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePlaybackSettings(highQuality: Boolean, spatialAudio: Boolean, gapless: Boolean, crossfade: Int) {
        context.dataStore.edit { prefs ->
            prefs[HIGH_QUALITY_KEY] = highQuality
            prefs[SPATIAL_AUDIO_KEY] = spatialAudio
            prefs[GAPLESS_KEY] = gapless
            prefs[CROSSFADE_KEY] = crossfade
        }
    }

    private suspend fun saveAuth(response: AuthResponse) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = response.token
            prefs[USERNAME_KEY] = response.username
            prefs[EMAIL_KEY] = response.email
            response.profilePic?.let { pic ->
                prefs[PROFILE_PIC_KEY] = pic
            }
        }
    }
}
