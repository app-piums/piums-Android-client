package com.piums.cliente.ui.screens.inbox

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import com.piums.cliente.ui.components.PiumsSegmentedPicker
import com.piums.cliente.ui.components.SegmentedTab
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.ChatSocketManager
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val api: PiumsApiService,
    val tokenStorage: TokenStorage,
    val socketManager: ChatSocketManager
) : ViewModel() {

    var conversations by mutableStateOf<List<ConversationDto>>(emptyList())
        private set
    var isLoadingConversations by mutableStateOf(true)
        private set

    var activeConversation by mutableStateOf<ConversationDto?>(null)
        private set
    var messages by mutableStateOf<List<ChatMessageDto>>(emptyList())
        private set
    var isLoadingMessages by mutableStateOf(false)
        private set
    var messageInput by mutableStateOf("")
        private set
    var isSending by mutableStateOf(false)
        private set

    var disputes by mutableStateOf<List<DisputeDto>>(emptyList())
        private set
    var isLoadingDisputes by mutableStateOf(true)
        private set
    var showCreateDispute by mutableStateOf(false)
        private set

    init {
        refresh()
        socketManager.connect()
        viewModelScope.launch {
            socketManager.incomingMessages.collect { incoming ->
                val convId = activeConversation?.id ?: return@collect
                if (incoming.conversationId == convId &&
                    messages.none { it.id == incoming.id }) {
                    messages = messages + incoming
                }
            }
        }
        // Re-fetch messages after reconnect to recover any missed during disconnect
        viewModelScope.launch {
            socketManager.reconnected.collect {
                val convId = activeConversation?.id ?: return@collect
                runCatching { api.getMessages(convId, limit = 50) }
                    .getOrNull()?.list?.let { fresh ->
                        val existing = messages.map { it.id }.toSet()
                        val new = fresh.filter { it.id !in existing }
                        if (new.isNotEmpty()) messages = messages + new
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeConversation?.let { socketManager.leaveConversation(it.id) }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoadingConversations = true
            isLoadingDisputes = true
            try {
                conversations = (runCatching { api.getConversations(limit = 50) }
                    .onFailure { Log.e("PiumsAPI", "getConversations error", it) }
                    .getOrNull()?.list ?: emptyList())
                    .filter { (it.status ?: "").uppercase() !in setOf("CLOSED", "CANCELLED") }
                disputes = runCatching { api.getDisputes() }
                    .onFailure { Log.e("PiumsAPI", "getDisputes error", it) }
                    .getOrNull()?.all ?: emptyList()
            } finally {
                isLoadingConversations = false
                isLoadingDisputes = false
            }
        }
    }

    fun openConversation(conv: ConversationDto) {
        activeConversation = conv
        socketManager.joinConversation(conv.id)
        viewModelScope.launch {
            isLoadingMessages = true
            messages = runCatching { api.getMessages(conv.id, limit = 50) }
                .getOrNull()?.list ?: emptyList()
            isLoadingMessages = false
            runCatching { api.markConversationRead(conv.id) }
            socketManager.markRead(conv.id)
        }
    }

    fun closeConversation() {
        activeConversation?.let { socketManager.leaveConversation(it.id) }
        activeConversation = null
        messages = emptyList()
        messageInput = ""
    }

    fun onInputChange(text: String) { messageInput = text }

    fun sendMessage() {
        val conv = activeConversation ?: return
        val text = messageInput.trim()
        if (text.isBlank()) return
        messageInput = ""
        viewModelScope.launch {
            isSending = true
            val sent = runCatching {
                api.sendMessage(SendMessageRequest(conversationId = conv.id, content = text))
            }.getOrNull()
            if (sent != null && messages.none { it.id == sent.id }) {
                messages = messages + sent
            }
            isSending = false
        }
    }

    fun showCreateDisputeDialog(show: Boolean) { showCreateDispute = show }

    fun openConversationById(id: String) {
        val existing = conversations.firstOrNull { it.id == id }
        if (existing != null) {
            openConversation(existing)
            return
        }
        viewModelScope.launch {
            runCatching { api.getConversation(id) }
                .getOrNull()?.let { openConversation(it) }
        }
    }

    fun createDispute(bookingId: String, subject: String, description: String) {
        viewModelScope.launch {
            runCatching {
                val d = api.createDispute(CreateDisputeRequest(
                    bookingId    = bookingId,
                    disputeType  = "SERVICE_ISSUE",
                    subject      = subject,
                    description  = description
                ))
                disputes = listOf(d) + disputes
            }
            showCreateDispute = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onUnreadChanged: () -> Unit = {},
    onDisputeClick: (String) -> Unit = {},
    vm: InboxViewModel = hiltViewModel()
) {
    val isSocketConnected by vm.socketManager.isConnected.collectAsState()

    // Consume pending deep-link conversation, both on appear and while screen is active
    LaunchedEffect(Unit) {
        vm.socketManager.pendingDeepLinkConversationId.collect { id ->
            if (id != null) {
                vm.socketManager.clearPendingConversationId()
                vm.openConversationById(id)
            }
        }
    }

    val active = vm.activeConversation
    if (active != null) {
        ChatScreen(
            conversation    = active,
            messages        = vm.messages,
            isLoading       = vm.isLoadingMessages,
            isSending       = vm.isSending,
            myUserId        = vm.tokenStorage.userId ?: "",
            input           = vm.messageInput,
            isSocketOnline  = isSocketConnected,
            onInputChange   = vm::onInputChange,
            onSend          = vm::sendMessage,
            onBack          = {
                vm.closeConversation()
                onUnreadChanged()
            }
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(vm.isLoadingConversations) { if (!vm.isLoadingConversations) isRefreshing = false }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = { isRefreshing = true; vm.refresh() },
        modifier     = Modifier.fillMaxSize()
    ) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Mensajes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        )

        PiumsSegmentedPicker(
            tabs = listOf(
                SegmentedTab("Conversaciones", Icons.Default.Message),
                SegmentedTab("Quejas",         Icons.Default.ReportProblem)
            ),
            selectedIndex = pagerState.currentPage,
            onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> ConversationsTab(
                    conversations = vm.conversations,
                    isLoading     = vm.isLoadingConversations,
                    myUserId      = vm.tokenStorage.userId ?: "",
                    onOpen        = vm::openConversation
                )
                1 -> DisputesTab(
                    disputes      = vm.disputes,
                    isLoading     = vm.isLoadingDisputes,
                    onCreate      = { vm.showCreateDisputeDialog(true) },
                    onDisputeClick = onDisputeClick
                )
            }
        }
    }
    } // end PullToRefreshBox

    if (vm.showCreateDispute) {
        CreateDisputeDialog(
            onDismiss = { vm.showCreateDisputeDialog(false) },
            onConfirm = { bookingId, subject, desc ->
                vm.createDispute(bookingId, subject, desc)
            }
        )
    }
}

// ─── Conversations Tab ────────────────────────────────────────────────────────

@Composable
private fun ConversationsTab(
    conversations: List<ConversationDto>,
    isLoading: Boolean,
    myUserId: String,
    onOpen: (ConversationDto) -> Unit
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PiumsOrange)
        }
        conversations.isEmpty() -> InboxEmpty(
            icon = Icons.Default.ChatBubbleOutline,
            message = "Sin conversaciones aún",
            sub = "Cuando reserves un artista, el chat estará aquí"
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(conversations, key = { it.id }) { conv ->
                ConversationItem(conv = conv, onClick = { onOpen(conv) })
            }
        }
    }
}

