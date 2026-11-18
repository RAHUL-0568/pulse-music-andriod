package com.pulse.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulse.music.domain.Song
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PageSize
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.pulse.music.R
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import coil.compose.AsyncImage



@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToNowPlaying: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val sectionsLoading by viewModel.sectionsLoading.collectAsState()
    val username by viewModel.username.collectAsState()
    val profilePicUri by viewModel.profilePicUri.collectAsState()
    var showHistory by remember { mutableStateOf(false) }

    val calendar = java.util.Calendar.getInstance()
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
    
    val greetingPrefix = remember(hour, dayOfYear) {
        val morningGreetings = listOf("Rise and shine", "Start your day right", "Morning vibes", "Ready for the day")
        val afternoonGreetings = listOf("Keep the rhythm going", "Midday melodies", "Afternoon vibes", "Tune in")
        val eveningGreetings = listOf("Unwind and relax", "Evening tunes", "Set the mood", "Night vibes")
        
        val seed = hour + dayOfYear
        when (hour) {
            in 0..11 -> morningGreetings[seed % morningGreetings.size]
            in 12..16 -> afternoonGreetings[seed % afternoonGreetings.size]
            else -> eveningGreetings[seed % eveningGreetings.size]
        }
    }
    
    val greetingText = if (!username.isNullOrEmpty()) "$greetingPrefix, $username!" else "$greetingPrefix!"
    
    if (showHistory && uiState is HomeUiState.Success) {
        val state = uiState as HomeUiState.Success
        RecentlyPlayedScreen(
            songs = state.allRecentPlays ?: emptyList(),
            onBack = { showHistory = false },
            onSongClick = { song ->
                viewModel.playSong(song, state.allRecentPlays ?: emptyList())
                onNavigateToNowPlaying()
            },
            onRemoveSong = { song ->
                viewModel.removeRecentSong(song)
            }
        )
        return
    }
    
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
    val topBarColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (scrolled) Color(0xFF14090D) else Color.Transparent,
        animationSpec = tween(200),
        label = "home_topbar"
    )
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x55FF3B4E), Color(0x22D81B60), Color.Transparent)
                )
            )
    )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(com.pulse.music.ui.theme.PulseGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "Pulse Music Logo",
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Pulse",
                            fontSize = 22.sp,
                            fontFamily = com.pulse.music.ui.theme.SoraFamily,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.6).sp,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        if (!profilePicUri.isNullOrEmpty()) {
                            AsyncImage(
                                model = profilePicUri,
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = username?.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                val daypart = when (hour) {
                    in 5..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    in 17..21 -> "Good evening"
                    else -> "Late night"
                }
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
                    com.pulse.music.ui.components.PulseLine(modifier = Modifier.padding(top = 4.dp))
                }
            }
            
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    item { PlaylistRowSkeleton() }
                    item { PlaylistRowSkeleton() }
                }
                is HomeUiState.Error -> {
                    item { HomeErrorCard(message = state.message, onRetry = { viewModel.loadData(background = false) }) }
                }
                is HomeUiState.Success -> {
                    // Hero: the one thing to play right now
                    val heroSong = state.recentPlays.firstOrNull() ?: state.suggested.firstOrNull()
                    if (heroSong != null) {
                        item {
                            HeroCard(
                                song = heroSong,
                                label = if (state.recentPlays.isNotEmpty()) "CONTINUE LISTENING" else "PICKED FOR YOU",
                                onPlay = {
                                    viewModel.playSong(heroSong, if (state.recentPlays.isNotEmpty()) state.recentPlays else state.suggested)
                                    onNavigateToNowPlaying()
                                }
                            )
                        }
                    }
                                        if (state.recentPlays.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = "Recently Played", 
                                onSeeAll = if ((state.allRecentPlays?.size ?: 0) > 5) { { showHistory = true } } else null
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                                items(state.recentPlays) { song ->
                                    SongCard(song, onRemove = {
                                        viewModel.removeRecentSong(song)
                                    }, onClick = {
                                        viewModel.playSong(song, state.recentPlays)
                                        onNavigateToNowPlaying()
                                    })
                                }
                            }
                        }
                    }

                    // Shown while it loads, or once it has songs; hidden if the server gave us none.
                    if (state.suggested.isNotEmpty() || sectionsLoading.suggested) item {
                        SectionTitle("Picked for you")
                        
                        val shimmerBrush = rememberShimmerBrush()
                        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                        val pageWidth = screenWidth * 0.88f
                        val pageCount = maxOf((state.suggested.size + 7) / 8, 2)
                        val pagerState = rememberPagerState(pageCount = { pageCount })
                        
                        HorizontalPager(
                            state = pagerState,
                            pageSize = PageSize.Fixed(pageWidth),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            pageSpacing = 24.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            Row(
                                modifier = Modifier.width(pageWidth),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // Left column (4 songs)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    for (rowIndex in 0 until 4) {
                                        val songIndex = page * 8 + rowIndex
                                        if (songIndex < state.suggested.size) {
                                            val song = state.suggested[songIndex]
                                            SongRowItem(song = song, onClick = {
                                                viewModel.playSong(song, state.suggested)
                                                onNavigateToNowPlaying()
                                            })
                                        } else {
                                            ShimmerShelfSkeletonRowItem(shimmerBrush = shimmerBrush)
                                        }
                                    }
                                }
                                
                                // Right column (4 songs)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    for (rowIndex in 0 until 4) {
                                        val songIndex = page * 8 + 4 + rowIndex
                                        if (songIndex < state.suggested.size) {
                                            val song = state.suggested[songIndex]
                                            SongRowItem(song = song, onClick = {
                                                viewModel.playSong(song, state.suggested)
                                                onNavigateToNowPlaying()
                                            })
                                        } else {
                                            ShimmerShelfSkeletonRowItem(shimmerBrush = shimmerBrush)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Playlist rows are built from whatever the server's home response contains.
                    if (sectionsLoading.modules && state.modules.isEmpty()) {
                        item { PlaylistRowSkeleton() }
                        item { PlaylistRowSkeleton() }
                    }
                    state.modules.forEach { module ->
                        if (module.items.isNotEmpty()) {
                            item {
                                SectionTitle(module.title)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                                    items(module.items) { playlist ->
                                        PlaylistCard(
                                            playlist = com.pulse.music.domain.Playlist(
                                                id = playlist.id,
                                                title = playlist.title,
                                                image = playlist.image,
                                                songCount = playlist.songCount,
                                                source = playlist.source
                                            ),
                                            onClick = { onNavigateToAlbum(playlist.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Proper bottom safe area padding to scroll behind the floating nav bar
            item { Spacer(modifier = Modifier.height(160.dp)) }
        }
    }
    }
}

/** Grey placeholder row (title + cards) shown while a section is still loading. */
@Composable
fun PlaylistRowSkeleton() {
    val brush = rememberShimmerBrush()
    Column {
        Box(
            modifier = Modifier
                .padding(start = 16.dp, top = 32.dp, bottom = 16.dp)
                .width(160.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            repeat(3) {
                Column(modifier = Modifier.width(160.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(brush)
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

/** Friendly message with a retry button, instead of a raw error. */
@Composable
fun HomeErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFF8E8E9A), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
        ) {
            Text("Try again", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionTitle(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontFamily = com.pulse.music.ui.theme.SoraFamily,
            letterSpacing = (-0.6).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (onSeeAll != null) {
            Text(
                text = "See all",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF6B5E),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SongCard(song: Song, onRemove: (() -> Unit)? = null, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            if (!song.albumArt.isNullOrEmpty()) {
                coil.compose.AsyncImage(
                    model = song.albumArt,
                    contentDescription = song.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF22222B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = "Music", tint = Color.Gray, modifier = Modifier.size(48.dp))
                }
            }
            if (onRemove != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .clickable(onClick = onRemove)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = song.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song.artist,
            fontSize = 13.sp,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val shimmerColors = listOf(
        Color(0xFF1E1E1E),
        Color(0xFF2C2C2C),
        Color(0xFF1E1E1E)
    )
    
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(10f, 10f),
        end = Offset(translateAnim.value, translateAnim.value)
    )
}

@Composable
fun ShimmerShelfSkeletonRowItem(shimmerBrush: Brush) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.08f)
        )
    }
}

@Composable
fun SongRowItem(song: Song, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 500))
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1f,
            animationSpec = tween(durationMillis = 100),
            label = "scale"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(),
                    onClick = onClick
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    if (!song.albumArt.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = song.albumArt,
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF22222B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Music",
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        fontSize = 14.sp,
                        color = Color(0xFFA7A7A7),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { /* Menu action */ },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}


@Composable
fun PlaylistCard(playlist: com.pulse.music.domain.Playlist, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        if (!playlist.image.isNullOrEmpty()) {
            coil.compose.AsyncImage(
                model = playlist.image,
                contentDescription = playlist.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF22222B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlaylistPlay, contentDescription = "Playlist", tint = Color.Gray, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = playlist.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (playlist.songCount != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${playlist.songCount} Songs",
                fontSize = 13.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ArtistCard(artist: com.pulse.music.domain.Artist, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!artist.image.isNullOrEmpty()) {
            coil.compose.AsyncImage(
                model = artist.image,
                contentDescription = artist.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFF22222B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "Artist", tint = Color.Gray, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = artist.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyPlayedScreen(
    songs: List<Song>,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Recently Played", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(songs) { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!song.albumArt.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = song.albumArt,
                            contentDescription = song.title,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF22222B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemoveSong(song) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Featured card: full-bleed artwork, a clear label, and one obvious action. */
@Composable
fun HeroCard(song: Song, label: String, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(250.dp)
            .shadow(24.dp, shape, ambientColor = Color(0xFFFF3B4E), spotColor = Color(0xFFFF3B4E))
            .clip(shape)
            .background(Color(0xFF1B1B22))
            .border(1.dp, Color(0x1FFFFFFF), shape)
            .clickable(onClick = onPlay)
    ) {
        if (!song.albumArt.isNullOrEmpty()) {
            AsyncImage(
                model = song.albumArt,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to Color(0xF0050507)
                )
            )
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.5.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x66000000))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = com.pulse.music.ui.theme.SoraFamily,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.7).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    color = Color(0xCCFFFFFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(12.dp, CircleShape, spotColor = Color(0xFFFF3B4E))
                    .clip(CircleShape)
                    .background(com.pulse.music.ui.theme.PulseGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}