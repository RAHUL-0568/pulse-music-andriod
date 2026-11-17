package com.pulse.music.ui.nowplaying

import androidx.compose.ui.zIndex
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Group
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pulse.music.domain.LyricLine
import com.pulse.music.domain.Song
import com.pulse.music.player.RepeatMode
import kotlin.random.Random

val PulseRed = Color(0xFFF92839)

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToJam: () -> Unit = {}
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isLiked by viewModel.isCurrentSongLiked.collectAsState()
    val lyricsState by viewModel.lyricsState.collectAsState()
    val highlightedIndex by viewModel.highlightedLine.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isJamConnected by com.pulse.music.ui.jam.JamSessionManager.isConnected.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var showQueue by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(viewModel.getVolumeFraction()) }

    val songTitle = currentSong?.title ?: "Not Playing"
    val songArtist = currentSong?.artist ?: "Pulse Music"
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    var swipeAccumulator by remember { mutableStateOf(0f) }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeAccumulator < -150f) viewModel.skipNext()
                        else if (swipeAccumulator > 150f) viewModel.skipPrevious()
                        swipeAccumulator = 0f
                    },
                    onDragCancel = { swipeAccumulator = 0f },
                    onHorizontalDrag = { _, dragAmount -> swipeAccumulator += dragAmount }
                )
            }
    ) {
        // Notification Card layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(32.dp))
        ) {
            // Dynamic Blurred Image Background with Crossfade
            androidx.compose.animation.Crossfade(
                targetState = currentSong?.albumArt, 
                animationSpec = androidx.compose.animation.core.tween(800),
                label = "now_playing_crossfade"
            ) { art ->
                val overlayColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
                coil.compose.AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 80.dp)
                        .drawWithContent {
                            drawContent()
                            drawRect(overlayColor) // Darken overlay
                        },
                    contentScale = ContentScale.Crop,
                    error = androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.MusicNote)
                )
            }
        
        val scrollState = rememberScrollState()
        
        val nestedScrollConnection = remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                var overscroll = 0f
                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                ): androidx.compose.ui.geometry.Offset {
                    if (available.y > 0 && scrollState.value == 0) {
                        overscroll += available.y
                        if (overscroll > 150f) {
                            onBack()
                            overscroll = 0f
                        }
                        // Don't consume it so it feels natural, but we still intercepted the gesture!
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                    overscroll = 0f
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .verticalScroll(scrollState)
            ) {
                MainPlayerContent(
                    modifier = Modifier.height(screenHeight),
                    viewModel = viewModel,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    progress = progress,
                    currentPosition = currentPosition,
                    duration = duration,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    isLiked = isLiked,
                    volume = volume,
                    onVolumeChange = { volume = it },
                    showQueue = { showQueue = true },
                    showDevices = { 
                        try {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Bluetooth settings not available", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    showLyrics = { showLyrics = true },
                    onBack = onBack,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToJam = onNavigateToJam,
                    playlists = playlists,
                    formatTime = ::formatTime
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Lyrics",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp)
                        .clickable { showLyrics = true }
                ) {
                    val previewLines = when (val state = lyricsState) {
                        is LyricsState.Synced -> state.lines.drop(maxOf(0, highlightedIndex)).take(4).map { it.text }.joinToString("\n")
                        is LyricsState.Plain -> state.text.split("\n").take(4).joinToString("\n")
                        is LyricsState.Loading -> "Loading lyrics..."
                        is LyricsState.Unavailable -> "Lyrics not available."
                    }
                    Text(text = previewLines, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f), fontSize = 16.sp, lineHeight = 24.sp)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (recommendations.isNotEmpty()) {
                    Text(
                        text = "More like this",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        recommendations.take(5).forEach { recSong ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable { viewModel.playSong(recSong) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = recSong.albumArt,
                                    contentDescription = "Cover",
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.MusicNote)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(recSong.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(recSong.artist, color = MaterialTheme.colorScheme.onBackground.copy(0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(120.dp))
            }
            
            val collapseThreshold = 400f
            val collapseProgress = (scrollState.value / collapseThreshold).coerceIn(0f, 1f)
            
            if (collapseProgress > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .zIndex(1f)
                        .background(Color(0xEE111111).copy(alpha = collapseProgress))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = currentSong?.albumArt,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray),
                            contentScale = ContentScale.Crop,
                            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.MusicNote)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(songTitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = collapseProgress), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(songArtist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f * collapseProgress), fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = { viewModel.toggleLike() }) {
                            Icon(
                                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = (if (isLiked) PulseRed else MaterialTheme.colorScheme.onBackground).copy(alpha = collapseProgress)
                            )
                        }
                        IconButton(onClick = { viewModel.togglePlayPause() }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = collapseProgress)
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f * collapseProgress))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(2.dp)
                                .background(PulseRed.copy(alpha = collapseProgress))
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showQueue,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f)
        ) {
            QueuePanel(
                queue = queue, recommendations = recommendations, currentIndex = currentIndex,
                onSongClick = { viewModel.playFromQueue(it) }, onRemove = { viewModel.removeFromQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) }, onPlayRecommendation = { viewModel.playSong(it) },
                onClose = { showQueue = false }
            )
        }


        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f)
        ) {
            when (val state = lyricsState) {
                is LyricsState.Loading -> Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PulseRed) }
                is LyricsState.Synced -> LyricsPanel(lyrics = state.lines, highlightedIndex = highlightedIndex, onClose = { showLyrics = false }, onSeek = { viewModel.seekTo(it) })
                is LyricsState.Plain -> LyricsPanel(lyrics = state.text.split("\n").map { LyricLine(0L, it) }, highlightedIndex = -1, onClose = { showLyrics = false }, onSeek = null)
                is LyricsState.Unavailable -> Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Lyrics not available", color = MaterialTheme.colorScheme.onBackground); Spacer(modifier = Modifier.height(16.dp)); Button(onClick = { showLyrics = false }, colors = ButtonDefaults.buttonColors(containerColor = PulseRed, contentColor = MaterialTheme.colorScheme.background)) { Text("Close", fontWeight = FontWeight.Bold) } } }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPlayerContent(
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel,
    currentSong: Song?,
    isPlaying: Boolean,
    progress: Float,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isLiked: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    showQueue: () -> Unit,
    showDevices: () -> Unit,
    showLyrics: () -> Unit,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToJam: () -> Unit,
    playlists: List<com.pulse.music.data.local.PlaylistEntity>,
    formatTime: (Long) -> String
) {
    val songTitle = currentSong?.title ?: "Not Playing"
    val songArtist = currentSong?.artist ?: "Pulse Music"
    val isJamConnected by com.pulse.music.ui.jam.JamSessionManager.isConnected.collectAsState()

    // Colors as requested
    val bgColor = Color.Transparent
    val mutedGray = Color(0xFFB3B3B3)
    val pureWhite = Color(0xFFFFFFFF)

    var showMoreMenu by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaylistSelector by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
                .zIndex(10f)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = pureWhite, modifier = Modifier.size(36.dp))
        }

        IconButton(
            onClick = { showMoreMenu = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .zIndex(10f)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = pureWhite, modifier = Modifier.size(24.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ARTWORK
            Box(modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant), 
                contentAlignment = Alignment.Center
            ) {
                if (!currentSong?.albumArt.isNullOrEmpty()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val imageRequest = coil.request.ImageRequest.Builder(context)
                        .data(currentSong?.albumArt)
                        .crossfade(true)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                        
                    coil.compose.SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = PulseRed)
                            }
                        },
                        error = {
                            Icon(Icons.Default.MusicNote, contentDescription = "Music", tint = pureWhite.copy(alpha = 0.5f), modifier = Modifier.size(96.dp))
                        }
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = "Music", tint = pureWhite.copy(alpha = 0.5f), modifier = Modifier.size(96.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // SONG INFO (Top Section)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songTitle, 
                        color = pureWhite, 
                        fontSize = 26.sp, 
                        fontWeight = FontWeight.Bold, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val artists = songArtist.split(",").map { it.trim() }
                        artists.forEachIndexed { index, artist -> 
                            Text(
                                text = artist + if (index < artists.size - 1) "," else "", 
                                color = pureWhite.copy(alpha = 0.7f), 
                                fontSize = 18.sp, 
                                modifier = Modifier.clickable { onNavigateToArtist(artist) }
                            ) 
                        }
                    }
                }
                
                val heartScale by animateFloatAsState(
                    targetValue = if (isLiked) 1.2f else 1f,
                    animationSpec = tween(durationMillis = 200)
                )
                IconButton(onClick = { viewModel.toggleLike() }) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) PulseRed else pureWhite,
                        modifier = Modifier.size(24.dp).scale(heartScale)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PROGRESS BAR
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress,
                    onValueChange = { seekProgress -> viewModel.seekTo((seekProgress * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = pureWhite,
                        activeTrackColor = pureWhite,
                        inactiveTrackColor = Color(0xFF535353)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(currentPosition), color = mutedGray, fontSize = 13.sp)
                    Text(formatTime(duration), color = mutedGray, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PLAYBACK CONTROLS (Middle Row)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleShuffle() }) { 
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffleEnabled) pureWhite else mutedGray, modifier = Modifier.size(24.dp)) 
                }
                IconButton(onClick = { viewModel.skipPrevious() }) { 
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = pureWhite, modifier = Modifier.size(28.dp)) 
                }
                
                // Central Play/Pause
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(pureWhite)
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) { 
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color(0xFF1A1A2E), modifier = Modifier.size(32.dp)) 
                }
                
                IconButton(onClick = { viewModel.skipNext() }) { 
                    Icon(Icons.Default.SkipNext, "Next", tint = pureWhite, modifier = Modifier.size(28.dp)) 
                }
                IconButton(onClick = { viewModel.cycleRepeatMode() }) { 
                    val tintColor = if (repeatMode != RepeatMode.OFF) PulseRed else pureWhite
                    Icon(
                        imageVector = when (repeatMode) { 
                            RepeatMode.ONE -> Icons.Default.RepeatOne 
                            else -> Icons.Default.Repeat 
                        }, 
                        contentDescription = "Repeat", 
                        tint = tintColor, 
                        modifier = Modifier.size(24.dp)
                    ) 
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BOTTOM BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = showDevices) { 
                    Icon(Icons.Default.Speaker, contentDescription = "Device", tint = mutedGray, modifier = Modifier.size(24.dp)) 
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = showLyrics) { 
                        Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = mutedGray, modifier = Modifier.size(24.dp)) 
                    }
                    IconButton(onClick = showQueue) { 
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = mutedGray, modifier = Modifier.size(24.dp)) 
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        ) {
            Text(
                text = "Made with ❤️  by Rahul",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showMoreMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Header: Artwork, Title, Artist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = currentSong?.albumArt,
                        contentDescription = "Album Art",
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.MusicNote)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(songTitle, color = pureWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(songArtist, color = Color(0xFFAAAAAA), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF282828), thickness = 1.dp)

                // Menu Options
                val menuItems = mutableListOf(
                    Triple(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs", { viewModel.toggleLike() }),
                    Triple(Icons.Default.PlaylistAdd, "Add to Playlist", { showPlaylistSelector = true })
                )
                if (isJamConnected) {
                    menuItems.add(Triple(Icons.Default.QueueMusic, "Add to Queue", { currentSong?.let { viewModel.addToQueue(it) }; showMoreMenu = false }))
                }
                menuItems.addAll(listOf(
                    Triple(Icons.Default.Snooze, "Sleep Timer", { showSleepTimerDialog = true }),
                    // "Go to Album" removed: a song only knows its album's name, not its ID,
                    // and it was opening the album page with the song's ID (a blank page).
                    Triple(Icons.Default.Person, "Go to Artist", { currentSong?.artist?.split(",")?.firstOrNull()?.trim()?.let { onNavigateToArtist(it) } }),
                    Triple(Icons.Default.Group, "Start Jam", { 
                        android.util.Log.d("NAVIGATION", "Clicked Start Jam")
                        onNavigateToJam() 
                    })
                ))

                menuItems.forEach { (icon, text, action) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                action()
                                showMoreMenu = false
                            }
                            .padding(horizontal = 16.dp)
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = if (text.contains("Liked Songs") && isLiked) PulseRed else pureWhite, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text, color = pureWhite, fontSize = 16.sp)
                    }
                }
            }
        }
    }
    
    if (showPlaylistSelector) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSelector = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add to Playlist",
                        color = pureWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { 
                        showPlaylistSelector = false
                        showCreatePlaylistDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Playlist", tint = pureWhite)
                    }
                }
                LazyColumn {

                    items(playlists.size) { index ->
                        val playlist = playlists[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (currentSong != null) {
                                        viewModel.addSongToPlaylist(playlist.id, currentSong!!)
                                    }
                                    showPlaylistSelector = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = pureWhite, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(playlist.name, color = pureWhite, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
    
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreatePlaylistDialog = false 
                newPlaylistName = ""
            },
            containerColor = Color(0xFF282828),
            title = { Text("Create New Playlist", color = pureWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist Name", color = mutedGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = pureWhite,
                        unfocusedTextColor = pureWhite,
                        focusedBorderColor = PulseRed,
                        unfocusedBorderColor = mutedGray,
                        cursorColor = PulseRed
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank() && currentSong != null) {
                        viewModel.createPlaylistAndAddSong(newPlaylistName, currentSong!!)
                    }
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Create", color = PulseRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCreatePlaylistDialog = false 
                    newPlaylistName = ""
                }) {
                    Text("Cancel", color = mutedGray)
                }
            }
        )
    }
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsState()
    val sleepTimerTimeLeft by viewModel.sleepTimerTimeLeft.collectAsState()

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            containerColor = Color(0xFF282828),
            title = { Text("Sleep Timer", color = pureWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        com.pulse.music.player.SleepTimerMode.MIN_5,
                        com.pulse.music.player.SleepTimerMode.MIN_10,
                        com.pulse.music.player.SleepTimerMode.MIN_15,
                        com.pulse.music.player.SleepTimerMode.MIN_30,
                        com.pulse.music.player.SleepTimerMode.MIN_45,
                        com.pulse.music.player.SleepTimerMode.MIN_60,
                        com.pulse.music.player.SleepTimerMode.END_OF_TRACK
                    )
                    options.forEach { mode ->
                        val isActive = sleepTimerMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSleepTimer(mode)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.label, color = if (isActive) PulseRed else pureWhite, fontSize = 16.sp)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = PulseRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (mode.minutes != null) {
                                    val timeText = if (isActive) {
                                        val totalSeconds = sleepTimerTimeLeft / 1000
                                        val m = totalSeconds / 60
                                        val s = totalSeconds % 60
                                        String.format("%02d:%02d", m, s)
                                    } else {
                                        String.format("%02d:00", mode.minutes)
                                    }
                                    Text(
                                        text = timeText,
                                        color = if (isActive) PulseRed else Color.Gray,
                                        fontSize = 16.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    if (sleepTimerMode != com.pulse.music.player.SleepTimerMode.OFF) {
                        HorizontalDivider(color = Color(0xFF404040), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSleepTimer(com.pulse.music.player.SleepTimerMode.OFF)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 14.dp, horizontal = 8.dp)
                        ) {
                            Text("Turn off timer", color = pureWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Cancel", color = pureWhite)
                }
            }
        )
    }
}

