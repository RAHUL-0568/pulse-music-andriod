package com.pulse.music.ui.lockscreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulse.music.player.MusicPlayerManager
import kotlinx.coroutines.delay

@Composable
fun LockScreenPlayer(
    musicPlayerManager: MusicPlayerManager,
    onUnlock: () -> Unit
) {
    val currentSong by musicPlayerManager.currentSong.collectAsState()
    val isPlaying by musicPlayerManager.isPlaying.collectAsState()
    val currentPosition by musicPlayerManager.currentPosition.collectAsState()

    var isExpanded by remember { mutableStateOf(false) }

    // Animations for expanding
    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "Expansion"
    )

    // Dynamic background colors could be extracted from artwork, defaulting to black for now
    val backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f + (0.3f * expansionProgress))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -50 && !isExpanded) {
                        onUnlock()
                    } else if (dragAmount > 50 && isExpanded) {
                        isExpanded = false
                    }
                }
            }
    ) {
        // Blurred Background (only visible when expanded)
        if (expansionProgress > 0.01f && currentSong != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(expansionProgress)
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(currentSong?.albumArt?.replace("http://", "https://"))
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp),
                    error = androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.MusicNote),
                    fallback = androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.MusicNote)
                )
                // Dark gradient overlay to ensure text is readable and top clock is visible
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                                0.3f to Color.Transparent, // Let system clock show through
                                0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                            )
                        )
                )
            }
        }

        // Content
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (isExpanded) Alignment.Center else Alignment.BottomCenter
        ) {
            if (currentSong != null) {
                if (!isExpanded) {
                    CompactLockScreenCard(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        onPlayPause = { musicPlayerManager.togglePlayPause() },
                        onNext = { musicPlayerManager.skipNext() },
                        onPrev = { musicPlayerManager.skipPrevious() },
                        onClick = { isExpanded = true },
                        modifier = Modifier
                            .padding(bottom = 64.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                    )
                } else {
                    ExpandedLockScreenPlayer(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        progress = currentPosition,
                        onPlayPause = { musicPlayerManager.togglePlayPause() },
                        onNext = { musicPlayerManager.skipNext() },
                        onPrev = { musicPlayerManager.skipPrevious() },
                        onSeek = { musicPlayerManager.seekTo(it) },
                        onCollapse = { isExpanded = false },
                        expansionProgress = expansionProgress
                    )
                }
            }
        }
    }
}

@Composable
fun CompactLockScreenCard(
    song: com.pulse.music.domain.Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(40.dp),
        color = Color(0x66000000), // Semi-transparent glass
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(song.albumArt?.replace("http://", "https://"))
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.MusicNote),
                fallback = androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.MusicNote)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.background
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

@Composable
fun ExpandedLockScreenPlayer(
    song: com.pulse.music.domain.Song,
    isPlaying: Boolean,
    progress: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    expansionProgress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 150.dp, start = 24.dp, end = 24.dp, bottom = 48.dp)
            .clickable(onClick = onCollapse, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artwork
        AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(song.albumArt?.replace("http://", "https://"))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .allowHardware(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .scale(0.8f + (0.2f * expansionProgress))
                .clip(RoundedCornerShape(32.dp)),
            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.MusicNote),
            fallback = androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.MusicNote)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = song.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = song.artist,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Media Card
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}, // Consume clicks
            shape = RoundedCornerShape(24.dp),
            color = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("This Phone", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 14.sp)
                    Text("Media Output", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val duration = song.durationMs ?: 0L
                Slider(
                    value = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.onBackground,
                        activeTrackColor = MaterialTheme.colorScheme.onBackground,
                        inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(progress), color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                    Text(formatTime(duration), color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(40.dp))
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Like", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
