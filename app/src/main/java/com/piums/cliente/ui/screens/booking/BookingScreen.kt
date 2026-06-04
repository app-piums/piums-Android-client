package com.piums.cliente.ui.screens.booking

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.location.LocationSearchHelper
import com.piums.cliente.data.location.LocationSuggestion
import com.piums.cliente.utils.LocationHelper
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import com.piums.cliente.ui.components.LocationMapPickerSheet
import com.piums.cliente.ui.components.LocationSearchField
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.ui.theme.PiumsSuccess
import com.piums.cliente.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.*

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private val CITY_COORDS = mapOf(
    "Guatemala"           to Pair(14.6349, -90.5069),
    "Ciudad de Guatemala" to Pair(14.6349, -90.5069),
    "Antigua Guatemala"   to Pair(14.5586, -90.7295),
    "Antigua"             to Pair(14.5586, -90.7295),
    "Quetzaltenango"      to Pair(14.8444, -91.5183),
    "Cobán"               to Pair(15.4736, -90.3789),
    "Escuintla"           to Pair(14.3057, -90.7861),
    "Huehuetenango"       to Pair(15.3197, -91.4737),
    "Chiquimula"          to Pair(14.7981, -89.5433),
    "Zacapa"              to Pair(14.9717, -89.5344),
    "Jalapa"              to Pair(14.6339, -89.9881),
    "Jutiapa"             to Pair(14.2934, -89.8964),
    "Santa Rosa"          to Pair(14.2800, -90.2750),
    "Retalhuleu"          to Pair(14.5397, -91.6864),
    "San Marcos"          to Pair(14.9658, -91.7953),
    "Totonicapán"         to Pair(14.9108, -91.3606),
    "Sololá"              to Pair(14.7764, -91.1822),
    "Chimaltenango"       to Pair(14.6631, -90.8197),
    "Sacatepéquez"        to Pair(14.5586, -90.7295),
    "El Progreso"         to Pair(14.9428, -89.8650),
    "Baja Verapaz"        to Pair(15.1136, -90.1822),
    "Alta Verapaz"        to Pair(15.4736, -90.3789),
    "Petén"               to Pair(16.9328, -89.8929),
    "Flores"              to Pair(16.9328, -89.8929),
    "Izabal"              to Pair(15.7356, -88.6014),
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val api: PiumsApiService,
    private val locationSearchHelper: LocationSearchHelper,
    private val locationHelper: LocationHelper,
    private val tokenStorage: com.piums.cliente.data.local.TokenStorage,
    savedState: SavedStateHandle
) : ViewModel() {

    val artistId: String = checkNotNull(savedState["artistId"])

    var step by mutableStateOf(0)
        private set

    // Step 0 — Service
    var artist by mutableStateOf<ArtistDto?>(null)
        private set
    var services by mutableStateOf<List<ArtistServiceDto>>(emptyList())
        private set
    var selectedService by mutableStateOf<ArtistServiceDto?>(null)
        private set
    var isLoadingServices by mutableStateOf(true)
        private set

    // Step 1 — Date + Slots
    var selectedDate by mutableStateOf<LocalDate?>(null)
        private set
    var blockedDates by mutableStateOf<Set<String>>(emptySet())
        private set
    var timeSlots by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedTime by mutableStateOf<String?>(null)
        private set
    var isLoadingSlots by mutableStateOf(false)
        private set
    var currentMonth by mutableStateOf(YearMonth.now())
        private set
    var isMultiDay by mutableStateOf(false)
        private set
    var numDays by mutableStateOf(1)
        private set

    // Step 2 — Details
    var userEvents by mutableStateOf<List<EventDto>>(emptyList())
        private set
    var selectedEventId by mutableStateOf<String?>(null)
        private set
    var selectedEventType by mutableStateOf<EventType?>(null)
        private set
    var couponCode by mutableStateOf("")
        private set
    var isCouponApplied by mutableStateOf(false)
        private set
    var couponDiscount by mutableStateOf(0)
        private set
    var couponError by mutableStateOf<String?>(null)
        private set
    var isValidatingCoupon by mutableStateOf(false)
        private set
    var location by mutableStateOf("")
        private set
    var locationLat by mutableStateOf<Double?>(null)
        private set
    var locationLng by mutableStateOf<Double?>(null)
        private set
    var locationSuggestions by mutableStateOf<List<LocationSuggestion>>(emptyList())
        private set
    var notes by mutableStateOf("")
        private set
    var pricingResult by mutableStateOf<PricingCalculateResponse?>(null)
        private set
    var computedDistanceKm by mutableStateOf<Double?>(null)
        private set
    var isCalculatingPrice by mutableStateOf(false)
        private set
    var isLocatingGPS by mutableStateOf(false)
        private set

    private var locationSearchJob: Job? = null

    // Step 3 — Confirm
    var showConfirmModal by mutableStateOf(false)
        private set
    var isBooking by mutableStateOf(false)
        private set
    var bookingResult by mutableStateOf<BookingDto?>(null)
        private set
    var bookingError by mutableStateOf<String?>(null)
        private set

    // Derived price helpers
    val rawTotal: Int? get() = pricingResult?.totalAmount
        ?: selectedService?.basePrice?.let { it * numDays }

    val finalTotal: Int? get() = rawTotal?.let {
        if (isCouponApplied) maxOf(0, it - couponDiscount) else it
    }

    val formattedFinalTotal: String get() {
        val t = finalTotal ?: return selectedService?.formattedPrice ?: "—"
        return "$${String.format("%,.2f", t / 100.0)}"
    }

    init {
        // Pre-select date if navigated from ArtistSearchByDateScreen
        val preselectedDate = savedState.get<String>("date")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (preselectedDate != null) {
            selectedDate = preselectedDate
            currentMonth = YearMonth.from(preselectedDate)
        }

        // Pre-fill location from ArtistSearchByDateScreen
        val preselectedLat = savedState.get<String>("lat")
            ?.toDoubleOrNull()?.takeIf { it != 0.0 }
        val preselectedLng = savedState.get<String>("lng")
            ?.toDoubleOrNull()?.takeIf { it != 0.0 }
        val preselectedLocation = savedState.get<String>("location")
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
        if (preselectedLat != null && preselectedLng != null) {
            locationLat = preselectedLat
            locationLng = preselectedLng
        }
        if (preselectedLocation != null) {
            location = preselectedLocation
        }

        loadServicesAndArtist()
        viewModelScope.launch {
            userEvents = runCatching { api.getEvents() }.getOrNull()?.list ?: emptyList()
        }
    }

    private fun loadServicesAndArtist() {
        viewModelScope.launch {
            isLoadingServices = true
            try {
                artist   = runCatching { api.getArtist(artistId) }.getOrNull()?.resolved
                services = runCatching { api.getArtistServices(artistId) }.getOrNull()?.list ?: emptyList()
                val cal  = runCatching {
                    api.getAvailabilityCalendar(artistId, currentMonth.year, currentMonth.monthValue)
                }.getOrNull()
                blockedDates = cal?.allBlocked ?: emptySet()
                // If date was pre-selected from search, load time slots immediately
                selectedDate?.let { loadSlots(it) }
            } finally {
                isLoadingServices = false
            }
        }
    }

    fun selectService(svc: ArtistServiceDto) { selectedService = svc }

    fun nextStep() { if (step < 3) step++ }
    fun prevStep() { if (step > 0) step-- }

    fun changeMonth(delta: Int) {
        currentMonth = currentMonth.plusMonths(delta.toLong())
        viewModelScope.launch {
            runCatching {
                val cal = api.getAvailabilityCalendar(artistId, currentMonth.year, currentMonth.monthValue)
                blockedDates = cal.allBlocked
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        selectedTime = null
        loadSlots(date)
    }

    private fun loadSlots(date: LocalDate) {
        viewModelScope.launch {
            isLoadingSlots = true
            val result = runCatching {
                api.getTimeSlots(artistId, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }.getOrNull()
            timeSlots = result?.availableSlots
                ?: result?.slots?.filter { it.available }?.map { it.time }
                ?: emptyList()
            isLoadingSlots = false
        }
    }

    fun selectTime(time: String) { selectedTime = time }

    fun toggleMultiDay() {
        isMultiDay = !isMultiDay
        numDays = if (isMultiDay) maxOf(2, numDays) else 1
    }

    fun onNumDaysChange(v: Int) { numDays = v.coerceIn(if (isMultiDay) 2 else 1, 30) }

    fun onLocationChange(query: String) {
        location = query
        locationLat = null
        locationLng = null
        locationSearchJob?.cancel()
        if (query.length < 3) { locationSuggestions = emptyList(); return }
        locationSearchJob = viewModelScope.launch {
            delay(450L)
            locationSuggestions = locationSearchHelper.search(query)
        }
    }

    fun useMyLocation() {
        viewModelScope.launch {
            isLocatingGPS = true
            val loc = runCatching { locationHelper.getCurrentLocation() }.getOrNull()
                ?: runCatching { locationHelper.getLastKnownLocation() }.getOrNull()
            if (loc != null) {
                val suggestion = locationSearchHelper.reverseGeocode(loc.lat, loc.lng)
                if (suggestion != null) selectLocationSuggestion(suggestion)
            }
            isLocatingGPS = false
        }
    }

    fun selectLocationSuggestion(suggestion: LocationSuggestion) {
        location = suggestion.displayName
        locationLat = suggestion.lat
        locationLng = suggestion.lng
        locationSuggestions = emptyList()
        locationSearchJob?.cancel()
    }

    fun onCouponChange(v: String) {
        couponCode = v.uppercase()
        if (isCouponApplied) { isCouponApplied = false; couponDiscount = 0; couponError = null }
    }

    fun onCouponCodeChange(v: String) = onCouponChange(v)

    fun clearCoupon() {
        couponCode = ""; isCouponApplied = false; couponDiscount = 0; couponError = null
    }

    fun submitCoupon() = validateCoupon()

    fun validateCoupon() {
        val svc  = selectedService ?: return
        val code = couponCode.trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            isValidatingCoupon = true
            couponError = null
            val total = rawTotal ?: (svc.basePrice * numDays)
            val result = runCatching {
                api.validateCoupon(CouponValidateRequest(
                    code         = code,
                    artistId     = artistId,
                    serviceId    = svc.id,
                    bookingTotal = total
                ))
            }.getOrNull()
            if (result != null && result.valid) {
                isCouponApplied  = true
                couponDiscount   = result.discount
            } else {
                isCouponApplied  = false
                couponDiscount   = 0
                couponError      = result?.error ?: "Cupón inválido o no aplicable"
            }
            isValidatingCoupon = false
        }
    }

    fun selectEvent(eventId: String?) { selectedEventId = eventId }
    fun toggleEventType(type: EventType) { selectedEventType = if (selectedEventType == type) null else type }
    fun onNotesChange(v: String) { notes = v }

    fun calculatePrice() {
        val svc  = selectedService ?: return
        val date = selectedDate ?: return
        viewModelScope.launch {
            isCalculatingPrice = true
            val distKm = computeDistanceKm()
            computedDistanceKm = distKm
            pricingResult = runCatching {
                api.calculatePricing(PricingCalculateRequest(
                    serviceId     = svc.id,
                    scheduledDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    duration      = svc.duration,
                    locationLat   = locationLat,
                    locationLng   = locationLng,
                    distanceKm    = distKm,
                    numDays       = numDays
                ))
            }.getOrNull()
            isCalculatingPrice = false
        }
    }

    private fun computeDistanceKm(): Double? {
        val cLat = locationLat ?: return null
        val cLng = locationLng ?: return null
        val aLat = artist?.baseLocationLat
        val aLng = artist?.baseLocationLng
        if (aLat != null && aLng != null) {
            return haversineKm(cLat, cLng, aLat, aLng)
        }
        val cityCoords = artist?.city?.let { CITY_COORDS[it] }
        if (cityCoords != null) {
            return haversineKm(cLat, cLng, cityCoords.first, cityCoords.second)
        }
        return null
    }

    fun openConfirmModal()  { showConfirmModal = true }
    fun closeConfirmModal() { showConfirmModal = false }

    fun confirmBooking() {
        val svc  = selectedService ?: return
        val date = selectedDate ?: return
        viewModelScope.launch {
            isBooking = true
            bookingError = null
            showConfirmModal = false
            val isoDateTime = buildIsoDateTime(date, selectedTime)
            val result = runCatching {
                api.createBooking(CreateBookingRequest(
                    artistId        = artistId,
                    serviceId       = svc.id,
                    clientId        = tokenStorage.userId ?: "",
                    scheduledDate   = isoDateTime,
                    durationMinutes = svc.duration,
                    location        = location.takeIf { it.isNotBlank() },
                    locationLat     = locationLat,
                    locationLng     = locationLng,
                    clientNotes     = notes.takeIf { it.isNotBlank() },
                    numDays         = numDays,
                    eventId         = selectedEventId,
                    couponCode      = couponCode.takeIf { isCouponApplied },
                    eventType       = selectedEventType?.apiValue
                ))
            }
            result.onSuccess { bookingResult = it }
            result.onFailure { e ->
                bookingError = e.toUserMessage()
            }
            isBooking = false
        }
    }

    val canProceedStep0: Boolean get() = selectedService != null
    val canProceedStep1: Boolean get() = selectedDate != null && selectedTime != null
    val canProceedStep2: Boolean get() = true
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun buildIsoDateTime(date: LocalDate, time: String?): String {
    val t = time?.trim() ?: "00:00"
    val parts = t.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return date.atTime(h, m).atZone(java.time.ZoneId.systemDefault())
        .toInstant()
        .toString() // "2026-04-28T16:00:00Z"
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun BookingScreen(
    artistId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onPay: (bookingId: String) -> Unit = {},
    vm: BookingViewModel = hiltViewModel()
) {
    if (vm.bookingResult != null) {
        BookingSuccessScreen(booking = vm.bookingResult!!, artist = vm.artist, onDone = onDone, onPay = onPay)
        return
    }

    Column(Modifier.fillMaxSize()) {
        BookingTopBar(step = vm.step, onBack = { if (vm.step == 0) onBack() else vm.prevStep() })
        StepIndicator(step = vm.step)

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = vm.step,
                transitionSpec = {
                    if (targetState > initialState)
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    else
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                },
                label = "booking_step"
            ) { step ->
                when (step) {
                    0 -> ServiceStep(vm)
                    1 -> DateStep(vm)
                    2 -> DetailsStep(vm)
                    3 -> ConfirmStep(vm)
                    else -> {}
                }
            }
        }

        BookingBottomBar(vm = vm)
    }

    if (vm.showConfirmModal) {
        ConfirmModal(vm = vm)
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun BookingTopBar(step: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = when (step) {
                0 -> "Elige un servicio"
                1 -> "Fecha y hora"
                2 -> "Detalles del evento"
                3 -> "Confirmar reserva"
                else -> "Reservar"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i <= step) PiumsOrange
                        else MaterialTheme.colorScheme.outline.copy(0.2f)
                    )
            )
        }
    }
}