@Composable
fun DevicePanel(
    devices: List<android.media.AudioDeviceInfo>,
    onSelectDevice: (android.media.AudioDeviceInfo) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(top = 16.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Select Output Device",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), thickness = 1.dp)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDevice(device) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                                Icons.Default.Bluetooth
                            } else if (device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                                Icons.Default.Headphones
                            } else {
                                Icons.Default.Speaker
                            },
                            contentDescription = "Device Type",
                            tint = PulseRed
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = device.productName.toString().takeIf { it.isNotBlank() } ?: "Unknown Device",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformSeekbar(
    progress: Float,
    onSeek: (Float) -> Unit = {}
) {
    val barCount = 42
    val playedUntil = (barCount * progress).toInt()
    val barHeights = remember { List(barCount) { Random.nextInt(8, 30) } }
    var componentWidth by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .onSizeChanged { componentWidth = it.width }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (componentWidth > 0) {
                        val seekProgress = (offset.x / componentWidth).coerceIn(0f, 1f)
                        onSeek(seekProgress)
                    }
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount) {
            val height = barHeights[i]
            val color = if (i < playedUntil) PulseRed else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun QueuePanel(
    queue: List<Song>,
    recommendations: List<Song>,
    currentIndex: Int,
    onSongClick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayRecommendation: (Song) -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to current song
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < queue.size) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(top = 16.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Queue",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${queue.size} songs",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), thickness = 1.dp)

            // Song list
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = queue,
                    key = { index, song -> "${song.id}_$index" }
                ) { index, song ->
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isCurrent) PulseRed.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onSongClick(index) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Now playing indicator or track number
                        Box(
                            modifier = Modifier.width(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.Equalizer,
                                    contentDescription = "Playing",
                                    tint = PulseRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    "${index + 1}",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        AsyncImage(
                            model = song.albumArt,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.DarkGray),
                            contentScale = ContentScale.Crop,
                            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.MusicNote)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = if (isCurrent) PulseRed else MaterialTheme.colorScheme.onBackground,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!isCurrent) {
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (recommendations.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recommended based on what's playing",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                        )
                    }
                    items(
                        items = recommendations,
                        key = { song -> "rec_${song.id}" }
                    ) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayRecommendation(song) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.albumArt,
                                contentDescription = "Cover",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray),
                                contentScale = ContentScale.Crop,
                                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Album)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onAddToQueue(song) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add to Queue", tint = PulseRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsPanel(lyrics: List<LyricLine>, highlightedIndex: Int, onClose: () -> Unit, onSeek: ((Long) -> Unit)? = null) {
    val listState = rememberLazyListState()

    LaunchedEffect(highlightedIndex) {
        if (highlightedIndex >= 0 && highlightedIndex < lyrics.size) {
            val targetIndex = maxOf(0, highlightedIndex - 3)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(24.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close Lyrics", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            if (lyrics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No lyrics available", color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(lyrics) { index, line ->
                        val isHighlighted = index == highlightedIndex
                        val alpha by animateFloatAsState(if (isHighlighted) 1f else 0.4f)
                        val fontSize by animateFloatAsState(if (isHighlighted) 28f else 22f)

                        Text(
                            text = line.text,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
                            fontSize = fontSize.sp,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = onSeek != null && line.timeMs > 0) {
                                    onSeek?.invoke(line.timeMs)
                                }
                                .padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
