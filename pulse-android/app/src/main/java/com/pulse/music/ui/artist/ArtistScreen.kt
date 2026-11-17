package com.pulse.music.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pulse.music.domain.Artist
import com.pulse.music.domain.Song

val PulseAccent = Color(0xFFFA2D48)
val SurfaceContainer @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val TextMuted @Composable get() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
val TextSecondary @Composable get() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)

@Composable
fun ArtistScreen(
    artistName: String,
    viewModel: ArtistViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onShuffleClick: (List<Song>) -> Unit = {}
) {
    LaunchedEffect(artistName) {
        viewModel.loadArtist(artistName)
    }

    val artistInfo by viewModel.artistInfo.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState(initial = false)
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    var showAllTracks by remember { mutableStateOf(false) }
    var showAllAlbums by remember { mutableStateOf(false) }

    if (artistInfo == null && topTracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PulseAccent)
        }
        return
    }

    // Fallback info if API takes time
    val artist = (artistInfo as? Artist) ?: Artist(
        id = artistName,
        name = artistName,
        bio = "Loading...",
        image = "https://lh3.googleusercontent.com/aida/AP1WRLvV2_QrrsxSK99pbWV11nY-uBwqDwYEl9bVtJMRFyl7TNfTUsXbQL2isTMbBj4nB4FkAsCY4WhuiqUMsF8vSD9irBVKX3990QhiqyxhW8Dc4nenVr-3PxP6v647jxfYHiTFS-NPf-HhksxCzaT-PmJ2C95-UWT9mgxygrCgHwZRCB4dvifz6nxb4ijlW1FBZ1F9L0SpJSkTpN-Gb0Jh79W_2wrEajLp6oT9Th4cAET98zTVR9sqmghPW-eQ",
        similar = emptyList(),
        genres = emptyList()
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp) // Space for mini player
        ) {
            // Hero Section
            item {
                Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                    AsyncImage(
                        model = artist.image,
                        contentDescription = "Artist Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person)
                    )
                    
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                    startY = 200f
                                )
                            )
                    )

                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }

                    // Bottom info
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = artist.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        )
                        val followersText = artist.score?.let { "%,d followers".format(it) } ?: "${artist.name} listeners"
                        Text(
                            text = followersText,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // Action Buttons Row (Play, Shuffle, Follow, More)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayTracksFAB = topTracks.map { it as Song }
                        val isArtistPlaying = isPlaying && currentSong != null && displayTracksFAB.any { it.id == currentSong?.id }
                        // Play FAB
                        FloatingActionButton(
                            onClick = {
                                if (isArtistPlaying) {
                                    viewModel.togglePlayPause()
                                } else if (displayTracksFAB.isNotEmpty()) {
                                    onSongClick(displayTracksFAB.first(), displayTracksFAB)
                                }
                            },
                            containerColor = PulseAccent,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                if (isArtistPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Shuffle Button
                        IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Shuffle, 
                                contentDescription = "Shuffle", 
                                tint = if (isShuffleEnabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), 
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFollow(artist) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isFollowed) Color.Transparent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFollowed) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (isFollowed) "Following" else "Follow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // Popular Section
            item {
                Text(
                    text = "Popular",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp)
                )
            }

            // Popular Tracks
            val displayTracks = topTracks.map { it as Song }
            if (displayTracks.isEmpty()) {
                item {
                    Text("Loading popular tracks...", color = TextMuted, modifier = Modifier.padding(start = 20.dp))
                }
            } else {
                val tracksToShow = if (showAllTracks) displayTracks else displayTracks.take(6)
                itemsIndexed(tracksToShow) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable { onSongClick(song, displayTracks) }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(32.dp)
                        )
                        AsyncImage(
                            model = song.albumArt,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceContainer),
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
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
                        }
                    }
                }
                
                if (displayTracks.size > 6) {
                    item {
                        TextButton(
                            onClick = { showAllTracks = !showAllTracks },
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        ) {
                            Text(
                                text = if (showAllTracks) "Show less" else "Show more",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Albums
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Albums",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (showAllAlbums) "Show less" else "See all albums",
                        color = PulseAccent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { showAllAlbums = !showAllAlbums }
                    )
                }

                val displayAlbums = artist.albums ?: emptyList()
                if (displayAlbums.isEmpty()) {
                    Text("No albums available.", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
                } else if (showAllAlbums) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        displayAlbums.chunked(2).forEach { rowAlbums ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rowAlbums.forEach { album ->
                                    Column(modifier = Modifier.weight(1f).clickable { onAlbumClick(album.id) }) {
                                        AsyncImage(
                                            model = album.coverArt,
                                            contentDescription = "Album Cover",
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SurfaceContainer),
                                            contentScale = ContentScale.Crop,
                                            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Album)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = album.title,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = album.year?.toString() ?: "Unknown Year",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (rowAlbums.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(displayAlbums) { album ->
                            Column(modifier = Modifier.width(120.dp).clickable { onAlbumClick(album.id) }) {
                                AsyncImage(
                                    model = album.coverArt,
                                    contentDescription = "Album Cover",
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceContainer),
                                    contentScale = ContentScale.Crop,
                                    error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Album)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = album.title,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = album.year?.toString() ?: "Unknown Year",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // You may also like
            if (artist.similar.isNotEmpty()) {
                item {
                    Text(
                        text = "You may also like",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 16.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(artist.similar) { similarArtist ->
                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable { onArtistClick(similarArtist.id) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = similarArtist.image,
                                    contentDescription = similarArtist.name,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceContainer),
                                    contentScale = ContentScale.Crop,
                                    error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = similarArtist.name,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // About
            item {
                Text(
                    text = "About",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp)
                )

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = artist.bio ?: "No biography available.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (artist.genres.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), CircleShape)
                                    .background(SurfaceContainer, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(artist.genres.first(), color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), CircleShape)
                                .background(SurfaceContainer, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Artist info from Last.fm", color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