// ─── Bottom bar — always shows running total + CTA ────────────────────────────

@Composable
private fun BookingBottomBar(vm: BookingViewModel) {
    if (vm.step == 3) return   // confirm step handles its own button

    val enabled = when (vm.step) {
        0 -> vm.canProceedStep0
        1 -> vm.canProceedStep1
        else -> vm.canProceedStep2
    }
    val label = if (vm.step == 2) "Revisar reserva" else "Continuar"

    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Price display
            Column {
                Text("Total estimado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vm.isCouponApplied && vm.rawTotal != null) {
                        Text(
                            "$${String.format("%,.2f", vm.rawTotal!! / 100.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                    Text(vm.formattedFinalTotal,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (vm.isCouponApplied) PiumsSuccess else PiumsOrange)
                }
            }

            // CTA button
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (enabled)
                            Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438)))
                        else
                            Brush.linearGradient(listOf(
                                MaterialTheme.colorScheme.outline.copy(0.3f),
                                MaterialTheme.colorScheme.outline.copy(0.3f)
                            ))
                    )
                    .clickable(enabled = enabled) {
                        if (vm.step == 2) vm.calculatePrice()
                        vm.nextStep()
                    }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ─── Step 0: Service ──────────────────────────────────────────────────────────

@Composable
private fun ServiceStep(vm: BookingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        vm.artist?.let { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(PiumsOrange.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(artist.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PiumsOrange)
                }
                Column {
                    Text(artist.displayName, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    artist.city?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }
        }

        Text("¿Qué servicio necesitas?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp))

        if (vm.isLoadingServices) {
            repeat(3) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                )
            }
        } else {
            vm.services.forEach { svc ->
                ServiceCard(svc = svc, selected = svc == vm.selectedService,
                    onClick = { vm.selectService(svc) })
            }
        }
    }
}

