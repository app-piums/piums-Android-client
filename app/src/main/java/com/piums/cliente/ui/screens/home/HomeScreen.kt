package com.piums.cliente.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import coil.compose.AsyncImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.ArtistDto
import com.piums.cliente.data.remote.dto.BookingDto
import com.piums.cliente.notifications.NotificationsStore
import com.piums.cliente.ui.components.EventLocationPickerSheet
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: PiumsApiService,
    val tokenStorage: TokenStorage,
    notificationsStore: NotificationsStore
) : ViewModel() {

    val notifUnreadCount: StateFlow<Int> = notificationsStore.unreadCount

    var userName by mutableStateOf(tokenStorage.userName ?: "")
        private set

    var userAvatarUrl by mutableStateOf(tokenStorage.avatarUrl)
        private set

    var bookingDates by mutableStateOf<Set<String>>(emptySet())
        private set

    var nextBooking by mutableStateOf<BookingDto?>(null)
        private set

    var recommendedArtists by mutableStateOf<List<ArtistDto>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
        // Fallback para sesiones existentes donde el avatar no fue guardado
        if (userAvatarUrl == null) {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()?.let { url ->
                userAvatarUrl = url
                tokenStorage.avatarUrl = url
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            error = null
            val bookingsDeferred = async {
                runCatching { api.getBookings(page = 1, limit = 50) }
                    .onFailure { Log.e("PiumsAPI", "getBookings error", it) }
                    .getOrNull()
            }
            val artistsDeferred = async {
                runCatching { api.searchArtists(page = 1, limit = 20) }
                    .onFailure { Log.e("PiumsAPI", "searchArtists error", it) }
                    .getOrNull()
            }

            try {
                val bookings = bookingsDeferred.await()?.list ?: emptyList()
                val today = LocalDate.now().toString()
                bookingDates = bookings
                    .filter { it.status in listOf("PENDING", "CONFIRMED", "IN_PROGRESS", "RESCHEDULED") }
                    .mapNotNull { it.scheduledDate?.take(10) }
                    .toSet()
                nextBooking = bookings
                    .filter { it.status in listOf("CONFIRMED", "PENDING") }
                    .filter { (it.scheduledDate?.take(10) ?: "") >= today }
                    .minByOrNull { it.scheduledDate ?: "" }

                recommendedArtists = (artistsDeferred.await()?.list ?: emptyList()).filter { it.servicesCount > 0 || it.serviceIds?.isNotEmpty() == true || it.serviceTitles?.isNotEmpty() == true }
            } catch (e: Exception) {
                Log.e("PiumsAPI", "HomeViewModel refresh processing error", e)
                error = "No se pudo cargar la información"
                // Still assign artists even if bookings processing failed
                if (recommendedArtists.isEmpty()) {
                    recommendedArtists = (artistsDeferred.await()?.list ?: emptyList()).filter { it.servicesCount > 0 || it.serviceIds?.isNotEmpty() == true || it.serviceTitles?.isNotEmpty() == true }
                }
            } finally {
                isLoading = false
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSearchByDate: (date: String, lat: Float, lng: Float, location: String) -> Unit = { _, _, _, _ -> },
    vm: HomeViewModel = hiltViewModel()
) {
    val today = remember { LocalDate.now() }
    var showLocationPicker by remember { mutableStateOf(false) }
    var pickedDate         by remember { mutableStateOf<LocalDate?>(null) }
    val notifUnreadCount by vm.notifUnreadCount.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(vm.isLoading) { if (!vm.isLoading) isRefreshing = false }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = { isRefreshing = true; vm.refresh() },
        modifier     = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
        HomeHeader(
            userName = vm.userName,
            userAvatarUrl = vm.userAvatarUrl,
            notifUnreadCount = notifUnreadCount,
            onNotificationsClick = onNotificationsClick,
            onAvatarClick = onProfileClick
        )

        Spacer(Modifier.height(20.dp))

        MonthlyCalendar(
            today        = today,
            bookingDates = vm.bookingDates,
            nextBooking  = vm.nextBooking,
            onDayClick   = { day ->
                pickedDate = day
                showLocationPicker = true
            }
        )

        Spacer(Modifier.height(28.dp))

        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recomendados para ti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onSearchClick) {
                Text("Ver todos", color = PiumsOrange,
                    style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (vm.isLoading) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(4) { ArtistCardSkeleton() }
            }
        } else if (vm.recommendedArtists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        modifier = Modifier.size(36.dp))
                    Text("No hay artistas disponibles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.recommendedArtists) { artist ->
                    ArtistCard(artist = artist, onClick = { onArtistClick(artist.resolvedId) })
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        PromoBanner(onClick = onSearchClick)

        vm.error?.let { err ->
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(err, color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = vm::refresh) {
                    Text("Reintentar", color = PiumsOrange)
                }
            }
        }
        }
    } // end PullToRefreshBox

    // Location picker sheet — shown when user taps a calendar day
    if (showLocationPicker && pickedDate != null) {
        EventLocationPickerSheet(
            selectedDate = pickedDate!!,
            onDismiss    = { showLocationPicker = false },
            onConfirm    = { locationName, lat, lng ->
                showLocationPicker = false
                onSearchByDate(
                    pickedDate!!.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    lat.toFloat(),
                    lng.toFloat(),
                    locationName
                )
            }
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(
    userName: String,
    userAvatarUrl: String? = null,
    notifUnreadCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                "Hola, ${userName.ifBlank { "amigo" }}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "¿Qué talento buscas hoy?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNotificationsClick) {
                BadgedBox(badge = {
                    if (notifUnreadCount > 0) {
                        Badge(containerColor = PiumsOrange) {
                            Text(
                                if (notifUnreadCount > 99) "99+" else "$notifUnreadCount",
                                color = Color.White
                            )
                        }
                    }
                }) {
                    Icon(Icons.Default.Notifications, null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.7f))
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PiumsOrange.copy(0.15f))
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (userAvatarUrl != null) {
                    coil.compose.AsyncImage(
                        model = userAvatarUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = userName.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PiumsOrange
                    )
                }
            }
        }
    }
}

