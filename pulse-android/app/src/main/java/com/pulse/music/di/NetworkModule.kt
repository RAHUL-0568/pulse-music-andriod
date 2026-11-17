package com.pulse.music.di

import com.pulse.music.data.network.PulseApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pulse.music.data.repository.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import com.pulse.music.BuildConfig // Automatically generated if configured in build.gradle

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val headerInterceptor = Interceptor { chain ->
            val original = chain.request()
            val tokenKey = stringPreferencesKey("jwt_token")
            // NonCancellable prevents JobCancelledException when the calling
            // coroutine/request is cancelled (e.g. back press, screen rotation)
            val token = try {
                runBlocking(NonCancellable) {
                    context.dataStore.data.map { it[tokenKey] }.first()
                }
            } catch (e: Exception) {
                null
            }
            val builder = original.newBuilder()
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                
            if (!token.isNullOrEmpty()) {
                builder.header("Authorization", "Bearer $token")
            }
            
            val request = builder.method(original.method, original.body).build()
            chain.proceed(request)
        }

        val failoverInterceptor = Interceptor { chain ->
            val request = chain.request()
            
            val fallbackUrls = listOf(
                BuildConfig.API_BASE_URL,
                "https://pulse-music-backend-fallback-1.onrender.com/",
                "https://pulse-music-backend-fallback-2.onrender.com/"
            )

            val prefs = context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE)
            var currentIndex = prefs.getInt("active_backend_index", 0)

            var targetUrl = request.url.newBuilder()
                .scheme(fallbackUrls[currentIndex].toHttpUrl().scheme)
                .host(fallbackUrls[currentIndex].toHttpUrl().host)
                .port(fallbackUrls[currentIndex].toHttpUrl().port)
                .build()

            var finalRequest = request.newBuilder().url(targetUrl).build()
            var response: okhttp3.Response? = null
            var exception: Exception? = null

            try {
                response = chain.proceed(finalRequest)
            } catch (e: Exception) {
                exception = e
            }

            // If the current backend is dead (timeout, 5xx, or crash), fail over to the next one!
            if (response == null || !response.isSuccessful && response.code >= 500) {
                for (i in 1 until fallbackUrls.size) {
                    response?.close()
                    currentIndex = (currentIndex + 1) % fallbackUrls.size

                    targetUrl = request.url.newBuilder()
                        .scheme(fallbackUrls[currentIndex].toHttpUrl().scheme)
                        .host(fallbackUrls[currentIndex].toHttpUrl().host)
                        .port(fallbackUrls[currentIndex].toHttpUrl().port)
                        .build()

                    finalRequest = request.newBuilder().url(targetUrl).build()

                    try {
                        response = chain.proceed(finalRequest)
                        if (response.isSuccessful || response.code < 500) {
                            exception = null
                            // Save this working backend permanently!
                            prefs.edit().putInt("active_backend_index", currentIndex).apply()
                            break
                        }
                    } catch (e: Exception) {
                        exception = e
                    }
                }
            }

            if (exception != null && response == null) {
                throw exception
            }

            response ?: chain.proceed(request)
        }

        // Tells the app when the admin blocked / removed this account.
        // (Login attempts show the server message on the login screen themselves.)
        val accountGuardInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (!chain.request().url.encodedPath.contains("/auth/")) {
                if (response.header("X-Account-Blocked") != null) {
                    com.pulse.music.data.network.AccountEvents.report(
                        "Your account has been blocked. Contact support if you think this is a mistake."
                    )
                } else if (response.header("X-Account-Removed") != null) {
                    com.pulse.music.data.network.AccountEvents.report("This account no longer exists.")
                }
            }
            response
        }

        // 50 MB is plenty for JSON. (It used to be 1 TB.)
        val cache = okhttp3.Cache(java.io.File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)

        // Public catalogue data is cached for a sensible time per endpoint, so repeat
        // visits cost no data. Anything personal is never stored.
        val cacheControlInterceptor = Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method != "GET") return@Interceptor response

            val path = request.url.encodedPath
            val hour = 3600L
            val day = 24 * hour
            val maxAgeSecs: Long? = when {
                path.contains("/user/") || path.contains("/auth/") ||
                    path.contains("/jam/") || path.contains("/admin/") || path.endsWith("/health") -> null
                path.contains("/lyrics") -> 30 * day
                path.contains("/artist/") || path.contains("/album/") -> 7 * day
                path.contains("/search") || path.contains("/recommendations") -> day
                path.contains("/trending") -> 6 * hour
                path.contains("/home") -> 2 * hour
                else -> 10 * 60L
            }
            val value = if (maxAgeSecs == null) "no-store" else "public, max-age=$maxAgeSecs"
            response.newBuilder()
                .header("Cache-Control", value)
                .removeHeader("Pragma")
                .removeHeader("Vary")
                .build()
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(headerInterceptor)
            .addInterceptor(failoverInterceptor)
            .addInterceptor(accountGuardInterceptor)
            .addNetworkInterceptor(cacheControlInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // Adjust to Moshi or KotlinxSerialization if needed
            .build()
    }

    @Provides
    @Singleton
    fun providePulseApiService(retrofit: Retrofit): PulseApiService {
        return retrofit.create(PulseApiService::class.java)
    }
}



