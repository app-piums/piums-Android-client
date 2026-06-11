package com.piums.cliente.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.ArtistDto
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.*

// ─── Domain ───────────────────────────────────────────────────────────────────

data class ArtistWithAvailability(
    val artist: ArtistDto,
    val available: Boolean,
    val distanceKm: Double?,
    val mainServicePrice: Int?,
    val mainServiceName: String?
)

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
    "Quetzaltenango"      to Pair(14.8444, -91.5183),
    "Cobán"               to Pair(15.4736, -90.3789),
    "Escuintla"           to Pair(14.3057, -90.7861),
    "Huehuetenango"       to Pair(15.3197, -91.4737),
    "Flores"              to Pair(16.9328, -89.8929),
    "Chiquimula"          to Pair(14.7981, -89.5433),
)

enum class ByDateSortOption(val label: String) {
    RELEVANCE( "Relevancia"),
    RATING_DESC("Mejor calificados"),
    PRICE_ASC( "Precio: menor a mayor"),
    PRICE_DESC("Precio: mayor a menor"),
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class ArtistSearchByDateViewModel @Inject constructor(
    private val api: PiumsApiService,
    private val locationHelper: LocationHelper,
    private val locationSearchHelper: com.piums.cliente.data.location.LocationSearchHelper
) : ViewModel() {

    var artists by mutableStateOf<List<ArtistWithAvailability>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var userLat by mutableStateOf<Double?>(null)
        private set
    var userLng by mutableStateOf<Double?>(null)
        private set
    var locationLabel by mutableStateOf("Toca para usar tu ubicación")
        private set
    var isLocating by mutableStateOf(false)
        private set
    var selectedSpecialty by mutableStateOf<String?>(null)
    var showOnlyAvailable by mutableStateOf(true)
    var minPrice by mutableStateOf(0f)
    var maxPrice by mutableStateOf(50000f)
    var minRating by mutableStateOf(0.0)
    var selectedCity by mutableStateOf<String?>(null)
    var isVerified by mutableStateOf(false)
    var sortOption by mutableStateOf(ByDateSortOption.RELEVANCE)

    val hasActiveFilters: Boolean
        get() = selectedSpecialty != null || minPrice > 0 || maxPrice < 50000 ||
                minRating > 0 || selectedCity != null || isVerified ||
                sortOption != ByDateSortOption.RELEVANCE || !showOnlyAvailable

    fun clearFilters() {
        selectedSpecialty = null
        showOnlyAvailable = true
        minPrice = 0f; maxPrice = 50000f
        minRating = 0.0; selectedCity = null
        isVerified = false; sortOption = ByDateSortOption.RELEVANCE
    }

    var citySuggestions by mutableStateOf<List<com.piums.cliente.data.location.LocationSuggestion>>(emptyList())
        private set

    fun searchCity(query: String) {
        if (query.length < 2) { citySuggestions = emptyList(); return }
        viewModelScope.launch {
            citySuggestions = runCatching { locationSearchHelper.search(query) }.getOrDefault(emptyList())
        }
    }

    fun clearCitySuggestions() { citySuggestions = emptyList() }

    val displayed: List<ArtistWithAvailability>
        get() {
            var result = artists.filter { item ->
                val hasServices = item.artist.servicesCount > 0
                    || item.artist.serviceIds?.isNotEmpty() == true
                    || item.artist.serviceTitles?.isNotEmpty() == true
                if (!hasServices) return@filter false
                if (showOnlyAvailable && !item.available) return@filter false
                if (selectedSpecialty != null) {
                    val specs = item.artist.specialties?.joinToString(" ")?.lowercase() ?: ""
                    if (!specs.contains(selectedSpecialty!!.lowercase())) return@filter false
                }
                // El rango de precio ya no excluye: ordena por cercanía al presupuesto (abajo)
                if (minRating > 0) {
                    val rating = item.artist.averageRating ?: return@filter false
                    if (rating < minRating) return@filter false
                }
                if (selectedCity != null) {
                    val city = (item.artist.city ?: "").lowercase()
                    if (!city.contains(selectedCity!!.lowercase())) return@filter false
                }
                if (isVerified && !item.artist.isVerified) return@filter false
                true
            }
            result = when (sortOption) {
                ByDateSortOption.RATING_DESC  -> result.sortedByDescending { it.artist.averageRating ?: 0.0 }
                ByDateSortOption.PRICE_ASC    -> result.sortedBy { it.mainServicePrice ?: Int.MAX_VALUE }
                ByDateSortOption.PRICE_DESC   -> result.sortedByDescending { it.mainServicePrice ?: 0 }
                ByDateSortOption.RELEVANCE    -> {
                    // Con rango de precio activo: dentro del rango primero (más
                    // cercano al máximo), luego fuera por distancia, sin precio al final.
                    if (minPrice > 0 || maxPrice < 50000) {
                        val lo = minPrice.toInt()
                        val hi = if (maxPrice < 50000) maxPrice.toInt() else Int.MAX_VALUE
                        fun tier(p: Int?) = when { p == null -> 2; p in lo..hi -> 0; else -> 1 }
                        fun key(p: Int?): Int = when {
                            p == null -> 0
                            p in lo..hi -> if (hi == Int.MAX_VALUE) p - lo else -p
                            p > hi -> p - hi
                            else -> lo - p
                        }
                        result.sortedWith(compareBy({ tier(it.mainServicePrice) }, { key(it.mainServicePrice) }))
                    } else result
                }
            }
            return result
        }

    fun load(date: LocalDate, specialty: String? = selectedSpecialty) {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val year = date.year
                val month = date.monthValue

                // 1. Load artists (filtered by specialty when selected)
                val searchRes = api.searchArtists(page = 1, limit = 60, specialty = specialty)
                val rawArtists = searchRes.list

                // 2. Batch load calendars in parallel
                val calendarResults = rawArtists.map { artist ->
                    async {
                        val cal = runCatching {
                            api.getAvailabilityCalendar(
                                artistId = artist.resolvedId, year = year, month = month
                            )
                        }.getOrNull()
                        Pair(artist.resolvedId, cal)
                    }
                }.awaitAll().toMap()

                // 3. Enrich with availability + distance
                val enriched = rawArtists.map { artist ->
                    val cal = calendarResults[artist.resolvedId]
                    val available = if (cal != null) dateStr !in cal.allBlocked else true
                    val distance = computeDistance(artist)
                    ArtistWithAvailability(
                        artist = artist,
                        available = available,
                        distanceKm = distance,
                        mainServicePrice = artist.mainServicePrice,
                        mainServiceName = artist.mainServiceName
                    )
                }

                artists = sortArtists(enriched)
            } catch (e: Exception) {
                error = "Error al cargar artistas"
            }
            isLoading = false
        }
    }

    fun setLocation(lat: Double, lng: Double, label: String = "Ubicación del evento") {
        userLat = lat
        userLng = lng
        locationLabel = label
        if (artists.isNotEmpty()) {
            artists = sortArtists(artists.map { it.copy(distanceKm = computeDistance(it.artist)) })
        }
    }

    fun fetchLocation() {
        viewModelScope.launch {
            isLocating = true
            val loc = runCatching { locationHelper.getCurrentLocation() }.getOrNull()
                ?: runCatching { locationHelper.getLastKnownLocation() }.getOrNull()
            if (loc != null) {
                userLat = loc.lat
                userLng = loc.lng
                locationLabel = "%.4f, %.4f".format(loc.lat, loc.lng)
                artists = sortArtists(artists.map { item ->
                    item.copy(distanceKm = computeDistance(item.artist))
                })
            }
            isLocating = false
        }
    }

    private fun computeDistance(artist: ArtistDto): Double? {
        val lat = userLat ?: return null
        val lng = userLng ?: return null
        if (artist.baseLocationLat != null && artist.baseLocationLng != null) {
            return haversineKm(lat, lng, artist.baseLocationLat, artist.baseLocationLng)
        }
        val cityCoords = CITY_COORDS[artist.city] ?: return null
        return haversineKm(lat, lng, cityCoords.first, cityCoords.second)
    }

    private fun sortArtists(list: List<ArtistWithAvailability>) = list.sortedWith(
        compareByDescending<ArtistWithAvailability> { it.available }
            .thenBy { it.distanceKm ?: Double.MAX_VALUE }
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun ArtistSearchByDateScreen(
    onBack: () -> Unit,
    onArtistClick: (artistId: String, date: String, lat: Double?, lng: Double?, locationLabel: String) -> Unit,
    initialDate: LocalDate? = null,
    initialLat: Double? = null,
    initialLng: Double? = null,
    initialLocation: String? = null,
    vm: ArtistSearchByDateViewModel = hiltViewModel()
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(initialDate?.takeIf { !it.isBefore(today) } ?: today) }
    val dateStrip = remember { (0 until 30).map { today.plusDays(it.toLong()) } }
    var showFiltersSheet by remember { mutableStateOf(false) }

    // Pre-set location from picker
    LaunchedEffect(Unit) {
        if (initialLat != null && initialLng != null) {
            vm.setLocation(initialLat, initialLng, initialLocation ?: "Ubicación del evento")
        }
    }

    // Reload when date or specialty changes
    LaunchedEffect(selectedDate, vm.selectedSpecialty) {
        vm.load(selectedDate, vm.selectedSpecialty)
    }

    Column(Modifier.fillMaxSize()) {
        // ── Top bar ────────────────────────────────────────────────────────────
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
                "Artistas disponibles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // ── Date strip + location (sticky header) ──────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Month label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SELECCIONAR FECHA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        selectedDate.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
                            .replaceFirstChar { it.uppercase() } + " ${selectedDate.year}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PiumsOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Day strip
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dateStrip) { date ->
                        DayCell(
                            date = date,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            onClick = { selectedDate = date }
                        )
                    }
                }

                // Location button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !vm.isLocating) { vm.fetchLocation() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PiumsOrange.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (vm.isLocating) {
                            CircularProgressIndicator(
                                color = PiumsOrange,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                if (vm.userLat != null) Icons.Default.LocationOn
                                else Icons.Default.LocationSearching,
                                null,
                                tint = PiumsOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "UBICACIÓN DEL EVENTO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            vm.locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.Tune, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ── Filter bar ────────────────────────────────────────────────────────
        val filterLabel = buildList {
            vm.selectedSpecialty?.let { add(SPECIALTY_DISPLAY[it] ?: it) }
            vm.selectedCity?.let { add(it) }
            if (vm.minRating > 0) add("${vm.minRating.toInt()}+ ★")
            if (vm.sortOption != ByDateSortOption.RELEVANCE) add(vm.sortOption.label)
        }.joinToString(" · ").ifEmpty { "Filtrar artistas..." }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (vm.hasActiveFilters) PiumsOrange.copy(0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { showFiltersSheet = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Tune, null,
                tint = if (vm.hasActiveFilters) PiumsOrange else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                modifier = Modifier.size(18.dp))
            Text(
                filterLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (vm.hasActiveFilters) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (vm.hasActiveFilters) {
                Icon(Icons.Default.Cancel, null,
                    tint = PiumsOrange.copy(0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { vm.clearFilters() })
            }
        }

        // ── Results ────────────────────────────────────────────────────────────
        when {
            vm.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
            vm.error != null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(vm.error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.load(selectedDate) }) {
                    Text("Reintentar", color = PiumsOrange)
                }
            }
            else -> {
                // Header count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${vm.displayed.size} artista(s) encontrado(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Solo disponibles",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                        Switch(
                            checked = vm.showOnlyAvailable,
                            onCheckedChange = { vm.showOnlyAvailable = it },
                            modifier = Modifier.height(24.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PiumsOrange
                            )
                        )
                    }
                }

                if (vm.displayed.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                            modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (vm.showOnlyAvailable)
                                "No hay artistas disponibles para esa fecha"
                            else "No hay artistas que coincidan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(vm.displayed, key = { it.artist.resolvedId }) { item ->
                            ArtistAvailabilityCard(
                                item = item,
                                onClick = {
                                    onArtistClick(
                                        item.artist.resolvedId,
                                        selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                        vm.userLat,
                                        vm.userLng,
                                        vm.locationLabel
                                    )
                                }
                            )
                        }
                        item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    if (showFiltersSheet) {
        SearchByDateFiltersSheet(vm = vm, onDismiss = { showFiltersSheet = false })
    }
}

