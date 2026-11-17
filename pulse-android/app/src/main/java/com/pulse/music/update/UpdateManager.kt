package com.pulse.music.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object UpdateManager {

    private const val GITHUB_OWNER = "RAHUL-0568"
    private const val GITHUB_REPO = "Pulse-Music-Releases"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    val isDownloading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val downloadProgress = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val downloadedBytes = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val downloadStatusText = kotlinx.coroutines.flow.MutableStateFlow("Starting...")

    data class UpdateInfo(val hasUpdate: Boolean, val newVersion: String, val downloadUrl: String)

    fun cleanupOldUpdates(context: Context) {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val files = dir?.listFiles { _, name -> name.startsWith("Pulse-update-") && name.endsWith(".apk") }
            files?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val tagName = json.getString("tag_name")
                
                val cleanTag = tagName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                val cleanCurrent = currentVersion.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                val isNewer = cleanTag > cleanCurrent

                var apkUrl = ""
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (isNewer && apkUrl.isNotEmpty()) {
                    return@withContext UpdateInfo(true, tagName, apkUrl)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext UpdateInfo(false, currentVersion, "")
    }

    fun downloadAndInstallUpdate(context: Context, downloadUrl: String, scope: CoroutineScope) {
        if (isDownloading.value) return
        isDownloading.value = true
        downloadProgress.value = 0f
        downloadedBytes.value = 0L
        downloadStatusText.value = "Starting..."
        
        cleanupOldUpdates(context)

        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(downloadUrl)
                var connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                
                downloadStatusText.value = "Connecting..."
                connection.connect()
                
                // Handle redirects manually if needed
                var redirectCount = 0
                while (connection.responseCode / 100 == 3 && redirectCount < 5) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    connection = URL(newUrl).openConnection() as HttpURLConnection
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
                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                
                var lastUpdate = System.currentTimeMillis()

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 100) { // Update UI every 100ms
                        downloadedBytes.value = total
                        if (contentLength > 0) {
                            downloadProgress.value = total.toFloat() / contentLength.toFloat()
                        } else {
                            downloadProgress.value = -1f
                        }
                        lastUpdate = now
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()
                
                downloadProgress.value = 1f
                downloadStatusText.value = "Complete!"
                isDownloading.value = false
                
                withContext(Dispatchers.Main) {
                    installApk(context, outputFile)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                downloadStatusText.value = "Failed."
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
            Toast.makeText(context, "Failed to start installer.", Toast.LENGTH_SHORT).show()
        }
    }
}






