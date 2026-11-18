package com.pulse.music.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pulse.music.data.network.MusicCapsuleArtist
import com.pulse.music.data.network.MusicCapsuleResponse
import com.pulse.music.data.network.MusicCapsuleSong
import com.pulse.music.ui.auth.AuthViewModel
import kotlinx.coroutines.launch

// True-black look: near-black cards with hairline borders, one red accent.
private val Muted = Color(0xFF9A9A9A)
private val CardBg = Color(0xFF0F0F0F)
private val CardBorder = Color(0xFF1F1F1F)
private val Accent = Color(0xFFF92839)

@Composable
fun ProfileScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val token by viewModel.token.collectAsState()
    val username by viewModel.username.collectAsState()
    val email by viewModel.email.collectAsState()
    val profilePicUri by viewModel.profilePicUri.collectAsState()

    val highQuality by viewModel.highQuality.collectAsState()
    val spatialAudio by viewModel.spatialAudio.collectAsState()
    val gapless by viewModel.gapless.collectAsState()
    val crossfade by viewModel.crossfade.collectAsState()

    val likedCount by profileViewModel.likedCount.collectAsState()
    val playlistCount by profileViewModel.playlistCount.collectAsState()
    val followingCount by profileViewModel.followingCount.collectAsState()
    val listening by profileViewModel.listening.collectAsState()

    var showLogoutConfirm by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadProfilePic(context, it.toString()) }
    }

    // Logging out (or being logged out) sends us back to the login page
    LaunchedEffect(token) {
        if (token.isNullOrEmpty()) onNavigateToAuth()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                ProfileHeader(
                    username = username?.takeIf { it.isNotBlank() } ?: "Music lover",
                    email = email ?: "",
                    profilePicUri = profilePicUri,
                    onEditProfilePic = { launcher.launch("image/*") }
                )
            }
            item { StatsRow(likedCount, playlistCount, followingCount) }

            item { SectionLabel("Your listening") }
            item {
                ListeningSection(
                    state = listening,
                    onRetry = { profileViewModel.loadListening() },
                    onPlaySong = { song, all -> profileViewModel.playTopSong(song, all) }
                )
            }

            item { SectionLabel("Playback") }
            item {
                PlaybackSettings(
                    highQuality = highQuality,
                    spatialAudio = spatialAudio,
                    gapless = gapless,
                    crossfade = crossfade,
                    onUpdate = { hq, sa, gp, cf -> viewModel.updatePlaybackSettings(hq, sa, gp, cf) }
                )
            }

            item { SectionLabel("Storage") }
            item {
                StorageSettings(onClearCache = {
                    com.pulse.music.ui.home.HomeViewModel.cachedUiState = null
                    // Off the main thread. Downloaded songs (audio_cache) are kept: the player has
                    // that folder open, and deleting it just makes you download songs again.
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        context.cacheDir.listFiles()
                            ?.filter { it.name != "audio_cache" }
                            ?.forEach { file -> file.deleteRecursively() }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }

            item { SectionLabel("App") }
            item { AppUpdates(context = context) }

            item { SectionLabel("Account") }
            item { LogoutRow(onClick = { showLogoutConfirm = true }) }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Pulse Music  ·  v" + com.pulse.music.BuildConfig.VERSION_NAME,
                        color = Color(0xFF5E5E5E),
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                containerColor = Color(0xFF141414),
                title = { Text("Log out?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("You'll need to sign in again to use Pulse.", color = Muted) },
                confirmButton = {
                    TextButton(onClick = { showLogoutConfirm = false; viewModel.logout() }) {
                        Text("Log out", color = Accent, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", color = Color.White) }
                }
            )
        }
    }
}

// ───────────────────────── header + stats ─────────────────────────

@Composable
private fun ProfileHeader(username: String, email: String, profilePicUri: String?, onEditProfilePic: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // soft red glow behind the avatar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Accent.copy(alpha = 0.22f), Color.Transparent)))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 8.dp, start = 24.dp, end = 24.dp)
        ) {
            Box(modifier = Modifier.size(112.dp).clickable { onEditProfilePic() }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                        .border(3.dp, Brush.sweepGradient(listOf(Accent, Color(0xFFFF8A5B), Accent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePicUri.isNullOrEmpty()) {
                        Text(
                            text = username.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current).data(profilePicUri).crossfade(true).build(),
                            contentDescription = "Profile picture",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                // camera badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .border(2.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(username, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (email.isNotBlank()) {
                Text(email, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatsRow(liked: Int, playlists: Int, following: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(liked.toString(), "Liked songs", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(32.dp).background(CardBorder))
        StatItem(playlists.toString(), "Playlists", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(32.dp).background(CardBorder))
        StatItem(following.toString(), "Following", Modifier.weight(1f))
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

// ───────────────────────── listening stats ─────────────────────────

@Composable
private fun ListeningSection(
    state: ListeningState,
    onRetry: () -> Unit,
    onPlaySong: (MusicCapsuleSong, List<MusicCapsuleSong>) -> Unit
) {
    when (state) {
        is ListeningState.Loading -> {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) {
                        Box(Modifier.fillMaxWidth(if (it == 0) 0.5f else 0.9f).height(16.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1C1C1C)))
                    }
                }
            }
        }
        is ListeningState.Empty -> {
            SoftCard {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing here yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Play a few songs and your top artists and songs will show up here.",
                        color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        is ListeningState.Failed -> {
            SoftCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Couldn't load your stats.", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text("Retry", color = Accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        is ListeningState.Ready -> ListeningReady(state.data, onPlaySong)
    }
}

@Composable
private fun ListeningReady(data: MusicCapsuleResponse, onPlaySong: (MusicCapsuleSong, List<MusicCapsuleSong>) -> Unit) {
    Column {
        // two big numbers
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BigNumberCard(data.totalPlays.toString(), "Total plays", Modifier.weight(1f))
            BigNumberCard(data.uniqueSongs.toString(), "Different songs", Modifier.weight(1f))
        }

        if (data.topArtists.isNotEmpty()) {
            Text("Top artists", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(data.topArtists.take(8)) { artist -> TopArtistItem(artist) }
            }
        }

        if (data.topSongs.isNotEmpty()) {
            Text("Top songs", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
            val songs = data.topSongs.take(5)
            SoftCard {
                Column {
                    songs.forEachIndexed { index, song ->
                        TopSongRow(rank = index + 1, song = song, onClick = { onPlaySong(song, songs) })
                        if (index != songs.lastIndex) HorizontalDivider(color = CardBorder, modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BigNumberCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Text(value, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun TopArtistItem(artist: MusicCapsuleArtist) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.image.isNullOrBlank()) {
                AsyncImage(model = artist.image, contentDescription = artist.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(artist.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text("${artist.playCount} plays", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TopSongRow(rank: Int, song: MusicCapsuleSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rank.toString(), color = if (rank == 1) Accent else Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
            if (song.albumArt.isNotBlank()) {
                AsyncImage(model = song.albumArt, contentDescription = song.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text("${song.playCount}×", color = Muted, fontSize = 13.sp)
    }
}

// ───────────────────────── settings ─────────────────────────

@Composable
private fun SoftCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp)),
        content = content
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    tint: Color = Accent,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF181818)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (description != null) Text(description, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun PulseSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Accent,
            uncheckedThumbColor = Color(0xFF8A8A8A),
            uncheckedTrackColor = Color(0xFF1F1F1F),
            uncheckedBorderColor = Color(0xFF2E2E2E)
        )
    )
}

@Composable
private fun PlaybackSettings(
    highQuality: Boolean,
    spatialAudio: Boolean,
    gapless: Boolean,
    crossfade: Int,
    onUpdate: (Boolean, Boolean, Boolean, Int) -> Unit
) {
    SoftCard {
        SettingRow(
            icon = Icons.Default.HighQuality,
            title = "High quality",
            description = "Best sound on Wi-Fi and mobile data. Turn off to save data.",
            trailing = { PulseSwitch(highQuality) { onUpdate(it, spatialAudio, gapless, crossfade) } }
        )
        HorizontalDivider(color = CardBorder)
        SettingRow(
            icon = Icons.Default.Headphones,
            title = "Spatial audio",
            description = "Wider, more immersive sound",
            trailing = { PulseSwitch(spatialAudio) { onUpdate(highQuality, it, gapless, crossfade) } }
        )
        HorizontalDivider(color = CardBorder)
        SettingRow(
            icon = Icons.Default.SkipNext,
            title = "Gapless playback",
            description = "Songs from the same album play straight into each other",
            trailing = { PulseSwitch(gapless) { onUpdate(highQuality, spatialAudio, it, crossfade) } }
        )
        HorizontalDivider(color = CardBorder)
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Crossfade", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(if (crossfade == 0) "Off" else "${crossfade}s", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = crossfade.toFloat(),
                onValueChange = { onUpdate(highQuality, spatialAudio, gapless, it.toInt()) },
                valueRange = 0f..12f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Color(0xFF262626)
                )
            )
        }
    }
}

@Composable
private fun StorageSettings(onClearCache: () -> Unit) {
    val context = LocalContext.current
    var cacheSizeMb by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun calculateCacheSize() {
        var sizeBytes = 0L
        try {
            // songs you've played are kept on purpose (so they don't use data again), so they're not counted here
            context.cacheDir.listFiles()?.filter { it.name != "audio_cache" }?.forEach { f ->
                f.walkTopDown().filter { it.isFile }.forEach { sizeBytes += it.length() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cacheSizeMb = sizeBytes / (1024f * 1024f)
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { calculateCacheSize() }
    }

    SoftCard {
        SettingRow(
            icon = Icons.Default.DeleteSweep,
            title = "Clear cache",
            description = String.format("%.1f MB of images and temporary data. Downloaded songs are kept.", cacheSizeMb),
            tint = Muted,
            onClick = {
                onClearCache()
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.delay(500)
                    calculateCacheSize()
                }
            }
        )
    }
}

@Composable
private fun AppUpdates(context: android.content.Context) {
    val coroutineScope = rememberCoroutineScope()
    val update by com.pulse.music.update.UpdateManager.available.collectAsState()
    val downloadProgress by com.pulse.music.update.UpdateManager.downloadProgress.collectAsState()
    val downloadedBytes by com.pulse.music.update.UpdateManager.downloadedBytes.collectAsState()
    val downloadStatusText by com.pulse.music.update.UpdateManager.downloadStatusText.collectAsState()
    val isDownloading by com.pulse.music.update.UpdateManager.isDownloading.collectAsState()
    var isChecking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) } // short result such as "You're up to date"

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3500)
            message = null
        }
    }
    // quiet check when this page opens (the app also checks when it starts)
    LaunchedEffect(Unit) {
        com.pulse.music.update.UpdateManager.checkIfStale(com.pulse.music.BuildConfig.VERSION_NAME)
    }

    SoftCard {
        val title = when {
            isDownloading -> downloadStatusText
            isChecking -> "Checking for updates…"
            message != null -> message!!
            update != null -> "Update available: " + update!!.newVersion
            else -> "Check for updates"
        }
        val subtitle = if (update != null && !isDownloading) {
            "Tap to download and install"
        } else {
            "You have version " + com.pulse.music.BuildConfig.VERSION_NAME
        }
        SettingRow(
            icon = Icons.Default.SystemUpdate,
            title = title,
            description = subtitle,
            tint = if (update != null) Color(0xFF10B981) else Accent,
            onClick = {
                if (!isChecking && !isDownloading) {
                    val current = update
                    if (current != null) {
                        if (!com.pulse.music.update.UpdateManager.canInstall(context)) {
                            android.widget.Toast.makeText(context, "Allow Pulse to install apps, then tap Update again.", android.widget.Toast.LENGTH_LONG).show()
                            com.pulse.music.update.UpdateManager.openInstallSettings(context)
                        } else {
                            com.pulse.music.update.UpdateManager.downloadAndInstallUpdate(context, current.downloadUrl)
                        }
                    } else {
                        isChecking = true
                        coroutineScope.launch {
                            val result = com.pulse.music.update.UpdateManager.check(com.pulse.music.BuildConfig.VERSION_NAME)
                            message = when (result) {
                                is com.pulse.music.update.UpdateManager.UpdateCheck.Available -> null
                                is com.pulse.music.update.UpdateManager.UpdateCheck.UpToDate -> "You're up to date"
                                is com.pulse.music.update.UpdateManager.UpdateCheck.Failed -> "Couldn't reach the update server"
                            }
                            isChecking = false
                        }
                    }
                }
            },
            trailing = if (update != null && !isDownloading) {
                {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Accent).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("NEW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else null
        )
        if (isDownloading) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                if (downloadProgress >= 0f) {
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Accent, trackColor = Color(0xFF262626))
                    Text("${(downloadProgress * 100).toInt()}% downloaded", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp).align(Alignment.End))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp), color = Accent, trackColor = Color(0xFF262626))
                    Text(String.format("%.1f MB downloaded…", downloadedBytes / (1024f * 1024f)), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp).align(Alignment.End))
                }
            }
        }
    }
}
@Composable
private fun LogoutRow(onClick: () -> Unit) {
    SoftCard {
        SettingRow(
            icon = Icons.Default.Logout,
            title = "Log out",
            tint = Color(0xFFEF4444),
            onClick = onClick
        )
    }
}
