package com.pulse.music.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates come from the GitHub releases of the app repo. Every version I publish becomes a release
 * named v<versionName> with the APK attached. A newer version sets [available], which puts the red
 * badge on the Profile tab and the "Update available" row on the Profile page.
 */
object UpdateManager {

    private const val GITHUB_OWNER = "RAHUL-0568"
    private const val GITHUB_REPO = "pulse-music-andriod"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val CHECK_EVERY_MS = 10 * 60 * 1000L

    val isDownloading = MutableStateFlow(false)
    val downloadProgress = MutableStateFlow(0f)
    val downloadedBytes = MutableStateFlow(0L)
    val downloadStatusText = MutableStateFlow("Starting...")

    data class UpdateInfo(val hasUpdate: Boolean, val newVersion: String, val downloadUrl: String)

    sealed class UpdateCheck {
        object UpToDate : UpdateCheck()
        object Failed : UpdateCheck()
        data class Available(val info: UpdateInfo) : UpdateCheck()
    }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    /** Non-null when a newer version is published. Drives the Profile badge. */
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    private var lastGoodCheckMs = 0L

    /** "2.10.0" is newer than "2.9.1": compare number by number, not as one big number. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = Regex("\\d+").findAll(remote).map { it.value.toIntOrNull() ?: 0 }.toList()
        val b = Regex("\\d+").findAll(local).map { it.value.toIntOrNull() ?: 0 }.toList()
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Asks GitHub for the latest release. Never throws; a network problem is [UpdateCheck.Failed], not "up to date". */
    suspend fun check(currentVersion: String): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val connection = URL(GITHUB_API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "PulseMusic-App")

            val code = connection.responseCode
            if (code == 404) { // no release published yet
                lastGoodCheckMs = System.currentTimeMillis()
                _available.value = null
                return@withContext UpdateCheck.UpToDate
            }
            if (code != HttpURLConnection.HTTP_OK) return@withContext UpdateCheck.Failed

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tagName = json.getString("tag_name")

            var apkUrl = ""
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            lastGoodCheckMs = System.currentTimeMillis()
            if (isNewer(tagName, currentVersion) && apkUrl.isNotEmpty()) {
                val info = UpdateInfo(true, tagName, apkUrl)
                _available.value = info
                UpdateCheck.Available(info)
            } else {
                _available.value = null
                UpdateCheck.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheck.Failed
        }
    }

    /** Quiet background check (app start / coming back to the app), at most every 30 minutes. */
    suspend fun checkIfStale(currentVersion: String) {
        if (System.currentTimeMillis() - lastGoodCheckMs < CHECK_EVERY_MS) return
        check(currentVersion)
    }

    // Kept for older callers
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return when (val r = check(currentVersion)) {
            is UpdateCheck.Available -> r.info
            else -> UpdateInfo(false, currentVersion, "")
        }
    }

    fun cleanupOldUpdates(context: Context) {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val files = dir?.listFiles { _, name -> name.startsWith("Pulse-update-") && name.endsWith(".apk") }
            files?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Android asks the user to allow "install unknown apps" once for this app. */
    fun canInstall(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Open Settings and allow Pulse to install apps.", Toast.LENGTH_LONG).show()
        }
    }

    // Own scope, so leaving the Profile page doesn't cancel a download that is half done.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun downloadAndInstallUpdate(context: Context, downloadUrl: String, scope: CoroutineScope = appScope) {
        if (isDownloading.value) return
        isDownloading.value = true
        downloadProgress.value = 0f
        downloadedBytes.value = 0L
        downloadStatusText.value = "Starting..."

        cleanupOldUpdates(context)

        scope.launch(Dispatchers.IO) {
            try {
                var connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000

                downloadStatusText.value = "Connecting..."
                connection.connect()

                // GitHub redirects to its file servers; follow it ourselves (also across http/https)
                var redirectCount = 0
                while (connection.responseCode / 100 == 3 && redirectCount < 5) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    connection = URL(newUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.connect()
                    redirectCount++
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    downloadStatusText.value = "Failed: ${connection.responseCode}"
                    isDownloading.value = false
                    return@launch
                }

                val contentLength = connection.contentLength
                downloadStatusText.value = "Downloading..."

                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (dir != null && !dir.exists()) dir.mkdirs()
                val outputFile = File(dir, "Pulse-update-${System.currentTimeMillis()}.apk")

                val input = connection.inputStream
                val output = java.io.FileOutputStream(outputFile)
                val data = ByteArray(8192)
                var total = 0L
                var count: Int
                var lastUpdate = System.currentTimeMillis()

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 100) {
                        downloadedBytes.value = total
                        downloadProgress.value = if (contentLength > 0) total.toFloat() / contentLength.toFloat() else -1f
                        lastUpdate = now
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                downloadProgress.value = 1f
                downloadStatusText.value = "Download complete"
                isDownloading.value = false

                withContext(Dispatchers.Main) { installApk(context, outputFile) }
            } catch (e: Exception) {
                e.printStackTrace()
                downloadStatusText.value = "Download failed"
                isDownloading.value = false
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Couldn't open the installer.", Toast.LENGTH_SHORT).show()
        }
    }
}
