package com.piums.cliente.ui.screens.payments

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.MyCreditsResponse
import com.piums.cliente.data.remote.dto.PaymentDto
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.ui.theme.PiumsSuccess
import com.piums.cliente.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val api: PiumsApiService
) : ViewModel() {

    var payments by mutableStateOf<List<PaymentDto>>(emptyList())
        private set
    var credits  by mutableStateOf<MyCreditsResponse?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init { loadInitial() }

    fun loadInitial() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            launch {
                runCatching { api.getPayments(page = 1) }
                    .onSuccess  { payments = it.list }
                    .onFailure  { errorMessage = it.toUserMessage() }
            }
            launch {
                runCatching { api.getMyCredits() }
                    .onSuccess { credits = it }
            }
            isLoading = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun PaymentsScreen(
    onBack: () -> Unit,
    vm: PaymentsViewModel = hiltViewModel()
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
            Text("Mis Pagos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        when {
            vm.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
            vm.errorMessage != null && vm.payments.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp), Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.errorMessage ?: "Error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center)
                    Button(onClick = { vm.loadInitial() },
                        colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)) {
                        Text("Reintentar")
                    }
                }
            }
            vm.payments.isEmpty() -> EmptyPaymentsState()
            else -> PaymentsList(payments = vm.payments, credits = vm.credits)
        }
    }
}

@Composable
private fun PaymentsList(payments: List<PaymentDto>, credits: MyCreditsResponse?) {
    val grouped = remember(payments) { groupByMonth(payments) }
    val completedCount = remember(payments) { payments.count { it.status == "SUCCEEDED" || it.status == "COMPLETED" } }
    val totalPaid = remember(payments) {
        payments.filter { it.status == "SUCCEEDED" || it.status == "COMPLETED" }.sumOf { it.amount }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Credits card
        credits?.let { c ->
            if (c.totalAmount > 0) {
                item {
                    CreditsCard(c)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // Summary chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryChip(
                    icon  = Icons.Default.CheckCircle,
                    color = PiumsSuccess,
                    label = "Completados",
                    value = "$completedCount",
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    icon  = Icons.Default.AttachMoney,
                    color = PiumsOrange,
                    label = "Total pagado",
                    value = formatCents(totalPaid),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Grouped by month
        grouped.forEach { (month, monthPayments) ->
            item(key = "header_$month") {
                Text(
                    text     = month,
                    style    = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color    = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(monthPayments, key = { it.id }) { payment ->
                PaymentCard(payment = payment, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun PaymentCard(payment: PaymentDto, modifier: Modifier = Modifier) {
    val (statusColor, statusLabel, statusIcon) = paymentStatusInfo(payment.status)

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            // Side color bar
            Box(
                Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Max)
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(statusColor)
            )

            Column(Modifier.weight(1f)) {
                // Main row
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Status icon
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(20.dp))
                    }

                    // Description + status badge
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text  = payment.description ?: "Pago de reserva",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(20.dp),
                                color = statusColor.copy(0.12f)) {
                                Text(statusLabel, color = statusColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                        }
                    }

                    // Amount + date
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(payment.formattedAmount,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (payment.status == "REFUNDED") Color(0xFF8B5CF6) else PiumsOrange)
                        Text(formatDate(payment.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }

                // Booking row
                payment.bookingId?.let { bookingId ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.08f),
                        modifier = Modifier.padding(horizontal = 14.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.size(14.dp))
                        Text("Reserva ${bookingId.take(8).uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditsCard(credits: MyCreditsResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PiumsOrange.copy(0.06f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            .background(PiumsOrange.copy(0.12f)), Alignment.Center) {
            Icon(Icons.Default.AttachMoney, null, tint = PiumsOrange,
                modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Créditos disponibles",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Se aplican automáticamente en tu próxima reserva",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f), lineHeight = 16.sp)
        }
        Text(credits.formattedAmount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = PiumsOrange)
    }
}

@Composable
private fun SummaryChip(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }
}

@Composable
private fun EmptyPaymentsState() {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(90.dp).clip(CircleShape)
                    .background(PiumsOrange.copy(0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CreditCard, null,
                    tint = PiumsOrange.copy(0.5f), modifier = Modifier.size(42.dp))
            }
            Text("Sin transacciones",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Aquí verás el historial de tus pagos una vez que realices una reserva.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                textAlign = TextAlign.Center)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private data class StatusInfo(val color: Color, val label: String, val icon: ImageVector)

@Composable
private fun paymentStatusInfo(status: String): StatusInfo = when (status) {
    "SUCCEEDED", "COMPLETED" -> StatusInfo(PiumsSuccess, "Completado", Icons.Default.CheckCircle)
    "PENDING"                 -> StatusInfo(Color(0xFFF59E0B), "Pendiente", Icons.Default.Schedule)
    "PROCESSING"              -> StatusInfo(Color(0xFF3B82F6), "Procesando", Icons.Default.Sync)
    "FAILED"                  -> StatusInfo(MaterialTheme.colorScheme.error, "Fallido", Icons.Default.Cancel)
    "REFUNDED"                -> StatusInfo(Color(0xFF8B5CF6), "Reembolsado", Icons.Default.Undo)
    else                      -> StatusInfo(MaterialTheme.colorScheme.onSurface.copy(0.4f), status, Icons.Default.Info)
}

private fun formatCents(cents: Int): String = "$${String.format("%,.2f", cents / 100.0)}"

private fun formatDate(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "ES")))
    } catch (e: Exception) {
        iso.take(10)
    }
}

private fun groupByMonth(payments: List<PaymentDto>): List<Pair<String, List<PaymentDto>>> {
    val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
    val keyFmt   = DateTimeFormatter.ofPattern("yyyy-MM")
    val grouped  = LinkedHashMap<String, MutableList<PaymentDto>>()
    payments.forEach { p ->
        val key = try {
            val instant = Instant.parse(p.createdAt)
            val ym = YearMonth.from(instant.atZone(ZoneId.systemDefault()))
            ym.format(keyFmt)
        } catch (e: Exception) { p.createdAt.take(7) }
        grouped.getOrPut(key) { mutableListOf() }.add(p)
    }
    return grouped.entries.map { (key, list) ->
        val label = try {
            val ym = YearMonth.parse(key, DateTimeFormatter.ofPattern("yyyy-MM"))
            ym.format(monthFmt).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) { key }
        label to list
    }
}
