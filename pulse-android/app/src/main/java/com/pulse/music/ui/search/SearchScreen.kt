package com.pulse.music.ui.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

val PulseAccentSearch = Color(0xFFF92839)
private val SearchMuted = Color(0xFF9A9A9A)
private val SearchCardBg = Color(0xFF0F0F0F)
private val SearchFieldBg = Color(0xFF1A1A1A)
private val SearchBorder = Color(0xFF1F1F1F)
private val SearchBorderStrong = Color(0xFF2E2E2E)

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

    // Gives the debounced search time to start before we claim "no results".
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.query) {
        settled = false
        delay(700)
        settled = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (!isSearchBarFocused && uiState.query.isEmpty()) {
                        Text(
                            "Search",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchBarFocused || uiState.query.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.setQuery("")
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                        TextField(
                            value = uiState.query,
                            onValueChange = { viewModel.setQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .border(1.dp, if (isSearchBarFocused) SearchBorderStrong else SearchBorder, RoundedCornerShape(16.dp))
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchBarFocused = it.isFocused },
                            placeholder = { Text("Songs, artists, albums", color = SearchMuted, fontSize = 16.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = SearchMuted)
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SearchFieldBg,
                                unfocusedContainerColor = SearchCardBg,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = PulseAccentSearch
                            ),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = SearchMuted)
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
                                        .background(if (isSelected) Color.White else SearchFieldBg)
                                        .border(1.dp, if (isSelected) Color.White else SearchBorderStrong, CircleShape)
                                        .clickable { viewModel.setCategory(category) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = category,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                item { Spacer(modifier = Modifier.height(8.dp)) }
                if (isSearchBarFocused) {
                    if (quickSearches.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent searches", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Clear all",
                                    color = PulseAccentSearch,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { quickSearches.toList().forEach { viewModel.removeRecentSearch(it) } }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        item {
                            SearchEmptyState(
                                icon = Icons.Default.History,
                                title = "No recent searches",
                                hint = "Things you search for will show up here."
                            )
                        }
                    }
                    items(quickSearches) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setQuery(tag) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SearchFieldBg)
                                    .border(1.dp, SearchBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.History, null, tint = SearchMuted, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(tag, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.removeRecentSearch(tag) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = SearchMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                val allCategories = listOf("Romance", "Dance", "Workout", "Happy", "Chill", "Party", "Hip Hop", "EDM", "Travel", "Devotional", "Ghazal", "Wedding", "Sufi", "Kids", "Pop", "Indie", "Fusion", "Folk", "Retro", "Desi Hip Hop", "Top K-Pop", "Best Of 90s", "Best Of 2021", "Best Of 2022", "The 1990s", "The 2000s", "The 2010s", "Top JioTunes", "Throwback Top 20", "Carvaan", "Fresh Hits", "Mood Booster", "Road Trip", "Sleep", "Study", "Focus", "Gym Workout", "Relax", "Instrumental", "Lo-Fi", "Bollywood", "Punjabi Hits", "Hindi Hits", "Tamil Hits", "Telugu Hits", "Malayalam Hits", "Kannada Hits", "Marathi Hits", "Gujarati Hits", "Bengali Hits", "Bhojpuri Hits", "Haryanvi Hits", "Assamese Hits", "Odia Hits", "Rajasthani Hits", "International Hits", "Rock", "Jazz", "Classical", "Electronic", "Reggae", "Country", "Anime Songs", "K-Pop", "J-Pop", "Christmas", "Festival Songs", "Krishna Bhajans", "Hanuman Chalisa", "Shiv Bhajans", "Ganesh Bhajans", "Durga Bhajans", "Podcast Picks", "Comedy Podcasts", "True Crime", "Technology Podcasts", "Business Podcasts", "Motivational Podcasts", "News Podcasts", "Storytelling Podcasts", "Kids Stories", "Nursery Rhymes")

                if (!isSearchBarFocused) {
                    item {
                        Text(
                            "Browse channels",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        )
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
                                        // Neutral tile if the API failed so we don't break layout
                                        Box(
                                            modifier = Modifier
                                                .height(104.dp)
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(SearchCardBg)
                                                .border(1.dp, SearchBorder, RoundedCornerShape(16.dp))
                                        )
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
            val cat = uiState.activeCategory
            val showAll = cat == "All"
            val anyLoading = uiState.songs.isLoading || uiState.albums.isLoading ||
                uiState.artists.isLoading || uiState.onlinePlaylists.isLoading || uiState.isExactArtistLoading
            val otherArtists = uiState.artists.data.filter { it.id != uiState.exactArtist?.id }
            val hasAnything = uiState.songs.data.isNotEmpty() || uiState.albums.data.isNotEmpty() ||
                uiState.onlinePlaylists.data.isNotEmpty() || otherArtists.isNotEmpty() || uiState.exactArtist != null
            val allFailed = uiState.songs.isError && uiState.albums.isError && uiState.artists.isError

            if (!hasAnything && !anyLoading && settled) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    if (allFailed) {
                        SearchEmptyState(
                            icon = Icons.Default.Search,
                            title = "Couldn't load results",
                            hint = "Check your connection and try again."
                        )
                    } else {
                        SearchEmptyState(
                            icon = Icons.Default.Search,
                            title = "No results for \"${uiState.query}\"",
                            hint = "Check the spelling or try a different search."
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {

                    // EXACT ARTIST MATCH
                    if (showAll || cat == "Artists") {
                        if (uiState.isExactArtistLoading && showAll) {
                            item { SkeletonExactArtistItem() }
                        } else if (uiState.exactArtist != null) {
                            item {
                                val exact = uiState.exactArtist!!
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(SearchCardBg)
                                        .border(1.dp, SearchBorderStrong, RoundedCornerShape(18.dp))
                                        .clickable {
                                            viewModel.saveRecentSearch(uiState.query)
                                            onNavigateToArtist(exact.name)
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(80.dp).clip(CircleShape).background(SearchFieldBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!exact.image.isNullOrEmpty()) {
                                            AsyncImage(model = exact.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = SearchMuted)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exact.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("Artist", color = SearchMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }

                    // SONGS
                    if (showAll || cat == "Songs") {
                        if (showAll && (uiState.songs.data.isNotEmpty() || uiState.songs.isLoading)) {
                            item { SearchSectionTitle("Songs") }
                        }
                        if (uiState.songs.isLoading && uiState.songs.data.isEmpty()) {
                            items(4) { SkeletonSongRow() }
                        } else {
                            val displaySongs = if (showAll) uiState.songs.data.take(4) else uiState.songs.data
                            items(displaySongs) { song ->
                                SearchResultRow(
                                    title = song.title,
                                    subtitle = song.artist,
                                    imageUrl = song.albumArt,
                                    fallbackIcon = Icons.Default.MusicNote,
                                    onClick = { viewModel.playSong(song, displaySongs) }
                                ) {
                                    if (isJamConnected) {
                                        IconButton(onClick = { viewModel.addToQueue(song) }) {
                                            Icon(Icons.Default.Add, "Add to Queue", tint = SearchMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ALBUMS
                    if (showAll || cat == "Albums") {
                        if (showAll && (uiState.albums.data.isNotEmpty() || uiState.albums.isLoading)) {
                            item { SearchSectionTitle("Albums") }
                        }

                        if (uiState.albums.isLoading && uiState.albums.data.isEmpty()) {
                            if (showAll) {
                                item {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        items(3) { SkeletonCard() }
                                    }
                                }
                            } else {
                                items(4) { SkeletonSongRow() }
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
                                                Box(
                                                    modifier = Modifier
                                                        .size(140.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(SearchFieldBg)
                                                        .border(1.dp, SearchBorder, RoundedCornerShape(14.dp))
                                                ) {
                                                    if (!album.coverArt.isNullOrEmpty()) AsyncImage(model = album.coverArt, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(album.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(album.artist, color = SearchMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(uiState.albums.data) { album ->
                                    SearchResultRow(
                                        title = album.title,
                                        subtitle = "Album  •  ${album.artist}",
                                        imageUrl = album.coverArt,
                                        fallbackIcon = Icons.Default.Album,
                                        onClick = {
                                            viewModel.saveRecentSearch(uiState.query)
                                            onNavigateToAlbum(album.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // PLAYLISTS
                    if (showAll || cat == "Playlists") {
                        if (showAll && (uiState.onlinePlaylists.data.isNotEmpty() || uiState.onlinePlaylists.isLoading)) {
                            item { SearchSectionTitle("Playlists") }
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
                                                Box(
                                                    modifier = Modifier
                                                        .size(140.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(SearchFieldBg)
                                                        .border(1.dp, SearchBorder, RoundedCornerShape(14.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!playlist.image.isNullOrEmpty()) {
                                                        AsyncImage(model = playlist.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    } else {
                                                        Icon(Icons.Default.QueueMusic, null, tint = SearchMuted, modifier = Modifier.size(48.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(playlist.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(uiState.onlinePlaylists.data) { playlist ->
                                    SearchResultRow(
                                        title = playlist.title,
                                        subtitle = "Playlist",
                                        imageUrl = playlist.image,
                                        fallbackIcon = Icons.Default.QueueMusic,
                                        onClick = { onNavigateToAlbum(playlist.id) }
                                    )
                                }
                            }
                        }
                    }

                    // ARTISTS (Other artists)
                    if (showAll || cat == "Artists") {
                        if (showAll && (otherArtists.isNotEmpty() || uiState.artists.isLoading)) {
                            item { SearchSectionTitle("Artists") }
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
                                                Box(
                                                    modifier = Modifier
                                                        .size(100.dp)
                                                        .clip(CircleShape)
                                                        .background(SearchFieldBg)
                                                        .border(1.dp, SearchBorder, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!artist.image.isNullOrEmpty()) {
                                                        AsyncImage(model = artist.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    } else {
                                                        Icon(Icons.Default.Person, null, tint = SearchMuted, modifier = Modifier.size(40.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(artist.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(otherArtists) { artist ->
                                    SearchResultRow(
                                        title = artist.name,
                                        subtitle = "Artist",
                                        imageUrl = artist.image,
                                        fallbackIcon = Icons.Default.Person,
                                        circle = true,
                                        onClick = { onNavigateToArtist(artist.name) }
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(140.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SearchEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SearchFieldBg)
                .border(1.dp, SearchBorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SearchMuted, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(hint, color = SearchMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    circle: Boolean = false,
    trailing: @Composable () -> Unit = {}
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(SearchFieldBg)
                .border(1.dp, SearchBorder, shape),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(fallbackIcon, null, tint = SearchMuted)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = SearchMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
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
    Box(modifier = modifier.background(Color(0xFF2E2E2E).copy(alpha = alpha)))
}

@Composable
fun SkeletonExactArtistItem() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(modifier = Modifier.size(80.dp).clip(CircleShape))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            SkeletonBox(modifier = Modifier.width(150.dp).height(24.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun SkeletonSongRow(isCircle: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBox(modifier = Modifier.size(56.dp).clip(if (isCircle) CircleShape else RoundedCornerShape(10.dp)))
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
        SkeletonBox(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(14.dp)))
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

// Muted, non-violet tints used behind category artwork.
val searchCategoryColors = listOf(
    Color(0xFFB3202D), // Red
    Color(0xFFC25A1B), // Orange
    Color(0xFF1F6FB3), // Blue
    Color(0xFF117A6E), // Teal
    Color(0xFF2E8B4F), // Green
    Color(0xFFB8860B), // Gold
    Color(0xFFB02A62)  // Rose
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
    val tint = getColorForTitle(title)
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.45f))
            .border(1.dp, SearchBorderStrong, shape)
            .clickable { onClick() }
    ) {
        if (!imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        )
    }
}

@Composable
fun SkeletonCategoryCard() {
    SkeletonBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}
