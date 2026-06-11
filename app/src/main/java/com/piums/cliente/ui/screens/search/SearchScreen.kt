package com.piums.cliente.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.ArtistDto
import com.piums.cliente.data.remote.dto.SearchArtistsResponse
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.LatLng
import com.piums.cliente.utils.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SearchFilters(
    val specialty: String? = null,
    val city: String? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val minRating: Double? = null,
    val isVerified: Boolean? = null,
    val sortBy: String? = null,
    val sortOrder: String? = null
) {
    val isActive: Boolean get() =
        specialty != null || city != null || minPrice != null ||
        maxPrice != null || minRating != null || isVerified != null || sortBy != null
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: PiumsApiService,
    private val locationHelper: LocationHelper
) : ViewModel() {

    var currentLocation by mutableStateOf<LatLng?>(null)
        private set
    var useLocation by mutableStateOf(false)
        private set
    var isGettingLocation by mutableStateOf(false)
        private set

    fun toggleLocation(hasPermission: Boolean) {
        if (!hasPermission) return
        if (useLocation) {
            useLocation = false
            currentLocation = null
            if (hasSearched) search(reset = true)
        } else {
            viewModelScope.launch {
                isGettingLocation = true
                currentLocation = locationHelper.getLastKnownLocation()
                    ?: locationHelper.getCurrentLocation()
                useLocation = currentLocation != null
                isGettingLocation = false
                if (hasSearched) search(reset = true)
            }
        }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    var filters by mutableStateOf(SearchFilters())
        private set

    var artists by mutableStateOf<List<ArtistDto>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var hasSearched by mutableStateOf(false)
        private set

    var currentPage by mutableStateOf(1)
        private set

    var hasMore by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var expandedTerms by mutableStateOf<List<String>>(emptyList())
        private set

    var isSmartSearch by mutableStateOf(false)
        private set

    private var ignoreNextDebounce = false

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _query
                .debounce(400)
                .distinctUntilChanged()
                .collect { q ->
                    if (ignoreNextDebounce) { ignoreNextDebounce = false; return@collect }
                    if (q.isNotBlank() || filters.isActive) search(reset = true)
                    else if (q.isBlank()) {
                        artists = emptyList()
                        hasSearched = false
                        expandedTerms = emptyList()
                        isSmartSearch = false
                    }
                }
        }
    }

    fun onQueryChange(q: String) { _query.value = q }

    fun applyFilters(f: SearchFilters) {
        filters = f
        search(reset = true)
    }

    fun clearFilters() {
        filters = SearchFilters()
        artists = emptyList()
        hasSearched = false
        expandedTerms = emptyList()
        isSmartSearch = false
        _query.value = ""
    }

    fun searchBySpecialty(categoryId: String) {
        ignoreNextDebounce = true
        _query.value = ""
        filters = SearchFilters(specialty = categoryId)
        search(reset = true)
    }

    fun searchByPopular(term: String) {
        ignoreNextDebounce = true
        _query.value = term
        search(reset = true)
    }

    fun search(reset: Boolean = false) {
        viewModelScope.launch {
            if (reset) { currentPage = 1; artists = emptyList() }
            isLoading = true
            error = null
            try {
                val q = _query.value.trim()
                val loc = if (useLocation) currentLocation else null
                val response: SearchArtistsResponse
                if (q.isNotBlank()) {
                    val raw = api.smartSearch(
                        q = q, page = currentPage,
                        lat = loc?.lat, lng = loc?.lng,
                        specialty = filters.specialty, city = filters.city,
                        minPrice = filters.minPrice, maxPrice = filters.maxPrice,
                        minRating = filters.minRating, isVerified = filters.isVerified
                    )
                    expandedTerms = raw.expandedTerms ?: emptyList()
                    isSmartSearch = true
                    response = SearchArtistsResponse(artists = raw.list, data = null, pagination = raw.pagination)
                } else {
                    expandedTerms = emptyList()
                    isSmartSearch = false
                    response = api.searchArtists(
                        page = currentPage,
                        specialty = filters.specialty, city = filters.city,
                        minPrice = filters.minPrice, maxPrice = filters.maxPrice,
                        minRating = filters.minRating, isVerified = filters.isVerified,
                        sortBy = filters.sortBy, sortOrder = filters.sortOrder
                    )
                }
                val withServices = { a: com.piums.cliente.data.remote.dto.ArtistDto ->
                    a.servicesCount > 0 || a.serviceIds?.isNotEmpty() == true || a.serviceTitles?.isNotEmpty() == true
                }
                artists = if (reset) response.list.filter(withServices)
                          else artists + response.list.filter(withServices)
                hasMore = response.pagination?.hasMore == true
                hasSearched = true
            } catch (_: Exception) {
                error = "Error al buscar artistas"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (!hasMore || isLoading) return
        currentPage++
        search(reset = false)
    }
}

// ─── Categories ───────────────────────────────────────────────────────────────

private data class Category(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val specialtyId: String
)

private val CATEGORIES = listOf(
    Category("Música",     Icons.Default.MusicNote,   "MUSICO"),
    Category("Fotografía", Icons.Default.CameraAlt,   "FOTOGRAFO"),
    Category("Video",      Icons.Default.Videocam,    "VIDEOGRAFO"),
    Category("Animador",   Icons.Default.Celebration, "ANIMADOR"),
    Category("Creadores",  Icons.Default.PlayCircle,  "CREADOR_CONTENIDO"),
)

private val POPULAR_SEARCHES = listOf(
    "música en vivo", "fotógrafo bodas", "video boda", "payaso fiesta", "maestro ceremonias"
)

private val SPECIALTY_DISPLAY = mapOf(
    "MUSICO"     to "Música",
    "FOTOGRAFO"  to "Fotografía",
    "VIDEOGRAFO" to "Video",
    "ANIMADOR"   to "Animador",
    "CREADOR_CONTENIDO" to "Creadores"
)

private data class SortOption(val sortBy: String, val sortOrder: String?, val label: String)
private val SORT_OPTIONS = listOf(
    SortOption("",          null,   "Relevancia"),
    SortOption("rating",    "desc", "Mejor calificados"),
    SortOption("price_low", "asc",  "Precio: menor a mayor"),
    SortOption("price_high","desc", "Precio: mayor a menor"),
    SortOption("recent",    "desc", "Más recientes")
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onArtistClick: (String) -> Unit = {},
    vm: SearchViewModel = hiltViewModel()
) {
    val query by vm.query.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(vm.isLoading) { if (!vm.isLoading) isRefreshing = false }
    val searchBarLabel = remember(vm.filters) {
        val parts = buildList {
            vm.filters.specialty?.let { add(SPECIALTY_DISPLAY[it] ?: it) }
            vm.filters.city?.let { add(it) }
            vm.filters.minRating?.takeIf { it > 0 }?.let { add("${it.toInt()}+ ★") }
        }
        if (parts.isEmpty()) "¿Qué estás buscando?" else parts.joinToString(" · ")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        SearchBar(
            label = searchBarLabel,
            filterActive = vm.filters.isActive,
            onTap = { showFilters = true },
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Active filters chips row
        if (vm.filters.isActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                vm.filters.specialty?.let { FilterChip(SPECIALTY_DISPLAY[it] ?: it) }
                vm.filters.city?.let { FilterChip(it) }
                vm.filters.minRating?.let { FilterChip("≥ ${it.toInt()}★") }
                if (vm.filters.isVerified == true) FilterChip("Verificados")
                vm.filters.sortBy?.let { sb ->
                    FilterChip(SORT_OPTIONS.find { it.sortBy == sb }?.label ?: sb)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(onClick = vm::clearFilters)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Limpiar", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { if (vm.hasSearched || vm.filters.isActive) { isRefreshing = true; vm.search(reset = true) } },
            modifier     = Modifier.fillMaxSize()
        ) {
        when {
            !vm.hasSearched && query.isBlank() && !vm.filters.isActive -> {
                ExploreInitialView(
                    onCategoryClick = { cat -> vm.searchBySpecialty(cat.specialtyId) },
                    onPopularClick  = { term -> vm.searchByPopular(term) }
                )
            }
            vm.isLoading && vm.artists.isEmpty() -> {
                SearchResultsSkeleton()
            }
            vm.error != null && vm.artists.isEmpty() -> {
                ErrorState(message = vm.error!!, onRetry = { vm.search(reset = true) })
            }
            vm.artists.isEmpty() && vm.hasSearched -> {
                EmptyState(query = query)
            }
            else -> {
                SearchResults(
                    artists = vm.artists,
                    hasMore = vm.hasMore,
                    isLoadingMore = vm.isLoading,
                    isSmartSearch = vm.isSmartSearch,
                    expandedTerms = vm.expandedTerms,
                    onExpandedTermClick = { term -> vm.searchByPopular(term) },
                    onLoadMore = vm::loadMore,
                    onArtistClick = onArtistClick
                )
            }
        }
        } // PullToRefreshBox
    }

    if (showFilters) {
        FilterSheet(
            current = vm.filters,
            onApply = { vm.applyFilters(it); showFilters = false },
            onDismiss = { showFilters = false }
        )
    }
}

// ─── Search Bar ───────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    label: String,
    filterActive: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Search, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (filterActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(0.4f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (filterActive) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(PiumsOrange),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tune, null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp))
            }
        } else {
            Icon(Icons.Default.Tune, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Filter chips ─────────────────────────────────────────────────────────────

@Composable
private fun FilterChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PiumsOrange.copy(0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PiumsOrange)
    }
}

// ─── Explore Initial View ─────────────────────────────────────────────────────

@Composable
private fun ExploreInitialView(
    onCategoryClick: (Category) -> Unit,
    onPopularClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Categories section
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Todas las categorías",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            // 3-column grid — non-lazy since only 4 items
            val rows = CATEGORIES.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { cat ->
                            CategoryCell(
                                label = cat.label,
                                icon = cat.icon,
                                modifier = Modifier.weight(1f),
                                onClick = { onCategoryClick(cat) }
                            )
                        }
                        // Fill remaining columns if row is incomplete
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Popular searches section
        Column {
            Text(
                "Populares",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                    .padding(bottom = 12.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(POPULAR_SEARCHES) { term ->
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPopularClick(term) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            term,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PiumsOrange.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null,
                tint = PiumsOrange, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
    }
}

// ─── Results Grid ─────────────────────────────────────────────────────────────

@Composable
private fun SearchResults(
    artists: List<ArtistDto>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    isSmartSearch: Boolean,
    expandedTerms: List<String>,
    onExpandedTermClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val gridState = rememberLazyGridState()

    // Load more when near end
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= artists.size - 4 && hasMore && !isLoadingMore) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Results header
        item(span = { GridItemSpan(2) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${artists.size} artista(s) encontrado(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    if (isSmartSearch) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null,
                                tint = PiumsOrange, modifier = Modifier.size(12.dp))
                            Text("SmartSearch",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PiumsOrange)
                        }
                    }
                }
                if (expandedTerms.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("También buscamos:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        expandedTerms.forEach { term ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(PiumsOrange.copy(0.10f))
                                    .clickable { onExpandedTermClick(term) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(term,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = PiumsOrange)
                            }
                        }
                    }
                }
            }
        }

        items(artists, key = { it.resolvedId }) { artist ->
            ArtistResultCard(artist = artist, onClick = { onArtistClick(artist.resolvedId) })
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = PiumsOrange,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Cargando más artistas...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistResultCard(artist: ArtistDto, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // Cover photo / avatar / initials
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                val photoUrl = artist.coverUrl ?: artist.avatar
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
                            artist.displayName.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = PiumsOrange
                        )
                    }
                }
                if (artist.isVerified) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
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
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    artist.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                artist.specialties?.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = PiumsOrange, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        if (artist.totalReviews > 0) {
                            Text("(${artist.totalReviews})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    artist.city?.let {
                        Icon(Icons.Default.LocationOn, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.size(10.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                artist.formattedPrice?.let { price ->
                    Text(price, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold, color = PiumsOrange)
                }
            }
        }
    }
}