@Composable
private fun ConversationItem(conv: ConversationDto, onClick: () -> Unit) {
    val safeStatus = (conv.status ?: "").uppercase()
    val statusColor = when (safeStatus) {
        "ACTIVE"  -> PiumsOrange
        "PENDING" -> Color(0xFF3B82F6)
        else      -> MaterialTheme.colorScheme.onSurface.copy(0.4f)
    }
    val statusLabel = when (safeStatus) {
        "ACTIVE"  -> "Activo"
        "PENDING" -> "Pendiente"
        "CLOSED"  -> "Cerrado"
        else      -> safeStatus.lowercase().replaceFirstChar { it.uppercase() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.55f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar — foto real o ícono persona
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(statusColor.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (conv.artistAvatar != null) {
                AsyncImage(
                    model = conv.artistAvatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Default.Person, null,
                    tint = statusColor, modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    conv.artistName ?: "Artista",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                conv.lastMessageAt?.let {
                    Text(relativeDate(it), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
            }

            val lastMsg = conv.lastMessageContent ?: conv.messages?.lastOrNull()?.content
            if (lastMsg != null) {
                Text(lastMsg, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                // Pill de estado cuando no hay mensaje
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(statusColor.copy(0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall,
                        color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Unread count — cápsula naranja
        if (conv.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(PiumsOrange)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("${conv.unreadCount}", style = MaterialTheme.typography.labelSmall,
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

// ─── Chat Screen ──────────────────────────────────────────────────────────────

@Composable
internal fun ChatScreen(
    conversation: ConversationDto,
    messages: List<ChatMessageDto>,
    isLoading: Boolean,
    isSending: Boolean,
    myUserId: String,
    input: String,
    isSocketOnline: Boolean = false,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(PiumsOrange.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(conversation.artistName?.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold, color = PiumsOrange)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    conversation.artistName ?: "Artista",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isSocketOnline) {
                    Text("En línea", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50))
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        if (isLoading) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
        } else if (messages.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Envía el primer mensaje",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.4f))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg, isOwn = msg.senderId == myUserId)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe un mensaje...",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = PiumsOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.2f)
                )
            )
            FilledIconButton(
                onClick  = onSend,
                enabled  = input.trim().isNotBlank() && !isSending,
                modifier = Modifier.size(48.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor = PiumsOrange,
                    contentColor   = Color.White
                )
            ) {
                if (isSending) CircularProgressIndicator(color = Color.White,
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Send, null)
            }
        }
    }
}

@Composable
internal fun MessageBubble(message: ChatMessageDto, isOwn: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(
                    topStart    = 16.dp, topEnd      = 16.dp,
                    bottomStart = if (isOwn) 16.dp else 4.dp,
                    bottomEnd   = if (isOwn) 4.dp  else 16.dp
                ))
                .background(if (isOwn) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    formatMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOwn) Color.White.copy(0.65f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ─── Disputes Tab ─────────────────────────────────────────────────────────────

@Composable
private fun DisputesTab(
    disputes: List<DisputeDto>,
    isLoading: Boolean,
    onCreate: () -> Unit,
    onDisputeClick: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
            disputes.isEmpty() -> InboxEmpty(
                icon = Icons.Default.Gavel,
                message = "Sin quejas activas",
                sub = "Si tienes algún problema con una reserva, puedes reportarlo aquí"
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(disputes, key = { it.id }) { dispute ->
                    DisputeCard(dispute = dispute, onClick = { onDisputeClick(dispute.id) })
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).navigationBarsPadding(),
            containerColor = PiumsOrange,
            contentColor   = Color.White
        ) {
            Icon(Icons.Default.Add, null)
        }
    }
}

@Composable
internal fun DisputeCard(dispute: DisputeDto, onClick: (() -> Unit)? = null) {
    val status = dispute.statusEnum
    val statusColor = when (status) {
        DisputeStatus.OPEN     -> MaterialTheme.colorScheme.error
        DisputeStatus.RESOLVED -> Color(0xFF4CAF50)
        DisputeStatus.CLOSED   -> MaterialTheme.colorScheme.onSurface.copy(0.4f)
        else                   -> PiumsOrange
    }
    Card(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(dispute.subject, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(dispute.createdAt.take(10), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(status.displayName, style = MaterialTheme.typography.labelSmall,
                            color = statusColor, fontWeight = FontWeight.SemiBold)
                    }
                    if (onClick != null) {
                        Icon(Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(dispute.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)

            if (dispute.messages?.isNotEmpty() == true) {
                Text(
                    "${dispute.messages.size} mensaje${if (dispute.messages.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PiumsOrange
                )
            }
        }
    }
}

// ─── Create Dispute Dialog ────────────────────────────────────────────────────

@Composable
private fun CreateDisputeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var bookingId   by remember { mutableStateOf("") }
    var subject     by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar queja", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = bookingId, onValueChange = { bookingId = it },
                    label = { Text("ID de reserva *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )
                OutlinedTextField(
                    value = subject, onValueChange = { subject = it },
                    label = { Text("Asunto *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descripción *") },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (bookingId.isNotBlank() && subject.isNotBlank() && description.isNotBlank())
                        onConfirm(bookingId, subject, description)
                },
                enabled = bookingId.isNotBlank() && subject.isNotBlank() && description.isNotBlank()
            ) {
                Text("Enviar", color = PiumsOrange, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun InboxEmpty(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, sub: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(0.3f),
            modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.45f),
            textAlign = TextAlign.Center, lineHeight = 18.sp)
    }
}

private fun formatMessageTime(isoStr: String): String {
    return try {
        val instant = Instant.parse(isoStr)
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (e: Exception) {
        isoStr.take(10)
    }
}

private fun relativeDate(isoStr: String): String {
    val instant = runCatching {
        // Try with fractional seconds first, then without
        Instant.parse(isoStr)
    }.getOrElse {
        runCatching {
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
            fmt.parse(isoStr, Instant::from)
        }.getOrNull()
    } ?: return isoStr.take(10)

    val now = Instant.now()
    val diffSeconds = now.epochSecond - instant.epochSecond
    return when {
        diffSeconds < 60       -> "ahora"
        diffSeconds < 3600     -> "${diffSeconds / 60}m"
        diffSeconds < 86400    -> "${diffSeconds / 3600}h"
        diffSeconds < 604800   -> "${diffSeconds / 86400}d"
        else -> DateTimeFormatter
            .ofPattern("d MMM", Locale("es", "ES"))
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}