@Composable
private fun ServiceCard(svc: ArtistServiceDto, selected: Boolean, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PiumsOrange.copy(0.09f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, if (selected) PiumsOrange else MaterialTheme.colorScheme.outline.copy(0.15f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(svc.name, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                svc.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f), maxLines = 2)
                }
                svc.durationMin?.let {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.AccessTime, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                            modifier = Modifier.size(11.dp))
                        Text("$it min", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(svc.formattedPrice, fontWeight = FontWeight.Bold,
                    color = PiumsOrange, style = MaterialTheme.typography.bodyMedium)
                if (selected) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = PiumsOrange, modifier = Modifier.size(18.dp))
                }
            }
        }

        // "Qué incluye" expandable
        if (!svc.whatIsIncluded.isNullOrEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Qué incluye",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PiumsOrange)
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = PiumsOrange, modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    svc.whatIsIncluded.forEach { item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = PiumsSuccess, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                            Text(item, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.75f))
                        }
                    }
                }
            }
        }
    }
}

// ─── Step 1: Date + Slots ─────────────────────────────────────────────────────

@Composable
private fun DateStep(vm: BookingViewModel) {
    val today  = remember { LocalDate.now() }
    val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month navigation
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.changeMonth(-1) },
                enabled = vm.currentMonth > YearMonth.from(today)) {
                Icon(Icons.Default.ChevronLeft, null)
            }
            Text(
                vm.currentMonth.month.getDisplayName(TextStyle.FULL, Locale("es"))
                    .replaceFirstChar { it.uppercase() } + " ${vm.currentMonth.year}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { vm.changeMonth(1) }) {
                Icon(Icons.Default.ChevronRight, null)
            }
        }

        // Day headers
        Row(Modifier.fillMaxWidth()) {
            listOf("Do", "Lu", "Ma", "Mi", "Ju", "Vi", "Sá").forEach { day ->
                Text(day, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.4f))
            }
        }

        // Calendar grid
        val days = (1..vm.currentMonth.lengthOfMonth()).map { vm.currentMonth.atDay(it) }
        val firstDayOffset = (vm.currentMonth.atDay(1).dayOfWeek.value % 7)
        val cells = List(firstDayOffset) { null } + days
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val dateStr    = day.format(isoFmt)
                        val isBlocked  = dateStr in vm.blockedDates
                        val isPast     = day < today
                        val isSelected = day == vm.selectedDate
                        val isDisabled = isBlocked || isPast

                        Box(
                            modifier = Modifier
                                .weight(1f).aspectRatio(1f).padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(when {
                                    isSelected -> PiumsOrange
                                    isBlocked  -> MaterialTheme.colorScheme.error.copy(0.10f)
                                    else       -> Color.Transparent
                                })
                                .clickable(enabled = !isDisabled) { vm.selectDate(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${day.dayOfMonth}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> Color.White
                                    isDisabled -> MaterialTheme.colorScheme.onBackground.copy(0.25f)
                                    else       -> MaterialTheme.colorScheme.onBackground
                                })
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // Time slots
        if (vm.selectedDate != null) {
            Text("Hora disponible", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)

            if (vm.isLoadingSlots) {
                CircularProgressIndicator(color = PiumsOrange,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(24.dp),
                    strokeWidth = 2.dp)
            } else if (vm.timeSlots.isEmpty()) {
                Text("No hay horarios disponibles para esta fecha.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.timeSlots) { slot ->
                        val sel = slot == vm.selectedTime
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp,
                                    if (sel) Color.Transparent else MaterialTheme.colorScheme.outline.copy(0.2f),
                                    RoundedCornerShape(10.dp))
                                .clickable { vm.selectTime(slot) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(slot,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // Multi-day toggle
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Evento multi-día",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Para eventos de 2 días o más",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
            Switch(
                checked = vm.isMultiDay,
                onCheckedChange = { vm.toggleMultiDay() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PiumsOrange)
            )
        }

        AnimatedVisibility(visible = vm.isMultiDay) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PiumsOrange.copy(0.07f))
                    .border(1.dp, PiumsOrange.copy(0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Número de días",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { vm.onNumDaysChange(vm.numDays - 1) },
                        enabled = vm.numDays > 2) {
                        Icon(Icons.Default.Remove, null,
                            tint = if (vm.numDays > 2) PiumsOrange else MaterialTheme.colorScheme.outline)
                    }
                    Text("${vm.numDays}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PiumsOrange)
                    IconButton(onClick = { vm.onNumDaysChange(vm.numDays + 1) }) {
                        Icon(Icons.Default.Add, null, tint = PiumsOrange)
                    }
                }
            }
        }
    }
}

