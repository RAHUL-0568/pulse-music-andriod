package com.pulse.music.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

val PulseRed = Color(0xFFF92839)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToNowPlaying: () -> Unit = {},
    onNavigateToPlaylist: (Int) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val filters = listOf("Playlists", "Albums", "Artists", "Local")
    var selectedFilter by remember { mutableStateOf("") }

    val localSongs by viewModel.localSongs.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isGridView by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf("Recents") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val basePlaylists = if (searchQuery.isBlank()) playlists else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    val baseArtists = if (searchQuery.isBlank()) followedArtists else followedArtists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    val baseLocalSongs = if (searchQuery.isBlank()) localSongs else localSongs.filter { it.title.contains(searchQuery, ignoreCase = true) || (it.artist?.contains(searchQuery, ignoreCase = true) == true) }

    val filteredPlaylists = if (sortOption == "Alphabetical") basePlaylists.sortedBy { it.name.lowercase() } else basePlaylists
    val filteredArtists = if (sortOption == "Alphabetical") baseArtists.sortedBy { it.name.lowercase() } else baseArtists
    val filteredLocalSongs = if (sortOption == "Alphabetical") baseLocalSongs.sortedBy { it.title.lowercase() } else baseLocalSongs

    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var hasAskedPermission by remember { mutableStateOf(false) }
    
    var showSpotifyDialog by remember { mutableStateOf(false) }
    var spotifyLink by remember { mutableStateOf("") }
    val isImporting by viewModel.isImporting.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            showSpotifyDialog = false
            spotifyLink = ""
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        hasAskedPermission = true
        if (isGranted) viewModel.loadLocalSongs()
    }

    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.loadLocalSongs()
    }

    LaunchedEffect(selectedFilter) {
        if ((selectedFilter == "Local") && !hasPermission && !hasAskedPermission) {
            hasAskedPermission = true
            permissionLauncher.launch(permission)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearching) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                        placeholder = { Text("Search in Library", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF2A2A2A),
                            unfocusedContainerColor = Color(0xFF2A2A2A),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Gray,
                                modifier = Modifier.clickable { 
                                    isSearching = false
                                    searchQuery = ""
                                }
                            )
                        }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Your Library", 
                            color = MaterialTheme.colorScheme.onBackground, 
                            fontSize = 34.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!isSearching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = "Search", 
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp).clickable { isSearching = true }
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        Box {
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = "Add", 
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(28.dp).clickable { showAddMenu = true }
                            )
                            MaterialTheme(
                                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                            ) {
                                DropdownMenu(
                                    expanded = showAddMenu,
                                    onDismissRequest = { showAddMenu = false },
                                    modifier = Modifier.background(Color(0xFF2A2A2A))
                                ) {
                                DropdownMenuItem(
                                    text = { Text("Create Playlist", color = Color.White) },
                                    onClick = {
                                        showCreatePlaylistDialog = true
                                        showAddMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import from Spotify", color = Color.White) },
                                    onClick = {
                                        showSpotifyDialog = true
                                        showAddMenu = false
                                    }
                                )
                            }
                            }
                        }
                    }
                }
            }

            // Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters) { filter ->
                    val isSelected = filter == selectedFilter
                    val bgColor by animateColorAsState(if (isSelected) Color.White else Color(0xFF2A2A2A))
                    val txtColor by animateColorAsState(if (isSelected) Color.Black else Color.White)

                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material.ripple.rememberRipple(color = Color.Gray)
                            ) { 
                                selectedFilter = if (isSelected) "" else filter 
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            color = txtColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Sort Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showSortMenu = true }.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sortOption, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(Color(0xFF2A2A2A))
                    ) {
                        listOf("Recents", "Alphabetical").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.White) },
                                onClick = {
                                    sortOption = option
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
                Icon(
                    if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle View",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp).clickable { isGridView = !isGridView }
                )
            }

            // Library Items
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isGridView) 2 else 1),
                modifier = Modifier.fillMaxSize().padding(horizontal = if (isGridView) 16.dp else 0.dp), 
                contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp),
                horizontalArrangement = if (isGridView) Arrangement.spacedBy(16.dp) else Arrangement.Start,
                verticalArrangement = if (isGridView) Arrangement.spacedBy(16.dp) else Arrangement.Top
            ) {
                // Liked Songs
                if (selectedFilter.isEmpty() || selectedFilter == "Playlists") {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .clickable { onNavigateToPlaylist(-1) }
                                .padding(horizontal = 24.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF450af5), Color(0xFFc4efd9)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = "Liked", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Liked Songs", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = Color(0xFF1DB954), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Playlist • ${likedSongs.size} songs", color = Color(0xFFA7A7A7), fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                // Playlists
                if (selectedFilter.isEmpty() || selectedFilter == "Playlists") {
                    items(filteredPlaylists) { playlist ->
                        val songs by remember(playlist.id) {
                            viewModel.getSongsForPlaylist(playlist.id)
                        }.collectAsState(initial = emptyList())
                        val totalMs = songs.sumOf { it.durationMs ?: 0L }
                        val totalSecs = totalMs / 1000
                        val hours = totalSecs / 3600
                        val mins = (totalSecs % 3600) / 60
                        val durationStr = when {
                            totalMs == 0L -> ""
                            hours > 0 -> " • ${hours}hr ${mins}min"
                            else -> " • ${mins} min"
                        }
                        val validCovers = songs.mapNotNull { it.albumArt }.filter { it.isNotEmpty() }
                        
                        LibraryItem(
                            isGridView = isGridView,
                            title = playlist.name,
                            subtitle = "Playlist • ${songs.size} songs$durationStr",
                            onClick = { onNavigateToPlaylist(playlist.id) },
                            imageContent = {
                                if (validCovers.size >= 4) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            AsyncImage(model = validCovers[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                            AsyncImage(model = validCovers[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                        }
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            AsyncImage(model = validCovers[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                            AsyncImage(model = validCovers[3], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                                        }
                                    }
                                } else if (validCovers.isNotEmpty()) {
                                    AsyncImage(model = validCovers[0], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlist", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }
                        )
                    }
                }

                // Artists
                if (selectedFilter.isEmpty() || selectedFilter == "Artists") {
                    items(filteredArtists) { artist ->
                        LibraryItem(
                            isGridView = isGridView,
                            title = artist.name,
                            subtitle = "Artist",
                            isCircle = true,
                            onClick = { onNavigateToArtist(artist.name) },
                            imageContent = {
                                if (!artist.image.isNullOrEmpty()) {
                                    AsyncImage(model = artist.image, contentDescription = artist.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = "Artist", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }
                        )
                    }
                }
                
                // Downloaded / Local
                if (selectedFilter == "Local") {
                    items(filteredLocalSongs) { song ->
                        LibraryItem(
                            isGridView = isGridView,
                            title = song.title,
                            subtitle = "Song • ${song.artist}",
                            onClick = { 
                                viewModel.playSong(song, filteredLocalSongs)
                                onNavigateToNowPlaying() 
                            },
                            imageContent = {
                                if (!song.albumArt.isNullOrEmpty()) {
                                    AsyncImage(model = song.albumArt, contentDescription = song.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.MusicNote, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        )
                    }
                }
            }
        }
        
        // Spotify Import Dialog
        
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                newPlaylistName = ""
            },
            title = { Text("Create Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color.Gray,
                        focusedContainerColor = Color(0xFF2A2A2A),
                        unfocusedContainerColor = Color(0xFF2A2A2A)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            showCreatePlaylistDialog = false
                            newPlaylistName = ""
                        }
                    }
                ) {
                    Text("Create", color = Color(0xFF1DB954))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                    }
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    if (showSpotifyDialog) {
            AlertDialog(
                onDismissRequest = { if (!isImporting) showSpotifyDialog = false },
                containerColor = Color(0xFF2A2A2A),
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                textContentColor = MaterialTheme.colorScheme.onBackground,
                title = { Text("Import Spotify Playlist") },
                text = {
                    Column {
                        Text("Paste your public Spotify playlist link below.", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = spotifyLink,
                            onValueChange = { spotifyLink = it },
                            placeholder = { Text("https://open.spotify.com/playlist/...", color = Color.DarkGray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = PulseRed,
                                unfocusedBorderColor = Color.Gray
                            ),
                            singleLine = true,
                            enabled = !isImporting
                        )
                        if (isImporting) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = PulseRed, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Importing and matching tracks...", color = PulseRed, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.importSpotifyPlaylist(spotifyLink) },
                        colors = ButtonDefaults.buttonColors(containerColor = PulseRed, contentColor = MaterialTheme.colorScheme.background),
                        enabled = spotifyLink.isNotBlank() && !isImporting
                    ) {
                        Text("Import", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSpotifyDialog = false }, enabled = !isImporting) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}


@Composable
fun LibraryItem(
    isGridView: Boolean,
    title: String,
    subtitle: String,
    isCircle: Boolean = false,
    onClick: () -> Unit,
    imageContent: @Composable () -> Unit
) {
    if (isGridView) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(if (isCircle) CircleShape else RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                imageContent()
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFFA7A7A7), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clickable { onClick() }
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(if (isCircle) CircleShape else RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                imageContent()
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFFA7A7A7), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
