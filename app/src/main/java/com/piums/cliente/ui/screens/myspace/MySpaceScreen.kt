package com.piums.cliente.ui.screens.myspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import com.piums.cliente.ui.components.PiumsSegmentedPicker
import com.piums.cliente.ui.components.SegmentedTab
import com.piums.cliente.ui.screens.coupons.CouponCard
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.ui.theme.PiumsWarning
import com.piums.cliente.ui.theme.PiumsSuccess
import com.piums.cliente.ui.theme.PiumsError
import com.piums.cliente.ui.theme.PiumsInfo
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MySpaceViewModel @Inject constructor(
    private val api: PiumsApiService
) : ViewModel() {

    // Bookings
    var bookings by mutableStateOf<List<BookingDto>>(emptyList())
        private set
    var isLoadingBookings by mutableStateOf(true)
        private set
    var bookingFilter by mutableStateOf<String?>(null)
        private set
    var cancellingId by mutableStateOf<String?>(null)
        private set
    var artistNameCache by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    // Events
    var events by mutableStateOf<List<EventDto>>(emptyList())
        private set
    var isLoadingEvents by mutableStateOf(true)
        private set
    var showCreateEvent by mutableStateOf(false)
        private set

    // Coupons
    var coupons by mutableStateOf<List<CouponDto>>(emptyList())
        private set
    var isLoadingCoupons by mutableStateOf(true)
        private set

    // Favorites
    var favorites by mutableStateOf<List<FavoriteDto>>(emptyList())
        private set
    var isLoadingFavorites by mutableStateOf(true)
        private set
    var favoriteArtistCache by mutableStateOf<Map<String, ArtistDto>>(emptyMap())
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val b = async {
                runCatching { api.getBookings(page = 1, limit = 50) }
                    .onFailure { Log.e("PiumsAPI", "getBookings error", it) }
                    .getOrNull()
            }
            val e = async {
                runCatching { api.getEvents() }
                    .onFailure { Log.e("PiumsAPI", "getEvents error", it) }
                    .getOrNull()
            }
            val f = async {
                runCatching { api.getFavorites(limit = 50) }
                    .onFailure { Log.e("PiumsAPI", "getFavorites error", it) }
                    .getOrNull()
            }
            val c = async {
                runCatching { api.getCoupons() }
                    .onFailure { Log.e("PiumsAPI", "getCoupons error", it) }
                    .getOrNull()
            }

            val bRes = b.await()
            Log.d("PiumsAPI", "bookings → bookings:${bRes?.bookings?.size} data:${bRes?.data?.size} items:${bRes?.items?.size} list:${bRes?.list?.size}")
            bookings  = bRes?.list ?: emptyList()
            events    = e.await()?.list ?: emptyList()
            favorites = f.await()?.list ?: emptyList()
            coupons   = c.await()?.list ?: emptyList()

            isLoadingBookings  = false
            isLoadingEvents    = false
            isLoadingFavorites = false
            isLoadingCoupons   = false

            prefetchArtistNames(bookings)
            prefetchFavoriteArtists(favorites)
        }
    }

    private fun prefetchArtistNames(list: List<BookingDto>) {
        val missing = list.map { it.artistId }
            .distinct()
            .filter { it.isNotBlank() && it !in artistNameCache }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val fetched = mutableMapOf<String, String>()
            missing.map { id ->
                async {
                    runCatching { api.getArtist(id) }
                        .getOrNull()?.resolved?.displayName
                        ?.takeIf { it != "Artista" }
                        ?.let { fetched[id] = it }
                }
            }.forEach { it.await() }
            if (fetched.isNotEmpty()) artistNameCache = artistNameCache + fetched
        }
    }

    private fun prefetchFavoriteArtists(list: List<FavoriteDto>) {
        val missing = list
            .filter { it.entityType == "ARTIST" }
            .map { it.entityId }
            .distinct()
            .filter { it.isNotBlank() && it !in favoriteArtistCache }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val fetched = mutableMapOf<String, ArtistDto>()
            missing.map { id ->
                async {
                    runCatching { api.getArtist(id) }
                        .getOrNull()?.resolved
                        ?.let { fetched[id] = it }
                }
            }.forEach { it.await() }
            if (fetched.isNotEmpty()) favoriteArtistCache = favoriteArtistCache + fetched
        }
    }

    fun filterBookings(status: String?) { bookingFilter = status }

    private val filterGroups = mapOf(
        "PENDING"   to setOf("PENDING", "CARD_AUTHORIZED"),
        "CONFIRMED" to setOf("CONFIRMED", "ANTICIPO_PAID", "IN_PROGRESS"),
        "COMPLETED" to setOf("COMPLETED", "DELIVERED"),
        "CANCELLED" to setOf("CANCELLED_CLIENT", "CANCELLED_ARTIST", "REJECTED", "NO_SHOW"),
    )

    val filteredBookings: List<BookingDto> get() {
        val list = when (bookingFilter) {
            null -> bookings
            else -> {
                val group = filterGroups[bookingFilter]
                if (group != null) bookings.filter { it.status in group }
                else bookings.filter { it.status == bookingFilter }
            }
        }
        return list.sortedBy { it.scheduledDate }
    }

    // Reviews
    var reviewingBooking by mutableStateOf<BookingDto?>(null)
        private set
    var isSubmittingReview by mutableStateOf(false)
        private set

    fun startReview(booking: BookingDto) { reviewingBooking = booking }
    fun cancelReview() { reviewingBooking = null }

    fun submitReview(rating: Int, comment: String) {
        val booking = reviewingBooking ?: return
        viewModelScope.launch {
            isSubmittingReview = true
            runCatching {
                api.createReview(CreateReviewRequest(
                    artistId  = booking.artistId,
                    bookingId = booking.id,
                    rating    = rating,
                    comment   = comment.takeIf { it.isNotBlank() }
                ))
            }
            reviewingBooking = null
            isSubmittingReview = false
        }
    }

    fun cancelBooking(id: String) {
        viewModelScope.launch {
            cancellingId = id
            runCatching { api.cancelBooking(id) }
            bookings = bookings.map { if (it.id == id) it.copy(status = "CANCELLED_CLIENT") else it }
            cancellingId = null
        }
    }

    fun showCreateEvent(show: Boolean) { showCreateEvent = show }

    fun createEvent(name: String, date: String?, location: String?, notes: String?, description: String? = null) {
        viewModelScope.launch {
            val ev = runCatching {
                api.createEvent(CreateEventRequest(
                    name = name, eventDate = date, location = location,
                    notes = notes, description = description
                ))
            }.getOrNull()?.data
            if (ev != null) events = listOf(ev) + events
            showCreateEvent = false
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteEvent(id) }
            events = events.filter { it.id != id }
            if (selectedEvent?.id == id) selectedEvent = null
        }
    }

    var editingEvent by mutableStateOf<EventDto?>(null)
        private set

    var selectedEvent by mutableStateOf<EventDto?>(null)
        private set

    fun selectEvent(event: EventDto) { selectedEvent = event }
    fun clearSelectedEvent() { selectedEvent = null }

    fun startEditEvent(event: EventDto) { editingEvent = event }
    fun cancelEditEvent() { editingEvent = null }

    fun updateEvent(id: String, name: String, date: String?, location: String?, notes: String?) {
        updateEventFull(id, name, date, location, notes, null)
    }

    fun updateEventFull(id: String, name: String, date: String?, location: String?, notes: String?, description: String?) {
        viewModelScope.launch {
            val ev = runCatching {
                api.updateEvent(id, CreateEventRequest(
                    name = name, eventDate = date, location = location,
                    notes = notes, description = description
                ))
            }.getOrNull()?.data
            if (ev != null) {
                events = events.map { if (it.id == id) ev else it }
                if (selectedEvent?.id == id) selectedEvent = ev
            }
            editingEvent = null
        }
    }

    fun addBookingToEvent(eventId: String, bookingId: String) {
        viewModelScope.launch {
            runCatching { api.linkBookingToEvent(eventId, bookingId) }
            val updated = runCatching { api.getEvent(eventId) }.getOrNull()?.data
            if (updated != null) {
                events = events.map { if (it.id == eventId) updated else it }
                if (selectedEvent?.id == eventId) selectedEvent = updated
            }
            runCatching { api.getBookings(page = 1, limit = 50) }.getOrNull()?.list?.let {
                bookings = it
            }
        }
    }

    fun removeFavorite(favoriteId: String) {
        viewModelScope.launch {
            runCatching { api.removeFavorite(favoriteId) }
            favorites = favorites.filter { it.id != favoriteId }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MySpaceScreen(
    onArtistClick: (String) -> Unit = {},
    onBookingClick: (String) -> Unit = {},
    vm: MySpaceViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf(
        SegmentedTab("Reservas",  Icons.Default.CalendarMonth),
        SegmentedTab("Boletos",   Icons.Default.ConfirmationNumber),
        SegmentedTab("Eventos",   Icons.Default.Event),
        SegmentedTab("Cupones",   Icons.Default.LocalOffer),
        SegmentedTab("Favoritos", Icons.Default.Favorite)
    )

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(vm.isLoadingBookings) { if (!vm.isLoadingBookings) isRefreshing = false }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = { isRefreshing = true; vm.refresh() },
        modifier     = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Text(
                "Mi Espacio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )

            PiumsSegmentedPicker(
                tabs         = tabs,
                selectedIndex = pagerState.currentPage,
                onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                modifier     = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> BookingsTab(vm = vm, onBookingClick = onBookingClick, onArtistClick = onArtistClick)
                    1 -> TicketsTabScreen()
                    2 -> EventsTab(vm = vm)
                    3 -> CouponsTab(vm = vm)
                    4 -> FavoritesTab(vm = vm, onArtistClick = onArtistClick)
                }
            }
        }
    }

    if (vm.showCreateEvent) {
        EventFormSheet(
            onDismiss = { vm.showCreateEvent(false) },
            onSave = { name, date, location, notes, description ->
                vm.createEvent(name, date, location, notes, description)
            }
        )
    }

    vm.editingEvent?.let { event ->
        EventFormSheet(
            event     = event,
            onDismiss = vm::cancelEditEvent,
            onSave = { name, date, location, notes, description ->
                vm.updateEventFull(event.id, name, date, location, notes, description)
            }
        )
    }

    vm.selectedEvent?.let { event ->
        EventDetailSheet(
            event    = event,
            vm       = vm,
            onDismiss = vm::clearSelectedEvent
        )
    }

    vm.reviewingBooking?.let { booking ->
        ReviewDialog(
            artistName  = vm.artistNameCache[booking.artistId] ?: booking.resolvedArtistName ?: "Artista",
            isSubmitting = vm.isSubmittingReview,
            onDismiss   = vm::cancelReview,
            onSubmit    = { rating, comment -> vm.submitReview(rating, comment) }
        )
    }
}

