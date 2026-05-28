package com.piums.cliente.ui.screens.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.PaymentMethodDto
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class WalletViewModel @Inject constructor(private val api: PiumsApiService) : ViewModel() {

    var methods by mutableStateOf<List<PaymentMethodDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var settingDefaultId by mutableStateOf<String?>(null)
        private set
    var deletingId by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            runCatching { api.getPaymentMethods() }
                .onSuccess  { methods = it.list }
                .onFailure  { error = it.toUserMessage() }
            isLoading = false
        }
    }

    fun setDefault(method: PaymentMethodDto) {
        viewModelScope.launch {
            settingDefaultId = method.id
            runCatching { api.setDefaultPaymentMethod(method.id) }
                .onSuccess {
                    methods = methods.map { m -> m.copy(isDefault = m.id == method.id) }
                }
                .onFailure { error = it.toUserMessage() }
            settingDefaultId = null
        }
    }

    fun delete(method: PaymentMethodDto) {
        viewModelScope.launch {
            deletingId = method.id
            runCatching { api.deletePaymentMethod(method.id) }
                .onSuccess { methods = methods.filter { it.id != method.id } }
                .onFailure { error = it.toUserMessage() }
            deletingId = null
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun WalletScreen(
    onBack: () -> Unit,
    vm: WalletViewModel = hiltViewModel()
) {
    var confirmDelete by remember { mutableStateOf<PaymentMethodDto?>(null) }

    confirmDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Eliminar tarjeta") },
            text  = { Text("¿Eliminar la tarjeta ${toDelete.displayBrand} ${toDelete.maskedNumber}?") },
            confirmButton = {
                TextButton(onClick = { vm.delete(toDelete); confirmDelete = null }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null)
            }
            Text(
                "Mis Tarjetas",
                style    = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }

        when {
            vm.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
            vm.error != null && vm.methods.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp), Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.error ?: "Error", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.load() },
                        colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)) {
                        Text("Reintentar")
                    }
                }
            }
            vm.methods.isEmpty() -> EmptyWalletState()
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    // Horizontal card carousel
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        vm.methods.forEach { method ->
                            CardTile(
                                method           = method,
                                isSettingDefault = vm.settingDefaultId == method.id,
                                onSetDefault     = { if (!method.isDefault) vm.setDefault(method) }
                            )
                        }
                    }
                }

                item {
                    // Method rows list
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        vm.methods.forEachIndexed { idx, method ->
                            MethodRow(
                                method           = method,
                                isDeleting       = vm.deletingId == method.id,
                                isSettingDefault = vm.settingDefaultId == method.id,
                                canDelete        = vm.methods.size > 1,
                                onSetDefault     = { vm.setDefault(method) },
                                onDelete         = { confirmDelete = method }
                            )
                            if (idx < vm.methods.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp),
                                    color    = MaterialTheme.colorScheme.outline.copy(0.08f)
                                )
                            }
                        }
                    }
                }

                item { WalletInfoSection() }
            }
        }
    }
}

@Composable
private fun CardTile(
    method: PaymentMethodDto,
    isSettingDefault: Boolean,
    onSetDefault: () -> Unit
) {
    val gradient = cardGradient(method.brand)
    Box(
        modifier = Modifier
            .size(width = 280.dp, height = 170.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .clickable(onClick = onSetDefault)
            .padding(20.dp)
    ) {
        // decorative circles
        Box(Modifier.size(160.dp).offset(80.dp, (-60).dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.06f)))
        Box(Modifier.size(120.dp).offset((-60).dp, 70.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.04f)))

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Text(method.displayBrand, fontWeight = FontWeight.Bold,
                    color = Color.White, fontSize = 16.sp)
                if (method.isDefault) {
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(0.2f)) {
                        Text("Principal", color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("•••• •••• •••• ${method.last4 ?: "••••"}",
                    color = Color.White, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                if (method.expiryDisplay.isNotEmpty()) {
                    Text("Vence ${method.expiryDisplay}",
                        color = Color.White.copy(0.6f),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (isSettingDefault) {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.size(20.dp).align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun MethodRow(
    method: PaymentMethodDto,
    isDeleting: Boolean,
    isSettingDefault: Boolean,
    canDelete: Boolean,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    val brandColor = brandTint(method.brand)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .background(brandColor.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CreditCard, null, tint = brandColor,
                modifier = Modifier.size(22.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${method.displayBrand} ${method.maskedNumber}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (method.isDefault) {
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = PiumsOrange.copy(0.12f)) {
                        Text("Principal", color = PiumsOrange,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            if (method.expiryDisplay.isNotEmpty()) {
                Text("Vence ${method.expiryDisplay}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
            if (!canDelete) {
                Text("Mínimo 1 tarjeta requerida",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
            }
        }

        if (isDeleting || isSettingDefault) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp, color = PiumsOrange)
        } else {
            DropdownMenuBox(
                method   = method,
                canDelete = canDelete,
                onSetDefault = onSetDefault,
                onDelete     = onDelete
            )
        }
    }
}

@Composable
private fun DropdownMenuBox(
    method: PaymentMethodDto,
    canDelete: Boolean,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                if (canDelete) Icons.Default.MoreVert else Icons.Default.Lock,
                null,
                tint = if (canDelete) MaterialTheme.colorScheme.onSurface.copy(0.5f)
                else Color(0xFFF59E0B)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!method.isDefault) {
                DropdownMenuItem(
                    text = { Text("Establecer como principal") },
                    leadingIcon = { Icon(Icons.Default.Star, null, tint = PiumsOrange) },
                    onClick = { expanded = false; onSetDefault() }
                )
            }
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text("Eliminar tarjeta",
                        color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null,
                        tint = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun WalletInfoSection() {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoRow(Icons.Default.Lock,    Color(0xFF22C55E), "Pagos seguros",
            "Tus datos de tarjeta son procesados de forma segura y no se almacenan en nuestros servidores.")
        InfoRow(Icons.Default.Star,    PiumsOrange,       "Tarjeta principal",
            "Toca cualquier tarjeta en el carrusel para establecerla como predeterminada.")
        InfoRow(Icons.Default.Delete,  Color(0xFFEF4444), "Control total",
            "Puedes eliminar tarjetas en cualquier momento. Se requiere al menos 1 tarjeta guardada.")
    }
}

@Composable
private fun InfoRow(icon: ImageVector, color: Color, title: String, subtitle: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp).padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f), lineHeight = 16.sp)
        }
    }
}

@Composable
private fun EmptyWalletState() {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.CreditCard, null,
                tint    = PiumsOrange.copy(0.4f),
                modifier = Modifier.size(64.dp))
            Text("Sin métodos de pago",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Agrega una tarjeta al realizar tu primera reserva.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                textAlign = TextAlign.Center)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun cardGradient(brand: String?): Brush = when (brand?.lowercase()) {
    "visa"       -> Brush.linearGradient(listOf(Color(0xFF1A1F71), Color(0xFF2563EB)))
    "mastercard" -> Brush.linearGradient(listOf(Color(0xFF1C1C1C), Color(0xFFEB0029)))
    "amex"       -> Brush.linearGradient(listOf(Color(0xFF007AC1), Color(0xFF00A3E0)))
    else         -> Brush.linearGradient(listOf(Color(0xFF2D2D2D), Color(0xFF444444)))
}

private fun brandTint(brand: String?): Color = when (brand?.lowercase()) {
    "visa"       -> Color(0xFF1A1F71)
    "mastercard" -> Color(0xFFEB001B)
    "amex"       -> Color(0xFF006FCF)
    else         -> Color(0xFF6B7280)
}
