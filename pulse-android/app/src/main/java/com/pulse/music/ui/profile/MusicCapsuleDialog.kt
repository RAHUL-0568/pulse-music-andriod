package com.pulse.music.ui.profile

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pulse.music.data.network.MusicCapsuleResponse

@Composable
fun MusicCapsuleDialog(
    capsule: MusicCapsuleResponse,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowColor1 by infiniteTransition.animateColor(
        initialValue = Color(0xFFFF4081),
        targetValue = Color(0xFF7C4DFF),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val glowColor2 by infiniteTransition.animateColor(
        initialValue = Color(0xFF7C4DFF),
        targetValue = Color(0xFF00E5FF),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF121212)) // Solid dark background underneath
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(glowColor1.copy(alpha = 0.4f), glowColor2.copy(alpha = 0.2f), Color.Transparent)
                )
            ),
        containerColor = Color.Transparent,
        textContentColor = Color.White,
        titleContentColor = Color.White,
        title = {
            Text(
                text = "Your Music Capsule",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Artist
                if (capsule.topArtists.isNotEmpty()) {
                    val topArtist = capsule.topArtists.first()
                    Text(
                        text = "Top Artist",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = topArtist.name,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = glowColor1,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${capsule.totalPlays}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Total Plays", fontSize = 12.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${capsule.uniqueSongs}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Unique Songs", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Top Songs List
                Text(
                    text = "Your Top Tracks",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 12.dp)
                )

                capsule.topSongs.forEachIndexed { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.width(24.dp)
                        )
                        AsyncImage(
                            model = song.albumArt,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = song.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White, maxLines = 1)
                            Text(text = song.artist, fontSize = 14.sp, color = Color.Gray, maxLines = 1)
                        }
                        Text(text = "${song.playCount} plays", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = glowColor1),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Close Capsule", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}