// ─── Step 2: Details ──────────────────────────────────────────────────────────

@Composable
private fun DetailsStep(vm: BookingViewModel) {
    var showMapPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (vm.location.isEmpty() && vm.locationLat == null && !vm.isLocatingGPS) vm.useMyLocation()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Location
        LocationSearchField(
            value                = vm.location,
            onValueChange        = vm::onLocationChange,
            suggestions          = vm.locationSuggestions,
            onSuggestionSelected = vm::selectLocationSuggestion,
            hasCoordinates       = vm.locationLat != null,
            isLocatingGPS        = vm.isLocatingGPS,
            onUseMyLocation      = vm::useMyLocation
        )

        // Map picker
        OutlinedButton(
            onClick  = { showMapPicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            border   = androidx.compose.foundation.BorderStroke(1.dp, PiumsOrange.copy(0.5f))
        ) {
            Icon(Icons.Default.Map, null, tint = PiumsOrange, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Ubicar en mapa", color = PiumsOrange)
        }

        // Notes
        OutlinedTextField(
            value         = vm.notes,
            onValueChange = vm::onNotesChange,
            label         = { Text("Notas para el artista") },
            placeholder   = { Text("Temática, requerimientos especiales...") },
            modifier      = Modifier.fillMaxWidth().height(100.dp),
            shape         = RoundedCornerShape(12.dp),
            maxLines      = 4,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = PiumsOrange,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
            )
        )

        // Coupon code + Apply
        CouponField(vm = vm)

        // Event type picker
        EventTypePickerSection(
            selected = vm.selectedEventType,
            onToggle = vm::toggleEventType
        )

        // Event picker
        if (vm.userEvents.isNotEmpty()) {
            EventPickerRow(
                events          = vm.userEvents,
                selectedEventId = vm.selectedEventId,
                onSelect        = vm::selectEvent
            )
        }
    }

    if (showMapPicker) {
        LocationMapPickerSheet(
            initialLat = vm.locationLat ?: 14.6349,
            initialLng = vm.locationLng ?: -90.5069,
            onDismiss  = { showMapPicker = false },
            onConfirm  = { suggestion ->
                vm.selectLocationSuggestion(suggestion)
                showMapPicker = false
            }
        )
    }
}

