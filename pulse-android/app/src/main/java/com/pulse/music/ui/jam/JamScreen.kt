package com.pulse.music.ui.jam

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pulse.music.ui.auth.AuthTextField
import com.pulse.music.ui.auth.ErrorBanner
import com.pulse.music.ui.auth.PrimaryButton
import org.json.JSONObject

private val JamCard = Color(0xFF0F0F0F)
private val JamBorder = Color(0xFF1F1F1F)
private val JamMuted = Color(0xFF9A9A9A)
private val JamRed = Color(0xFFF92839)
private val JamBubbleOther = Color(0xFF1C1C1C)

private val avatarPalette = listOf(
    Color(0xFFF92839),
    Color(0xFFE8743B),
    Color(0xFF19A974),
    Color(0xFF2D8CFF),
    Color(0xFFE0A526),
    Color(0xFF1AA6B7)
)

private fun avatarColor(id: String): Color =
    avatarPalette[(id.hashCode() and 0x7fffffff) % avatarPalette.size]

@Composable
private fun InitialAvatar(id: String, size: Int = 40, online: Boolean? = null) {
    val initial = id.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(modifier = Modifier.size(size.dp)) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(avatarColor(id)),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.4f).sp)
        }
        if (online != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (online) Color(0xFF2ECC71) else Color(0xFF555555))
            )
        }
    }
}

