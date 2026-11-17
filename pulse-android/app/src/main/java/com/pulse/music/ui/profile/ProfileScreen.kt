package com.pulse.music.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulse.music.ui.auth.AuthViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
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

    val capsuleState by viewModel.capsuleState.collectAsState()
    var showCapsule by remember { mutableStateOf(false) }

    val isLastDayOfMonth = remember {
        val calendar = java.util.Calendar.getInstance()
        val today = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val lastDay = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        today == lastDay
    }
    
    // Image picker launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadProfilePic(context, it.toString()) }
    }
    
    // If user somehow logs out, go to auth
    LaunchedEffect(token) {
        if (token.isNullOrEmpty()) {
            onNavigateToAuth()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { 
                ProfileHero(
                    username = username ?: "User", 
                    email = email ?: "user@example.com",
                    profilePicUri = profilePicUri,
                    onEditProfilePic = { launcher.launch("image/*") }
                ) 
            }
            if (isLastDayOfMonth) {
                item {
                    Button(
                        onClick = {
                            viewModel.fetchMusicCapsule()
                            showCapsule = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFFF4081), Color(0xFF7C4DFF))
                                )
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("View Your Music Capsule", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            item { 
                PlaybackSettings(
                    highQuality = highQuality,
                    spatialAudio = spatialAudio,
                    gapless = gapless,
                    crossfade = crossfade,
                    onUpdate = { hq, sa, gp, cf -> 
                        viewModel.updatePlaybackSettings(hq, sa, gp, cf)
                    }
                ) 
            }
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
            item {
                AppUpdates(context = context)
            }
            item {
                AccountActions(onLogout = { viewModel.logout() })
            }
        }
        if (showCapsule && capsuleState != null) {
            MusicCapsuleDialog(
                capsule = capsuleState!!,
                onDismiss = { showCapsule = false }
            )
        }
    }
}

@Composable
private fun ProfileHero(username: String, email: String, profilePicUri: String?, onEditProfilePic: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .clickable { onEditProfilePic() },
                contentAlignment = Alignment.Center
            ) {
                if (profilePicUri.isNullOrEmpty()) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(50.dp))
                } else {
                    android.util.Log.d("ProfileScreen", "Loading profile pic from: $profilePicUri")
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(profilePicUri)
                            .crossfade(true)
                            .listener(
                                onError = { _, result ->
                                    android.util.Log.e("ProfileScreen", "Failed to load profile pic: ${result.throwable.message}", result.throwable)
                                },
                                onSuccess = { _, _ ->
                                    android.util.Log.d("ProfileScreen", "Profile pic loaded successfully")
                                }
                            )
                            .build(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Edit icon overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Profile Picture", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(username, color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(email, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PlaybackSettings(
    highQuality: Boolean,
    spatialAudio: Boolean,
    gapless: Boolean,
    crossfade: Int,
    onUpdate: (Boolean, Boolean, Boolean, Int) -> Unit
) {
    Column {
        Text("Playback", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        SettingsCard {
            SettingRow(
                icon = Icons.Default.HighQuality, 
                title = "High Quality Streaming", 
                description = "Always stream at 320kbps",
                trailing = { Switch(checked = highQuality, onCheckedChange = { onUpdate(it, spatialAudio, gapless, crossfade) }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF92839), checkedTrackColor = Color(0xFFF92839).copy(alpha = 0.5f))) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            SettingRow(
                icon = Icons.Default.Headphones, 
                title = "Spatial Audio", 
                description = "Enable immersive soundstage",
                trailing = { Switch(checked = spatialAudio, onCheckedChange = { onUpdate(highQuality, it, gapless, crossfade) }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF92839), checkedTrackColor = Color(0xFFF92839).copy(alpha = 0.5f))) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            SettingRow(
                icon = Icons.Default.SkipNext, 
                title = "Gapless Playback", 
                description = "Eliminate pauses between tracks",
                trailing = { Switch(checked = gapless, onCheckedChange = { onUpdate(highQuality, spatialAudio, it, crossfade) }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF92839), checkedTrackColor = Color(0xFFF92839).copy(alpha = 0.5f))) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Crossfade", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("${crossfade}s", color = Color(0xFFF92839), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = crossfade.toFloat(),
                    onValueChange = { onUpdate(highQuality, spatialAudio, gapless, it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFF92839), activeTrackColor = Color(0xFFF92839))
                )
            }
        }
    }
}

