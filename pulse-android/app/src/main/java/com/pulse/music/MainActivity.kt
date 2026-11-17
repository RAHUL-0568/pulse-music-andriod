@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.pulse.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulse.music.player.MusicPlayerManager
import com.pulse.music.ui.home.HomeScreen
import com.pulse.music.ui.library.LibraryScreen
import com.pulse.music.ui.nowplaying.NowPlayingScreen
import com.pulse.music.ui.jam.JamScreen
import com.pulse.music.ui.nowplaying.PulseRed
import com.pulse.music.ui.search.SearchScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.rememberCoroutineScope

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    @Inject
    lateinit var authRepository: com.pulse.music.data.repository.AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto-clear cache if it exceeds 150MB
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // Delete old update APKs immediately on startup to save space
                com.pulse.music.update.UpdateManager.cleanupOldUpdates(this@MainActivity)

                // audio_cache (downloaded songs, up to 1 GB) and image_cache (covers) manage their own
                // size. Wiping them here made every song and cover download again after each launch.
                val selfManaged = setOf("audio_cache", "image_cache")
                val clearable = cacheDir.listFiles()?.filter { it.name !in selfManaged } ?: emptyList()

                var sizeBytes = 0L
                clearable.forEach { f -> f.walkTopDown().filter { it.isFile }.forEach { sizeBytes += it.length() } }
                val cacheSizeMb = sizeBytes / (1024f * 1024f)

                if (cacheSizeMb > 150f) {
                    clearable.forEach { file ->
                        file.deleteRecursively()
                    }
                    com.pulse.music.ui.home.HomeViewModel.cachedUiState = null
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(this@MainActivity, "Auto-cleared cache ($cacheSizeMb MB)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        if (intent?.getBooleanExtra("open_profile", false) == true) openProfileRequest.value = true

        val initialToken = runBlocking { authRepository.authToken.first() }
        val startDest = if (initialToken.isNullOrEmpty()) "auth_login" else "home"

        setContent {
            com.pulse.music.ui.theme.PulseMusicTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val showBottomNav = currentRoute != "now_playing" && currentRoute != "auth_login" && currentRoute != "auth_register" && currentRoute != "auth_forgot_password"
                val showMiniPlayer = currentRoute != "now_playing" && currentRoute != "auth_login" && currentRoute != "auth_register" && currentRoute != "auth_forgot_password"
                val currentSong by musicPlayerManager.currentSong.collectAsState()
                val isPlaying by musicPlayerManager.isPlaying.collectAsState()
                val isBuffering by musicPlayerManager.isBuffering.collectAsState()
                // A newer version is published: a red dot appears on the Profile tab
                val updateAvailable by com.pulse.music.update.UpdateManager.available.collectAsState()
                val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                val scope = rememberCoroutineScope()

                // The admin blocked or removed this account: sign out and say why.
                val forcedLogout by com.pulse.music.data.network.AccountEvents.forcedLogout.collectAsState()
                androidx.compose.runtime.LaunchedEffect(forcedLogout) {
                    val message = forcedLogout ?: return@LaunchedEffect
                    com.pulse.music.data.network.AccountEvents.consume()
                    com.pulse.music.ui.jam.JamSessionManager.disconnect()
                    musicPlayerManager.stopAndClear()
                    authRepository.logout()
                    android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                    navController.navigate("auth_login") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                // Tapping the "Update available" notification opens Profile
                androidx.compose.runtime.LaunchedEffect(openProfileRequest.value) {
                    if (openProfileRequest.value) {
                        openProfileRequest.value = false
                        navController.navigate("profile") { launchSingleTop = true }
                    }
                }

                // A pop-up the first time a new version is found while the app is open
                var dismissedUpdate by remember { mutableStateOf<String?>(null) }
                val foundUpdate = updateAvailable
                if (foundUpdate != null && dismissedUpdate != foundUpdate.newVersion &&
                    currentRoute != null && !currentRoute.startsWith("auth")
                ) {
                    AlertDialog(
                        onDismissRequest = { dismissedUpdate = foundUpdate.newVersion },
                        containerColor = Color(0xFF141414),
                        title = { Text("Update available", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = { Text("Pulse Music ${foundUpdate.newVersion} is ready to install.", color = Color(0xFF9A9A9A)) },
                        confirmButton = {
                            TextButton(onClick = {
                                dismissedUpdate = foundUpdate.newVersion
                                navController.navigate("profile") { launchSingleTop = true }
                            }) { Text("View update", color = PulseRed, fontWeight = FontWeight.SemiBold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { dismissedUpdate = foundUpdate.newVersion }) { Text("Later", color = Color.White) }
                        }
                    )
                }

                Scaffold { paddingValues ->
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startDest,
                            enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
                            exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) },
                            popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
                            popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) }
                        ) {
                            composable("auth_login") {
                                com.pulse.music.ui.auth.LoginScreen(
                                    onNavigateToHome = {
                                        navController.navigate("home") {
                                            popUpTo("auth_login") { inclusive = true }
                                        }
                                    },
                                    onNavigateToRegister = {
                                        navController.navigate("auth_register")
                                    },
                                    onNavigateToForgotPassword = {
                                        navController.navigate("auth_forgot_password")
                                    }
                                )
                            }
                            composable("auth_register") {
                                com.pulse.music.ui.auth.RegisterScreen(
                                    onNavigateToHome = {
                                        navController.navigate("home") {
                                            popUpTo("auth_register") { inclusive = true }
                                            popUpTo("auth_login") { inclusive = true }
                                        }
                                    },
                                    onNavigateToLogin = {
                                        navController.navigate("auth_login") {
                                            popUpTo("auth_register") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("auth_forgot_password") {
                                com.pulse.music.ui.auth.ForgotPasswordScreen(
                                    onNavigateToLogin = {
                                        navController.navigate("auth_login") {
                                            popUpTo("auth_forgot_password") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(
                                "home",
                                enterTransition = { androidx.compose.animation.EnterTransition.None },
                                exitTransition = { androidx.compose.animation.ExitTransition.None }
                            ) {
                                HomeScreen(
                                    onNavigateToNowPlaying = {
                                        navController.navigate("now_playing")
                                    },
                                    onNavigateToAlbum = { albumId ->
                                        navController.navigate("album/$albumId")
                                    },
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate("artist/$artistName")
                                    },
                                    onNavigateToProfile = {
                                        navController.navigate("profile")
                                    }
                                )
                            }
                            composable(
                                "search",
                                enterTransition = { androidx.compose.animation.EnterTransition.None },
                                exitTransition = { androidx.compose.animation.ExitTransition.None }
                            ) {
                                SearchScreen(
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate("artist/$artistName")
                                    },
                                    onNavigateToAlbum = { albumId ->
                                        navController.navigate("album/$albumId")
                                    },
                                    focusRequester = searchFocusRequester
                                )
                            }
                            composable("artist/{artistName}") { backStackEntry ->
                                val artistName = backStackEntry.arguments?.getString("artistName") ?: return@composable
                                com.pulse.music.ui.artist.ArtistScreen(
                                    artistName = artistName,
                                    onBack = { navController.popBackStack() },
                                    onSongClick = { song, playlist -> 
                                        musicPlayerManager.playSongFromList(song, playlist) 
                                    },
                                    onAlbumClick = { albumId ->
                                        navController.navigate("album/$albumId")
                                    },
                                    onArtistClick = { simArtistName ->
                                        navController.navigate("artist/$simArtistName")
                                    },
                                    onShuffleClick = { playlist ->
                                        val shuffled = playlist.shuffled()
                                        if (shuffled.isNotEmpty()) {
                                            musicPlayerManager.setShuffleEnabled(true)
                                            musicPlayerManager.playSongFromList(shuffled.first(), shuffled)
                                        }
                                    }
                                )
                            }
                            composable("album/{albumId}") { backStackEntry ->
                                val albumId = backStackEntry.arguments?.getString("albumId") ?: return@composable
                                com.pulse.music.ui.album.AlbumScreen(
                                    albumId = albumId,
                                    onBack = { navController.popBackStack() },
                                    onSongClick = { song, playlist ->
                                        musicPlayerManager.playSongFromList(song, playlist)
                                    }
                                )
                            }
                            composable(
                                "library",
                                enterTransition = { androidx.compose.animation.EnterTransition.None },
                                exitTransition = { androidx.compose.animation.ExitTransition.None }
                            ) {
                                LibraryScreen(
                                    onNavigateToNowPlaying = {
                                        navController.navigate("now_playing")
                                    },
                                    onNavigateToPlaylist = { playlistId ->
                                        navController.navigate("playlist/$playlistId")
                                    },
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate("artist/$artistName")
                                    }
                                )
                            }
                            composable(
                                "profile",
                                enterTransition = { androidx.compose.animation.EnterTransition.None },
                                exitTransition = { androidx.compose.animation.ExitTransition.None }
                            ) {
                                com.pulse.music.ui.profile.ProfileScreen(
                                    onNavigateToAuth = {
                                        navController.navigate("auth_login") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(
                                "playlist/{playlistId}",
                                arguments = listOf(androidx.navigation.navArgument("playlistId") { type = androidx.navigation.NavType.IntType })
                            ) { backStackEntry ->
                                val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: return@composable
                                com.pulse.music.ui.playlist.PlaylistScreen(
                                    playlistId = playlistId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("now_playing") {
                                NowPlayingScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigateToArtist = { artistName -> navController.navigate("artist/$artistName") },
                                    onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                                    onNavigateToJam = { 
                                        android.util.Log.d("NAVIGATION", "Navigating to jam")
                                        navController.navigate("jam") 
                                    }
                                )
                            }
                            composable(
                                "jam?roomId={roomId}",
                                arguments = listOf(androidx.navigation.navArgument("roomId") {
                                    type = androidx.navigation.NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }),
                                deepLinks = listOf(
                                    androidx.navigation.navDeepLink { uriPattern = "pulse://jam/{roomId}" },
                                    androidx.navigation.navDeepLink { uriPattern = "https://pulsemusic.app/jam/{roomId}" }
                                )
                            ) { backStackEntry ->
                                val roomId = backStackEntry.arguments?.getString("roomId")
                                JamScreen(
                                    roomId = roomId,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                        // Floating UI Overlay (MiniPlayer + BottomNav)
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            if (showMiniPlayer && currentSong != null) {
                                val queue by musicPlayerManager.queue.collectAsState()
                                val currentIndex by musicPlayerManager.currentIndex.collectAsState()
                                val isShuffleEnabled by musicPlayerManager.shuffleEnabled.collectAsState()
                                MiniPlayer(
                                    queue = queue,
                                    currentIndex = currentIndex,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    isShuffleEnabled = isShuffleEnabled,
                                    onTogglePlay = { musicPlayerManager.togglePlayPause() },
                                    onToggleShuffle = { musicPlayerManager.toggleShuffle() },
                                    onSkipNext = { musicPlayerManager.skipNext() },
                                    onSkipPrev = { musicPlayerManager.skipToPreviousTrack() },
                                    onClick = { navController.navigate("now_playing") }
                                )
                            }

                            if (showBottomNav) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Transparent)
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                            }
                                        }
                                ) {

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                                            .background(Color.Transparent)
                                            .padding(bottom = 12.dp, top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val items = listOf(
                                            Triple("Home", Icons.Default.Home, "home"),
                                            Triple("Search", Icons.Default.Search, "search"),
                                            Triple("Your Library", Icons.Default.LibraryMusic, "library"),
                                            Triple("Profile", Icons.Default.Person, "profile")
                                        )
                                        items.forEach { (label, icon, route) ->
                                            val selected = currentRoute == route
                                            val color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                                            val activeColor = if (selected) PulseRed else color
                                            val scale by animateFloatAsState(
                                                targetValue = if (selected) 1.05f else 1f,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                label = "nav_scale"
                                            )
                                            
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        if (selected && route == "search") {
                                                            scope.launch {
                                                                try { searchFocusRequester.requestFocus() } catch (e: Exception) {}
                                                            }
                                                        } else {
                                                            navController.navigate(route) {
                                                                popUpTo(navController.graph.startDestinationId) { saveState = false }
                                                                launchSingleTop = true
                                                                restoreState = false
                                                            }
                                                        }
                                                    }
                                                    .scale(scale),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                if (route == "profile" && updateAvailable != null) {
                                                    BadgedBox(badge = { Badge(containerColor = PulseRed) }) {
                                                        Icon(icon, contentDescription = "$label, update available", tint = activeColor, modifier = Modifier.size(26.dp))
                                                    }
                                                } else {
                                                    Icon(icon, contentDescription = label, tint = activeColor, modifier = Modifier.size(26.dp))
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(label, color = activeColor, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
    }

    // Set when the user taps the update notification
    private val openProfileRequest = mutableStateOf(false)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_profile", false)) openProfileRequest.value = true
    }

    override fun onResume() {
        super.onResume()
        // quiet update check (at most every 30 minutes) when the app opens or comes back
        lifecycleScope.launch {
            com.pulse.music.update.UpdateManager.checkIfStale(com.pulse.music.BuildConfig.VERSION_NAME)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            musicPlayerManager.release()
        }
    }
}

@Composable
fun MiniPlayer(
    queue: List<com.pulse.music.domain.Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isShuffleEnabled: Boolean = false,
    onTogglePlay: () -> Unit,
    onToggleShuffle: (() -> Unit)? = null,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onClick: () -> Unit
) {
    val currentSong = queue.getOrNull(currentIndex) ?: return
    val nextSong = queue.getOrNull(currentIndex + 1)
    val prevSong = queue.getOrNull(currentIndex - 1)

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var hasHapticFired by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val threshold = widthPx * 0.15f

        // Background tracks (Preview)
        if (offsetX.value < 0f && nextSong != null) {
            val alpha = (kotlin.math.abs(offsetX.value) / widthPx).coerceIn(0f, 1f)
            MiniPlayerCard(
                song = nextSong,
                isPlaying = false,
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha * 0.8f
                        scaleX = 0.9f + (alpha * 0.1f)
                        scaleY = 0.9f + (alpha * 0.1f)
                    }
            )
        } else if (offsetX.value > 0f && prevSong != null) {
            val alpha = (kotlin.math.abs(offsetX.value) / widthPx).coerceIn(0f, 1f)
            MiniPlayerCard(
                song = prevSong,
                isPlaying = false,
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha * 0.8f
                        scaleX = 0.9f + (alpha * 0.1f)
                        scaleY = 0.9f + (alpha * 0.1f)
                    }
            )
        }

        // Foreground track
        MiniPlayerCard(
            song = currentSong,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isShuffleEnabled = isShuffleEnabled,
            onTogglePlay = onTogglePlay,
            onToggleShuffle = onToggleShuffle,
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX.value
                    if (kotlin.math.abs(offsetX.value) > 0) {
                        val scale = 1f - (kotlin.math.abs(offsetX.value) / widthPx) * 0.05f
                        scaleX = scale
                        scaleY = scale
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val dx = offsetX.value
                                if (dx < -threshold && nextSong != null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    offsetX.animateTo(-widthPx, tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                    onSkipNext()
                                    offsetX.snapTo(0f) // Instantly snap to 0, seamless with the preview!
                                } else if (dx > threshold && prevSong != null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    offsetX.animateTo(widthPx, tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                    onSkipPrev()
                                    offsetX.snapTo(0f) // Instantly snap to 0, seamless with the preview!
                                } else {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                                }
                                hasHapticFired = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newVal = offsetX.value + dragAmount
                                if (newVal < 0 && nextSong == null) {
                                    offsetX.snapTo((offsetX.value + dragAmount * 0.2f).coerceAtMost(0f))
                                } else if (newVal > 0 && prevSong == null) {
                                    offsetX.snapTo((offsetX.value + dragAmount * 0.2f).coerceAtLeast(0f))
                                } else {
                                    offsetX.snapTo(newVal)
                                }

                                if (kotlin.math.abs(offsetX.value) > threshold && !hasHapticFired) {
                                    hasHapticFired = true
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else if (kotlin.math.abs(offsetX.value) <= threshold && hasHapticFired) {
                                    hasHapticFired = false
                                }
                            }
                        }
                    )
                }
                .clickable { onClick() }
        )
    }
}

@Composable
fun MiniPlayerCard(
    song: com.pulse.music.domain.Song,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isShuffleEnabled: Boolean = false,
    onTogglePlay: (() -> Unit)? = null,
    onToggleShuffle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp)),
        color = Color(0xFF242424),
        shadowElevation = 8.dp,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF3E3E3E)),
                contentAlignment = Alignment.Center
            ) {
                if (!song.albumArt.isNullOrEmpty()) {
                    coil.compose.AsyncImage(
                        model = song.albumArt,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = "Music", tint = Color.Gray, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = song.artist,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onTogglePlay != null) {
                if (onToggleShuffle != null) {
                    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Shuffle, 
                            contentDescription = "Shuffle", 
                            tint = if (isShuffleEnabled) Color.White else Color.White.copy(alpha = 0.4f), 
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(48.dp)) {
                    if (isBuffering) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}


