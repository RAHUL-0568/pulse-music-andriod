package com.pulse.music

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

@HiltAndroidApp
class PulseApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        com.pulse.music.ui.jam.JamSessionManager.applicationContext = this

        // Check for a new app version about once an hour, even while the app is closed
        val updateCheck = androidx.work.PeriodicWorkRequestBuilder<com.pulse.music.update.UpdateCheckWorker>(1, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "pulse_update_check",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            updateCheck
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            // Keep covers on disk even when the CDN sends short cache headers.
            .respectCacheHeaders(false)
            .components {
                // Small thumbnails don't need the 500x500 cover: ask the CDN for a smaller one.
                add(object : coil.intercept.Interceptor {
                    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                        val data = chain.request.data
                        val widthPx = (chain.size.width as? coil.size.Dimension.Pixels)?.px
                        if (data is String && widthPx != null && data.contains("saavncdn.com") && data.contains("500x500")) {
                            val smaller = when {
                                widthPx <= 200 -> "150x150"
                                widthPx <= 360 -> "250x250"
                                else -> null
                            }
                            if (smaller != null) {
                                return chain.proceed(chain.request.newBuilder().data(data.replace("500x500", smaller)).build())
                            }
                        }
                        return chain.proceed(chain.request)
                    }
                })
            }
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