private fun eventTypeIcon(type: EventType) = when (type) {
    EventType.CUMPLEANOS  -> Icons.Default.Cake
    EventType.BODA        -> Icons.Default.Favorite
    EventType.GRADUACION  -> Icons.Default.School
    EventType.QUINCEANERA -> Icons.Default.AutoAwesome
    EventType.CORPORATIVO -> Icons.Default.Business
    EventType.CONCIERTO   -> Icons.Default.MusicNote
    EventType.FIESTA      -> Icons.Default.Celebration
    EventType.BABY_SHOWER -> Icons.Default.ChildCare
    EventType.BAUTIZO     -> Icons.Default.WaterDrop
    EventType.ANIVERSARIO -> Icons.Default.WineBar
    EventType.OTRO        -> Icons.Default.HelpOutline
}

@Composable
private fun EventTypePickerSection(
    selected: EventType?,
    onToggle: (EventType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("¿Para qué es el evento?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("(Opcional)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
        val columns = 3
        val types = EventType.entries
        val rows = (types.size + columns - 1) / columns
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(rows) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until columns) {
                        val idx = rowIndex * columns + col
                        if (idx < types.size) {
                            val type = types[idx]
                            val isSelected = selected == type
                            Surface(
                                onClick = { onToggle(type) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = eventTypeIcon(type),
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else PiumsOrange,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = type.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CouponField(vm: BookingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value         = vm.couponCode,
                onValueChange = vm::onCouponChange,
                label         = { Text("Código de cupón (opcional)") },
                placeholder   = { Text("ej. PIUMS20") },
                leadingIcon   = {
                    Icon(Icons.Default.LocalOffer, null,
                        tint = if (vm.isCouponApplied) PiumsSuccess else PiumsOrange)
                },
                trailingIcon  = if (vm.couponCode.isNotEmpty()) ({
                    IconButton(onClick = vm::clearCoupon) {
                        Icon(Icons.Default.Close, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }) else null,
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = if (vm.isCouponApplied) PiumsSuccess else PiumsOrange,
                    unfocusedBorderColor = if (vm.isCouponApplied) PiumsSuccess.copy(0.5f)
                                          else MaterialTheme.colorScheme.outline.copy(0.3f)
                )
            )

            Box(
                modifier = Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            vm.isCouponApplied  -> PiumsSuccess.copy(0.15f)
                            vm.couponCode.isNotBlank() -> PiumsOrange
                            else -> MaterialTheme.colorScheme.outline.copy(0.2f)
                        }
                    )
                    .clickable(
                        enabled = vm.couponCode.isNotBlank() && !vm.isCouponApplied && !vm.isValidatingCoupon
                    ) { vm.validateCoupon() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    vm.isValidatingCoupon -> CircularProgressIndicator(
                        color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    vm.isCouponApplied -> Icon(Icons.Default.CheckCircle, null,
                        tint = PiumsSuccess, modifier = Modifier.size(22.dp))
                    else -> Text("Aplicar", color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        when {
            vm.isCouponApplied ->
                Text("✓ Cupón aplicado · -$${String.format("%,.2f", vm.couponDiscount / 100.0)}",
                    color = PiumsSuccess,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
            vm.couponError != null ->
                Text(vm.couponError!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ─── Step 3: Confirm ──────────────────────────────────────────────────────────

@Composable
private fun ConfirmStep(vm: BookingViewModel) {
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale("es", "ES"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Título ──
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Resumen de Reserva",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Revisa los detalles antes de confirmar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
        }

        // ── Artist card ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PiumsOrange.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (vm.artist?.displayName?.take(2) ?: "?").uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PiumsOrange
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    vm.artist?.displayName ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    vm.selectedService?.name ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                )
            }
        }

        // ── Detail rows with icons ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            vm.selectedDate?.let { date ->
                ReviewRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Fecha",
                    value = date.format(dateFmt).replaceFirstChar { it.uppercase() }
                )
            }
            vm.selectedTime?.let { time ->
                HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                ReviewRow(icon = Icons.Default.Schedule, label = "Hora", value = time)
            }
            if (vm.isMultiDay) {
                HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                ReviewRow(icon = Icons.Default.DateRange, label = "Días", value = "${vm.numDays} días")
            }
            if (vm.location.isNotBlank()) {
                HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                ReviewRow(icon = Icons.Default.LocationOn, label = "Lugar", value = vm.location)
            }
            if (vm.notes.isNotBlank()) {
                HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                ReviewRow(icon = Icons.Default.Notes, label = "Notas", value = vm.notes)
            }
        }

        // ── Location nudge ──
        if (vm.locationLat == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PiumsOrange.copy(0.08f))
                    .border(1.dp, PiumsOrange.copy(0.25f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, null, tint = PiumsOrange, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Agrega tu ubicación",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("Necesaria para calcular el costo de traslado del artista.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                        lineHeight = 15.sp)
                }
                if (vm.isLocatingGPS) {
                    CircularProgressIndicator(color = PiumsOrange, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { vm.useMyLocation(); vm.calculatePrice() })
                }
            }
        }

        // ── Desglose de Precio ──
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Desglose de Precio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PiumsOrange.copy(0.07f))
                    .border(1.dp, PiumsOrange.copy(0.2f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                if (vm.isCalculatingPrice) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = PiumsOrange,
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Calculando precio...", color = PiumsOrange,
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val items = vm.pricingResult?.items
                        if (!items.isNullOrEmpty()) {
                            // Item-by-item from backend (excludes DISCOUNT items — shown separately)
                            items.filter { it.type != "DISCOUNT" }.forEach { item ->
                                val isTravel = item.type == "TRAVEL"
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isTravel) {
                                            Icon(Icons.Default.DirectionsCar, null,
                                                tint = PiumsOrange, modifier = Modifier.size(16.dp))
                                        }
                                        Text(item.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isTravel) PiumsOrange
                                                    else MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                            modifier = Modifier.weight(1f))
                                    }
                                    Text(
                                        "$${String.format("%,.2f", item.totalPriceCents / 100.0)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isTravel) PiumsOrange
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            // Fallback to breakdown summary
                            val breakdown = vm.pricingResult?.breakdown
                            breakdown?.baseCents?.let { base ->
                                PriceRow(vm.selectedService?.name ?: "Servicio",
                                    "$${String.format("%,.2f", base / 100.0)}")
                            }
                            breakdown?.travelCents?.takeIf { it > 0 }?.let { travel ->
                                val dist = vm.computedDistanceKm
                                val label = if (dist != null) {
                                    val included = 10.0
                                    val extra = maxOf(0.0, dist - included)
                                    "Desplazamiento (%.1f km adicionales)".format(extra)
                                } else "Traslado"
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.DirectionsCar, null,
                                            tint = PiumsOrange, modifier = Modifier.size(16.dp))
                                        Text(label, style = MaterialTheme.typography.bodySmall,
                                            color = PiumsOrange, modifier = Modifier.weight(1f))
                                    }
                                    Text("$${String.format("%,.2f", travel / 100.0)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium, color = PiumsOrange)
                                }
                            }
                        }

                        if (vm.isCouponApplied && vm.couponDiscount > 0) {
                            PriceRow("Descuento cupón",
                                "-$${String.format("%,.2f", vm.couponDiscount / 100.0)}",
                                valueColor = PiumsSuccess)
                        }

                        HorizontalDivider(color = PiumsOrange.copy(0.2f))

                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge)
                            Column(horizontalAlignment = Alignment.End) {
                                if (vm.isCouponApplied && vm.rawTotal != null) {
                                    Text("$${String.format("%,.2f", vm.rawTotal!! / 100.0)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                        textDecoration = TextDecoration.LineThrough)
                                }
                                Text(vm.formattedFinalTotal,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (vm.isCouponApplied) PiumsSuccess else PiumsOrange)
                            }
                        }
                    }
                }
            }
        }

        // ── Viáticos info banner ──
        val hasTravel = vm.pricingResult?.items?.any { it.type == "TRAVEL" } == true ||
            (vm.pricingResult?.breakdown?.travelCents ?: 0) > 0
        if (hasTravel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Info, null,
                    tint = PiumsOrange, modifier = Modifier.size(18.dp))
                Text(
                    "Los viáticos cubren transporte, alimentación y hospedaje del artista.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                    lineHeight = 16.sp
                )
            }
        }

        // ── Coupon section ──
        CouponSection(vm = vm)

        vm.bookingError?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        // ── Confirm button ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438))))
                .clickable(enabled = !vm.isBooking, onClick = vm::openConfirmModal),
            contentAlignment = Alignment.Center
        ) {
            if (vm.isBooking) {
                CircularProgressIndicator(color = Color.White,
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Confirmar reserva", color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Text(
            "Al confirmar, el artista recibirá tu solicitud y deberá aceptarla antes de que sea oficial.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.45f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ReviewRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PiumsOrange, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.width(60.dp))
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f))
    }
}

