package com.piums.cliente.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class LocationMapPickerViewModel @Inject constructor(
    private val locationSearchHelper: LocationSearchHelper
) : ViewModel() {

    var currentAddress by mutableStateOf<String?>(null)     ; private set
    var isGeocoding    by mutableStateOf(false)             ; private set
    private var geocodeJob: Job? = null

    fun onMapMoved(lat: Double, lng: Double) {
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            delay(600)
            isGeocoding = true
            val result = locationSearchHelper.reverseGeocode(lat, lng)
            currentAddress = result?.displayName
            isGeocoding = false
        }
    }
}

// ─── Sheet ────────────────────────────────────────────────────────────────────

private const val INITIAL_LAT = 14.6349
private const val INITIAL_LNG = -90.5069

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationMapPickerSheet(
    initialLat: Double = INITIAL_LAT,
    initialLng: Double = INITIAL_LNG,
    onDismiss: () -> Unit,
    onConfirm: (LocationSuggestion) -> Unit,
    vm: LocationMapPickerViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context    = LocalContext.current

    // Track center coordinates in local state so the confirm button uses latest
    var centerLat by remember { mutableDoubleStateOf(initialLat) }
    var centerLng by remember { mutableDoubleStateOf(initialLng) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(initialLat, initialLng))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = null,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column {
            // Map area with centered pin overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                AndroidView(
                    factory  = { mapView },
                    modifier = Modifier.fillMaxSize(),
                    update   = {}
                )

                // Fixed crosshair pin at map center
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint     = PiumsOrange,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .offset(y = (-20).dp)
                )
            }

            // Address row + confirm button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, null,
                        tint = PiumsOrange, modifier = Modifier.size(18.dp))
                    if (vm.isGeocoding) {
                        CircularProgressIndicator(color = PiumsOrange,
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Buscando dirección...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    } else {
                        Text(
                            vm.currentAddress ?: "Mueve el mapa para seleccionar una ubicación",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (vm.currentAddress != null)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                    }
                }

                Button(
                    onClick = {
                        val address = vm.currentAddress ?: ""
                        onConfirm(LocationSuggestion(address, centerLat, centerLng))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
                ) {
                    Text("Usar este lugar", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Map listener: update center state and trigger reverse geocoding on map move
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(e: ScrollEvent): Boolean {
                val center = mapView.mapCenter
                centerLat = center.latitude
                centerLng = center.longitude
                vm.onMapMoved(center.latitude, center.longitude)
                return true
            }
            override fun onZoom(e: ZoomEvent): Boolean {
                val center = mapView.mapCenter
                centerLat = center.latitude
                centerLng = center.longitude
                vm.onMapMoved(center.latitude, center.longitude)
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
