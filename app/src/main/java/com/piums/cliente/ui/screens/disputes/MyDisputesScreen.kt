package com.piums.cliente.ui.screens.disputes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.DisputeDto
import com.piums.cliente.data.remote.dto.DisputeStatus
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MyDisputesViewModel @Inject constructor(
    private val api: PiumsApiService
) : ViewModel() {

    var disputes by mutableStateOf<List<DisputeDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            runCatching { api.getDisputes() }
                .onSuccess { disputes = it.all }
                .onFailure { error = "No se pudieron cargar las quejas" }
            isLoading = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun MyDisputesScreen(
    onBack: () -> Unit,
    onDisputeClick: (String) -> Unit,
    vm: MyDisputesViewModel = hiltViewModel()
) {
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
            Text("Mis Quejas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        when {
            vm.isLoading && vm.disputes.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PiumsOrange)
                }
            }
            vm.disputes.isEmpty() -> {
                EmptyDisputesState()
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    vm.error?.let {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    items(vm.disputes, key = { it.id }) { dispute ->
                        DisputeRow(
                            dispute = dispute,
                            onClick = { onDisputeClick(dispute.id) }
                        )
                    }
                    if (vm.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = PiumsOrange,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Dispute Row ──────────────────────────────────────────────────────────────

@Composable
private fun DisputeRow(dispute: DisputeDto, onClick: () -> Unit) {
    val statusColor = disputeStatusColor(dispute.statusEnum)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(statusColor.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ReportProblem, null,
                tint = statusColor, modifier = Modifier.size(22.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dispute.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatusPill(dispute.statusEnum)
            }

            Text(
                dispute.disputeType.replace("_", " ").lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarMonth, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    modifier = Modifier.size(12.dp))
                Text(shortDate(dispute.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))

                if ((dispute.priority ?: 0) >= 2) {
                    Text("·", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    Icon(Icons.Default.Warning, null,
                        tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                    Text("Alta prioridad",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF59E0B))
                }
            }
        }

        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
            modifier = Modifier.size(18.dp))
    }
}

// ─── Status Pill ──────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(status: DisputeStatus) {
    val color = disputeStatusColor(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color)
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyDisputesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null,
            tint = MaterialTheme.colorScheme.onBackground.copy(0.2f),
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Sin quejas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("No has abierto ninguna queja. Si tuviste un problema con un servicio, puedes reportarlo desde el detalle de tu reserva.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
            textAlign = TextAlign.Center)
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun disputeStatusColor(status: DisputeStatus): Color = when (status) {
    DisputeStatus.OPEN          -> Color(0xFFF59E0B)
    DisputeStatus.IN_REVIEW     -> Color(0xFF3B82F6)
    DisputeStatus.AWAITING_INFO -> Color(0xFFEAB308)
    DisputeStatus.RESOLVED      -> Color(0xFF22C55E)
    DisputeStatus.CLOSED        -> Color(0xFF6B7280)
    DisputeStatus.ESCALATED     -> Color(0xFFEF4444)
}

private fun shortDate(isoStr: String): String {
    return try {
        val instant = Instant.parse(isoStr)
        val formatter = DateTimeFormatter
            .ofPattern("d 'de' MMM yyyy", Locale("es", "ES"))
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) { isoStr.take(10) }
}
