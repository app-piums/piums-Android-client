package com.piums.cliente.ui.screens.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.ui.theme.PiumsSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class ArtistProfileViewModel @Inject constructor(
    private val api: PiumsApiService,
    savedState: SavedStateHandle
) : ViewModel() {

    private val artistId: String = savedState.get<String>("artistId").orEmpty()

    var artist by mutableStateOf<ArtistDto?>(null)
        private set

    var services by mutableStateOf<List<ArtistServiceDto>>(emptyList())
        private set
    var servicesError by mutableStateOf<String?>(null)
        private set

    var reviews by mutableStateOf<List<ReviewDto>>(emptyList())
        private set

    var portfolio by mutableStateOf<List<PortfolioItem>>(emptyList())
        private set

    var certifications by mutableStateOf<List<CertificationDto>>(emptyList())
        private set

    var dayOffers by mutableStateOf<Map<String, com.piums.cliente.data.remote.dto.ServiceDayOffer>>(emptyMap())
        private set

    var isFavorite by mutableStateOf(false)
        private set

    private var favoriteId by mutableStateOf<String?>(null)

    var isLoading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun refresh() { load() }

    private fun load() {
        if (artistId.isEmpty()) {
            error = "No se pudo cargar el perfil"
            isLoading = false
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val artistDeferred    = async { runCatching { api.getArtist(artistId) }.getOrNull() }
                val servicesDeferred  = async { runCatching { api.getArtistServices(artistId) } }
                val reviewsDeferred   = async { runCatching { api.getReviews(artistId, limit = 5) }.getOrNull() }
                val portfolioDeferred = async { runCatching { api.getArtistPortfolio(artistId) }.getOrNull() }
                val favDeferred       = async { runCatching { api.checkFavorite(entityId = artistId) }.getOrNull() }

                val artistResponse = artistDeferred.await()
                artist         = artistResponse?.resolved
                certifications = artistResponse?.resolvedCertifications ?: emptyList()
                val servicesResult = servicesDeferred.await()
                val loadedServices = servicesResult.getOrNull()?.list ?: emptyList()
                services      = loadedServices
                servicesError = if (servicesResult.isFailure) "Error del servidor. Intenta más tarde" else null
                reviews   = reviewsDeferred.await()?.list ?: emptyList()
                portfolio = portfolioDeferred.await()?.list ?: emptyList()
                favDeferred.await()?.let { fav ->
                    isFavorite = fav.isFavorite
                    favoriteId = fav.favoriteId
                }
                // Cargar day offers en paralelo para todos los servicios
                if (loadedServices.isNotEmpty()) {
                    val today = java.time.LocalDate.now().toString()
                    val offers = loadedServices.map { svc ->
                        async {
                            runCatching { api.getDayOffers(svc.id) }.getOrNull()
                                ?.all?.firstOrNull { o ->
                                    o.isActive &&
                                    (o.validFrom == null || o.validFrom <= today) &&
                                    (o.validUntil == null || o.validUntil >= today)
                                }?.let { svc.id to it }
                        }
                    }.mapNotNull { it.await() }.toMap()
                    dayOffers = offers
                }
            } catch (_: Exception) {
                error = "No se pudo cargar el perfil"
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val wasF = isFavorite
            isFavorite = !wasF
            try {
                if (wasF) {
                    favoriteId?.let { api.removeFavorite(it) }
                    favoriteId = null
                } else {
                    val added = api.addFavorite(AddFavoriteRequest(entityId = artistId))
                    favoriteId = added.id
                }
            } catch (_: Exception) {
                isFavorite = wasF
            }
        }
    }

    // ── Escribir reseña ───────────────────────────────────────────────────────
    var showWriteReview by mutableStateOf(false)
        private set
    var reviewBookings by mutableStateOf<List<BookingDto>>(emptyList())
        private set
    var selectedReviewBookingId by mutableStateOf<String?>(null)
        private set
    var reviewRating by mutableStateOf(0)
        private set
    var reviewComment by mutableStateOf("")
        private set
    var isLoadingReviewBookings by mutableStateOf(false)
        private set
    var isSubmittingReview by mutableStateOf(false)
        private set
    var reviewError by mutableStateOf<String?>(null)
        private set
    var reviewSuccess by mutableStateOf(false)
        private set

    fun openWriteReview() {
        showWriteReview = true
        reviewRating = 0; reviewComment = ""; reviewError = null; reviewSuccess = false
        viewModelScope.launch {
            isLoadingReviewBookings = true
            val res = runCatching { api.getBookings(status = "COMPLETED", limit = 50) }.getOrNull()
            reviewBookings = res?.list?.filter { it.artistId == artistId } ?: emptyList()
            selectedReviewBookingId = reviewBookings.firstOrNull()?.id
            isLoadingReviewBookings = false
        }
    }

    fun closeWriteReview() { showWriteReview = false }
    fun onRatingChange(r: Int)     { reviewRating = r }
    fun onCommentChange(c: String) { reviewComment = c }
    fun selectReviewBooking(id: String) { selectedReviewBookingId = id }

    fun submitReview() {
        val bid = selectedReviewBookingId ?: return
        if (reviewRating == 0) { reviewError = "Selecciona una calificación"; return }
        viewModelScope.launch {
            isSubmittingReview = true; reviewError = null
            runCatching {
                api.createReview(CreateReviewRequest(
                    artistId  = artistId,
                    bookingId = bid,
                    rating    = reviewRating,
                    comment   = reviewComment.ifBlank { null }
                ))
            }.onSuccess {
                reviewSuccess = true
                reviews = runCatching { api.getReviews(artistId, limit = 5) }
                    .getOrNull()?.list ?: reviews
            }.onFailure { reviewError = "No se pudo enviar la reseña. Intenta de nuevo." }
            isSubmittingReview = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistProfileScreen(
    artistId: String,
    onBack: () -> Unit,
    onBook: (String) -> Unit,
    vm: ArtistProfileViewModel = hiltViewModel()
) {
    var isRefreshing  by remember { mutableStateOf(false) }
    var detailService by remember { mutableStateOf<ArtistServiceDto?>(null) }
    LaunchedEffect(vm.isLoading) { if (!vm.isLoading) isRefreshing = false }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = { isRefreshing = true; vm.refresh() },
        modifier     = Modifier.fillMaxSize()
    ) {
    Box(Modifier.fillMaxSize()) {
        when {
            vm.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PiumsOrange)
                }
            }
            vm.error != null -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(0.6f),
                        modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(vm.error!!, color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
                    ) { Text("Reintentar", color = Color.White) }
                    TextButton(onClick = onBack) { Text("Volver") }
                }
            }
            vm.artist != null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    item {
                        ProfileHeader(
                            artist     = vm.artist!!,
                            isFavorite = vm.isFavorite,
                            onBack     = onBack,
                            onFavorite = vm::toggleFavorite
                        )
                    }

                    // Stats
                    item {
                        StatsRow(artist = vm.artist!!)
                    }

                    // Bio
                    vm.artist!!.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                        item { BioSection(bio = bio) }
                    }

                    // Certifications
                    if (vm.certifications.isNotEmpty()) {
                        item { SectionTitle("Certificaciones") }
                        item { CertificationsSection(certs = vm.certifications) }
                    }

                    // Services — siempre visible
                    item { SectionTitle("Servicios") }
                    item {
                        if (vm.services.isNotEmpty()) {
                            ServicesSection(
                                services        = vm.services,
                                dayOffers       = vm.dayOffers,
                                onBook          = { onBook(artistId) },
                                onServiceDetail = { detailService = it }
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Sin servicios disponibles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                                )
                                vm.servicesError?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Reviews — siempre visible para poder escribir reseña
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reseñas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground)
                            TextButton(onClick = { vm.openWriteReview() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.StarRate, null,
                                    tint = PiumsOrange, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Escribir reseña",
                                    color = PiumsOrange,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (vm.reviews.isEmpty()) {
                        item {
                            Text("Aún no hay reseñas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(0.45f),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        }
                    } else {
                        items(vm.reviews) { review ->
                            ReviewItem(review = review)
                        }
                    }

                    // Portfolio
                    if (vm.portfolio.isNotEmpty()) {
                        item {
                            SectionTitle("Portafolio (${vm.portfolio.size})")
                        }
                        item { PortfolioGrid(items = vm.portfolio) }
                    }

                    // Social links
                    val artist = vm.artist!!
                    if (artist.instagram != null || artist.website != null) {
                        item {
                            SocialLinksSection(
                                instagram = artist.instagram,
                                website   = artist.website
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
    } // PullToRefreshBox

    // Service detail sheet
    detailService?.let { svc ->
        ServiceDetailSheet(
            service   = svc,
            onDismiss = { detailService = null },
            onBook    = { detailService = null; onBook(artistId) }
        )
    }

    // Write review sheet
    if (vm.showWriteReview) {
        WriteReviewSheet(
            artistName          = vm.artist?.let { it.name ?: it.nombre } ?: "",
            bookings            = vm.reviewBookings,
            selectedBookingId   = vm.selectedReviewBookingId,
            rating              = vm.reviewRating,
            comment             = vm.reviewComment,
            isLoading           = vm.isLoadingReviewBookings,
            isSubmitting        = vm.isSubmittingReview,
            error               = vm.reviewError,
            success             = vm.reviewSuccess,
            onSelectBooking     = vm::selectReviewBooking,
            onRatingChange      = vm::onRatingChange,
            onCommentChange     = vm::onCommentChange,
            onSubmit            = vm::submitReview,
            onDismiss           = vm::closeWriteReview
        )
    }
}

// ─── Profile Header ───────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    artist: ArtistDto,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit
) {
    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFEF4444) else Color.White,
        animationSpec = tween(200),
        label = "heart"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(PiumsOrange.copy(0.30f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null,
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null, tint = heartColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Avatar
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(PiumsOrange.copy(0.15f))
                        .border(3.dp, PiumsOrange, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (artist.avatarUrl != null) {
                        AsyncImage(
                            model            = artist.avatarUrl,
                            contentDescription = null,
                            modifier         = Modifier.fillMaxSize(),
                            contentScale     = ContentScale.Crop
                        )
                    } else {
                        Text(
                            artist.displayName.firstOrNull()?.uppercase() ?: "?",
                            fontSize   = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color      = PiumsOrange
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Name + verified
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    artist.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (artist.isVerified) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PiumsOrange)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Verificado", style = MaterialTheme.typography.labelSmall,
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Location & specialties
            if (!artist.city.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                        modifier = Modifier.size(14.dp))
                    Text(
                        listOfNotNull(artist.city, artist.state, artist.country).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.6f)
                    )
                }
            }

            artist.specialties?.takeIf { it.isNotEmpty() }?.let { specs ->
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(specs) { spec ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PiumsOrange.copy(0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(spec, style = MaterialTheme.typography.labelSmall,
                                color = PiumsOrange, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ─── Stats Row ────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(artist: ArtistDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            label = "Calificación",
            value = if (artist.rating > 0) String.format("%.1f", artist.rating) else "—",
            icon  = Icons.Default.Star
        )
        StatDivider()
        StatItem(
            label = "Reseñas",
            value = "${artist.totalReviews}",
            icon  = Icons.Default.ChatBubbleOutline
        )
        StatDivider()
        StatItem(
            label = "Reservas",
            value = "${artist.totalBookings}",
            icon  = Icons.Default.CalendarMonth
        )
        StatDivider()
        StatItem(
            label = "Servicios",
            value = "${artist.servicesCount}",
            icon  = Icons.Default.DesignServices
        )
    }
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.12f)
    )
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null,
            tint = PiumsOrange, modifier = Modifier.size(18.dp))
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = PiumsOrange)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outline.copy(0.15f))
    )
}

// ─── Bio ──────────────────────────────────────────────────────────────────────

@Composable
private fun BioSection(bio: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 16.dp)) {
        Text("Sobre mí", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            bio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp
        )
        if (bio.length > 120) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (expanded) "Ver menos" else "Ver más",
                    color = PiumsOrange, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.12f)
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

// ─── Services ─────────────────────────────────────────────────────────────────

@Composable
private fun ServicesSection(
    services: List<ArtistServiceDto>,
    dayOffers: Map<String, com.piums.cliente.data.remote.dto.ServiceDayOffer> = emptyMap(),
    onBook: () -> Unit,
    onServiceDetail: (ArtistServiceDto) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        services.forEach { svc ->
            ServiceCard(service = svc, dayOffer = dayOffers[svc.id],
                onBook = onBook, onDetail = { onServiceDetail(svc) })
        }
        Spacer(Modifier.height(8.dp))
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.12f)
    )
}

