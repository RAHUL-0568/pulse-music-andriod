package com.pulse.music.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulse.music.BuildConfig
import com.pulse.music.MainActivity

/**
 * Runs about once an hour in the background (even when the app is closed) and posts a notification
 * the first time it sees a newer version. Tapping the notification opens the Profile page.
 */
class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = UpdateManager.check(BuildConfig.VERSION_NAME)
        if (result is UpdateManager.UpdateCheck.Available) {
            val prefs = applicationContext.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            // one notification per version, not one per hour
            if (prefs.getString("notified_version", null) != result.info.newVersion) {
                if (postNotification(result.info.newVersion)) {
                    prefs.edit().putString("notified_version", result.info.newVersion).apply()
                }
            }
        }
        return Result.success()
    }

    private fun postNotification(version: String): Boolean {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("app_updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a new version of Pulse Music is available"
            }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_profile", true)
        }
        val pending = PendingIntent.getActivity(ctx, 4711, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(ctx, "app_updates")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update available")
            .setContentText("Pulse Music $version is ready. Tap to install.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(ctx).notify(4711, notification)
        return true
    }
}