// ─── Monthly Calendar ─────────────────────────────────────────────────────────

private val DAY_HEADERS = listOf("L", "M", "M", "J", "V", "S", "D")
private val ES_LOCALE = Locale("es")

@Composable
private fun MonthlyCalendar(
    today: LocalDate,
    bookingDates: Set<String>,
    nextBooking: BookingDto? = null,
    onDayClick: ((LocalDate) -> Unit)? = null
) {
    var displayMonth by remember { mutableStateOf(YearMonth.from(today)) }
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Cells: Monday-aligned grid, nulls for padding before day 1
    val firstDayOffset = (displayMonth.atDay(1).dayOfWeek.value - 1) // 0=Mon … 6=Sun
    val daysInMonth   = displayMonth.lengthOfMonth()
    val cells = buildList<LocalDate?> {
        repeat(firstDayOffset) { add(null) }
        for (d in 1..daysInMonth) add(displayMonth.atDay(d))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        // Month header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { displayMonth = displayMonth.minusMonths(1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ChevronLeft, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier.size(20.dp))
            }
            Text(
                text = displayMonth.month.getDisplayName(TextStyle.FULL, ES_LOCALE)
                    .replaceFirstChar { it.uppercase() } + " ${displayMonth.year}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = { displayMonth = displayMonth.plusMonths(1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Day-of-week header
        Row(Modifier.fillMaxWidth()) {
            DAY_HEADERS.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Day grid — rows of 7
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                val padded = week + List(7 - week.size) { null }
                padded.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val dateStr    = day.format(formatter)
                            val isToday    = day == today
                            val hasBooking = dateStr in bookingDates
                            val isPast     = day < today
                            Box(
                                modifier = Modifier.clickable(enabled = !isPast && onDayClick != null) {
                                    onDayClick?.invoke(day)
                                }
                            ) {
                                CalendarDayCell(day.dayOfMonth, isToday, hasBooking, isPast)
                            }
                        }
                    }
                }
            }
        }

        // Booking count legend
        val monthBookings = bookingDates.count { it.startsWith(displayMonth.toString()) }
        if (monthBookings > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(PiumsOrange))
                Spacer(Modifier.width(5.dp))
                Text(
                    "$monthBookings reserva${if (monthBookings != 1) "s" else ""} este mes",
                    style = MaterialTheme.typography.labelSmall,
                    color = PiumsOrange
                )
            }
        }

        // Next booking card
        if (nextBooking != null) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(0.1f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PiumsOrange.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Brush, null,
                        tint = PiumsOrange, modifier = Modifier.size(16.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        nextBooking.resolvedArtistName ?: nextBooking.code ?: "Reserva confirmada",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    val subtitle = buildString {
                        val dateStr = nextBooking.scheduledDate.take(10)
                        val parsed = runCatching {
                            java.time.LocalDate.parse(dateStr)
                        }.getOrNull()
                        if (parsed != null) {
                            val fmt = java.time.format.DateTimeFormatter.ofPattern(
                                "EEE, d MMM", java.util.Locale("es", "ES")
                            )
                            append(parsed.format(fmt).replaceFirstChar { it.uppercase() })
                        } else {
                            append(dateStr)
                        }
                        nextBooking.scheduledTime?.let { append(" · $it") }
                    }
                    Text(subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = PiumsOrange)
                }
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    hasBooking: Boolean,
    isPast: Boolean
) {
    Column(
        modifier = Modifier.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isToday    -> PiumsOrange
                        hasBooking -> PiumsOrange.copy(0.15f)
                        else       -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$day",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                color = when {
                    isToday -> Color.White
                    isPast  -> MaterialTheme.colorScheme.onSurface.copy(0.3f)
                    else    -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (hasBooking && !isToday) PiumsOrange else Color.Transparent)
        )
    }
}

