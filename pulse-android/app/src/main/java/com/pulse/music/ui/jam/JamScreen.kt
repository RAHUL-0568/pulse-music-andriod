package com.pulse.music.ui.jam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import java.util.UUID

@Composable
fun JamScreen(
    viewModel: JamViewModel = hiltViewModel(),
    roomId: String? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val isConnected by viewModel.isConnected.collectAsState()
    val currentRoomId by viewModel.currentRoomId.collectAsState()
    val isPendingApproval by viewModel.isPendingApproval.collectAsState()
    val joinRequests by viewModel.joinRequests.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()

    LaunchedEffect(roomId) {
        viewModel.fetchMyJams()
        if (roomId != null && !isConnected) {
            viewModel.connectToJamSession(roomId)
        }
    }

    // Re-fetch jams whenever user returns to the lobby (disconnects from a session)
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            viewModel.fetchMyJams()
        }
    }

    val myJams by viewModel.myJams.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Queue", "Chat", "Participants")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .navigationBarsPadding()
    ) {
        if (!isConnected) {
            var joinRoomId by remember { mutableStateOf("") }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Jam Session",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Group Session", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Listen with friends perfectly in sync.", fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    var jamName by remember { mutableStateOf("") }
                    Text("Create a New Jam", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = jamName,
                        onValueChange = { jamName = it },
                        label = { Text("Jam Name", color = Color.Gray) },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (jamName.isNotBlank()) {
                                viewModel.createJam(jamName)
                                jamName = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                    ) {
                        Text("Create Jam", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text("OR Join with Code", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    androidx.compose.material3.OutlinedTextField(
                        value = joinRoomId,
                        onValueChange = { joinRoomId = it.uppercase() },
                        label = { Text("Enter Room Code", color = Color.Gray) },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            if (joinRoomId.isNotBlank()) {
                                viewModel.connectToJamSession(joinRoomId, isCreating = false)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Join Jam", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                if (myJams.isNotEmpty()) {
                    item {
                        Text("My Jams", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    items(myJams) { jam ->
                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.connectToJamSession(jam.roomCode) },
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.DarkGray)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(jam.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Code: ${jam.roomCode} • ${jam.memberCount} members", color = Color.LightGray, fontSize = 14.sp)
                                }
                                IconButton(
                                    onClick = {
                                        if (jam.hostId == currentUserId) {
                                            viewModel.deleteJam(jam.jamId)
                                        } else {
                                            viewModel.leaveJam(jam.jamId)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (isPendingApproval) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Waiting for host to approve...", color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.disconnect() }) {
                        Text("Cancel")
                    }
                }
            }
        } else {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Room: $currentRoomId", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Join my jam! pulse://jam/$currentRoomId")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Jam"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Invite")
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = Color(0xFF1DB954)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, color = if (selectedTabIndex == index) Color.White else Color.Gray) }
                    )
                }
            }

            // Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTabIndex) {
                    0 -> JamQueueTab(viewModel)
                    1 -> JamChatTab(viewModel)
                    2 -> JamParticipantsTab(viewModel)
                }
            }
            
            // Footer
            Button(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
            ) {
                Text("Leave Session", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

@Composable
fun JamQueueTab(viewModel: JamViewModel) {
    val queue by viewModel.queue.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        
        if (queue.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Queue is empty. Add some songs!", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(queue) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.playJamSong(item)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(item.optString("title", "Unknown Title"), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(item.optString("added_by", "Unknown"), color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JamChatTab(viewModel: JamViewModel) {
    val messages by viewModel.chatMessages.collectAsState()
    var messageText by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
            items(messages) { msg ->
                val text = msg.optString("text", "")
                val sender = msg.optString("sender_id", "Unknown")
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(sender, fontSize = 12.sp, color = Color.Gray)
                    Text(text, color = Color.White, fontSize = 16.sp)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Say something...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1DB954),
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { 
                    if (messageText.isNotBlank()) {
                        viewModel.sendChatMessage(messageText)
                        messageText = "" 
                    }
                },
                modifier = Modifier.background(Color(0xFF1DB954), shape = MaterialTheme.shapes.small)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
fun JamParticipantsTab(viewModel: JamViewModel) {
    val participants by viewModel.participants.collectAsState()
    val joinRequests by viewModel.joinRequests.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    
    val myInfo = participants.firstOrNull { it.optString("user_id") == currentUserId }
    val isHost = myInfo?.optString("role") == "HOST"

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isHost && joinRequests.isNotEmpty()) {
            item {
                Text("Pending Requests", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(joinRequests) { reqUserId ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color.Gray, shape = MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(reqUserId, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    
                    IconButton(onClick = { viewModel.approveJoin(reqUserId) }) {
                        Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color.Green)
                    }
                    IconButton(onClick = { viewModel.rejectJoin(reqUserId) }) {
                        Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color.Red)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Divider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            Text("In Room", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(participants) { part ->
            val userId = part.optString("user_id", "Unknown")
            val role = part.optString("role", "PARTICIPANT")
            val online = part.optBoolean("online", false)
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(if(online) Color(0xFF1DB954) else Color.Gray, shape = MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(userId, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(role, color = Color.Gray, fontSize = 12.sp)
                }
                
                if (isHost && userId != currentUserId) {
                    IconButton(onClick = { viewModel.removeParticipant(userId) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red)
                    }
                }
            }
        }
    }
}
