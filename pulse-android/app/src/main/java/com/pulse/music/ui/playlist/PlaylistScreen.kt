package com.pulse.music.ui.playlist

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: Int,
    viewModel: PlaylistViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    val playlistInfo by viewModel.playlistInfo.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState(initial = false)
    val isFavorite by viewModel.isFavorite.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        viewModel.renamePlaylist(newPlaylistName)
                        showRenameDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sticky header calculation
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val showCollapsedToolbarTitle by remember { derivedStateOf { firstVisibleItemIndex > 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(visible = showCollapsedToolbarTitle, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            text = playlistInfo?.name ?: "Playlist",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (showCollapsedToolbarTitle) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.DarkGray.copy(alpha = 0.6f), MaterialTheme.colorScheme.background),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF2E2E2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            val firstArt = playlistSongs.firstOrNull { !it.albumArt.isNullOrEmpty() }?.albumArt
                            if (firstArt != null) {
                                AsyncImage(
                                    model = firstArt,
                                    contentDescription = "Playlist Cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = playlistInfo?.name ?: "Playlist",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Playlist",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        val totalDurationMs = playlistSongs.sumOf { it.durationMs ?: 0L }
                        val minutes = totalDurationMs / 60000
                        Text(
                            text = "2026 • ${playlistSongs.size} Songs • $minutes min",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val interactionSourceFav = remember { MutableInteractionSource() }
                        val isPressedFav by interactionSourceFav.collectIsPressedAsState()
                        val scaleFav by animateFloatAsState(targetValue = if (isPressedFav) 0.8f else 1f, label = "fav_scale")
                        
                        IconButton(
                            onClick = { viewModel.toggleFavorite() },
                            modifier = Modifier.scale(scaleFav),
                            interactionSource = interactionSourceFav
                        ) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFF92839) else MaterialTheme.colorScheme.onBackground
                            )
                        }
                        
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Share Playlist")
                                putExtra(Intent.EXTRA_TEXT, "Check out my playlist: ${playlistInfo?.name} on Pulse Music!")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        
                        var expandedMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { expandedMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(
                                expanded = expandedMenu,
                                onDismissRequest = { expandedMenu = false }
                            ) {
                                if (playlistId != -1) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Playlist") },
                                        onClick = {
                                            expandedMenu = false
                                            newPlaylistName = playlistInfo?.name ?: ""
                                            showRenameDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Playlist") },
                                        onClick = { 
                                            expandedMenu = false 
                                            viewModel.deletePlaylist()
                                            onBack()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Shuffle, 
                                contentDescription = "Shuffle", 
                                tint = if (isShuffleEnabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), 
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        val interactionSourcePlay = remember { MutableInteractionSource() }
                        val isPressedPlay by interactionSourcePlay.collectIsPressedAsState()
                        val scalePlay by animateFloatAsState(targetValue = if (isPressedPlay) 0.9f else 1f, label = "play_scale")
                        
                        val isPlaylistPlaying = isPlaying && currentSong != null && playlistSongs.any { it.id == currentSong?.id }

                        FloatingActionButton(
                            onClick = {
                                if (isPlaylistPlaying) {
                                    viewModel.togglePlayPause()
                                } else {
                                    viewModel.playAll(playlistSongs)
                                }
                            },
                            containerColor = Color(0xFFF92839),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(64.dp)
                                .scale(scalePlay)
                                .shadow(4.dp, CircleShape),
                            interactionSource = interactionSourcePlay
                        ) {
                            Icon(
                                if (isPlaylistPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.background,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            if (playlistSongs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No songs in this playlist.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                }
            } else {
                itemsIndexed(playlistSongs, key = { index, song -> "${song.id}_$index" }) { _, song ->
                    val isActive = currentSong?.id == song.id
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playSong(song, playlistSongs) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2E2E2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!song.albumArt.isNullOrEmpty()) {
                                AsyncImage(
                                    model = song.albumArt,
                                    contentDescription = song.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                            }
                            
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.pulse.music.ui.components.PlayingAnimation()
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (isActive) Color(0xFFF92839) else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = song.artist,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp
                            )
                        }
                        
                        val durationText = song.durationMs?.let { ms ->
                            val sec = ms / 1000
                            val m = sec / 60
                            val s = sec % 60
                            String.format("%d:%02d", m, s)
                        } ?: ""

                        Text(
                            text = durationText,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        
                        var songMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { songMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                            }
                            DropdownMenu(
                                expanded = songMenu,
                                onDismissRequest = { songMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Playlist") },
                                    onClick = {
                                        songMenu = false
                                        viewModel.removeSong(song.id)
                                    }
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