// ─── Promo Banner ─────────────────────────────────────────────────────────────

@Composable
private fun PromoBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PiumsOrange)
            .heightIn(min = 180.dp)
    ) {
        // Círculos decorativos
        Box(
            Modifier
                .size(160.dp)
                .offset(x = 180.dp, y = (-30).dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.08f))
        )
        Box(
            Modifier
                .size(100.dp)
                .offset(x = 230.dp, y = 30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.06f))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "¿Tienes un evento\nespecial?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 26.sp
            )
            Text(
                "Contrata artistas profesionales\ny haz tu evento único e inolvidable.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(0.85f),
                lineHeight = 18.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                Text(
                    "Explorar artistas",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PiumsOrange
                )
            }
        }
    }
}

// ─── Artist Card ──────────────────────────────────────────────────────────────

private fun specialtyGradient(specialties: List<String>?): Brush {
    val s = specialties?.firstOrNull()?.lowercase() ?: ""
    val colors = when {
        "músic" in s || "music" in s || "cantor" in s || "cantante" in s ->
            listOf(Color(0xFFFF6A00), Color(0xFFF59E0B))
        "dj" in s ->
            listOf(Color(0xFFFF6A00), Color(0xFFE91E8C))
        "fotograf" in s ->
            listOf(Color(0xFF00AEEF), Color(0xFF1E3A8A))
        "video" in s ->
            listOf(Color(0xFF4F46E5), Color(0xFFE91E8C))
        "diseñ" in s || "design" in s ->
            listOf(Color(0xFF00AEEF), Color(0xFF10B981))
        "bail" in s || "danza" in s ->
            listOf(Color(0xFFFF6A00), Color(0xFFEF4444))
        "maquillaj" in s ->
            listOf(Color(0xFFF472B6), Color(0xFF7C2D12))
        "tatua" in s ->
            listOf(Color(0xFF1E40AF), Color(0xFF7C3AED))
        "pintur" in s || "pintor" in s ->
            listOf(Color(0xFF06B6D4), Color(0xFF10B981))
        "mag" in s ->
            listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
        "creador" in s || "contenido" in s ->
            listOf(Color(0xFF06B6D4), Color(0xFF0EA5E9))
        else ->
            listOf(Color(0xFFFF6A00), Color(0xFF00AEEF))
    }
    return Brush.linearGradient(colors)
}

@Composable
private fun ArtistCard(artist: ArtistDto, onClick: () -> Unit = {}) {
    val gradient = specialtyGradient(artist.specialties)
    val initials = artist.displayName.split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")

    // Box externo — permite que el avatar sobresalga del clip de la Card
    Box(Modifier.width(160.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                // Cover — foto si existe, sino gradiente
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    if (artist.coverUrl != null) {
                        AsyncImage(
                            model = artist.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(gradient)) {
                            Text(
                                initials.take(1),
                                fontSize = 70.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(0.12f),
                                modifier = Modifier.align(Alignment.BottomEnd).offset(y = 16.dp)
                            )
                        }
                    }
                    // TOP RATED badge
                    if (artist.rating >= 4.8 && artist.totalReviews > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color.White)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text("TOP RATED", fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF6A00),
                                letterSpacing = 0.5.sp)
                        }
                    }
                }

                // Info section — padding top para el avatar seam
                Column(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        artist.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val specialty = artist.specialties?.firstOrNull()
                    val city = artist.city
                    if (specialty != null || city != null) {
                        Text(
                            listOfNotNull(specialty, city).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (artist.rating > 0.0) {
                            Icon(Icons.Default.Star, null,
                                tint = Color(0xFFF59E0B), modifier = Modifier.size(11.dp))
                            Text(String.format("%.1f", artist.rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.65f))
                            Spacer(Modifier.width(2.dp))
                        }
                        artist.formattedPrice?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PiumsOrange, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Avatar circular en la unión cover/info (seam) — igual que iOS
        Box(
            modifier = Modifier
                .offset(x = 10.dp, y = 102.dp) // centro del avatar a 18dp bajo el cover
                .size(36.dp)
                .clip(CircleShape)
                .background(gradient)
                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artist.avatar != null) {
                AsyncImage(
                    model = artist.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(initials, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ArtistCardSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue  = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    Card(
        modifier = Modifier.width(160.dp).height(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
        )
    ) {}
}
