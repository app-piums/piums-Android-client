package com.piums.cliente.ui.screens.disputes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.CreateDisputeRequest
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Dispute Types ────────────────────────────────────────────────────────────

internal enum class DisputeTypeOption(
    val raw: String,
    val label: String,
    val icon: ImageVector
) {
    CANCELLATION("CANCELLATION",    "Cancelación",              Icons.Default.Cancel),
    QUALITY     ("QUALITY",         "Calidad del servicio",     Icons.Default.StarHalf),
    REFUND      ("REFUND",          "Reembolso",                Icons.Default.Replay),
    NO_SHOW     ("NO_SHOW",         "No me presenté",           Icons.Default.PersonOff),
    ARTIST_NO_SHOW("ARTIST_NO_SHOW","Artista no se presentó",   Icons.Default.PersonRemove),
    PRICING     ("PRICING",         "Precio / cargos",          Icons.Default.AttachMoney),
    BEHAVIOR    ("BEHAVIOR",        "Comportamiento",           Icons.Default.Warning),
    OTHER       ("OTHER",           "Otro",                     Icons.Default.HelpOutline)
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class CreateDisputeViewModel @Inject constructor(
    private val api: PiumsApiService,
    savedState: SavedStateHandle
) : ViewModel() {

    private val bookingId: String = checkNotNull(savedState["bookingId"])

    internal var disputeType by mutableStateOf(DisputeTypeOption.OTHER)
        private set
    var subject     by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var isLoading   by mutableStateOf(false)
        private set
    var isSuccess   by mutableStateOf(false)
        private set
    var error       by mutableStateOf<String?>(null)
        private set

    val canSubmit: Boolean get() = subject.isNotBlank() && description.isNotBlank() && !isLoading

    internal fun setType(type: DisputeTypeOption) { disputeType = type }
    fun onSubjectChange(v: String)     { subject = v }
    fun onDescriptionChange(v: String) { description = v }

    fun submit() {
        if (!canSubmit) return
        viewModelScope.launch {
            isLoading = true
            error = null
            runCatching {
                api.createDispute(CreateDisputeRequest(
                    bookingId   = bookingId,
                    disputeType = disputeType.raw,
                    subject     = subject.trim(),
                    description = description.trim()
                ))
            }.onSuccess {
                isSuccess = true
            }.onFailure {
                error = it.toUserMessage()
            }
            isLoading = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CreateDisputeScreen(
    onBack: () -> Unit,
    vm: CreateDisputeViewModel = hiltViewModel()
) {
    if (vm.isSuccess) {
        DisputeSuccessScreen(onDone = onBack)
        return
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
            Text("Nueva queja",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Info card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PiumsOrange.copy(0.08f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Info, null, tint = PiumsOrange,
                    modifier = Modifier.size(20.dp).padding(top = 1.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("¿Tuviste un problema?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Nuestro equipo revisará tu caso en 24-48 horas hábiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
            }

            // Type grid
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tipo de queja",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)

                val types = DisputeTypeOption.entries
                for (row in types.chunked(2)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { type ->
                            TypeCard(
                                type = type,
                                isSelected = vm.disputeType == type,
                                modifier = Modifier.weight(1f),
                                onClick = { vm.setType(type) }
                            )
                        }
                        if (row.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Subject
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Asunto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)
                OutlinedTextField(
                    value = vm.subject,
                    onValueChange = vm::onSubjectChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Resumen breve del problema") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )
            }

            // Description
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Descripción",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Describe detalladamente lo ocurrido",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                OutlinedTextField(
                    value = vm.description,
                    onValueChange = vm::onDescriptionChange,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    placeholder = { Text("Explica lo que sucedió...") },
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )
            }

            // Error
            vm.error?.let { msg ->
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
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Submit
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (vm.canSubmit) PiumsOrange
                        else MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                    .clickable(enabled = vm.canSubmit, onClick = vm::submit),
                contentAlignment = Alignment.Center
            ) {
                if (vm.isLoading) {
                    CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Enviar queja", color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// ─── Type Card ────────────────────────────────────────────────────────────────

@Composable
private fun TypeCard(
    type: DisputeTypeOption,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent
                        else MaterialTheme.colorScheme.outline.copy(0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(type.icon, null,
                tint = if (isSelected) Color.White else PiumsOrange,
                modifier = Modifier.size(22.dp))
            Text(type.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 2)
        }
    }
}

// ─── Success ──────────────────────────────────────────────────────────────────

@Composable
private fun DisputeSuccessScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null,
            tint = PiumsOrange, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(24.dp))
        Text("Queja enviada",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Nuestro equipo revisará tu caso y te contactará en 24-48 horas hábiles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PiumsOrange)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Text("Listo", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}