@Composable
private fun CouponSection(vm: BookingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("¿Tienes un cupón?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocalOffer, null, tint = PiumsOrange, modifier = Modifier.size(16.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = vm.couponCode,
                    onValueChange = vm::onCouponCodeChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { inner ->
                        if (vm.couponCode.isEmpty()) {
                            Text("Código de cupón",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (vm.isCouponApplied) {
                IconButton(onClick = vm::clearCoupon, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Cancel, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            } else {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (vm.couponCode.isBlank()) MaterialTheme.colorScheme.outline.copy(0.2f)
                            else PiumsOrange
                        )
                        .clickable(enabled = vm.couponCode.isNotBlank() && !vm.isValidatingCoupon,
                            onClick = vm::submitCoupon)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (vm.isValidatingCoupon) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Aplicar", color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (vm.isCouponApplied && vm.couponDiscount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PiumsSuccess.copy(0.08f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = PiumsSuccess, modifier = Modifier.size(16.dp))
                Text("Cupón aplicado",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PiumsSuccess,
                    modifier = Modifier.weight(1f))
                Text("-$${String.format("%,.2f", vm.couponDiscount / 100.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = PiumsSuccess)
            }
        }

        vm.couponError?.let { err ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Error, null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                Text(err, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, color = valueColor)
    }
}

// ─── Confirmation Modal ───────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ConfirmModal(vm: BookingViewModel) {
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = vm::closeConfirmModal,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle       = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle bar
            Box(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                Alignment.Center
            ) {
                Box(
                    Modifier.size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(2.dp))
                )
            }

            // Title + orange underline
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Confirmar Reserva",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    Modifier.width(40.dp).height(3.dp)
                        .background(PiumsOrange, RoundedCornerShape(2.dp))
                )
            }

            // Artist card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PiumsOrange.copy(0.06f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PiumsOrange.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (vm.artist?.displayName?.take(2) ?: "?").uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PiumsOrange
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "ARTISTA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        vm.artist?.displayName ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    vm.artist?.specialties?.firstOrNull()?.let {
                        Text(it.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }

            // Detail rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            ) {
                ConfirmRow("sparkles", "Servicio", vm.selectedService?.name ?: "—")
                HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                ConfirmRow("calendar_today", "Fecha",
                    vm.selectedDate?.format(dateFmt)?.replaceFirstChar { it.uppercase() } ?: "—")
                HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                ConfirmRow("schedule", "Hora", vm.selectedTime ?: "—")
                if (vm.isMultiDay) {
                    HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                    ConfirmRow("event_repeat", "Días", "${vm.numDays} días")
                }
                HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))
                // Total row — highlighted
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(vm.formattedFinalTotal, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = PiumsOrange)
                }
            }

            // Legal text
            Text(
                "Al confirmar, aceptas nuestras políticas de cancelación y términos de servicio. La solicitud será enviada al artista.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )

            // Confirm button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438))))
                    .clickable(enabled = !vm.isBooking, onClick = vm::confirmBooking),
                contentAlignment = Alignment.Center
            ) {
                if (vm.isBooking) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sí, confirmar", color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Cancel link
            TextButton(
                onClick = vm::closeConfirmModal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar", color = PiumsOrange, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConfirmRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (icon) {
                "sparkles" -> Icons.Default.AutoAwesome
                "calendar_today" -> Icons.Default.CalendarToday
                "schedule" -> Icons.Default.Schedule
                "event_repeat" -> Icons.Default.EventRepeat
                else -> Icons.Default.Info
            },
            contentDescription = null,
            tint = PiumsOrange,
            modifier = Modifier.size(18.dp)
        )
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
    }
}