@Composable
private fun ServiceCard(
    service: ArtistServiceDto,
    dayOffer: com.piums.cliente.data.remote.dto.ServiceDayOffer? = null,
    onBook: () -> Unit,
    onDetail: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.15f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(service.name ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        if (service.isMainService == true) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PiumsOrange.copy(0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Principal", style = MaterialTheme.typography.labelSmall,
                                    color = PiumsOrange)
                            }
                        }
                    }
                    service.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val duration = service.durationMin?.let {
                        if (service.durationMax != null && service.durationMax != it)
                            "$it–${service.durationMax} min"
                        else "$it min"
                    }
                    duration?.let {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.AccessTime, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                modifier = Modifier.size(11.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(service.formattedPrice,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, color = PiumsOrange)
                    dayOffer?.badgeLabel?.let { badge ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF22C55E).copy(0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(badge, style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF22C55E), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Botones: Ver detalles + Reservar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDetail,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PiumsOrange.copy(0.5f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Ver detalles", color = PiumsOrange,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438))))
                        .clickable(onClick = onBook),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null,
                            tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("Reservar", color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ─── Reviews ──────────────────────────────────────────────────────────────────

@Composable
private fun ReviewItem(review: ReviewDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PiumsOrange.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        review.clientName?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = PiumsOrange
                    )
                }
                Text(
                    review.clientName ?: "Cliente",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            // Stars
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { i ->
                    Icon(
                        if (i < review.rating.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                        null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        review.comment?.takeIf { it.isNotBlank() }?.let { comment ->
            Spacer(Modifier.height(4.dp))
            Text(comment, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.7f),
                lineHeight = 18.sp)
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.outline.copy(0.08f)
        )
    }
}

// ─── Social Links ─────────────────────────────────────────────────────────────

@Composable
private fun SocialLinksSection(instagram: String?, website: String?) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
        Text(
            "Redes Sociales",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
            modifier   = Modifier.padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            instagram?.let { handle ->
                val url = if (handle.startsWith("http")) handle
                          else "https://instagram.com/${handle.trimStart('@')}"
                SocialChip(
                    icon    = Icons.Default.PhotoCamera,
                    label   = "Instagram",
                    onClick = { runCatching { uriHandler.openUri(url) } }
                )
            }
            website?.let { site ->
                val url = if (site.startsWith("http")) site else "https://$site"
                SocialChip(
                    icon    = Icons.Default.Language,
                    label   = "Sitio web",
                    onClick = { runCatching { uriHandler.openUri(url) } }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SocialChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PiumsOrange.copy(0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = PiumsOrange, modifier = Modifier.size(15.dp))
        Text(label,
            style      = MaterialTheme.typography.labelMedium,
            color      = PiumsOrange,
            fontWeight = FontWeight.SemiBold)
    }
}

// ─── Certifications ───────────────────────────────────────────────────────────

@Composable
private fun CertificationsSection(certs: List<CertificationDto>) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        certs.forEach { cert ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = PiumsOrange,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(cert.name ?: "", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    val meta = listOfNotNull(cert.issuer, cert.issueYear)
                        .joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                }
                if (!cert.certificateUrl.isNullOrBlank()) {
                    IconButton(onClick = {
                        runCatching { uriHandler.openUri(cert.certificateUrl) }
                    }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Ver certificado",
                            tint = PiumsOrange, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ─── Portfolio ────────────────────────────────────────────────────────────────

@Composable
private fun PortfolioGrid(items: List<PortfolioItem>) {
    val uriHandler = LocalUriHandler.current
    var fullscreenIndex by remember { mutableIntStateOf(-1) }
    val visible = items.take(9)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        visible.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item ->
                    val itemIndex = visible.indexOf(item)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (item.isVideo) {
                                    val ytId = item.youtubeVideoId
                                    if (ytId != null) {
                                        runCatching {
                                            uriHandler.openUri("https://www.youtube.com/watch?v=$ytId")
                                        }
                                    } else {
                                        runCatching { uriHandler.openUri(item.url) }
                                    }
                                } else {
                                    fullscreenIndex = itemIndex
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val imageUrl = if (item.isVideo) item.youtubeThumbnailUrl ?: item.url
                                       else item.url
                        AsyncImage(
                            model              = imageUrl,
                            contentDescription = item.caption,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                        if (item.isVideo) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Video",
                                    tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                // Fill remaining cells in last row
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // Fullscreen image viewer
    if (fullscreenIndex >= 0) {
        val imageItems = visible.filter { !it.isVideo }
        val initialPage = visible.filterIndexed { i, it -> i < fullscreenIndex && !it.isVideo }.size
            .coerceAtMost(imageItems.size - 1).coerceAtLeast(0)

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreenIndex = -1 },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenIndex = -1 },
                contentAlignment = Alignment.Center
            ) {
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = initialPage,
                    pageCount = { imageItems.size }
                )
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    AsyncImage(
                        model              = imageItems[page].url,
                        contentDescription = imageItems[page].caption,
                        modifier           = Modifier
                            .fillMaxSize()
                            .clickable { /* consume click — no cerrar al tocar imagen */ },
                        contentScale       = ContentScale.Fit
                    )
                }
                // Cerrar
                IconButton(
                    onClick = { fullscreenIndex = -1 },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar",
                        tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// ─── ServiceDetailSheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceDetailSheet(
    service: ArtistServiceDto,
    onDismiss: () -> Unit,
    onBook: () -> Unit
) {
    val pricingLabel = when (service.pricingType) {
        "FIXED"   -> "Precio fijo"
        "HOURLY"  -> "Por hora"
        "PACKAGE" -> "Paquete"
        else      -> null
    }
    val durationLabel = service.durationMin?.let { min ->
        if (service.durationMax != null && service.durationMax != min)
            "$min–${service.durationMax} min"
        else if (min >= 60) {
            val h = min / 60; val m = min % 60
            if (m == 0) "$h h" else "$h h $m min"
        } else "$min min"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabecera servicio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PiumsOrange.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = PiumsOrange,
                        modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(service.name ?: "", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    service.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(service.formattedPrice, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = PiumsOrange)
            }

            // Duración + tipo de precio
            if (durationLabel != null || pricingLabel != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    durationLabel?.let {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.AccessTime, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                modifier = Modifier.size(14.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }
                    pricingLabel?.let {
                        Text("·", color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                }
            }

            // Qué incluye
            val included = service.whatIsIncluded?.filter { it.isNotBlank() } ?: emptyList()
            if (included.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Qué incluye", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                    included.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = PiumsOrange,
                                modifier = Modifier.size(16.dp))
                            Text(item, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

            // Botón Reservar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438))))
                    .clickable(onClick = onBook),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CalendarMonth, null,
                        tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Reservar", color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// ─── WriteReviewSheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteReviewSheet(
    artistName: String,
    bookings: List<BookingDto>,
    selectedBookingId: String?,
    rating: Int,
    comment: String,
    isLoading: Boolean,
    isSubmitting: Boolean,
    error: String?,
    success: Boolean,
    onSelectBooking: (String) -> Unit,
    onRatingChange: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PiumsOrange)
                    }
                }
                success -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = PiumsOrange,
                            modifier = Modifier.size(52.dp))
                        Text("¡Reseña enviada!", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Gracias por compartir tu experiencia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar", color = PiumsOrange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                bookings.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.EventBusy, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                            modifier = Modifier.size(44.dp))
                        Text("Sin reservas completadas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text("Solo puedes dejar una reseña después de completar una reserva con este artista.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                            textAlign = TextAlign.Center)
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar", color = PiumsOrange)
                        }
                    }
                }
                else -> {
                    // Título
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Escribir reseña", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text(artistName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    }

                    // Selector de reserva (si hay más de una)
                    if (bookings.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Reserva a reseñar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold)
                            bookings.forEach { booking ->
                                val sel = selectedBookingId == booking.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (sel) PiumsOrange.copy(0.1f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { onSelectBooking(booking.id) }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        null,
                                        tint = if (sel) PiumsOrange else MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(booking.resolvedArtistName ?: artistName ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold)
                                        Text((booking.scheduledDate ?: "").take(10),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                    }
                                }
                            }
                        }
                    }

                    // Estrellas
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Calificación", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(5) { i ->
                                Icon(
                                    if (i < rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { onRatingChange(i + 1) }
                                )
                            }
                        }
                        val ratingLabel = listOf("", "Malo", "Regular", "Bueno", "Muy bueno", "Excelente")
                            .getOrElse(rating) { "" }
                        if (ratingLabel.isNotEmpty()) {
                            Text(ratingLabel, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }

                    // Comentario
                    OutlinedTextField(
                        value = comment,
                        onValueChange = onCommentChange,
                        label = { Text("Comentario (opcional)") },
                        placeholder = { Text("Cuéntanos tu experiencia con el artista...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PiumsOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                        )
                    )

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }

                    // Botón enviar
                    Button(
                        onClick = onSubmit,
                        enabled = rating > 0 && !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White,
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Enviar reseña", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