// ─── Specialty data ───────────────────────────────────────────────────────────

private val SPECIALTY_DISPLAY = mapOf(
    "MUSICO"     to "Música",
    "FOTOGRAFO"  to "Fotografía",
    "VIDEOGRAFO" to "Video",
    "ANIMADOR"   to "Animador"
)

private data class SpecialtyItem(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val SPECIALTY_ITEMS = listOf(
    SpecialtyItem("MUSICO",     "Música",     Icons.Default.MusicNote),
    SpecialtyItem("FOTOGRAFO",  "Fotografía", Icons.Default.CameraAlt),
    SpecialtyItem("VIDEOGRAFO", "Video",      Icons.Default.Videocam),
    SpecialtyItem("ANIMADOR",   "Animador",   Icons.Default.Celebration),
)

// ─── SearchByDateFiltersSheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchByDateFiltersSheet(
    vm: ArtistSearchByDateViewModel,
    onDismiss: () -> Unit
) {
    var cityText by remember { mutableStateOf(vm.selectedCity ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("Aplicar", color = PiumsOrange) }
            }

            // Especialidad
            FilterSection(title = "Especialidad") {
                val rows = SPECIALTY_ITEMS.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { sp ->
                                val selected = vm.selectedSpecialty == sp.id
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { vm.selectedSpecialty = if (selected) null else sp.id }
                                        .padding(vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(sp.icon, null, tint = if (selected) Color.White else PiumsOrange, modifier = Modifier.size(22.dp))
                                    Text(sp.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (row.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

            // Disponibilidad
            FilterSection(title = "Disponibilidad") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Solo artistas disponibles", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = vm.showOnlyAvailable,
                        onCheckedChange = { vm.showOnlyAvailable = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = PiumsOrange, checkedThumbColor = Color.White)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

            // Rango de precio
            FilterSection(title = "Rango de precio") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Mínimo: $${(vm.minPrice / 100).toInt()}", style = MaterialTheme.typography.bodySmall, color = PiumsOrange)
                        Text(if (vm.maxPrice >= 50000) "Máximo: Sin límite" else "Máximo: $${(vm.maxPrice / 100).toInt()}", style = MaterialTheme.typography.bodySmall, color = PiumsOrange)
                    }
                    RangeSlider(
                        value = vm.minPrice..vm.maxPrice,
                        onValueChange = { vm.minPrice = it.start; vm.maxPrice = it.endInclusive },
                        valueRange = 0f..50000f, steps = 499,
                        colors = SliderDefaults.colors(activeTrackColor = PiumsOrange, thumbColor = PiumsOrange)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

            // Calificación mínima
            FilterSection(title = "Calificación mínima") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        (1..5).forEach { star ->
                            val filled = star.toDouble() <= vm.minRating
                            IconButton(onClick = {
                                vm.minRating = if (vm.minRating == star.toDouble()) 0.0 else star.toDouble()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (filled) Icons.Default.Star else Icons.Default.StarOutline,
                                    null,
                                    tint = if (filled) PiumsOrange else MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    if (vm.minRating > 0) {
                        Text("${vm.minRating.toInt()} estrella${if (vm.minRating == 1.0) "" else "s"} o más",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

            // Ciudad o zona
            FilterSection(title = "Ciudad o zona") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = cityText,
                        onValueChange = { cityText = it; vm.selectedCity = it.takeIf { t -> t.isNotBlank() }; vm.searchCity(it) },
                        placeholder = { Text("Buscar ciudad o zona...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PiumsOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                        ),
                        trailingIcon = if (cityText.isNotEmpty()) ({
                            IconButton(onClick = { cityText = ""; vm.selectedCity = null; vm.clearCitySuggestions() }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }) else null
                    )
                    vm.citySuggestions.take(4).forEach { suggestion ->
                        Text(
                            suggestion.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cityText = suggestion.displayName; vm.selectedCity = suggestion.displayName; vm.clearCitySuggestions() }
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

            // Ordenar por
            FilterSection(title = "Ordenar por") {
                Column {
                    ByDateSortOption.entries.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.sortOption = opt }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(opt.label, style = MaterialTheme.typography.bodyMedium)
                            if (vm.sortOption == opt) Icon(Icons.Default.Check, null, tint = PiumsOrange, modifier = Modifier.size(18.dp))
                        }
                        if (opt != ByDateSortOption.entries.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

            // Solo verificados
            FilterSection(title = "Solo verificados") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostrar solo artistas verificados", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = vm.isVerified,
                        onCheckedChange = { vm.isVerified = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = PiumsOrange, checkedThumbColor = Color.White)
                    )
                }
            }

            // Limpiar filtros
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(0.3f))
                    .clickable { vm.clearFilters(); cityText = ""; vm.clearCitySuggestions(); onDismiss() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Limpiar todos los filtros",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

// ─── DayCell ──────────────────────────────────────────────────────────────────

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val dayName = date.dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale("es", "ES"))
        .uppercase()
    val dayNum = date.dayOfMonth.toString()

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) PiumsOrange
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (isToday && !isSelected) Modifier.background(Color.Transparent)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isToday && !isSelected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter).height(2.dp).fillMaxWidth(),
                    color = PiumsOrange.copy(0.6f)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayName,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White
                        else MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Text(
                dayNum,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isSelected -> Color.White
                    isToday    -> PiumsOrange
                    else       -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// ─── ArtistAvailabilityCard ────────────────────────────────────────────────────

@Composable
private fun ArtistAvailabilityCard(
    item: ArtistWithAvailability,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.available) 1f else 0.62f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // Cover photo / avatar — same dimensions as ArtistResultCard in SearchScreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                val photoUrl = item.artist.coverUrl ?: item.artist.avatar
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PiumsOrange.copy(0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.artist.displayName.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 48.sp, fontWeight = FontWeight.Bold, color = PiumsOrange
                        )
                    }
                }
                // Availability badge — top end
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            if (item.available) Color(0xFF22C55E).copy(0.92f)
                            else Color(0xFF6B7280).copy(0.85f)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (item.available) "Disponible" else "Ocupado",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Verified badge — top start
                if (item.artist.isVerified) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(PiumsOrange)
                            .padding(3.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }
            }

            // Info — matches SearchScreen card
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.artist.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                item.artist.specialties?.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = PiumsOrange, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.artist.rating > 0.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.Star, null,
                            tint = Color(0xFFF59E0B), modifier = Modifier.size(11.dp))
                        Text(String.format("%.1f", item.artist.rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        if (item.artist.totalReviews > 0) {
                            Text("(${item.artist.totalReviews})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                }
                item.distanceKm?.let { km ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.size(10.dp))
                        Text("%.1f km".format(km),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
                item.mainServicePrice?.takeIf { it > 0 }?.let { price ->
                    Text(
                        "$${String.format("%,.2f", price.toDouble())}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold, color = PiumsOrange
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (item.available) PiumsOrange else Color(0xFF6B7280).copy(0.3f))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Reservar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.available) Color.White
                                else MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                }
            }
        }
    }
}
