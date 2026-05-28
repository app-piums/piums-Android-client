package com.piums.cliente.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.location.LocationSearchHelper
import com.piums.cliente.data.location.LocationSuggestion
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class EventLocationPickerViewModel @Inject constructor(
    private val locationSearchHelper: LocationSearchHelper,
    private val locationHelper: LocationHelper
) : ViewModel() {

    companion object {
        const val DEFAULT_LAT = 14.6349
        const val DEFAULT_LNG = -90.5069
    }

    var centerLat     by mutableStateOf(DEFAULT_LAT); private set
    var centerLng     by mutableStateOf(DEFAULT_LNG); private set
    var currentAddress by mutableStateOf<String?>(null); private set
    var isGeocoding   by mutableStateOf(false); private set

    var searchQuery  by mutableStateOf(""); private set
    var suggestions  by mutableStateOf<List<LocationSuggestion>>(emptyList()); private set
    var isLocating   by mutableStateOf(false); private set

    private var suppressGeocode = false
    private var geocodeJob: Job? = null
    private var searchJob: Job?  = null

    fun onMapMoved(lat: Double, lng: Double) {
        if (suppressGeocode) return
        centerLat = lat; centerLng = lng
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            delay(600)
            isGeocoding = true
            val result = locationSearchHelper.reverseGeocode(lat, lng)
            currentAddress = result?.displayName
            isGeocoding = false
        }
    }

    fun onQueryChange(query: String) {
        searchQuery = query
        if (query.isBlank()) { suggestions = emptyList(); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            suggestions = locationSearchHelper.search(query).take(5)
        }
    }

    fun clearQuery() {
        searchQuery = ""
        suggestions = emptyList()
    }

    fun onSuggestionSelected(s: LocationSuggestion, centerMap: (Double, Double) -> Unit) {
        searchQuery    = s.displayName
        currentAddress = s.displayName
        centerLat      = s.lat; centerLng = s.lng
        suggestions    = emptyList()
        suppressGeocode = true
        centerMap(s.lat, s.lng)
        viewModelScope.launch {
            delay(2000)
            suppressGeocode = false
        }
    }

    fun fetchGpsLocation(centerMap: (Double, Double) -> Unit) {
        viewModelScope.launch {
            isLocating = true
            val loc = runCatching { locationHelper.getCurrentLocation() }.getOrNull()
                ?: runCatching { locationHelper.getLastKnownLocation() }.getOrNull()
            if (loc != null) {
                centerLat = loc.lat; centerLng = loc.lng
                suppressGeocode = false
                centerMap(loc.lat, loc.lng)
                onMapMoved(loc.lat, loc.lng)
            }
            isLocating = false
        }
    }

    val confirmedName: String
        get() = currentAddress ?: searchQuery.ifBlank { "" }
}

// ─── Sheet ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLocationPickerSheet(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (locationName: String, lat: Double, lng: Double) -> Unit,
    vm: EventLocationPickerViewModel = hiltViewModel()
) {
    val context    = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dateLabel = remember(selectedDate) {
        val fmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))
        selectedDate.format(fmt).replaceFirstChar { it.uppercase() }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(vm.centerLat, vm.centerLng))
        }
    }
    val centerMap: (Double, Double) -> Unit = remember(mapView) { { lat, lng ->
        mapView.controller.animateTo(GeoPoint(lat, lng))
    }}

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = null,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column {
            // Drag handle
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                Alignment.Center
            ) {
                Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(0.25f)))
            }

            // Header
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "¿Dónde será el evento?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(dateLabel, style = MaterialTheme.typography.bodyMedium,
                    color = PiumsOrange, fontWeight = FontWeight.Medium)
            }

            // Map with crosshair + GPS button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = {})

                // Crosshair pin
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.LocationOn, null,
                        tint = PiumsOrange, modifier = Modifier.size(36.dp))
                    Box(
                        Modifier.size(12.dp, 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(0.2f))
                    )
                }

                // GPS button
                FilledIconButton(
                    onClick  = { vm.fetchGpsLocation(centerMap) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(40.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = PiumsOrange, contentColor = Color.White
                    )
                ) {
                    if (vm.isLocating) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Search field + suggestions
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value         = vm.searchQuery,
                    onValueChange = { vm.onQueryChange(it) },
                    placeholder   = { Text("Busca un lugar o arrastra el mapa") },
                    leadingIcon   = {
                        Icon(
                            if (vm.currentAddress != null && vm.searchQuery.isNotEmpty())
                                Icons.Default.LocationOn else Icons.Default.Search,
                            null,
                            tint = if (vm.currentAddress != null && vm.searchQuery.isNotEmpty())
                                PiumsOrange else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    },
                    trailingIcon = if (vm.searchQuery.isNotEmpty()) {{
                        IconButton(onClick = { vm.clearQuery() }) {
                            Icon(Icons.Default.Clear, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        }
                    }} else null,
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth(),
                    shape      = RoundedCornerShape(14.dp),
                    colors     = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )

                if (vm.suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        vm.suggestions.forEachIndexed { idx, s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.onSuggestionSelected(s, centerMap) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, null,
                                    tint = PiumsOrange, modifier = Modifier.size(18.dp))
                                Text(s.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f))
                            }
                            if (idx < vm.suggestions.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(start = 46.dp))
                            }
                        }
                    }
                }
            }

            // Current address row (when no suggestions)
            if (vm.suggestions.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null,
                        tint = PiumsOrange.copy(0.7f), modifier = Modifier.size(16.dp))
                    if (vm.isGeocoding) {
                        CircularProgressIndicator(color = PiumsOrange,
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Detectando dirección…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    } else {
                        Text(
                            vm.currentAddress ?: "Mueve el mapa para elegir la ubicación",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vm.currentAddress != null)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Confirm button
            Button(
                onClick  = { onConfirm(vm.confirmedName, vm.centerLat, vm.centerLng) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp)
                    .navigationBarsPadding(),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
            ) {
                Text(
                    if (vm.confirmedName.isNotBlank()) "Buscar artistas aquí" else "Buscar artistas",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // Map lifecycle + move listener
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(e: ScrollEvent): Boolean {
                val c = mapView.mapCenter
                vm.onMapMoved(c.latitude, c.longitude)
                return true
            }
            override fun onZoom(e: ZoomEvent): Boolean {
                val c = mapView.mapCenter
                vm.onMapMoved(c.latitude, c.longitude)
                return true
            }
        }
        mapView.addMapListener(listener)
        mapView.onResume()
        onDispose {
            mapView.removeMapListener(listener)
            mapView.onPause()
            mapView.onDetach()
        }
    }
}
