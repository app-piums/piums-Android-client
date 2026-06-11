package com.piums.cliente.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DisputeDetailViewModel @Inject constructor(
    private val api: PiumsApiService,
    val tokenStorage: TokenStorage,
    savedState: SavedStateHandle
) : ViewModel() {

    private val disputeId: String = checkNotNull(savedState["disputeId"])

    var dispute by mutableStateOf<DisputeDto?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var newMessage by mutableStateOf("")
        private set
    var isSending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            dispute = runCatching { api.getDispute(disputeId) }.getOrElse {
                error = "No se pudo cargar la queja"
                null
            }
            isLoading = false
        }
    }

    fun onMessageChange(text: String) { newMessage = text }

    fun sendMessage() {
        val text = newMessage.trim()
        if (text.isBlank()) return
        newMessage = ""
        viewModelScope.launch {
            isSending = true
            val result = runCatching {
                api.addDisputeMessage(disputeId, AddDisputeMessageRequest(text))
            }
            if (result.isSuccess) {
                // Reload to get updated messages list
                dispute = runCatching { api.getDispute(disputeId) }.getOrNull() ?: dispute
            } else {
                error = "Error al enviar el mensaje"
            }
            isSending = false
        }
    }

    val canSendMessage: Boolean
        get() = when (dispute?.statusEnum) {
            DisputeStatus.OPEN,
            DisputeStatus.IN_REVIEW,
            DisputeStatus.AWAITING_INFO,
            DisputeStatus.ESCALATED -> true
            else -> false
        }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun DisputeDetailScreen(
    onBack: () -> Unit,
    vm: DisputeDetailViewModel = hiltViewModel()
) {
    val dispute = vm.dispute
    val listState = rememberLazyListState()
    val messages = dispute?.messages ?: emptyList()

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Queja",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        when {
            vm.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
            dispute == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(0.6f),
                        modifier = Modifier.size(48.dp))
                    Text(vm.error ?: "Error al cargar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                    TextButton(onClick = vm::load) { Text("Reintentar", color = PiumsOrange) }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header card
                    item { HeaderCard(dispute) }

                    // Resolution card
                    dispute.resolution?.let { res ->
                        item { ResolutionCard(res, dispute.refundAmount) }
                    }

                    // Description
                    item {
                        SectionCard(title = "Descripción", icon = Icons.Default.Description) {
                            Text(
                                dispute.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                            )
                        }
                    }

                    // Messages
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Forum, null,
                                tint = PiumsOrange, modifier = Modifier.size(18.dp))
                            Text("Mensajes", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (messages.isEmpty()) {
                        item {
                            Text(
                                "Aún no hay mensajes en esta queja.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            DisputeMessageBubble(
                                message  = msg,
                                isOwn    = msg.senderId == vm.tokenStorage.userId
                            )
                        }
                    }

                    vm.error?.let { err ->
                        item {
                            Text(err, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Message input
                if (vm.canSendMessage) {
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
                            value = vm.newMessage,
                            onValueChange = vm::onMessageChange,
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
                            onClick  = vm::sendMessage,
                            enabled  = vm.newMessage.trim().isNotBlank() && !vm.isSending,
                            modifier = Modifier.size(48.dp),
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = PiumsOrange,
                                contentColor   = Color.White
                            )
                        ) {
                            if (vm.isSending) CircularProgressIndicator(color = Color.White,
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Send, null)
                        }
                    }
                }
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun HeaderCard(dispute: DisputeDto) {
    val status = dispute.statusEnum
    val statusColor = when (status) {
        DisputeStatus.OPEN         -> MaterialTheme.colorScheme.error
        DisputeStatus.RESOLVED     -> Color(0xFF4CAF50)
        DisputeStatus.CLOSED       -> MaterialTheme.colorScheme.onSurface.copy(0.4f)
        else                       -> PiumsOrange
    }
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ReportProblem, null,
                        tint = PiumsOrange, modifier = Modifier.size(16.dp))
                    Text(
                        dispute.disputeType.replace("_", " "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PiumsOrange
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(status.displayName, style = MaterialTheme.typography.labelSmall,
                        color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(dispute.subject, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CalendarToday, null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    Text(dispute.createdAt.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
                if ((dispute.priority ?: 0) >= 2) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Warning, null,
                            modifier = Modifier.size(12.dp), tint = PiumsOrange)
                        Text("Alta prioridad", style = MaterialTheme.typography.labelSmall,
                            color = PiumsOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolutionCard(resolution: String, refundAmount: Int?) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, null,
                    tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                Text("Resolución", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
            }
            Text(resolution, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            if (refundAmount != null && refundAmount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CreditCard, null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                    Text("Reembolso: $${String.format("%.2f", refundAmount / 100.0)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = PiumsOrange, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun DisputeMessageBubble(message: DisputeMessageDto, isOwn: Boolean) {
    val senderLabel = when {
        isOwn                                         -> null
        message.senderRole in listOf("admin", "staff") -> "Soporte Piums"
        else                                          -> "Artista"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            senderLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(
                        topStart    = 16.dp, topEnd      = 16.dp,
                        bottomStart = if (isOwn) 16.dp else 4.dp,
                        bottomEnd   = if (isOwn) 4.dp  else 16.dp
                    ))
                    .background(if (isOwn) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    message.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                message.createdAt.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.35f)
            )
        }
    }
}