// ─── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false))
    }
}

// ─── Event picker row ─────────────────────────────────────────────────────────

@Composable
private fun EventPickerRow(
    events: List<EventDto>,
    selectedEventId: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = events.firstOrNull { it.id == selectedEventId }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Event, null, tint = PiumsOrange, modifier = Modifier.size(18.dp))
                Column {
                    Text("Vincular a evento",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    Text(selected?.name ?: "Sin evento",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Icon(Icons.Default.ArrowDropDown, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sin evento", color = MaterialTheme.colorScheme.onSurface.copy(0.6f)) },
                onClick = { onSelect(null); expanded = false }
            )
            events.forEach { event ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(event.name, style = MaterialTheme.typography.bodyMedium)
                            event.eventDate?.take(10)?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = PiumsOrange)
                            }
                        }
                    },
                    onClick = { onSelect(event.id); expanded = false },
                    leadingIcon = if (event.id == selectedEventId) ({
                        Icon(Icons.Default.CheckCircle, null, tint = PiumsOrange,
                            modifier = Modifier.size(16.dp))
                    }) else null
                )
            }
        }
    }
}

// ─── Success ──────────────────────────────────────────────────────────────────

@Composable
private fun BookingSuccessScreen(
    booking: BookingDto,
    artist: ArtistDto?,
    onDone: () -> Unit,
    onPay: (String) -> Unit = {}
) {
    val dateFmt = DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", Locale("es", "ES"))
    val dateLabel = runCatching {
        LocalDate.parse(booking.scheduledDate.take(10)).format(dateFmt)
    }.getOrElse { booking.scheduledDate.take(10) }

    val formattedTime = booking.scheduledTime?.let { t ->
        val parts = t.trim().split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size >= 2) {
            val h = parts[0]; val m = parts[1]
            val suffix = if (h >= 12) "PM" else "AM"
            val h12 = when { h > 12 -> h - 12; h == 0 -> 12; else -> h }
            "%d:%02d %s".format(h12, m, suffix)
        } else t
    }

    // iOS: paymentPending = paymentStatus == "PENDING"
    val paymentPending = booking.paymentStatus == "PENDING"

    val anticipoRequired = booking.anticipoRequired == true
    val anticipoCents = if (anticipoRequired) (booking.anticipoAmount ?: booking.totalPrice) else booking.totalPrice
    val anticipoFormatted = "$${String.format("%,.2f", anticipoCents / 100.0)}"

    val artistDisplayName = booking.resolvedArtistName ?: artist?.name ?: "—"
    val initials = artistDisplayName
        .split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "??" }
    val specialty = artist?.specialties?.firstOrNull() ?: booking.serviceName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar — "Cerrar" pill (iOS: toolbar leading button)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, PiumsOrange.copy(0.5f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Cerrar", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = PiumsOrange)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Check icon — iOS: Circle(green.0.15, 88) + checkmark.circle.fill(size 50, green)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(PiumsSuccess.copy(alpha = 0.15f)))
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = PiumsSuccess, modifier = Modifier.size(50.dp))
        }

        Spacer(Modifier.height(14.dp))

        Text("¡Reserva Confirmada!", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tu reserva ha sido creada exitosamente. El profesional revisará tu solicitud en breve.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
            textAlign = TextAlign.Center, lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(28.dp))

        // PaymentCtaCard — shown when paymentStatus == PENDING (iOS: paymentPending)
        if (paymentPending) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, PiumsOrange.copy(0.3f), RoundedCornerShape(16.dp))
                    .clickable { onPay(booking.id) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PiumsOrange.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CreditCard, null, tint = PiumsOrange,
                        modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (anticipoRequired) "Pagar anticipo" else "Confirmar pago",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("$anticipoFormatted · Seguro con Tilopay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                }
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.35f))
            }
            Spacer(Modifier.height(28.dp))
        }

        // Código de reserva — iOS: orange.opacity(0.08) bg + orange.opacity(0.2) border
        booking.code?.let { code ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PiumsOrange.copy(0.08f))
                    .border(1.dp, PiumsOrange.copy(0.2f), RoundedCornerShape(16.dp))
                    .padding(vertical = 20.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("CÓDIGO DE RESERVA", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f), letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(code, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(28.dp))
        }

        // Detalles del Servicio card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Detalles del Servicio", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.08f))

            // Artist row — iOS: orange.opacity(0.15) bg + orange text initials
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PiumsOrange.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = PiumsOrange)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(artistDisplayName, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    specialty?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    }
                }
            }

            // Info grid 2×2 — iOS: LazyVGrid(2 flexible columns)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // INFORMACIÓN DEL EVENTO
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("INFORMACIÓN DEL EVENTO",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f), letterSpacing = 0.8.sp)
                    Text(dateLabel, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    formattedTime?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    }
                }
                // UBICACIÓN
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("UBICACIÓN",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f), letterSpacing = 0.8.sp)
                    Text(booking.location ?: "No especificada",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp)
                    Text("Modalidad Presencial", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // ESTADO
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ESTADO",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f), letterSpacing = 0.8.sp)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(PiumsOrange))
                        Text(booking.statusEnum.displayName.uppercase(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                // RESUMEN DE PAGO
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RESUMEN DE PAGO",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f), letterSpacing = 0.8.sp)
                    if (anticipoRequired && booking.anticipoAmount != null) {
                        Text(anticipoFormatted, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = PiumsOrange)
                        Text("Anticipo · Total ${booking.formattedTotal}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    } else {
                        Text(booking.formattedTotal, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = PiumsOrange)
                        val discountInfo = if ((booking.discountAmount ?: 0) > 0 && booking.couponCode != null) {
                            "${booking.couponCode} · -$${String.format("%,.2f", (booking.discountAmount ?: 0) / 100.0)}"
                        } else null
                        Text(discountInfo ?: "USD Total",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (discountInfo != null) PiumsSuccess
                                    else MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Próximos Pasos card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Próximos Pasos", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            val cardAuthorized = booking.paymentStatus == "CARD_AUTHORIZED"
            val steps: List<Pair<Boolean, String>> = if (paymentPending) listOf(
                Pair(true,  "Completa el pago del anticipo para confirmar tu reserva."),
                Pair(false, "$artistDisplayName será notificado cuando el pago sea recibido."),
                Pair(false, "El saldo restante se cobra automáticamente 72h antes del evento.")
            ) else if (cardAuthorized) listOf(
                Pair(true,  "Tu tarjeta fue pre-autorizada exitosamente. El cobro se realizará cuando el artista confirme."),
                Pair(false, "$artistDisplayName revisará tu solicitud y confirmará en las próximas horas."),
                Pair(false, "Si no confirma, la retención en tu tarjeta se libera automáticamente.")
            ) else listOf(
                Pair(true,  "$artistDisplayName revisará tu solicitud de reserva en las próximas 24 horas."),
                Pair(false, "Recibirás una notificación por correo una vez sea confirmada."),
                Pair(false, "Podrás chatear con el profesional directamente desde tu panel.")
            )

            steps.forEachIndexed { idx, (isActive, text) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isActive) PiumsOrange else PiumsOrange.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.White else PiumsOrange)
                    }
                    Text(text, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        modifier = Modifier.weight(1f), lineHeight = 18.sp)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Botones — iOS logic: "Pagar ahora" if pending, then "Ver Mis Reservas" (orange solid if no pending)
        if (paymentPending) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PiumsOrange)
                    .clickable { onPay(booking.id) },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("Pagar ahora", color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Ver Mis Reservas — orange text on subtle bg when payment pending, solid orange when not
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (paymentPending) PiumsOrange.copy(0.08f) else PiumsOrange)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Text("Ver Mis Reservas", fontWeight = FontWeight.Bold,
                color = if (paymentPending) PiumsOrange else Color.White,
                style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(10.dp))

        // Ir al Dashboard
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Text("Ir al Dashboard", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(40.dp))
    }
}
