package com.piums.cliente.ui.screens.coupons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.CouponDto
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class CouponsViewModel @Inject constructor(
    private val api: PiumsApiService
) : ViewModel() {

    var coupons by mutableStateOf<List<CouponDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            runCatching { api.getCoupons() }
                .onSuccess { coupons = it.list }
                .onFailure { error = "No se pudieron cargar los cupones" }
            isLoading = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CouponsScreen(
    onBack: () -> Unit,
    vm: CouponsViewModel = hiltViewModel()
) {
    Column(Modifier.fillMaxSize()) {
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
                "Mis cupones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        when {
            vm.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
            vm.error != null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(vm.error!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = vm::load) {
                    Text("Reintentar", color = PiumsOrange)
                }
            }
            vm.coupons.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.LocalOffer, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(0.3f),
                    modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "No tienes cupones disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.coupons, key = { it.id }) { coupon ->
                    CouponCard(coupon)
                }
            }
        }
    }
}

// ─── CouponCard ───────────────────────────────────────────────────────────────

@Composable
internal fun CouponCard(coupon: CouponDto) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    coupon.name?.let {
                        Text(it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1)
                    }
                    coupon.description?.let {
                        Text(it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                            maxLines = 2)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    coupon.formattedDiscount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = PiumsOrange
                )
            }

            // Ticket divider with notches
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
            ) {
                HorizontalDivider(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(0.15f)
                )
            }

            // Code + meta + copy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        coupon.code,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        fontWeight = FontWeight.Bold,
                        color = PiumsOrange
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        coupon.minimumAmount?.let { min ->
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowUpward, null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                Text("Mín. $${String.format("%.0f", min)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                            }
                        }
                        coupon.expiresAt?.let { exp ->
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                Text(exp.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                            }
                        }
                        if (coupon.maxUses == 1 || coupon.maxUsesPerUser == 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ConfirmationNumber, null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                Text("1 uso",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (copied) Color(0xFF22C55E).copy(0.1f)
                            else PiumsOrange.copy(0.1f)
                        )
                        .clickable {
                            clipboard.setText(AnnotatedString(coupon.code))
                            copied = true
                            scope.launch { delay(2_000); copied = false }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if (copied) Color(0xFF22C55E) else PiumsOrange
                        )
                        Text(
                            if (copied) "Copiado" else "Copiar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (copied) Color(0xFF22C55E) else PiumsOrange
                        )
                    }
                }
            }
        }
    }
}
