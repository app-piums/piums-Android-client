package com.piums.cliente.ui.screens.booking

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    private val api: PiumsApiService,
    savedState: SavedStateHandle
) : ViewModel() {

    val bookingId: String = checkNotNull(savedState["bookingId"])

    var booking by mutableStateOf<BookingDto?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Reschedule sheet
    var showReschedule by mutableStateOf(false)
        private set
    var rescheduleMonth by mutableStateOf(YearMonth.now())
        private set
    var selectedDate by mutableStateOf<LocalDate?>(null)
        private set
    var availableSlots by mutableStateOf<List<TimeSlot>>(emptyList())
        private set
    var selectedSlot by mutableStateOf<TimeSlot?>(null)
        private set
    var rescheduleReason by mutableStateOf("")
        private set
    var isRescheduling by mutableStateOf(false)
        private set
    var rescheduleError by mutableStateOf<String?>(null)
        private set
    var isLoadingSlots by mutableStateOf(false)
        private set

    var isReportingNoShow by mutableStateOf(false)
        private set
    var noShowError by mutableStateOf<String?>(null)
        private set

    var collaborators by mutableStateOf<List<BookingCollaborator>>(emptyList())
        private set

    var replacementSearch by mutableStateOf<ReplacementSearchDto?>(null)
        private set
    var replacementAction by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            val loaded = runCatching { api.getBooking(bookingId) }.getOrElse {
                error = "No se pudo cargar la reserva"
                null
            }
            booking = loaded
            isLoading = false
            if (loaded?.status == "CANCELLED_ARTIST") loadReplacementSearch()
        }
        viewModelScope.launch {
            runCatching { api.getBookingCollaborators(bookingId) }
                .onSuccess { collaborators = it.all.filter { c -> c.status == "ACCEPTED" } }
        }
    }

    private fun loadReplacementSearch() {
        viewModelScope.launch {
            replacementSearch = runCatching { api.getReplacementSearch(bookingId) }.getOrNull()
        }
    }

    fun acceptReplacement() {
        viewModelScope.launch {
            replacementAction = "accepting"
            runCatching { api.acceptReplacement(bookingId) }
                .onSuccess { replacementSearch = replacementSearch?.copy(status = "SEARCHING") }
            replacementAction = null
        }
    }

    fun declineReplacement() {
        viewModelScope.launch {
            replacementAction = "declining"
            runCatching { api.declineReplacement(bookingId) }
                .onSuccess { replacementSearch = replacementSearch?.copy(status = "OPTED_OUT") }
            replacementAction = null
        }
    }

    fun openReschedule() {
        rescheduleMonth = YearMonth.now()
        selectedDate = null
        selectedSlot = null
        rescheduleReason = ""
        rescheduleError = null
        showReschedule = true
    }

    fun closeReschedule() { showReschedule = false }

    fun changeRescheduleMonth(delta: Int) {
        rescheduleMonth = rescheduleMonth.plusMonths(delta.toLong())
        selectedDate = null
        selectedSlot = null
    }

    fun selectRescheduleDate(date: LocalDate) {
        selectedDate = date
        selectedSlot = null
        val artistId = booking?.artistId ?: return
        viewModelScope.launch {
            isLoadingSlots = true
            val resp = runCatching {
                api.getTimeSlots(artistId, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }.getOrNull()
            availableSlots = resp?.slots
                ?: resp?.availableSlots?.map { TimeSlot(it) }
                ?: emptyList()
            isLoadingSlots = false
        }
    }

    fun selectSlot(slot: TimeSlot) { selectedSlot = slot }
    fun onReasonChange(text: String) { rescheduleReason = text }

    fun reportNoShow() {
        viewModelScope.launch {
            isReportingNoShow = true
            noShowError = null
            runCatching { api.reportNoShow(bookingId) }
                .onSuccess { booking = it }
                .onFailure { noShowError = "No se pudo reportar. Intenta de nuevo." }
            isReportingNoShow = false
        }
    }

    fun confirmReschedule() {
        val date = selectedDate ?: return
        viewModelScope.launch {
            isRescheduling = true
            rescheduleError = null
            val result = runCatching {
                api.rescheduleBooking(
                    bookingId,
                    RescheduleRequest(
                        scheduledDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        reason = rescheduleReason.ifBlank { null }
                    )
                )
            }
            if (result.isSuccess) {
                booking = result.getOrNull()
                showReschedule = false
            } else {
                rescheduleError = "No se pudo reprogramar. Intenta con otra fecha."
            }
            isRescheduling = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun BookingDetailScreen(
    onBack: () -> Unit,
    onPay: (String) -> Unit = {},
    onOpenDispute: (String) -> Unit = {},
    onLeaveReview: (String) -> Unit = {},
    vm: BookingDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current

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
            Text("Detalle de reserva",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        when {
            vm.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
            vm.booking == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.3f),
                        modifier = Modifier.size(48.dp))
                    Text(vm.error ?: "Error al cargar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                    TextButton(onClick = vm::load) { Text("Reintentar", color = PiumsOrange) }
                }
            }
            else -> {
                val booking = vm.booking!!
                var showNoShowConfirm by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero: centered status circle + code
                    StatusHero(booking)

                    // Reemplazo de artista
                    if (booking.status == "CANCELLED_ARTIST") {
                        vm.replacementSearch?.let { rep ->
                            ReplacementCard(
                                replacement    = rep,
                                action         = vm.replacementAction,
                                onAccept       = vm::acceptReplacement,
                                onDecline      = vm::declineReplacement
                            )
                        }
                    }

                    // Participantes
                    DetailCard(title = "Participantes") {
                        ParticipantRow(
                            role      = "Artista",
                            name      = booking.resolvedArtistName ?: "—",
                            tintColor = PiumsOrange,
                            icon      = Icons.Default.MicExternalOn
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color    = MaterialTheme.colorScheme.outline.copy(0.08f)
                        )
                        ParticipantRow(
                            role      = "Cliente",
                            name      = "Tú",
                            tintColor = Color(0xFF3B82F6),
                            icon      = Icons.Default.Person
                        )
                    }

                    // Equipo adicional (colaboradores aceptados)
                    if (vm.collaborators.isNotEmpty()) {
                        DetailCard(title = "Equipo adicional") {
                            vm.collaborators.forEachIndexed { idx, collab ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (collab.artistAvatar != null) {
                                        coil.compose.AsyncImage(
                                            model = collab.artistAvatar,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(PiumsOrange.copy(0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Person, null,
                                                tint = PiumsOrange,
                                                modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            collab.artistName ?: "Colaborador",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        collab.role?.let {
                                            Text(it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                                        }
                                    }
                                }
                                if (idx < vm.collaborators.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 64.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(0.08f)
                                    )
                                }
                            }
                        }
                    }

                    // Booking code box
                    booking.code?.let { code ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PiumsOrange.copy(0.25f), RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(PiumsOrange.copy(0.07f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "CÓDIGO DE RESERVA",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    code,
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = PiumsOrange,
                                    fontSize   = 18.sp,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    }

                    // Attendance code box
                    if (booking.status in listOf("CONFIRMED", "IN_PROGRESS") &&
                        !booking.attendanceCode.isNullOrEmpty()) {
                        val attCode = booking.attendanceCode!!
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF2563EB).copy(alpha = 0.06f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "CÓDIGO DE ASISTENCIA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    attCode.forEach { digit ->
                                        Box(
                                            modifier = Modifier
                                                .size(width = 36.dp, height = 44.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(8.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                digit.toString(),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Dáselo al artista cuando llegue",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                TextButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("codigo_asistencia", attCode)
                                    cm.setPrimaryClip(clip)
                                }) {
                                    Text("Copiar código", color = Color(0xFF2563EB))
                                }
                            }
                        }
                    }

                    // Información del Evento (2-column grid)
                    DetailCard(title = "Información del Evento") {
                        val cells = buildList {
                            add("FECHA" to booking.scheduledDate.take(10))
                            booking.scheduledTime?.let { add("HORA" to it.take(5)) }
                            booking.duration?.let { add("DURACIÓN" to "${it} min") }
                            add("ESTADO" to booking.statusEnum.displayName)
                            booking.paymentStatus?.let { ps ->
                                val displayStatus = when (ps) {
                                    "CARD_AUTHORIZED"    -> "Tarjeta pre-autorizada"
                                    "ANTICIPO_PAID"      -> "Anticipo pagado"
                                    "FULLY_PAID"         -> "Pagado completo"
                                    "PENDING"            -> "Pago pendiente"
                                    "CHARGING_REMAINING" -> "Cobrando saldo"
                                    "REFUNDED"           -> "Reembolsado"
                                    else -> ps.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }
                                }
                                add("PAGO" to displayStatus)
                            }
                        }
                        // 2-column grid
                        val rows = cells.chunked(2)
                        rows.forEachIndexed { rowIdx, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                row.forEach { (label, value) ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            label,
                                            style      = MaterialTheme.typography.labelSmall,
                                            color      = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            value,
                                            style      = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            if (rowIdx < rows.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color    = MaterialTheme.colorScheme.outline.copy(0.07f)
                                )
                            }
                        }
                        // Location full-width if present
                        booking.location?.let { loc ->
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color    = MaterialTheme.colorScheme.outline.copy(0.07f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.LocationOn, null,
                                    tint     = PiumsOrange,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                Column {
                                    Text("UBICACIÓN",
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.5.sp)
                                    Text(loc,
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = MaterialTheme.colorScheme.onSurface,
                                        maxLines   = 2,
                                        overflow   = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        // Service row
                        booking.serviceName?.let { svc ->
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color    = MaterialTheme.colorScheme.outline.copy(0.07f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, null,
                                    tint     = PiumsOrange,
                                    modifier = Modifier.size(16.dp))
                                Column {
                                    Text("SERVICIO",
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.5.sp)
                                    Text(svc,
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    // Resumen de Pago
                    DetailCard(title = "Resumen de Pago") {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val currency = booking.currency
                            val serviceBase = booking.servicePrice ?: booking.totalPrice
                            val serviceFmt = "$${String.format("%,.2f", serviceBase / 100.0)}"
                            if (booking.anticipoRequired == true && booking.anticipoAmount != null) {
                                PaymentRow("Servicio base", serviceFmt)
                                if ((booking.travelPrice ?: 0) > 0)
                                    PaymentRow("Viáticos / traslado", "$${String.format("%,.2f", booking.travelPrice!! / 100.0)}")
                                if ((booking.addonsPrice ?: 0) > 0)
                                    PaymentRow("Add-ons", "$${String.format("%,.2f", booking.addonsPrice!! / 100.0)}")
                                val anticipo = booking.anticipoAmount
                                val formatted = "$${String.format("%,.2f", anticipo / 100.0)}"
                                val remaining = booking.totalPrice - anticipo
                                val remainingFmt = "$${String.format("%,.2f", remaining / 100.0)}"
                                PaymentRow("Anticipo (50%)", formatted, valueColor = PiumsOrange)
                                PaymentRow("Saldo restante", remainingFmt)
                            } else {
                                PaymentRow("Servicio base", serviceFmt)
                                if ((booking.travelPrice ?: 0) > 0)
                                    PaymentRow("Viáticos / traslado", "$${String.format("%,.2f", booking.travelPrice!! / 100.0)}")
                                if ((booking.addonsPrice ?: 0) > 0)
                                    PaymentRow("Add-ons", "$${String.format("%,.2f", booking.addonsPrice!! / 100.0)}")
                            }
                            if (booking.couponCode != null && booking.discountAmount != null && booking.discountAmount > 0) {
                                val discountFmt = "-$${String.format("%,.2f", booking.discountAmount / 100.0)}"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalOffer, null,
                                            tint = Color(0xFF22C55E),
                                            modifier = Modifier.size(14.dp))
                                        Text("Cupón ${booking.couponCode}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                                    }
                                    Text(discountFmt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF22C55E))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                            val finalTotal = if (booking.discountAmount != null && booking.discountAmount > 0)
                                "$${String.format("%,.2f", (booking.totalPrice - booking.discountAmount) / 100.0)}"
                            else booking.formattedTotal
                            PaymentRow(
                                label      = "Total",
                                value      = finalTotal,
                                labelBold  = true,
                                valueColor = PiumsOrange
                            )
                        }
                    }

                    // Notas
                    booking.notes?.let { notes ->
                        DetailCard(title = "Notas") {
                            Text(
                                notes,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurface.copy(0.8f)
                            )
                        }
                    }

                    // Acciones
                    DetailCard(title = "Acciones") {
                        Column {
                            // Sonidista addon payment section
                            val snd = booking.sonidistaBooking
                            if (snd != null) {
                                when {
                                    snd.status == "CONFIRMED" && snd.paymentStatus == "PENDING" -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(PiumsOrange)
                                                .clickable { onPay(snd.id) }
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment     = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CreditCard, null,
                                                    tint     = Color.White,
                                                    modifier = Modifier.size(18.dp))
                                                Text("Pagar sonidista ($${String.format("%,.2f", snd.totalPrice / 100.0)})",
                                                    color      = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    style      = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    }
                                    snd.status == "CONFIRMED" && snd.paymentStatus == "CARD_AUTHORIZED" -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                            Text("Sonidista — pago reservado", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    snd.status == "PENDING" -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Schedule, null, tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                                            Text("Sonidista — esperando respuesta", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    snd.status == "REJECTED" -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            Text("Sonidista no disponible", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.08f))
                            }

                            // Pay now (highlighted)
                            if (booking.canPay) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp))
                                        .background(PiumsOrange)
                                        .clickable { onPay(vm.bookingId) }
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CreditCard, null,
                                            tint     = Color.White,
                                            modifier = Modifier.size(18.dp))
                                        Text("Pagar ahora",
                                            color      = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style      = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(0.08f))
                            }

                            ActionRow(Icons.Default.CalendarMonth, "Agregar al calendario",
                                PiumsOrange) { addToCalendar(context, booking) }

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 52.dp),
                                color    = MaterialTheme.colorScheme.outline.copy(0.08f))
                            ActionRow(Icons.Default.Share, "Compartir reserva",
                                MaterialTheme.colorScheme.onSurface.copy(0.7f)
                            ) { shareBooking(context, booking) }

                            if (booking.canReschedule) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color    = MaterialTheme.colorScheme.outline.copy(0.08f))
                                ActionRow(Icons.Default.EditCalendar, "Reprogramar",
                                    MaterialTheme.colorScheme.onSurface.copy(0.7f)
                                ) { vm.openReschedule() }
                            }

                            if (booking.canReview) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color    = MaterialTheme.colorScheme.outline.copy(0.08f))
                                ActionRow(Icons.Default.StarRate, "Dejar reseña", PiumsOrange) {
                                    onLeaveReview(vm.bookingId)
                                }
                            }

                            if (booking.canDispute) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color    = MaterialTheme.colorScheme.outline.copy(0.08f))
                                ActionRow(Icons.Default.ReportProblem, "Abrir queja",
                                    MaterialTheme.colorScheme.error) {
                                    onOpenDispute(vm.bookingId)
                                }
                            }

                            if (booking.canReportNoShow) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color    = MaterialTheme.colorScheme.outline.copy(0.08f))
                                ActionRow(Icons.Default.PersonOff, "Reportar no-presentación",
                                    MaterialTheme.colorScheme.error,
                                    enabled = !vm.isReportingNoShow) {
                                    showNoShowConfirm = true
                                }
                                vm.noShowError?.let {
                                    Text(it,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        color    = MaterialTheme.colorScheme.error,
                                        style    = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }

                if (showNoShowConfirm) {
                    AlertDialog(
                        onDismissRequest = { showNoShowConfirm = false },
                        title = { Text("¿Reportar no-presentación?") },
                        text  = { Text("Confirma que el artista no se presentó al evento en la fecha acordada.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showNoShowConfirm = false
                                vm.reportNoShow()
                            }) {
                                Text("Confirmar", color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNoShowConfirm = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                if (vm.showReschedule) {
                    RescheduleSheet(vm = vm)
                }
            }
        }
    }
}

// ─── Reschedule Sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleSheet(vm: BookingDetailViewModel) {
    ModalBottomSheet(
        onDismissRequest = vm::closeReschedule,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Reprogramar reserva",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)

            // Month navigation
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.changeRescheduleMonth(-1) }) {
                    Icon(Icons.Default.ChevronLeft, null)
                }
                Text(
                    vm.rescheduleMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { vm.changeRescheduleMonth(1) }) {
                    Icon(Icons.Default.ChevronRight, null)
                }
            }

            // Calendar grid
            val today = LocalDate.now()
            val ym = vm.rescheduleMonth
            val firstDay = ym.atDay(1).dayOfWeek.value % 7
            val days = (1..ym.lengthOfMonth()).map { ym.atDay(it) }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Day headers
                Row(Modifier.fillMaxWidth()) {
                    listOf("D","L","M","M","J","V","S").forEach { d ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(d, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                }
                val cells = List(firstDay) { null } + days
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { i ->
                            val date = week.getOrNull(i)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            date == vm.selectedDate -> PiumsOrange
                                            date == today -> PiumsOrange.copy(0.12f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .let { mod ->
                                        if (date != null && !date.isBefore(today))
                                            mod.clickable { vm.selectRescheduleDate(date) }
                                        else mod
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                date?.let {
                                    Text(
                                        "${it.dayOfMonth}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            it == vm.selectedDate -> Color.White
                                            it.isBefore(today)   -> MaterialTheme.colorScheme.onSurface.copy(0.25f)
                                            else                  -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (it == vm.selectedDate) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Time slots
            AnimatedVisibility(vm.selectedDate != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hora disponible",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                    if (vm.isLoadingSlots) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                            color = PiumsOrange, strokeWidth = 2.dp)
                    } else if (vm.availableSlots.isEmpty()) {
                        Text("No hay horarios disponibles para esta fecha",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.availableSlots) { slot ->
                                val isSelected = vm.selectedSlot == slot
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { vm.selectSlot(slot) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(slot.time.take(5),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White
                                                else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // Reason field
            OutlinedTextField(
                value = vm.rescheduleReason,
                onValueChange = vm::onReasonChange,
                label = { Text("Motivo (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = PiumsOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                )
            )

            vm.rescheduleError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick  = vm::confirmReschedule,
                enabled  = vm.selectedDate != null && !vm.isRescheduling,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
            ) {
                if (vm.isRescheduling) {
                    CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirmar reprogramación",
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun StatusHero(booking: BookingDto) {
    val status = booking.statusEnum
    val statusColor = Color(status.color)
    val statusIcon = when (status) {
        BookingStatus.CONFIRMED,
        BookingStatus.PAYMENT_COMPLETED,
        BookingStatus.DISPUTE_RESOLVED          -> Icons.Default.CheckCircle
        BookingStatus.COMPLETED,
        BookingStatus.DELIVERED                 -> Icons.Default.TaskAlt
        BookingStatus.PENDING,
        BookingStatus.RESCHEDULED,
        BookingStatus.RESCHEDULE_PENDING_ARTIST,
        BookingStatus.RESCHEDULE_PENDING_CLIENT -> Icons.Default.HourglassTop
        BookingStatus.PAYMENT_PENDING           -> Icons.Default.CreditCard
        BookingStatus.IN_PROGRESS               -> Icons.Default.PlayCircle
        BookingStatus.DISPUTE_OPEN              -> Icons.Default.ReportProblem
        BookingStatus.CANCELLED_CLIENT,
        BookingStatus.CANCELLED_ARTIST,
        BookingStatus.REJECTED                  -> Icons.Default.Cancel
        BookingStatus.NO_SHOW                   -> Icons.Default.PersonOff
    }
    Column(
        modifier             = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(statusColor.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(statusIcon, null,
                tint     = statusColor,
                modifier = Modifier.size(40.dp))
        }
        Text(status.displayName,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground)
        booking.code?.let {
            Text("# $it",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onBackground.copy(0.45f))
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                title,
                modifier   = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ParticipantRow(
    role: String,
    name: String,
    tintColor: Color,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tintColor.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null,
                tint     = tintColor,
                modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(role,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                fontWeight = FontWeight.Medium)
            Text(name,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PaymentRow(
    label: String,
    value: String,
    labelBold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Normal,
            color      = MaterialTheme.colorScheme.onSurface.copy(if (labelBold) 1f else 0.7f))
        Text(value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.SemiBold,
            color      = valueColor)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null,
            tint     = if (enabled) tint else tint.copy(0.4f),
            modifier = Modifier.size(20.dp))
        Text(label,
            modifier = Modifier.weight(1f),
            style    = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color    = if (enabled) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(0.4f))
        Icon(Icons.Default.ChevronRight, null,
            tint     = MaterialTheme.colorScheme.onSurface.copy(0.25f),
            modifier = Modifier.size(18.dp))
    }
}

// ─── Calendar helper ──────────────────────────────────────────────────────────

private fun shareBooking(context: android.content.Context, booking: BookingDto) {
    val lines = buildList {
        add("Reserva Piums")
        booking.code?.let { add("Código: $it") }
        booking.resolvedArtistName?.let { add("Artista: $it") }
        booking.serviceName?.let { add("Servicio: $it") }
        add("Fecha: ${booking.scheduledDate.take(10)}")
        booking.scheduledTime?.let { add("Hora: ${it.take(5)}") }
        booking.location?.let { add("Lugar: $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
    }
    context.startActivity(Intent.createChooser(intent, "Compartir reserva"))
}

private fun addToCalendar(context: android.content.Context, booking: BookingDto) {
    val dateStr = booking.scheduledDate        // "YYYY-MM-DD"
    val timeStr = booking.scheduledTime ?: "00:00"
    val parts   = dateStr.split("-")
    val tParts  = timeStr.split(":")
    val beginMs = runCatching {
        val cal = java.util.Calendar.getInstance()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(),
                tParts[0].toInt(), tParts.getOrNull(1)?.toInt() ?: 0, 0)
        cal.timeInMillis
    }.getOrElse { System.currentTimeMillis() }

    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.Events.TITLE, "Piums — ${booking.resolvedArtistName ?: "Reserva"}")
        putExtra(CalendarContract.Events.DESCRIPTION,
            "${booking.serviceName ?: ""}\n${booking.notes ?: ""}")
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMs + 60 * 60 * 1000)
        booking.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
    }
    runCatching { context.startActivity(intent) }
}

// ─── Replacement Card ─────────────────────────────────────────────────────────

@Composable
private fun ReplacementCard(
    replacement: ReplacementSearchDto,
    action: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val isIdle = action == null
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B).copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PersonSearch, null,
                        tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Buscar artista de reemplazo",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                    Text("El artista canceló tu reserva.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
            }

            when (replacement.status.uppercase()) {
                "PENDING", "OFFERED" -> {
                    Text(
                        "Encontramos artistas disponibles para tu fecha. ¿Deseas que busquemos un reemplazo?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.75f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick  = onDecline,
                            enabled  = isIdle,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp)
                        ) {
                            if (action == "declining") {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Declinar")
                            }
                        }
                        Button(
                            onClick  = onAccept,
                            enabled  = isIdle,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                        ) {
                            if (action == "accepting") {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                                    color = Color.White)
                            } else {
                                Text("Aceptar", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                "SEARCHING" -> {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = Color(0xFFF59E0B))
                        Text("Buscando artista de reemplazo...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    }
                }
                "OPTED_OUT" -> {
                    Text("Rechazaste la búsqueda de reemplazo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
                "EXPIRED" -> {
                    Text("La búsqueda de reemplazo ha expirado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
                else -> {
                    Text("Estado: ${replacement.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            }
        }
    }
}
