package com.piums.cliente.ui.screens.myspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import com.piums.cliente.ui.components.PiumsSegmentedPicker
import com.piums.cliente.ui.components.SegmentedTab
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class TicketsViewModel @Inject constructor(
    private val api: PiumsApiService
) : ViewModel() {

    private val _events = MutableStateFlow<List<TicketEvent>>(emptyList())
    val events: StateFlow<List<TicketEvent>> = _events.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _myPurchases = MutableStateFlow<List<TicketPurchase>>(emptyList())
    val myPurchases: StateFlow<List<TicketPurchase>> = _myPurchases.asStateFlow()

    private val _redirectUrl = MutableStateFlow<String?>(null)
    val redirectUrl: StateFlow<String?> = _redirectUrl.asStateFlow()

    private val _purchaseCompleted = MutableStateFlow(false)
    val purchaseCompleted: StateFlow<Boolean> = _purchaseCompleted.asStateFlow()

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    private var pendingPurchaseId: String? = null
    private var currentPage = 1
    private val pageLimit = 12
    private var hasMore = true

    init {
        loadInitial()
        loadMyPurchases()
    }

    fun loadInitial() {
        viewModelScope.launch {
            currentPage = 1
            _events.value = emptyList()
            hasMore = true
            loadNextPage()
        }
    }

    fun loadNextPage() {
        if (_isLoading.value || !hasMore) return
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { api.listTicketEvents(page = currentPage, limit = pageLimit) }
                .onSuccess { res ->
                    val new = res.all.filter { it.isPublished || it.isSoldOut }
                    _events.value = if (currentPage == 1) new else _events.value + new
                    hasMore = new.size == pageLimit
                    currentPage++
                }
            _isLoading.value = false
        }
    }

    fun loadMyPurchases() {
        viewModelScope.launch {
            runCatching { api.myTicketPurchases() }
                .onSuccess { _myPurchases.value = it.all.sortedByDescending { p -> p.createdAt } }
        }
    }

    fun purchase(event: TicketEvent, tier: TicketTier, qty: Int,
                 buyerName: String, buyerEmail: String) {
        viewModelScope.launch {
            _purchaseError.value = null
            _purchaseCompleted.value = false
            val body = buildMap<String, Any> {
                put("tierId", tier.id)
                put("quantity", qty)
                put("buyerName", buyerName)
                put("buyerEmail", buyerEmail)
                put("returnUrl", "piums://tickets/confirmacion")
            }
            runCatching { api.purchaseTicket(event.id, body) }
                .onSuccess { res ->
                    pendingPurchaseId = res.purchase?.id ?: res.purchaseId
                    _redirectUrl.value = res.redirectUrl
                    if (res.redirectUrl == null) pollPurchaseStatus()
                }
                .onFailure { _purchaseError.value = "No se pudo procesar la compra" }
        }
    }

    fun onPaymentReturn() {
        _redirectUrl.value = null
        pollPurchaseStatus()
    }

    private fun pollPurchaseStatus() {
        val pid = pendingPurchaseId ?: return
        viewModelScope.launch {
            repeat(10) {
                delay(3_000)
                val res = runCatching { api.getTicketPurchase(pid) }.getOrNull()
                if (res?.resolved?.isPaid == true) {
                    _purchaseCompleted.value = true
                    loadMyPurchases()
                    return@launch
                }
            }
        }
    }

    fun clearPurchaseCompleted() { _purchaseCompleted.value = false }
    fun clearPurchaseError()    { _purchaseError.value = null }
}

// ─── Pantalla Principal de Tickets (tabs: Eventos | Mis Boletos) ──────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsTabScreen(vm: TicketsViewModel = hiltViewModel()) {
    val events      by vm.events.collectAsState()
    val myPurchases by vm.myPurchases.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val purchaseCompleted by vm.purchaseCompleted.collectAsState()
    val purchaseError by vm.purchaseError.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var detailEvent by remember { mutableStateOf<TicketEvent?>(null) }
    var detailPurchase by remember { mutableStateOf<TicketPurchase?>(null) }

    val tabs = listOf(
        SegmentedTab("Eventos", Icons.Default.ConfirmationNumber),
        SegmentedTab("Mis Boletos", Icons.Default.QrCode)
    )

    if (purchaseCompleted) {
        LaunchedEffect(Unit) {
            selectedTab = 1
            detailEvent = null
            vm.clearPurchaseCompleted()
        }
    }

    purchaseError?.let { err ->
        LaunchedEffect(err) {
            delay(3_000)
            vm.clearPurchaseError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PiumsSegmentedPicker(
            tabs          = tabs,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )

        purchaseError?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth())
        }

        when (selectedTab) {
            0 -> EventsGrid(
                events    = events,
                isLoading = isLoading,
                onLoadMore = { vm.loadNextPage() },
                onEventClick = { detailEvent = it }
            )
            1 -> MyTicketsList(
                purchases = myPurchases,
                onPurchaseClick = { detailPurchase = it }
            )
        }
    }

    detailEvent?.let { event ->
        TicketEventDetailSheet(
            event  = event,
            onDismiss = { detailEvent = null },
            onPurchase = { tier, qty, name, email ->
                vm.purchase(event, tier, qty, name, email)
                detailEvent = null
            }
        )
    }

    detailPurchase?.let { purchase ->
        TicketQrSheet(purchase = purchase, onDismiss = { detailPurchase = null })
    }
}

// ─── Grid de Eventos ──────────────────────────────────────────────────────────

