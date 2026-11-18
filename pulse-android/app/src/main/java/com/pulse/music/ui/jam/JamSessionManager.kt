package com.pulse.music.ui.jam

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

object JamSessionManager {
    var applicationContext: Context? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId: StateFlow<String?> = _currentRoomId.asStateFlow()

    private var _syncCounter = 0L
    private val _incomingSync = MutableStateFlow<Triple<Boolean, Long, Long>?>(null)
    val incomingSync: StateFlow<Triple<Boolean, Long, Long>?> = _incomingSync.asStateFlow()

    private val _incomingSong = MutableStateFlow<com.pulse.music.domain.Song?>(null)
    val incomingSong: StateFlow<com.pulse.music.domain.Song?> = _incomingSong

    private val _incomingSongWithSync = MutableStateFlow<Pair<com.pulse.music.domain.Song, Pair<Boolean, Long>>?>(null)
    val incomingSongWithSync: StateFlow<Pair<com.pulse.music.domain.Song, Pair<Boolean, Long>>?> = _incomingSongWithSync.asStateFlow()

    private val _incomingShuffle = MutableStateFlow<Boolean?>(null)
    val incomingShuffle: StateFlow<Boolean?> = _incomingShuffle.asStateFlow()

    private val _incomingRepeat = MutableStateFlow<String?>(null)
    val incomingRepeat: StateFlow<String?> = _incomingRepeat.asStateFlow()

    private val _incomingQueueSync = MutableStateFlow<Triple<List<JSONObject>, Int, Long>?>(null)
    val incomingQueueSync: StateFlow<Triple<List<JSONObject>, Int, Long>?> = _incomingQueueSync.asStateFlow()

    private val _incomingQueueAppend = MutableStateFlow<List<JSONObject>?>(null)
    val incomingQueueAppend: StateFlow<List<JSONObject>?> = _incomingQueueAppend.asStateFlow()

    /** Set by the person who creates a Jam, so it starts from the song they are playing. */
    @Volatile var seedQueueOnConnect: Boolean = false
    fun takeSeedFlag(): Boolean { val v = seedQueueOnConnect; seedQueueOnConnect = false; return v }

    private val _queue = MutableStateFlow<List<JSONObject>>(emptyList())
    val queue: StateFlow<List<JSONObject>> = _queue.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<JSONObject>>(emptyList())
    val chatMessages: StateFlow<List<JSONObject>> = _chatMessages.asStateFlow()

    private val _participants = MutableStateFlow<List<JSONObject>>(emptyList())
    val participants: StateFlow<List<JSONObject>> = _participants.asStateFlow()

    private val _isPendingApproval = MutableStateFlow(false)
    val isPendingApproval: StateFlow<Boolean> = _isPendingApproval.asStateFlow()

    private val _joinRequests = MutableStateFlow<List<String>>(emptyList())
    val joinRequests: StateFlow<List<String>> = _joinRequests.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    val isHost: Boolean
        get() {
            val currentUserId = _currentUserId.value ?: return false
            val participantsList = _participants.value
            for (p in participantsList) {
                if (p.optString("user_id") == currentUserId && p.optString("role") == "HOST") {
                    return true
                }
            }
            return false
        }