@Composable
private fun PremiumCard() {
    val premiumBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.2f), Color(0xFFEF4444).copy(alpha = 0.2f))
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(premiumBrush)
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ListenFree Premium", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Enjoy ad-free music listening, offline playback, and highest audio quality.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = MaterialTheme.colorScheme.background),
                shape = RoundedCornerShape(50)
            ) {
                Text("Manage Subscription", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp), content = content)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFF92839), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(description, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailing()
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
private fun AppUpdates(context: android.content.Context) {
    val coroutineScope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf<com.pulse.music.update.UpdateManager.UpdateInfo?>(null) }
    
    val downloadProgress by com.pulse.music.update.UpdateManager.downloadProgress.collectAsState()
    val downloadedBytes by com.pulse.music.update.UpdateManager.downloadedBytes.collectAsState()
    val downloadStatusText by com.pulse.music.update.UpdateManager.downloadStatusText.collectAsState()
    val isDownloading by com.pulse.music.update.UpdateManager.isDownloading.collectAsState()
    
    var showUpToDate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isDownloading) {
            val info = com.pulse.music.update.UpdateManager.checkForUpdate(com.pulse.music.BuildConfig.VERSION_NAME)
            if (info != null && info.hasUpdate) {
                updateAvailable = info
            }
        }
    }

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text("App", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isChecking && !isDownloading) {
                            if (showUpToDate) return@clickable
                            isChecking = true
                            coroutineScope.launch {
                                try {
                                    val updateInfo = updateAvailable ?: com.pulse.music.update.UpdateManager.checkForUpdate(com.pulse.music.BuildConfig.VERSION_NAME)
                                    if (updateInfo != null && updateInfo.hasUpdate) {
                                        updateAvailable = updateInfo
                                        com.pulse.music.update.UpdateManager.downloadAndInstallUpdate(context, updateInfo.downloadUrl, coroutineScope)
                                    } else {
                                        showUpToDate = true
                                        android.widget.Toast.makeText(context, "You are on the latest version!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Failed to check for updates", android.widget.Toast.LENGTH_SHORT).show()
                                } finally {
                                    isChecking = false
                                }
                            }
                        }
                    }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color.LightGray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val titleText = when {
                            isDownloading -> downloadStatusText
                            isChecking -> "Checking for updates..."
                            showUpToDate -> "You are up to date!"
                            updateAvailable != null -> "New update available (" + updateAvailable!!.newVersion + ")"
                            else -> "Check for Updates"
                        }
                        Text(titleText, color = if (updateAvailable != null && !showUpToDate && !isDownloading) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text("Current version: " + com.pulse.music.BuildConfig.VERSION_NAME, color = Color.Gray, fontSize = 12.sp)
                    }
                    if (!isDownloading && !showUpToDate && !isChecking) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
                
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (downloadProgress >= 0f) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFFF92839),
                            trackColor = Color.DarkGray
                        )
                        Text(
                            text = "${(downloadProgress * 100).toInt()}% downloaded",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                        )
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFFF92839),
                            trackColor = Color.DarkGray
                        )
                        val mb = String.format("%.1f", downloadedBytes / (1024f * 1024f))
                        Text(
                            text = "$mb MB downloaded...",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountActions(onLogout: () -> Unit) {
    Column {
        Text("Account", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        SettingsCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLogout() }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Log Out", color = Color(0xFFEF4444), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "Made with ❤️  by Rahul",
                color = Color.Gray.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StorageSettings(onClearCache: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cacheSizeMb by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun calculateCacheSize() {
        var sizeBytes = 0L
        try {
            context.cacheDir.walkTopDown().filter { it.isFile }.forEach { sizeBytes += it.length() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cacheSizeMb = sizeBytes / (1024f * 1024f)
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            calculateCacheSize()
        }
    }

    Column {
        Text("Storage", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        SettingsCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        onClearCache()
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            kotlinx.coroutines.delay(500)
                            calculateCacheSize()
                        }
                    }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Cache", tint = Color.Gray, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        val formattedSize = String.format("%.1f MB", cacheSizeMb)
                        Text("Clear Cache ($formattedSize)", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Free up space by removing cached audio and images", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}



