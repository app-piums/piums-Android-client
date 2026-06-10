package com.piums.cliente.ui.screens.myspace

import android.app.DatePickerDialog
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piums.cliente.data.remote.dto.BookingDto
import com.piums.cliente.data.remote.dto.CreateEventRequest
import com.piums.cliente.data.remote.dto.EventDto
import com.piums.cliente.ui.theme.PiumsOrange
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Event Detail Sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    event: EventDto,
    vm: MySpaceViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddBooking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        EventDetailContent(
            event = event,
            vm = vm,
            onEdit = { showEdit = true },
            onDelete = { showDeleteConfirm = true },
            onAddBooking = { showAddBooking = true },
            onDismiss = onDismiss
        )
    }

    if (showEdit) {
        EventFormSheet(
            event = event,
            onDismiss = { showEdit = false },
            onSave = { name, date, location, notes, description ->
                vm.updateEventFull(event.id, name, date, location, notes, description)
                showEdit = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar evento?") },
            text = { Text("Se eliminará \"${event.name}\" permanentemente.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteEvent(event.id)
                    onDismiss()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddBooking) {
        AddBookingToEventSheet(
            event = event,
            vm = vm,
            onDismiss = { showAddBooking = false }
        )
    }
}

@Composable
private fun EventDetailContent(
    event: EventDto,
    vm: MySpaceViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddBooking: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val statusColor = when (event.status.uppercase()) {
        "ACTIVE"    -> Color(0xFF22C55E)
        "CANCELLED" -> Color(0xFFEF4444)
        else        -> PiumsOrange
    }
    val statusLabel = when (event.status.uppercase()) {
        "ACTIVE"    -> "Activo"
        "CANCELLED" -> "Cancelado"
        else        -> "Borrador"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {

        // ── Hero ─────────────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
                .padding(vertical = 28.dp, horizontal = 24.dp)
                .padding(horizontal = 16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                event.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Código del evento ─────────────────────────────────────────────
        event.code?.let { code ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PiumsOrange.copy(alpha = 0.08f))
                    .border(1.dp, PiumsOrange.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(vertical = 18.dp)
            ) {
                Text(
                    "CÓDIGO DEL EVENTO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    code,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Información del Evento ────────────────────────────────────────
        EventDetailCard(title = "Información del Evento") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EventInfoCell(label = "FECHA", modifier = Modifier.weight(1f)) {
                    Text(
                        formatEventDate(event.eventDate),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                EventInfoCell(label = "UBICACIÓN", modifier = Modifier.weight(1f)) {
                    Text(
                        event.location ?: "No especificada",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!event.description.isNullOrBlank() || !event.notes.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    event.description?.takeIf { it.isNotBlank() }?.let {
                        EventInfoCell(label = "DESCRIPCIÓN", modifier = Modifier.weight(1f)) {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    event.notes?.takeIf { it.isNotBlank() }?.let {
                        EventInfoCell(label = "NOTAS", modifier = Modifier.weight(1f)) {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (event.description.isNullOrBlank() || event.notes.isNullOrBlank()) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Reservas del Evento ───────────────────────────────────────────
        val bookings = event.bookings ?: emptyList()
        val totalCents = bookings.sumOf { it.totalPrice }
        EventDetailCard(title = "Reservas del Evento (${bookings.size})") {
            if (bookings.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.35f)
                    )
                    Text(
                        "Aún no hay reservas asociadas a este evento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Column {
                    bookings.forEachIndexed { idx, booking ->
                        if (idx > 0) HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(0.1f)
                        )
                        EventBookingRow(booking)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(0.15f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total del Evento",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$%.2f".format(totalCents / 100.0),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PiumsOrange
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Button(
                onClick = onAddBooking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar Reserva", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Acciones ──────────────────────────────────────────────────────
        EventDetailCard(title = "Acciones") {
            EventActionButton(
                icon = Icons.Default.Edit,
                label = "Editar evento",
                color = PiumsOrange,
                onClick = onEdit
            )
            EventActionButton(
                icon = Icons.Default.CalendarToday,
                label = "Agregar al Calendario",
                color = Color(0xFF3B82F6),
                onClick = {
                    val beginMs = runCatching {
                        val raw = event.eventDate ?: return@runCatching System.currentTimeMillis()
                        LocalDate.parse(raw.take(10))
                            .atStartOfDay(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    }.getOrElse { System.currentTimeMillis() }
                    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                        putExtra(CalendarContract.Events.TITLE, event.name)
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMs + 3_600_000L)
                        event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                        event.notes?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                    }
                    runCatching { context.startActivity(intent) }
                }
            )
            EventActionButton(
                icon = Icons.Default.Share,
                label = "Compartir evento",
                color = Color(0xFF0D9488),
                onClick = {
                    val text = buildString {
                        append(event.name)
                        event.eventDate?.let { append(" — ${it.take(10)}") }
                        event.location?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
                        event.code?.let { append("\nCodigo: $it") }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, "Compartir evento")) }
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(0.12f)
            )
            EventActionButton(
                icon = Icons.Default.Delete,
                label = "Eliminar evento",
                color = Color(0xFFEF4444),
                onClick = onDelete
            )
        }
    }
}

// ─── Event Form Sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormSheet(
    event: EventDto? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, date: String?, location: String?, notes: String?, description: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var name by remember { mutableStateOf(event?.name ?: "") }
    var date by remember { mutableStateOf(event?.eventDate?.take(10) ?: "") }
    var location by remember { mutableStateOf(event?.location ?: "") }
    var notes by remember { mutableStateOf(event?.notes ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = PiumsOrange, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (event == null) "Nuevo evento" else "Editar evento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { if (name.isNotBlank()) onSave(name, date.ifBlank { null }, location.ifBlank { null }, notes.ifBlank { null }, description.ifBlank { null }) },
                    enabled = name.isNotBlank()
                ) {
                    Text("Guardar", color = if (name.isNotBlank()) PiumsOrange else MaterialTheme.colorScheme.onSurface.copy(0.3f), fontWeight = FontWeight.SemiBold)
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del evento *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PiumsOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                )
            )

            // Date — tappable row that shows DatePickerDialog
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(12.dp))
                    .clickable {
                        val today = LocalDate.now()
                        val initial = runCatching { LocalDate.parse(date) }.getOrElse { today }
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> date = "%04d-%02d-%02d".format(y, m + 1, d) },
                            initial.year, initial.monthValue - 1, initial.dayOfMonth
                        ).show()
                    },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PiumsOrange, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Fecha", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        Text(
                            if (date.isBlank()) "Seleccionar fecha" else formatEventDate(date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (date.isBlank()) MaterialTheme.colorScheme.onSurface.copy(0.4f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Location
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Ubicación") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = PiumsOrange) },
                trailingIcon = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PiumsOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                )
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PiumsOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                )
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PiumsOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                )
            )
        }
    }

}

// ─── Add Booking To Event Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookingToEventSheet(
    event: EventDto,
    vm: MySpaceViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var addingId by remember { mutableStateOf<String?>(null) }
    var successId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val availableBookings = remember(vm.bookings, event.bookings) {
        val linkedIds = (event.bookings ?: emptyList()).map { it.id }.toSet()
        vm.bookings.filter { b ->
            b.id !in linkedIds && b.eventId == null &&
            b.status in setOf("PENDING", "CONFIRMED", "CARD_AUTHORIZED", "ANTICIPO_PAID", "IN_PROGRESS")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Agregar Reserva", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }

            if (availableBookings.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(48.dp))
                        Text("Sin reservas disponibles", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), textAlign = TextAlign.Center)
                        Text("Las reservas confirmadas o pendientes que no están en otro evento aparecerán aquí.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), textAlign = TextAlign.Center)
                    }
                }
            } else {
                // Info banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PiumsOrange.copy(alpha = 0.08f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = PiumsOrange, modifier = Modifier.size(18.dp))
                    Text(
                        "Selecciona la reserva para vincularla a \"${event.name}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(availableBookings, key = { it.id }) { booking ->
                        val isAdding = addingId == booking.id
                        val isDone = successId == booking.id
                        AddBookingRow(
                            booking = booking,
                            artistName = vm.artistNameCache[booking.artistId] ?: booking.resolvedArtistName,
                            isAdding = isAdding,
                            isDone = isDone,
                            onClick = {
                                if (!isAdding && !isDone) {
                                    addingId = booking.id
                                    scope.launch {
                                        vm.addBookingToEvent(event.id, booking.id)
                                        successId = booking.id
                                        addingId = null
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AddBookingRow(
    booking: BookingDto,
    artistName: String?,
    isAdding: Boolean,
    isDone: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (booking.status) {
        "PENDING", "CARD_AUTHORIZED" -> Color(0xFFF59E0B)
        "CONFIRMED", "ANTICIPO_PAID", "IN_PROGRESS" -> Color(0xFF3B82F6)
        else -> Color(0xFF6B7280)
    }
    val statusLabel = when (booking.status) {
        "PENDING" -> "Pendiente"
        "CARD_AUTHORIZED" -> "Autorizada"
        "CONFIRMED", "ANTICIPO_PAID" -> "Confirmada"
        "IN_PROGRESS" -> "En progreso"
        else -> booking.status
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isDone) 2.dp else 0.dp,
                color = if (isDone) Color(0xFF22C55E) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = !isAdding && !isDone) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.12f))
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = statusColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    booking.code ?: booking.id.take(8),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(statusLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!artistName.isNullOrBlank()) {
                Text(artistName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(
                booking.scheduledDate.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Text(
                "$%.2f".format(booking.totalPrice / 100.0),
                style = MaterialTheme.typography.bodySmall,
                color = PiumsOrange,
                fontWeight = FontWeight.SemiBold
            )
        }
        when {
            isDone -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(26.dp))
            isAdding -> CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp, color = PiumsOrange)
            else -> Icon(Icons.Default.AddCircle, contentDescription = null, tint = PiumsOrange, modifier = Modifier.size(26.dp))
        }
    }
}

// ─── Shared sub-components ────────────────────────────────────────────────────

@Composable
private fun EventDetailCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun EventInfoCell(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
            letterSpacing = 0.8.sp
        )
        content()
    }
}

@Composable
private fun EventBookingRow(booking: BookingDto) {
    val statusColor = when (booking.status) {
        "PENDING"                    -> Color(0xFFF59E0B)
        "CONFIRMED", "ANTICIPO_PAID" -> Color(0xFF3B82F6)
        "IN_PROGRESS"                -> PiumsOrange
        "COMPLETED", "DELIVERED"     -> Color(0xFF22C55E)
        "CANCELLED_CLIENT", "CANCELLED_ARTIST", "REJECTED" -> Color(0xFFEF4444)
        else                         -> Color(0xFF6B7280)
    }
    val statusLabel = when (booking.status) {
        "PENDING"                    -> "Pendiente"
        "CONFIRMED"                  -> "Confirmada"
        "ANTICIPO_PAID"              -> "Anticipo pagado"
        "IN_PROGRESS"                -> "En progreso"
        "COMPLETED"                  -> "Completada"
        "DELIVERED"                  -> "Entregada"
        "CANCELLED_CLIENT"           -> "Cancelada"
        "CANCELLED_ARTIST"           -> "Cancelada"
        "REJECTED"                   -> "Rechazada"
        else                         -> booking.status
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                booking.code ?: booking.id.take(8),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Text(booking.scheduledDate.take(10), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$%.2f".format(booking.totalPrice / 100.0),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PiumsOrange
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EventActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color.copy(alpha = 0.12f))
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (color == Color(0xFFEF4444)) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(16.dp))
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatEventDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "Sin fecha"
    return runCatching {
        val d = LocalDate.parse(raw.take(10))
        val fmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, yyyy", Locale("es", "ES"))
        d.format(fmt).replaceFirstChar { it.uppercase() }
    }.getOrElse { raw.take(10) }
}