@Composable
private fun EventsGrid(
    events: List<TicketEvent>,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    onEventClick: (TicketEvent) -> Unit
) {
    if (events.isEmpty() && !isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ConfirmationNumber, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                    modifier = Modifier.size(48.dp))
                Text("No hay eventos disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(events, key = { it.id }) { event ->
            TicketEventCard(event = event, onClick = { onEventClick(event) })
            if (event == events.lastOrNull()) {
                LaunchedEffect(Unit) { onLoadMore() }
            }
        }
        if (isLoading) {
            item(span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                    CircularProgressIndicator(color = PiumsOrange, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun TicketEventCard(event: TicketEvent, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .alpha(if (event.isSoldOut) 0.7f else 1f)
    ) {
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            if (event.imageUrl != null) {
                AsyncImage(model = event.imageUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize()
                    .background(Brush.linearGradient(
                        listOf(PiumsOrange.copy(0.7f), Color(0xFFE91E8C).copy(0.5f))
                    )))
            }
            if (event.isSoldOut) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("AGOTADO", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(event.name, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = PiumsOrange,
                    modifier = Modifier.size(11.dp))
                Text(event.venue, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Desde", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                Text(event.formattedMinPrice, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = PiumsOrange)
            }
        }
    }
}

// ─── Mis Boletos ──────────────────────────────────────────────────────────────

@Composable
private fun MyTicketsList(
    purchases: List<TicketPurchase>,
    onPurchaseClick: (TicketPurchase) -> Unit
) {
    if (purchases.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.QrCode, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                    modifier = Modifier.size(48.dp))
                Text("No tienes boletos aún",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }
        return
    }

    val upcoming = purchases.filter { it.isUpcoming }
    val past     = purchases.filter { !it.isUpcoming }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (upcoming.isNotEmpty()) {
            Text("Próximos", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            upcoming.forEach { p ->
                PurchaseCard(purchase = p, onClick = { onPurchaseClick(p) })
            }
        }
        if (past.isNotEmpty()) {
            Text("Pasados", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                modifier = Modifier.padding(top = 8.dp))
            past.forEach { p ->
                PurchaseCard(purchase = p, onClick = { onPurchaseClick(p) })
            }
        }
    }
}

@Composable
private fun PurchaseCard(purchase: TicketPurchase, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PiumsOrange.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ConfirmationNumber, null, tint = PiumsOrange,
                modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(purchase.ticketEvent?.name ?: "Evento",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(purchase.tier?.name ?: "Boleto · x${purchase.quantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            Text(purchase.formattedTotal, style = MaterialTheme.typography.bodySmall,
                color = PiumsOrange, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.Default.QrCode2, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
            modifier = Modifier.size(20.dp))
    }
}

// ─── Sheet Detalle de Evento para Compra ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketEventDetailSheet(
    event: TicketEvent,
    onDismiss: () -> Unit,
    onPurchase: (TicketTier, Int, String, String) -> Unit
) {
    var selectedTier by remember { mutableStateOf<TicketTier?>(null) }
    var qty by remember { mutableIntStateOf(1) }
    var buyerName by remember { mutableStateOf("") }
    var buyerEmail by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(event.name, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = PiumsOrange,
                    modifier = Modifier.size(14.dp))
                Text("${event.venue} · ${event.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

            Text("Selecciona tu tipo de boleto",
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

            event.tiers.forEach { tier ->
                val sel = selectedTier?.id == tier.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (sel) PiumsOrange.copy(0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = !tier.isSoldOut) { selectedTier = tier }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(tier.name, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (tier.isSoldOut)
                                MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            else MaterialTheme.colorScheme.onSurface)
                        tier.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                        }
                        if (tier.isSoldOut) {
                            Text("Agotado", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("${tier.available} disponibles",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                    Text(tier.formattedPrice, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, color = PiumsOrange)
                }
            }

            if (selectedTier != null) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Cantidad:", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { if (qty > 1) qty-- },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, null, tint = PiumsOrange)
                    }
                    Text("$qty", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        val max = selectedTier?.available ?: 1
                        if (qty < max) qty++
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, null, tint = PiumsOrange)
                    }
                }

                OutlinedTextField(
                    value = buyerName, onValueChange = { buyerName = it },
                    label = { Text("Nombre del comprador") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )
                OutlinedTextField(
                    value = buyerEmail, onValueChange = { buyerEmail = it },
                    label = { Text("Email del comprador") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )

                val tier = selectedTier!!
                val total = tier.priceCents * qty
                val canBuy = buyerName.isNotBlank() && buyerEmail.contains("@")

                Button(
                    onClick = { onPurchase(tier, qty, buyerName, buyerEmail) },
                    enabled = canBuy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
                ) {
                    Text("Comprar · $${String.format("%,.2f", total / 100.0)}",
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ─── Sheet QR del Boleto ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketQrSheet(purchase: TicketPurchase, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(purchase.ticketEvent?.name ?: "Boleto",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Text(purchase.tier?.name ?: "Entrada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))

            // QR placeholder (requiere librería zxing para generarlo)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.QrCode2, null, tint = PiumsOrange,
                        modifier = Modifier.size(80.dp))
                    Text(purchase.code, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        textAlign = TextAlign.Center)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Cantidad", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    Text("x${purchase.quantity}", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    Text(purchase.formattedTotal, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = PiumsOrange)
                }
            }

            val statusColor = when (purchase.status) {
                "PAGADO" -> Color(0xFF22C55E)
                "USADO"  -> MaterialTheme.colorScheme.onSurface.copy(0.5f)
                else     -> Color(0xFFF59E0B)
            }
            val statusLabel = when (purchase.status) {
                "PAGADO"       -> "Válido"
                "USADO"        -> "Usado"
                "REEMBOLSADO"  -> "Reembolsado"
                "EXPIRADO"     -> "Expirado"
                else           -> purchase.status
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(0.12f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(statusLabel, color = statusColor, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
