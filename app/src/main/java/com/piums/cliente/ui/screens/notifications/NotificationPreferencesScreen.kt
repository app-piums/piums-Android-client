package com.piums.cliente.ui.screens.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.NotifPreferencesDto
import com.piums.cliente.data.remote.dto.UpdateNotifPreferencesRequest
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class NotifPreferencesViewModel @Inject constructor(
    private val api: PiumsApiService
) : ViewModel() {

    var prefs by mutableStateOf(NotifPreferencesDto())
        private set
    var saved by mutableStateOf(NotifPreferencesDto())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set

    val hasChanges: Boolean
        get() = prefs != saved

    init { load() }

    private fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            runCatching { api.getNotifPreferences() }
                .onSuccess { prefs = it; saved = it }
                .onFailure { error = "No se pudieron cargar las preferencias" }
            isLoading = false
        }
    }

    fun update(block: NotifPreferencesDto.() -> NotifPreferencesDto) {
        prefs = prefs.block()
        successMessage = null
    }

    fun save() {
        if (!hasChanges || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            runCatching {
                api.updateNotifPreferences(
                    UpdateNotifPreferencesRequest(
                        emailEnabled         = prefs.emailEnabled,
                        smsEnabled           = prefs.smsEnabled,
                        pushEnabled          = prefs.pushEnabled,
                        bookingNotifications = prefs.bookingNotifications,
                        paymentNotifications = prefs.paymentNotifications,
                        reviewNotifications  = prefs.reviewNotifications,
                        marketingNotifications = prefs.marketingNotifications,
                        dndEnabled           = prefs.dndEnabled,
                        dndStartHour         = prefs.dndStartHour,
                        dndEndHour           = prefs.dndEndHour
                    )
                )
            }
                .onSuccess { saved = it; prefs = it; successMessage = "Preferencias guardadas" }
                .onFailure { error = "Error al guardar" }
            isSaving = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun NotificationPreferencesScreen(
    onBack: () -> Unit,
    vm: NotifPreferencesViewModel = hiltViewModel()
) {
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Notificaciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (vm.isSaving) {
                CircularProgressIndicator(
                    color = PiumsOrange,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(
                    onClick = vm::save,
                    enabled = vm.hasChanges
                ) {
                    Text(
                        "Guardar",
                        color = if (vm.hasChanges) PiumsOrange
                                else MaterialTheme.colorScheme.onBackground.copy(0.3f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        when {
            vm.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                vm.successMessage?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        color = PiumsOrange.copy(0.1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(12.dp),
                            color = PiumsOrange,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                vm.error?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.error.copy(0.1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                PrefSection(title = "Canales", footer = "Controla por qué medio recibes las notificaciones.") {
                    PrefToggle(
                        icon = Icons.Default.Email, iconTint = Color(0xFF2196F3),
                        label = "Email",
                        checked = vm.prefs.emailEnabled,
                        onCheckedChange = { vm.update { copy(emailEnabled = it) } }
                    )
                    PrefToggle(
                        icon = Icons.Default.Sms, iconTint = Color(0xFF4CAF50),
                        label = "SMS",
                        checked = vm.prefs.smsEnabled,
                        onCheckedChange = { vm.update { copy(smsEnabled = it) } }
                    )
                    PrefToggle(
                        icon = Icons.Default.Notifications, iconTint = PiumsOrange,
                        label = "Push",
                        checked = vm.prefs.pushEnabled,
                        onCheckedChange = { vm.update { copy(pushEnabled = it) } }
                    )
                }

                PrefSection(
                    title = "Tipos de notificación",
                    footer = "Las notificaciones de seguridad siempre se enviarán independientemente de estas preferencias."
                ) {
                    PrefToggle(
                        icon = Icons.Default.CalendarToday, iconTint = PiumsOrange,
                        label = "Reservas",
                        checked = vm.prefs.bookingNotifications,
                        onCheckedChange = { vm.update { copy(bookingNotifications = it) } }
                    )
                    PrefToggle(
                        icon = Icons.Default.CreditCard, iconTint = Color(0xFF4CAF50),
                        label = "Pagos",
                        checked = vm.prefs.paymentNotifications,
                        onCheckedChange = { vm.update { copy(paymentNotifications = it) } }
                    )
                    PrefToggle(
                        icon = Icons.Default.Star, iconTint = Color(0xFFFFC107),
                        label = "Reseñas",
                        checked = vm.prefs.reviewNotifications,
                        onCheckedChange = { vm.update { copy(reviewNotifications = it) } }
                    )
                    PrefToggle(
                        icon = Icons.Default.LocalOffer, iconTint = Color(0xFF9C27B0),
                        label = "Promociones",
                        checked = vm.prefs.marketingNotifications,
                        onCheckedChange = { vm.update { copy(marketingNotifications = it) } }
                    )
                }

                PrefSection(
                    title = "No molestar",
                    footer = if (vm.prefs.dndEnabled)
                        "No recibirás notificaciones entre las ${hourLabel(vm.prefs.dndStartHour)} y las ${hourLabel(vm.prefs.dndEndHour)}."
                    else null
                ) {
                    PrefToggle(
                        icon = Icons.Default.DarkMode, iconTint = Color(0xFF3F51B5),
                        label = "No molestar",
                        checked = vm.prefs.dndEnabled,
                        onCheckedChange = { vm.update { copy(dndEnabled = it) } }
                    )
                    if (vm.prefs.dndEnabled) {
                        HourPickerRow(
                            label = "Desde",
                            hour = vm.prefs.dndStartHour,
                            onHourChange = { vm.update { copy(dndStartHour = it) } }
                        )
                        HourPickerRow(
                            label = "Hasta",
                            hour = vm.prefs.dndEndHour,
                            onHourChange = { vm.update { copy(dndEndHour = it) } }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun PrefSection(
    title: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(0.45f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp)
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column { content() }
    }
    footer?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)
        )
    }
}

@Composable
private fun PrefToggle(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PiumsOrange,
                checkedTrackColor = PiumsOrange.copy(0.3f)
            )
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.07f)
    )
}

@Composable
private fun HourPickerRow(label: String, hour: Int, onHourChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Schedule, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(hourLabel(hour), color = PiumsOrange, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowDropDown, null, tint = PiumsOrange)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (0 until 24).forEach { h ->
                    DropdownMenuItem(
                        text = { Text(hourLabel(h)) },
                        onClick = { onHourChange(h); expanded = false }
                    )
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.07f)
    )
}

private fun hourLabel(h: Int): String {
    val period = if (h < 12) "AM" else "PM"
    val display = when (h) { 0 -> 12; in 13..23 -> h - 12; else -> h }
    return "$display:00 $period"
}
