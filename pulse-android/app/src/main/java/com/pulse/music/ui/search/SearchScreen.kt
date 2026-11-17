package com.pulse.music.ui.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

val PulseAccentSearch = Color(0xFFF92839)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearchBarFocused by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val isJamConnected by com.pulse.music.ui.jam.JamSessionManager.isConnected.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchBarFocused || uiState.query.isNotEmpty()) {
                            IconButton(onClick = { 
                                viewModel.setQuery("")
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        TextField(
                            value = uiState.query,
                            onValueChange = { viewModel.setQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchBarFocused = it.isFocused },
                            placeholder = { Text("What do you want to listen to?", color = Color.Gray, fontSize = 16.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF242424),
                                unfocusedContainerColor = Color(0xFF242424),
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = PulseAccentSearch
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                    }
                                }
                            }
                        )
                    }

                    if (uiState.query.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("All", "Songs", "Albums", "Playlists", "Artists").forEach { category ->
                                val isSelected = uiState.activeCategory == category
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSelected) PulseAccentSearch else Color(0xFF242424))
                                        .clickable { viewModel.setCategory(category) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = category,
                                        color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.query.isEmpty()) {
            val quickSearches = uiState.recentSearches
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                if (isSearchBarFocused) {
                    if (quickSearches.isNotEmpty()) {
                        item { 
                            Text("Recent searches", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
                        }
                    } else {
                        item { 
                            Text("No recent searches", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp))
                        }
                    }
                    items(quickSearches) { tag ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.setQuery(tag) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF242424)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.History, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(tag, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.removeRecentSearch(tag) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                
                val allCategories = listOf("Romance", "Dance", "Workout", "Happy", "Chill", "Party", "Hip Hop", "EDM", "Travel", "Devotional", "Ghazal", "Wedding", "Sufi", "Kids", "Pop", "Indie", "Fusion", "Folk", "Retro", "Desi Hip Hop", "Top K-Pop", "Best Of 90s", "Best Of 2021", "Best Of 2022", "The 1990s", "The 2000s", "The 2010s", "Top JioTunes", "Throwback Top 20", "Carvaan", "Fresh Hits", "Mood Booster", "Road Trip", "Sleep", "Study", "Focus", "Gym Workout", "Relax", "Instrumental", "Lo-Fi", "Bollywood", "Punjabi Hits", "Hindi Hits", "Tamil Hits", "Telugu Hits", "Malayalam Hits", "Kannada Hits", "Marathi Hits", "Gujarati Hits", "Bengali Hits", "Bhojpuri Hits", "Haryanvi Hits", "Assamese Hits", "Odia Hits", "Rajasthani Hits", "International Hits", "Rock", "Jazz", "Classical", "Electronic", "Reggae", "Country", "Anime Songs", "K-Pop", "J-Pop", "Christmas", "Festival Songs", "Krishna Bhajans", "Hanuman Chalisa", "Shiv Bhajans", "Ganesh Bhajans", "Durga Bhajans", "Podcast Picks", "Comedy Podcasts", "True Crime", "Technology Podcasts", "Business Podcasts", "Motivational Podcasts", "News Podcasts", "Storytelling Podcasts", "Kids Stories", "Nursery Rhymes")

                if (!isSearchBarFocused) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Channels", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Text("See All", fontSize = 18.sp, color = PulseAccentSearch)
                        }
                    }

                    val chunkedCategories = allCategories.chunked(2)
                    items(chunkedCategories) { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { query ->
                                Box(modifier = Modifier.weight(1f)) {
                                    LaunchedEffect(query) {
                                        viewModel.fetchCategoryPlaylist(query)
                                    }
                                    val data = viewModel.categoryPlaylists[query]
                                    val isLoading = viewModel.categoryLoading[query] ?: true
                                    
                                    if (isLoading) {
                                        SkeletonCategoryCard()
                                    } else if (data != null) {
                                        CategoryCard(
                                            title = query,
                                            imageUrl = data.image,
                                            onClick = { onNavigateToAlbum(data.id) }
                                        )
                                    } else {
                                        // Empty state if API failed so we don't break layout
                                        Box(modifier = Modifier.height(180.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF242424)))
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                item { Spacer(modifier = Modifier.height(140.dp)) }
            }
        } else {
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                val cat = uiState.activeCategory
                val showAll = cat == "All"

                // EXACT ARTIST MATCH
                if (showAll || cat == "Artists") {
                    if (uiState.isExactArtistLoading && showAll) {
                        item { SkeletonExactArtistItem() }
                    } else if (uiState.exactArtist != null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.saveRecentSearch(uiState.query)
                                        onNavigateToArtist(uiState.exactArtist!!.name) 
                                    }
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF242424)), contentAlignment = Alignment.Center) {
                                    if (!uiState.exactArtist!!.image.isNullOrEmpty()) {
                                        AsyncImage(model = uiState.exactArtist!!.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(uiState.exactArtist!!.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text("Artist", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }

                // SONGS
                if (showAll || cat == "Songs") {
                    if (showAll && (uiState.songs.data.isNotEmpty() || uiState.songs.isLoading)) {
                        item {
                            Text("Songs", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                    if (uiState.songs.isLoading && uiState.songs.data.isEmpty()) {
                        items(4) { SkeletonSongRow() }
                    } else {
                        val displaySongs = if (showAll) uiState.songs.data.take(4) else uiState.songs.data
                        items(displaySongs) { song ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.playSong(song, displaySongs) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF242424)), contentAlignment = Alignment.Center) {
                                    if (!song.albumArt.isNullOrEmpty()) {
                                        AsyncImage(model = song.albumArt, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Default.MusicNote, null, tint = Color.Gray)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, color = Color.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isJamConnected) {
                                    IconButton(onClick = { viewModel.addToQueue(song) }) { Icon(Icons.Default.Add, "Add to Queue", tint = Color.Gray) }
                                }
                                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = Color.Gray) }
                            }
                        }
                    }
                }

                // ALBUMS
                if (showAll || cat == "Albums") {
                    if (showAll && (uiState.albums.data.isNotEmpty() || uiState.albums.isLoading)) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Albums", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                    
                    if (uiState.albums.isLoading && uiState.albums.data.isEmpty()) {
                        if (showAll) {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(3) { SkeletonCard() }
                                }
                            }
                        } else {
                            items(4) { SkeletonSongRow() } // Use rows for full page
                        }
                    } else if (uiState.albums.data.isNotEmpty()) {
                        if (showAll) {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(uiState.albums.data) { album ->
                                        Column(modifier = Modifier.width(140.dp).clickable { 
                                            viewModel.saveRecentSearch(uiState.query)
                                            onNavigateToAlbum(album.id) 
                                        }) {
                                            Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242424))) {
                                                if (!album.coverArt.isNullOrEmpty()) AsyncImage(model = album.coverArt, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(album.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Album • ${album.artist}", color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        } else {
                            items(uiState.albums.data) { album ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { 
                                        viewModel.saveRecentSearch(uiState.query)
                                        onNavigateToAlbum(album.id) 
                                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF242424))) {
                                        if (!album.coverArt.isNullOrEmpty()) AsyncImage(model = album.coverArt, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(album.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Album • ${album.artist}", color = Color.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }

                // PLAYLISTS
                if (showAll || cat == "Playlists") {
                    if (showAll && (uiState.onlinePlaylists.data.isNotEmpty() || uiState.onlinePlaylists.isLoading)) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Playlists", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                    if (uiState.onlinePlaylists.isLoading && uiState.onlinePlaylists.data.isEmpty()) {
                        if (showAll) {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(3) { SkeletonCard() }
                                }
                            }
                        } else {
                            items(3) { SkeletonSongRow() }
                        }
                    } else if (uiState.onlinePlaylists.data.isNotEmpty()) {
                        if (showAll) {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(uiState.onlinePlaylists.data) { playlist ->
                                        Column(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .clickable { onNavigateToAlbum(playlist.id) }
                                        ) {
                                            Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242424)), contentAlignment = Alignment.Center) {
                                                if (!playlist.image.isNullOrEmpty()) {
                                                    AsyncImage(model = playlist.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                } else {
                                                    Icon(Icons.Default.QueueMusic, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(48.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(playlist.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        } else {
                            items(uiState.onlinePlaylists.data) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToAlbum(playlist.id) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF242424)), contentAlignment = Alignment.Center) {
                                        if (!playlist.image.isNullOrEmpty()) {
                                            AsyncImage(model = playlist.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Icon(Icons.Default.QueueMusic, null, tint = MaterialTheme.colorScheme.onBackground)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(playlist.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // ARTISTS (Other artists)
                if (showAll || cat == "Artists") {
                    val otherArtists = uiState.artists.data.filter { it.id != uiState.exactArtist?.id }
                    if (showAll && (otherArtists.isNotEmpty() || uiState.artists.isLoading)) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Artists", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                    if (uiState.artists.isLoading && uiState.artists.data.isEmpty()) {
                        if (showAll) {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(4) { SkeletonCircle() }
                                }
                            }
                        } else {
                            items(4) { SkeletonSongRow(isCircle = true) }
                        }
                    } else if (otherArtists.isNotEmpty()) {
                        if (showAll) {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(otherArtists) { artist ->
                                        Column(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .clickable { onNavigateToArtist(artist.name) },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color(0xFF242424))) {
                                                if (!artist.image.isNullOrEmpty()) AsyncImage(model = artist.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(artist.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        } else {
                            items(otherArtists) { artist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToArtist(artist.name) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF242424))) {
                                        if (!artist.image.isNullOrEmpty()) AsyncImage(model = artist.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(artist.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Artist", color = Color.Gray, fontSize = 14.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(140.dp)) }
            }
        }
    }
}

@Composable
fun SkeletonBox(modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(modifier = modifier.background(Color(0xFF333333).copy(alpha = alpha)))
}

@Composable
fun SkeletonExactArtistItem() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        SkeletonBox(modifier = Modifier.size(80.dp).clip(CircleShape))
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBox(modifier = Modifier.width(150.dp).height(24.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)))
    }
}

@Composable
fun SkeletonSongRow(isCircle: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBox(modifier = Modifier.size(56.dp).clip(if (isCircle) CircleShape else RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun SkeletonCard() {
    Column(modifier = Modifier.width(140.dp)) {
        SkeletonBox(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)))
    }
}

@Composable
fun SkeletonCircle() {
    Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SkeletonBox(modifier = Modifier.size(100.dp).clip(CircleShape))
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)))
    }
}

val searchCategoryColors = listOf(
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFFFF9800), // Orange
    Color(0xFF2196F3), // Blue
    Color(0xFF009688), // Teal
    Color(0xFFFFC107), // Yellow
    Color(0xFF4CAF50), // Green
    Color(0xFFF44336)  // Red
)

fun getColorForTitle(title: String): Color {
    val hash = kotlin.math.abs(title.hashCode())
    return searchCategoryColors[hash % searchCategoryColors.size]
}

@Composable
fun CategoryCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    val color = getColorForTitle(title)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1f, animationSpec = tween(200))
    
    val titleSize = when {
        title.length < 10 -> 24.sp
        title.length < 20 -> 22.sp
        else -> 19.sp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material.ripple.rememberRipple()) { onClick() }
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(color, color.copy(alpha = 0.6f)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().alpha(0.1f)) {
            val w = size.width
            val h = size.height
            val step = 15.dp.toPx()
            for (i in -w.toInt() until (w + h).toInt() step step.toInt()) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.1f),
                    start = androidx.compose.ui.geometry.Offset(i.toFloat(), 0f),
                    end = androidx.compose.ui.geometry.Offset(i.toFloat() - h, h),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(20.dp).align(Alignment.TopStart)
        )
        
        if (!imageUrl.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 15.dp, y = 20.dp)
                    .rotate(-18f)
                    .size(110.dp)
                    .shadow(6.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun SkeletonCategoryCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF333333))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().alpha(0.1f)) {
            val w = size.width
            val h = size.height
            val step = 15.dp.toPx()
            for (i in -w.toInt() until (w + h).toInt() step step.toInt()) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.1f),
                    start = androidx.compose.ui.geometry.Offset(i.toFloat(), 0f),
                    end = androidx.compose.ui.geometry.Offset(i.toFloat() - h, h),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onBackground.copy(alpha = alpha * 0.1f)))
    }
}