@Composable
fun JamScreen(
    viewModel: JamViewModel = hiltViewModel(),
    roomId: String? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isConnected by viewModel.isConnected.collectAsState()
    val currentRoomId by viewModel.currentRoomId.collectAsState()
    val isPendingApproval by viewModel.isPendingApproval.collectAsState()
    val joinRequests by viewModel.joinRequests.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val participants by viewModel.participants.collectAsState()

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
    val tabs = listOf("Queue", "Chat", "People")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Brush.verticalGradient(listOf(JamRed.copy(alpha = 0.18f), Color.Transparent)))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .navigationBarsPadding()
        ) {
            if (!isConnected) {
                var joinRoomId by remember { mutableStateOf("") }
                var jamName by remember { mutableStateOf("") }
                var nameError by remember { mutableStateOf<String?>(null) }
                var codeError by remember { mutableStateOf<String?>(null) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(JamRed.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = JamRed, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Jam", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Listen together in real time. Start a Jam or join your friends with a code.",
                            fontSize = 15.sp,
                            color = JamMuted,
                            lineHeight = 21.sp
                        )
                        Spacer(Modifier.height(28.dp))

                        // Create
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(JamCard)
                                .border(1.dp, JamBorder, RoundedCornerShape(18.dp))
                                .padding(20.dp)
                        ) {
                            Text("Start a Jam", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Text("Give it a name and invite friends.", fontSize = 14.sp, color = JamMuted)
                            Spacer(Modifier.height(16.dp))
                            AuthTextField(
                                value = jamName,
                                onValueChange = { jamName = it; nameError = null },
                                label = "Jam name",
                                icon = Icons.Default.MusicNote,
                                imeAction = ImeAction.Done,
                                isError = nameError != null
                            )
                            ErrorBanner(nameError)
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(
                                text = "Create Jam",
                                onClick = {
                                    if (jamName.isBlank()) {
                                        nameError = "Enter a name for your Jam."
                                    } else {
                                        viewModel.createJam(jamName.trim())
                                        jamName = ""
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Join
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(JamCard)
                                .border(1.dp, JamBorder, RoundedCornerShape(18.dp))
                                .padding(20.dp)
                        ) {
                            Text("Join with a code", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Text("Ask the host for their room code.", fontSize = 14.sp, color = JamMuted)
                            Spacer(Modifier.height(16.dp))
                            AuthTextField(
                                value = joinRoomId,
                                onValueChange = { joinRoomId = it.uppercase(); codeError = null },
                                label = "Room code",
                                icon = Icons.Default.Group,
                                imeAction = ImeAction.Done,
                                isError = codeError != null
                            )
                            ErrorBanner(codeError)
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(
                                text = "Join Jam",
                                onClick = {
                                    if (joinRoomId.isBlank()) {
                                        codeError = "Enter the room code to join."
                                    } else {
                                        viewModel.connectToJamSession(joinRoomId.trim(), isCreating = false)
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(32.dp))
                        Text("My Jams", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                    }

                    if (myJams.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(JamCard)
                                    .border(1.dp, JamBorder, RoundedCornerShape(18.dp))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = JamMuted, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No Jams yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Create one above and it will show up here.",
                                    fontSize = 14.sp,
                                    color = JamMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(myJams) { jam ->
                            val isOwner = jam.hostId == currentUserId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(JamCard)
                                    .border(1.dp, JamBorder, RoundedCornerShape(18.dp))
                                    .clickable { viewModel.connectToJamSession(jam.roomCode) }
                                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(avatarColor(jam.name)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        jam.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "J",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        jam.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Code ${jam.roomCode}  -  ${jam.memberCount} ${if (jam.memberCount == 1) "member" else "members"}",
                                        color = JamMuted,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isOwner) {
                                            viewModel.deleteJam(jam.jamId)
                                        } else {
                                            viewModel.leaveJam(jam.jamId)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = if (isOwner) "Delete Jam" else "Leave Jam",
                                        tint = JamMuted
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (isPendingApproval) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = JamRed, strokeWidth = 3.dp)
                        Spacer(Modifier.height(24.dp))
                        Text("Waiting for approval", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The host needs to let you in. This usually takes a moment.",
                            fontSize = 15.sp,
                            color = JamMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(JamCard)
                                .border(1.dp, JamBorder, RoundedCornerShape(14.dp))
                                .clickable { viewModel.disconnect() }
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                val code = currentRoomId ?: ""
                val inviteLink = "pulse://jam/$code"

                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    // Header
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("In a Jam", fontSize = 13.sp, color = JamMuted)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Room code",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, JamBorder, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.disconnect() }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Leave", color = JamRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(JamCard)
                                .border(1.dp, JamBorder, RoundedCornerShape(18.dp))
                                .padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                code,
                                modifier = Modifier.weight(1f),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", tint = JamMuted)
                            }
                            IconButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Join my jam! $inviteLink")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Jam"))
                                },
                                modifier = Modifier.clip(CircleShape).background(JamRed)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Invite friends", tint = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = JamRed,
                        divider = { HorizontalDivider(color = JamBorder) }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val label = if (index == 2 && participants.isNotEmpty()) "$title (${participants.size})"
                            else if (index == 2) title
                            else title
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedTabIndex == index) Color.White else JamMuted
                                    )
                                }
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
                    Spacer(Modifier.height(88.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 14.sp, color = JamMuted, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

@Composable
fun JamQueueTab(viewModel: JamViewModel) {
    val queue by viewModel.queue.collectAsState()

    if (queue.isEmpty()) {
        EmptyHint(
            "The queue is empty",
            "Songs added to this Jam will show up here for everyone."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            items(queue) { item: JSONObject ->
                val art = item.optString("image", "").ifEmpty { item.optString("albumArt", "") }
                val artist = item.optString("artist", "").ifEmpty { "" }
                val addedBy = item.optString("added_by", "")
                val subtitle = when {
                    artist.isNotEmpty() -> artist
                    addedBy.isNotEmpty() -> "Added by $addedBy"
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.playJamSong(item) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(JamBubbleOther),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = JamMuted)
                        if (art.isNotEmpty()) {
                            AsyncImage(
                                model = art,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.optString("title", "Unknown Title"),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                subtitle,
                                color = JamMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
    val currentUserId by viewModel.currentUserId.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        if (messageText.isNotBlank()) {
            viewModel.sendChatMessage(messageText.trim())
            messageText = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            EmptyHint(
                "No messages yet",
                "Say hi to everyone in the Jam.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg: JSONObject ->
                    val text = msg.optString("text", "")
                    val sender = msg.optString("sender", "").ifEmpty { msg.optString("sender_id", "Unknown") }
                    val mine = sender == currentUserId
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                    ) {
                        if (!mine) {
                            Text(
                                sender,
                                fontSize = 12.sp,
                                color = JamMuted,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (mine) 18.dp else 4.dp,
                                        bottomEnd = if (mine) 4.dp else 18.dp
                                    )
                                )
                                .background(if (mine) JamRed else JamBubbleOther)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = JamBorder)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message", color = JamMuted) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = JamCard,
                    unfocusedContainerColor = JamCard,
                    focusedBorderColor = JamRed,
                    unfocusedBorderColor = JamBorder,
                    cursorColor = JamRed
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { send() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (messageText.isNotBlank()) JamRed else JamBubbleOther)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (isHost && joinRequests.isNotEmpty()) {
            item {
                Text("Join requests", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(12.dp))
            }
            items(joinRequests) { reqUserId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(JamCard)
                        .border(1.dp, JamBorder, RoundedCornerShape(18.dp))
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InitialAvatar(reqUserId)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        reqUserId,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { viewModel.approveJoin(reqUserId) }) {
                        Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color(0xFF2ECC71))
                    }
                    IconButton(onClick = { viewModel.rejectJoin(reqUserId) }) {
                        Icon(Icons.Default.Close, contentDescription = "Reject", tint = JamRed)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        item {
            Text("In this Jam", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
        }
        items(participants) { part: JSONObject ->
            val userId = part.optString("user_id", "Unknown")
            val role = part.optString("role", "PARTICIPANT")
            val online = part.optBoolean("online", false)
            val isMe = userId == currentUserId

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialAvatar(userId, size = 44, online = online)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isMe) "$userId (you)" else userId,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (role == "HOST") {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(JamRed.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Host", color = JamRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(if (online) "Online" else "Offline", color = JamMuted, fontSize = 13.sp)
                }

                if (isHost && !isMe) {
                    IconButton(onClick = { viewModel.removeParticipant(userId) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = JamMuted)
                    }
                }
            }
            HorizontalDivider(color = JamBorder)
        }
    }
}