    fun connectToJamSession(roomId: String, userId: String, isCreating: Boolean = false, token: String? = null) {
        if (_isConnected.value) return // already connected
        
        _currentRoomId.value = roomId
        _currentUserId.value = userId
        _chatMessages.value = emptyList()

        val baseUrl = com.pulse.music.BuildConfig.API_BASE_URL
        val wsBaseUrl = if (baseUrl.contains("vercel.app")) {
            "wss://pulse-music-backend-fallback-1.onrender.com/"
        } else {
            baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        }
        val encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8")
        var urlStr = "${wsBaseUrl}api/v1/jam/ws/$roomId/$encodedUserId" + if (isCreating) "?action=create" else ""
        if (token != null) {
            urlStr += if (urlStr.contains("?")) "&token=$token" else "?token=$token"
        }
        val request = Request.Builder()
            // Use computer's IP address so other devices on Wi-Fi can connect
            .url(urlStr)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                _isConnected.value = true
                _currentRoomId.value = roomId
                
                pingJob?.cancel()
                pingJob = scope.launch {
                    while (isActive) {
                        delay(25000) // Send a ping every 25 seconds
                        try {
                            webSocket.send(JSONObject().put("event", "ping").toString())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                val event = json.optString("event")
                when (event) {
                    "playback_synced" -> {
                        val state = json.optString("state")
                        val isPlaying = state == "PLAYING"
                        var position = json.optLong("position_ms")
                        val startedAt = json.optLong("started_at_ms", 0L)
                        
                        // Removed drift correction because server and client clocks are not synchronized
                        // and network latency is much smaller than clock skew.
                        
                        _incomingSync.value = Triple(isPlaying, position, ++_syncCounter)
                    }
                    "play_song" -> {
                        val songJson = json.getJSONObject("song")
                        val song = com.pulse.music.domain.Song(
                            id = songJson.getString("id"),
                            title = songJson.getString("title"),
                            artist = songJson.getString("artist"),
                            albumArt = songJson.optString("image").takeIf { it.isNotEmpty() },
                            source = songJson.optString("streamUrl").takeIf { it.isNotEmpty() } ?: ""
                        )
                        _incomingSong.value = song
                    }
                    "queue_updated" -> {
                        val queueArray = json.getJSONArray("queue")
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until queueArray.length()) {
                            list.add(queueArray.getJSONObject(i))
                        }
                        _queue.value = list
                        if (json.has("current_index")) {
                            // The host re-sent the whole queue with the position: apply it fully.
                            _incomingQueueSync.value = Triple(list, json.optInt("current_index", 0), json.optLong("position_ms", 0L))
                        } else {
                            // "Add song": the server sends only the list. Treating the missing position as 0
                            // restarted the playing song and jumped to the first one, so only append.
                            _incomingQueueAppend.value = list
                        }
                    }
                    "shuffle_updated" -> {
                        _incomingShuffle.value = json.optBoolean("enabled")
                    }
                    "repeat_updated" -> {
                        _incomingRepeat.value = json.optString("mode")
                    }
                    "session_state" -> {
                        _isPendingApproval.value = false
                        val session = json.getJSONObject("session")
                        val playbackState = session.optString("playback_state")
                        var position = session.optLong("position_ms")
                        val startedAt = session.optLong("started_at_ms", 0L)
                        val isPlaying = playbackState == "PLAYING"
                        
                        // Removed drift correction because server and client clocks are not synchronized
                        // and network latency is much smaller than clock skew.
                        
                        val currentSongJson = session.optJSONObject("current_song")
                        if (currentSongJson != null) {
                            val song = com.pulse.music.domain.Song(
                                id = currentSongJson.optString("id", ""),
                                title = currentSongJson.optString("title", "Unknown Title"),
                                artist = currentSongJson.optString("artist", "Unknown Artist"),
                                albumArt = currentSongJson.optString("image", ""),
                                source = currentSongJson.optString("streamUrl", "")
                            )
                            _incomingSongWithSync.value = Pair(song, Pair(isPlaying, position))
                            // Do not overwrite incomingSync, let PlaybackService handle it together
                        } else if ((session.optJSONArray("queue")?.length() ?: 0) > 0) {
                            _incomingSync.value = Triple(isPlaying, position, ++_syncCounter)
                        }
                        // else: a brand-new empty room says "paused"; that must not stop what you are playing.

                        val queueArray = session.optJSONArray("queue")
                        if (queueArray != null) {
                            val list = mutableListOf<JSONObject>()
                            for (i in 0 until queueArray.length()) {
                                list.add(queueArray.getJSONObject(i))
                            }
                            _queue.value = list
                        }
                        
                        val partsObj = session.optJSONObject("participants")
                        if (partsObj != null) {
                            val list = mutableListOf<JSONObject>()
                            val keys = partsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                list.add(partsObj.getJSONObject(key))
                            }
                            _participants.value = list
                        }
                    }
                    "chat_received" -> {
                        val msg = json.getJSONObject("message")
                        val currentList = _chatMessages.value.toMutableList()
                        currentList.add(msg)
                        _chatMessages.value = currentList
                        
                        val sender = msg.optString("sender")
                        val textStr = msg.optString("text")
                        if (sender != _currentUserId.value) {
                            showNotification("Jam Message from $sender", textStr)
                        }
                    }
                    "user_joined", "user_left" -> {
                        val partsObj = json.optJSONObject("participants")
                        if (partsObj != null) {
                            val list = mutableListOf<JSONObject>()
                            val keys = partsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                list.add(partsObj.getJSONObject(key))
                            }
                            _participants.value = list
                        }
                    }
                    "pending_approval" -> {
                        _isPendingApproval.value = true
                    }
                    "join_request" -> {
                        val userId = json.getString("user_id")
                        if (!_joinRequests.value.contains(userId)) {
                            _joinRequests.value = _joinRequests.value + userId
                            showNotification("Jam Join Request", "$userId wants to join your Jam session")
                        }
                    }
                    "join_rejected", "removed" -> {
                        disconnect()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                pingJob?.cancel()
                _isConnected.value = false
                _currentRoomId.value = null
                _participants.value = emptyList()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                pingJob?.cancel()
                _isConnected.value = false
                _currentRoomId.value = null
                
                if (response?.code == 403) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        applicationContext?.let { ctx ->
                            android.widget.Toast.makeText(ctx, "Incorrect Room Code", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    fun disconnect() {
        pingJob?.cancel()
        webSocket?.close(1000, "User left")
        webSocket?.cancel()
        webSocket = null
        _isConnected.value = false
        _currentRoomId.value = null
        _participants.value = emptyList()
        _chatMessages.value = emptyList()
    }

    private fun showNotification(title: String, message: String) {
        val context = applicationContext ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jam_notifications",
                "Jam Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Jam sessions"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val roomId = _currentRoomId.value
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("pulse://jam/${roomId ?: ""}"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, "jam_notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun broadcastPlayPause(isPlaying: Boolean, currentPositionMs: Long) {
        if (_isConnected.value && !_isPendingApproval.value) {
            val json = JSONObject().apply {
                put("event", if (isPlaying) "play" else "pause")
                put("position_ms", currentPositionMs)
            }
            webSocket?.send(json.toString())
        }
    }

    fun broadcastPlaySong(song: com.pulse.music.domain.Song) {
        if (_isConnected.value && !_isPendingApproval.value) {
            val payload = JSONObject().apply {
                put("event", "play_song")
                val songJson = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("image", song.albumArt ?: "")
                    if (song.source.isNotEmpty()) put("streamUrl", song.source)
                }
                put("song", songJson)
            }
            webSocket?.send(payload.toString())
        }
    }
    
    fun sendChatMessage(text: String) {
        if (_isConnected.value && !_isPendingApproval.value) {
            val json = JSONObject().apply {
                put("event", "chat")
                put("text", text)
            }
            webSocket?.send(json.toString())
        }
    }
    
    fun broadcastQueue(queueList: List<com.pulse.music.domain.Song>, currentIndex: Int, positionMs: Long) {
        if (_isConnected.value && !_isPendingApproval.value) {
            val queueJsonArray = org.json.JSONArray()
            queueList.forEach { song ->
                val songJson = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("albumArt", song.albumArt ?: "")
                    put("durationMs", song.durationMs ?: 0)
                    put("source", song.source)
                }
                queueJsonArray.put(songJson)
            }
            // The sender gets no echo from the server, so show what was sent in the Queue tab right away.
            _queue.value = (0 until queueJsonArray.length()).map { queueJsonArray.getJSONObject(it) }
            val payload = JSONObject().apply {
                put("event", "sync_queue")
                put("queue", queueJsonArray)
                put("current_index", currentIndex)
                put("position_ms", positionMs)
            }
            webSocket?.send(payload.toString())
        }
    }

    fun broadcastShuffle(enabled: Boolean) {
        if (_isConnected.value && !_isPendingApproval.value) {
            val payload = JSONObject().apply {
                put("event", "sync_shuffle")
                put("enabled", enabled)
            }
            webSocket?.send(payload.toString())
        }
    }

    fun broadcastRepeat(mode: String) {
        if (_isConnected.value && !_isPendingApproval.value) {
            val payload = JSONObject().apply {
                put("event", "sync_repeat")
                put("mode", mode)
            }
            webSocket?.send(payload.toString())
        }
    }
    
    fun addSongToQueue(song: com.pulse.music.domain.Song, addedBy: String) {
        if (_isConnected.value && !_isPendingApproval.value) {
            val songJson = JSONObject().apply {
                put("song_id", song.id)
                put("title", song.title)
                put("artist", song.artist)
                put("albumArt", song.albumArt ?: "")
                put("durationMs", song.durationMs ?: 0)
                put("source", song.source)
                put("added_by", addedBy)
            }
            val json = JSONObject().apply {
                put("event", "add_song")
                put("song", songJson)
            }
            webSocket?.send(json.toString())
        }
    }

    fun addSongToQueue(songId: String, title: String, addedBy: String) {
        if (_isConnected.value && !_isPendingApproval.value) {
            val songJson = JSONObject().apply {
                put("song_id", songId)
                put("title", title)
                put("added_by", addedBy)
            }
            val json = JSONObject().apply {
                put("event", "add_song")
                put("song", songJson)
            }
            webSocket?.send(json.toString())
        }
    }
    fun approveJoin(userId: String) {
        val payload = JSONObject().apply {
            put("event", "approve_join")
            put("target_user_id", userId)
        }
        webSocket?.send(payload.toString())
        _joinRequests.value = _joinRequests.value - userId
    }

    fun rejectJoin(userId: String) {
        val payload = JSONObject().apply {
            put("event", "reject_join")
            put("target_user_id", userId)
        }
        webSocket?.send(payload.toString())
        _joinRequests.value = _joinRequests.value - userId
    }

    fun removeParticipant(userId: String) {
        val payload = JSONObject().apply {
            put("event", "remove_participant")
            put("target_user_id", userId)
        }
        webSocket?.send(payload.toString())
    }
}