// ─── Bookings Tab ─────────────────────────────────────────────────────────────

@Composable
private fun BookingsTab(
    vm: MySpaceViewModel,
    onBookingClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    val statusFilters = listOf(
        null         to "Todas",
        "PENDING"    to "Pendientes",
        "CONFIRMED"  to "Confirmadas",
        "COMPLETED"  to "Completadas",
        "CANCELLED"  to "Canceladas"
    )

    Column(Modifier.fillMaxSize()) {
        // Filter chips
        LazyRow(
            modifier = Modifier.padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(statusFilters) { filter ->
                val status = filter.first
                val label  = filter.second
                val active = vm.bookingFilter == status
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.filterBookings(status) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = if (active) Color.White
                                else MaterialTheme.colorScheme.onSurface.copy(0.7f))
                }
            }
        }

        if (vm.isLoadingBookings) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
        } else if (vm.filteredBookings.isEmpty()) {
            EmptyTabState(icon = Icons.Default.CalendarMonth, message = "Sin reservas")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.filteredBookings, key = { it.id }) { booking ->
                    BookingCard(
                        booking       = booking,
                        artistName    = vm.artistNameCache[booking.artistId]
                                        ?: booking.resolvedArtistName,
                        cancelling    = vm.cancellingId == booking.id,
                        onCancel      = { vm.cancelBooking(booking.id) },
                        onReview      = { vm.startReview(booking) },
                        onClick       = { onBookingClick(booking.id) },
                        onArtistClick = onArtistClick
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun BookingCard(
    booking: BookingDto,
    artistName: String?,
    cancelling: Boolean,
    onCancel: () -> Unit,
    onReview: () -> Unit = {},
    onClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    val displayArtistName = artistName ?: "Artista"
    val status = booking.statusEnum
    val statusColor = Color(status.color)
    var isExpanded by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }

    val statusIcon = when (status) {
        BookingStatus.PENDING                   -> Icons.Default.Schedule
        BookingStatus.CONFIRMED                 -> Icons.Default.CheckCircle
        BookingStatus.PAYMENT_PENDING           -> Icons.Default.CreditCard
        BookingStatus.COMPLETED,
        BookingStatus.DELIVERED                 -> Icons.Default.Star
        BookingStatus.CANCELLED_CLIENT,
        BookingStatus.CANCELLED_ARTIST,
        BookingStatus.REJECTED                  -> Icons.Default.Cancel
        BookingStatus.RESCHEDULED,
        BookingStatus.RESCHEDULE_PENDING_ARTIST,
        BookingStatus.RESCHEDULE_PENDING_CLIENT -> Icons.Default.Refresh
        BookingStatus.IN_PROGRESS,
        BookingStatus.PAYMENT_COMPLETED,
        BookingStatus.DISPUTE_RESOLVED          -> Icons.Default.CheckCircle
        BookingStatus.DISPUTE_OPEN              -> Icons.Default.ReportProblem
        BookingStatus.NO_SHOW                   -> Icons.Default.Warning
    }

    val shortDate = remember(booking.scheduledDate) {
        try {
            val d = java.time.LocalDate.parse(booking.scheduledDate.take(10))
            val months = arrayOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
            "${d.dayOfMonth} ${months[d.monthValue - 1]}"
        } catch (e: Exception) { booking.scheduledDate.take(10) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            // ── Main row ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon square
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(statusIcon, null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Artist name + price/date
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                displayArtistName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = buildString {
                                booking.serviceName?.let { append(it) }
                                booking.code?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append(it)
                                }
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                booking.formattedTotal,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PiumsOrange
                            )
                            Text(
                                shortDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        }
                    }

                    // Status capsule + time + duration
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(statusColor.copy(0.12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                status.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = statusColor
                            )
                        }
                        booking.scheduledTime?.let { time ->
                            Icon(Icons.Default.AccessTime, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                modifier = Modifier.size(11.dp))
                            Text(time,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                        }
                        booking.duration?.let { dur ->
                            Icon(Icons.Default.Timer, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                modifier = Modifier.size(11.dp))
                            Text("$dur min",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                        }
                    }
                }
            }

            // ── Expand toggle ─────────────────────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.08f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isExpanded) "Ocultar detalles" else "Ver detalles",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = PiumsOrange
                )
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = PiumsOrange,
                    modifier = Modifier.size(16.dp)
                )
            }

            // ── Expanded content ──────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.08f))

                    // Location
                    booking.location?.takeIf { it.isNotBlank() }?.let { loc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                modifier = Modifier.size(13.dp))
                            Text(loc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }

                    // Notes
                    booking.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Notes, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                modifier = Modifier.size(13.dp).padding(top = 1.dp))
                            Text(notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }

                    // Cancel / Review buttons
                    if (booking.canCancel || booking.canReview) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (booking.canReview) {
                                TextButton(onClick = onReview) {
                                    Text("Calificar", color = PiumsOrange,
                                        style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            if (booking.canCancel) {
                                TextButton(onClick = { showCancel = true }) {
                                    if (cancelling) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Cancelar", color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    // Artist profile button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(PiumsOrange.copy(0.08f))
                            .clickable { onArtistClick(booking.artistId) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PiumsOrange.copy(0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                displayArtistName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PiumsOrange
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayArtistName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            booking.serviceName?.let {
                                Text(it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("Ver perfil",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = PiumsOrange)
                            Icon(Icons.Default.ArrowForward, null,
                                tint = PiumsOrange, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCancel) {
        val cancelMessage = when (booking.paymentStatus) {
            "CARD_AUTHORIZED" -> "Tu reserva será cancelada. No se realizará ningún cobro."
            "ANTICIPO_PAID"   -> {
                val refund = String.format("%.2f", (booking.anticipoAmount ?: 0) * 0.5 / 100.0)
                "Se aplicará una penalidad del 50%. Recibirás un reembolso de $$refund."
            }
            else              -> "Esta acción no se puede deshacer."
        }
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("¿Cancelar reserva?") },
            text  = { Text(cancelMessage) },
            confirmButton = {
                TextButton(onClick = { showCancel = false; onCancel() }) {
                    Text("Cancelar reserva", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancel = false }) { Text("Mantener") }
            }
        )
    }
}

// ─── Events Tab ───────────────────────────────────────────────────────────────

@Composable
private fun EventsTab(vm: MySpaceViewModel) {
    Box(Modifier.fillMaxSize()) {
        if (vm.isLoadingEvents) {
            CircularProgressIndicator(color = PiumsOrange,
                modifier = Modifier.align(Alignment.Center))
        } else if (vm.events.isEmpty()) {
            EmptyTabState(icon = Icons.Default.ConfirmationNumber, message = "Sin eventos creados",
                action = "Crear evento") { vm.showCreateEvent(true) }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.events, key = { it.id }) { event ->
                    EventCard(
                        event    = event,
                        onDelete = { vm.deleteEvent(event.id) },
                        onEdit   = { vm.startEditEvent(event) },
                        onClick  = { vm.selectEvent(event) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
        // FAB
        FloatingActionButton(
            onClick = { vm.showCreateEvent(true) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .navigationBarsPadding(),
            containerColor = PiumsOrange,
            contentColor   = Color.White
        ) {
            Icon(Icons.Default.Add, null)
        }
    }
}

@Composable
private fun EventCard(event: EventDto, onDelete: () -> Unit, onEdit: () -> Unit = {}, onClick: () -> Unit = {}) {
    var showDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.name, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    event.eventDate?.let {
                        Text(it.take(10), style = MaterialTheme.typography.bodySmall,
                            color = PiumsOrange)
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Edit, null,
                            tint = PiumsOrange.copy(0.7f),
                            modifier = Modifier.size(20.dp))
                    }
                    // Agregar al calendario del dispositivo
                    event.eventDate?.let { dateStr ->
                        IconButton(
                            onClick = {
                                val beginMs = runCatching {
                                    java.time.LocalDate.parse(dateStr.take(10))
                                        .atStartOfDay(java.time.ZoneId.systemDefault())
                                        .toInstant().toEpochMilli()
                                }.getOrElse { System.currentTimeMillis() }
                                val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                                    putExtra(CalendarContract.Events.TITLE, event.name)
                                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
                                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMs + 3_600_000)
                                    event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                                    event.notes?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                                }
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    // Compartir evento
                    IconButton(
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
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Share, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showDelete = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
            event.location?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        modifier = Modifier.size(12.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                }
            }
            val bookingCount = event.bookings?.size ?: 0
            if (bookingCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.BookOnline, null, tint = PiumsOrange,
                        modifier = Modifier.size(12.dp))
                    Text("$bookingCount reserva${if (bookingCount != 1) "s" else ""}  •  ${event.formattedTotal}",
                        style = MaterialTheme.typography.labelSmall, color = PiumsOrange)
                }
            }
        }
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("¿Eliminar evento?") },
            text  = { Text("Se eliminará \"${event.name}\" permanentemente.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

// ─── Coupons Tab ──────────────────────────────────────────────────────────────

@Composable
private fun CouponsTab(vm: MySpaceViewModel) {
    if (vm.isLoadingCoupons) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PiumsOrange)
        }
    } else if (vm.coupons.isEmpty()) {
        EmptyTabState(icon = Icons.Default.LocalOffer, message = "Sin cupones disponibles")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(vm.coupons, key = { it.id }) { coupon ->
                CouponCard(coupon)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ─── Favorites Tab ────────────────────────────────────────────────────────────

@Composable
private fun FavoritesTab(
    vm: MySpaceViewModel,
    onArtistClick: (String) -> Unit
) {
    if (vm.isLoadingFavorites) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PiumsOrange)
        }
    } else if (vm.favorites.isEmpty()) {
        EmptyTabState(icon = Icons.Default.FavoriteBorder, message = "Sin favoritos guardados")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.favorites, key = { it.id }) { fav ->
                FavoriteCard(
                    fav    = fav,
                    artist = vm.favoriteArtistCache[fav.entityId] ?: fav.artist,
                    onClick = { if (fav.entityId.isNotEmpty()) onArtistClick(fav.entityId) },
                    onRemove = { vm.removeFavorite(fav.id) }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun FavoriteCard(fav: FavoriteDto, artist: ArtistDto?, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar: image or initials fallback
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PiumsOrange.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (artist?.avatarUrl != null) {
                    coil.compose.AsyncImage(
                        model              = artist.avatarUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        artist?.name?.firstOrNull()?.uppercase() ?: "?",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = PiumsOrange
                    )
                }
            }

            // Info
            Column(
                modifier              = Modifier.weight(1f),
                verticalArrangement   = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    artist?.displayName ?: "Artista",
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                val subtitle = listOfNotNull(
                    artist?.specialties?.firstOrNull(),
                    artist?.city
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
            }

            // Rating + remove
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                artist?.averageRating?.takeIf { it > 0 }?.let { r ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.Star, null,
                            tint     = Color(0xFFF59E0B),
                            modifier = Modifier.size(12.dp))
                        Text(
                            String.format("%.1f", r),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick  = onRemove,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Favorite, null,
                        tint     = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ─── Review Dialog ────────────────────────────────────────────────────────────

@Composable
private fun ReviewDialog(
    artistName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating  by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calificar a $artistName", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(5) { i ->
                        IconButton(
                            onClick = { rating = i + 1 },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (i < rating) Icons.Default.Star else Icons.Default.StarBorder,
                                null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Text(
                    when (rating) {
                        1 -> "Muy malo"
                        2 -> "Malo"
                        3 -> "Regular"
                        4 -> "Bueno"
                        5 -> "Excelente"
                        else -> ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PiumsOrange, fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    label = { Text("Comentario (opcional)") },
                    placeholder = { Text("¿Cómo fue tu experiencia?") },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(rating, comment) },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = PiumsOrange,
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Enviar reseña", color = PiumsOrange, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ─── Edit Event Dialog ────────────────────────────────────────────────────────

@Composable
private fun EditEventDialog(
    event: EventDto,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?, String?) -> Unit
) {
    var name     by remember { mutableStateOf(event.name) }
    var date     by remember { mutableStateOf(event.eventDate?.take(10) ?: "") }
    var location by remember { mutableStateOf(event.location ?: "") }
    var notes    by remember { mutableStateOf(event.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar evento", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre del evento *") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f))
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f))
                )
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Dirección") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f))
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(
                        name,
                        date.takeIf { it.isNotBlank() },
                        location.takeIf { it.isNotBlank() },
                        notes.takeIf { it.isNotBlank() }
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("Guardar", color = PiumsOrange, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun EmptyTabState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(0.3f),
            modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)) {
                Text(action, color = Color.White)
            }
        }
    }
}
