package com.piums.cliente.data.remote.dto

data class CertificationDto(
    val id: String,
    val artistId: String? = null,
    val name: String,
    val issuer: String? = null,
    val issueDate: String? = null,
    val expiresAt: String? = null,
    val certificateUrl: String? = null
) {
    val issueYear: String? get() = issueDate?.take(4)
}

// Handles: {"artist": {...}}, {"data": {...}}, or direct ArtistDto at root
// Backend wraps single-artist responses in different keys depending on the endpoint
// certifications puede venir en la raíz o dentro del objeto artista
data class ArtistDetailResponse(
    val artist: ArtistDto?,
    val data: ArtistDto?,
    val user: ArtistDto?,
    val certifications: List<CertificationDto>? = null
) {
    val resolved: ArtistDto? get() = artist ?: data ?: user
    val resolvedCertifications: List<CertificationDto>
        get() = resolved?.certifications ?: certifications ?: emptyList()
}

data class ArtistDto(
    val id: String? = null,
    val name: String? = null,
    val nombre: String? = null,
    val bio: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val averageRating: Double?,
    val totalReviews: Int = 0,
    val totalBookings: Int = 0,
    val mainServicePrice: Int?,
    val mainServiceName: String?,
    val hourlyRateMin: Int?,
    val hourlyRateMax: Int?,
    val isVerified: Boolean = false,
    val isActive: Boolean = true,
    val isAvailable: Boolean = true,
    val servicesCount: Int = 0,
    val specialties: List<String>?,
    val serviceIds: List<String>?,
    val serviceTitles: List<String>?,
    val baseLocationLat: Double?,
    val baseLocationLng: Double?,
    val createdAt: String?,
    @com.google.gson.annotations.SerializedName(value = "avatar", alternate = ["avatarUrl"])
    val avatar: String? = null,
    @com.google.gson.annotations.SerializedName(value = "coverUrl", alternate = ["coverImage", "coverPhoto"])
    val coverUrl: String? = null,
    val instagram: String? = null,
    val website: String? = null,
    val certifications: List<CertificationDto>? = null,
    val hasSoundSystem: Boolean? = null
) {
    val resolvedId: String get() = id.orEmpty()
    val displayName: String get() = name?.takeIf { it.isNotBlank() }
        ?: nombre?.takeIf { it.isNotBlank() } ?: "Artista"
    val rating: Double get() = averageRating ?: 0.0
    val avatarUrl: String? get() = avatar ?: coverUrl
    val formattedPrice: String? get() = mainServicePrice?.let { "$${String.format("%,.2f", it.toDouble())}" }
}

data class SearchArtistsResponse(
    val artists: List<ArtistDto>?,
    val data: List<ArtistDto>?,
    val pagination: PaginationDto?
) {
    val list: List<ArtistDto> get() = artists ?: data ?: emptyList()
}

data class SmartSearchResponse(
    val artists: List<ArtistDto>?,
    val data: List<ArtistDto>?,
    val expandedTerms: List<String>?,
    val pagination: PaginationDto?
) {
    val list: List<ArtistDto> get() = artists ?: data ?: emptyList()
}

data class PaginationDto(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
) {
    val hasMore: Boolean get() = page < totalPages
}

data class ArtistServiceDto(
    val id: String,
    val artistId: String?,
    val name: String,
    val description: String?,
    val pricingType: String?,
    val basePrice: Int = 0,
    val currency: String = "USD",
    val durationMin: Int?,
    val durationMax: Int?,
    val status: String?,
    val isAvailable: Boolean?,
    val isMainService: Boolean?,
    val whatIsIncluded: List<String>?
) {
    val price: Int get() = basePrice
    val duration: Int get() = durationMin ?: 60
    val formattedPrice: String get() = "$${String.format("%,.2f", basePrice / 100.0)}"
}

data class ArtistServicesResponse(
    val services: List<ArtistServiceDto>?,
    val data: List<ArtistServiceDto>?
) {
    val list: List<ArtistServiceDto> get() = services ?: data ?: emptyList()
}

data class PortfolioItem(
    val id: String,
    val url: String,
    val caption: String?,
    val type: String? = null  // "image" | "video" — null = tratar como imagen
) {
    val isVideo: Boolean get() = type == "video" ||
        url.contains("youtube.com", ignoreCase = true) ||
        url.contains("youtu.be", ignoreCase = true)

    val youtubeVideoId: String? get() {
        if (!isVideo) return null
        val watchMatch = Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]+)").find(url)
        return watchMatch?.groupValues?.getOrNull(1)
    }
    val youtubeThumbnailUrl: String? get() = youtubeVideoId?.let {
        "https://img.youtube.com/vi/$it/mqdefault.jpg"
    }
}

data class PortfolioResponse(
    val portfolio: List<PortfolioItem>?,
    val items: List<PortfolioItem>?
) {
    val list: List<PortfolioItem> get() = portfolio ?: items ?: emptyList()
}

data class ReviewDto(
    val id: String,
    val clientId: String?,
    val artistId: String?,
    val bookingId: String?,
    val rating: Double,
    val comment: String?,
    val createdAt: String,
    val clientName: String?,
    val status: String?
)

data class ReviewsResponse(
    val reviews: List<ReviewDto>?,
    val data: List<ReviewDto>?,
    val pagination: PaginationDto?
) {
    val list: List<ReviewDto> get() = reviews ?: data ?: emptyList()
}

data class CreateReviewRequest(
    val artistId: String,
    val bookingId: String,
    val rating: Int,
    val comment: String?
)

data class AvailabilityCalendarResponse(
    val blockedDates: List<String>?,
    val occupiedDates: List<String>?,
    val bookedDates: List<String>?
) {
    val allBlocked: Set<String> get() =
        ((blockedDates ?: emptyList()) + (occupiedDates ?: emptyList()) + (bookedDates ?: emptyList())).toSet()
}

data class TimeSlot(val time: String, val available: Boolean = true)

data class TimeSlotsResponse(
    val slots: List<TimeSlot>?,
    val availableSlots: List<String>?
)

data class PricingCalculateRequest(
    val serviceId: String,
    val scheduledDate: String,
    val duration: Int,
    val locationLat: Double?,
    val locationLng: Double?,
    val distanceKm: Double?,
    val numDays: Int = 1
)

data class PricingCalculateResponse(
    val totalCents: Int?,
    val total: Int?,
    val breakdown: PricingBreakdown?,
    val items: List<PriceLineItem>?
) {
    val totalAmount: Int get() = totalCents ?: total ?: 0
}

data class PricingBreakdown(val baseCents: Int?, val travelCents: Int?)

data class PriceLineItem(
    val type: String,
    val name: String,
    val totalPriceCents: Int
)