@Composable
private fun ArtistResultSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
        )
    ) {}
}

// ─── Empty / Error states ─────────────────────────────────────────────────────

@Composable
private fun EmptyState(query: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(0.3f),
            modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (query.isNotBlank()) "Sin resultados para \"$query\""
            else "Sin resultados",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Intenta con otro término o ajusta los filtros",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null,
            tint = MaterialTheme.colorScheme.error.copy(0.6f),
            modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
        ) { Text("Reintentar", color = Color.White) }
    }
}

@Composable
private fun SearchResultsSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) { ArtistResultSkeleton() }
    }
}

// ─── Filter Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    current: SearchFilters,
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var specialty  by remember { mutableStateOf(current.specialty) }
    var city       by remember { mutableStateOf(current.city ?: "") }
    var minPrice   by remember { mutableStateOf((current.minPrice ?: 0).toFloat()) }
    var maxPrice   by remember { mutableStateOf((current.maxPrice ?: 50000).toFloat()) }
    var minRating  by remember { mutableStateOf(current.minRating ?: 0.0) }
    var verified   by remember { mutableStateOf(current.isVerified ?: false) }
    var selectedSort by remember {
        mutableStateOf(SORT_OPTIONS.find { it.sortBy == (current.sortBy ?: "") } ?: SORT_OPTIONS[0])
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtros", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    specialty = null; city = ""; minPrice = 0f; maxPrice = 50000f
                    minRating = 0.0; verified = false; selectedSort = SORT_OPTIONS[0]
                }) { Text("Limpiar todo", color = PiumsOrange) }
            }

            // ── Especialidad ─────────────────────────────
            FilterSection(title = "Especialidad") {
                val rows = CATEGORIES.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { cat ->
                                val selected = specialty == cat.specialtyId
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) PiumsOrange
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            specialty = if (selected) null else cat.specialtyId
                                        }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(cat.icon, null,
                                        modifier = Modifier.size(22.dp),
                                        tint = if (selected) Color.White
                                               else MaterialTheme.colorScheme.onSurface)
                                    Text(cat.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color.White
                                                else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (row.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Rango de precio ──────────────────────────
            FilterSection(title = "Rango de precio") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Mínimo: $${(minPrice / 100).toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Text(if (maxPrice >= 50000) "Máximo: Sin límite"
                             else "Máximo: $${(maxPrice / 100).toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                    RangeSlider(
                        value = minPrice..maxPrice,
                        onValueChange = { minPrice = it.start; maxPrice = it.endInclusive },
                        valueRange = 0f..50000f, steps = 499,
                        colors = SliderDefaults.colors(thumbColor = PiumsOrange, activeTrackColor = PiumsOrange)
                    )
                }
            }

            HorizontalDivider()

            // ── Calificación mínima ──────────────────────
            FilterSection(title = "Calificación mínima") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        (1..5).forEach { star ->
                            val filled = star.toDouble() <= minRating
                            Icon(
                                if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (filled) PiumsOrange else MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable {
                                        minRating = if (minRating == star.toDouble()) 0.0 else star.toDouble()
                                    }
                            )
                        }
                    }
                    if (minRating > 0) {
                        Text("${minRating.toInt()} estrella${if (minRating == 1.0) "" else "s"} o más",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }

            HorizontalDivider()

            // ── Ciudad ───────────────────────────────────
            FilterSection(title = "Ciudad o zona") {
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    placeholder = { Text("ej. Guatemala") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PiumsOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                )
            }

            HorizontalDivider()

            // ── Ordenar por ──────────────────────────────
            FilterSection(title = "Ordenar por") {
                Column {
                    SORT_OPTIONS.forEachIndexed { idx, opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSort = opt }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(opt.label, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                            if (selectedSort == opt) {
                                Icon(Icons.Default.Check, null,
                                    tint = PiumsOrange, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (idx < SORT_OPTIONS.lastIndex) HorizontalDivider()
                    }
                }
            }

            HorizontalDivider()

            // ── Solo verificados ─────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mostrar solo artistas verificados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                Switch(
                    checked = verified, onCheckedChange = { verified = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White,
                        checkedTrackColor = PiumsOrange)
                )
            }

            // ── Aplicar ──────────────────────────────────
            Button(
                onClick = {
                    onApply(SearchFilters(
                        specialty  = specialty,
                        city       = city.takeIf { it.isNotBlank() },
                        minPrice   = if (minPrice > 0) minPrice.toInt() else null,
                        maxPrice   = if (maxPrice < 50000) maxPrice.toInt() else null,
                        minRating  = if (minRating > 0) minRating else null,
                        isVerified = if (verified) true else null,
                        sortBy     = selectedSort.sortBy.takeIf { it.isNotBlank() },
                        sortOrder  = selectedSort.sortOrder
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
            ) {
                Text("Aplicar filtros", color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}
