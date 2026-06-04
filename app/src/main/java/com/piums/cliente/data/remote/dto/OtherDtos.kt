package com.piums.cliente.data.remote.dto

// ── Notifications ─────────────────────────────────────────────────────────────

data class NotificationDto(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val readAt: String?,
    val entityId: String?,
    val entityType: String?,
    val createdAt: String
) {
    val isRead: Boolean get() = readAt != null
}

data class NotificationsResponse(
    val notifications: List<NotificationDto>?,
    val data: List<NotificationDto>?,
    val pagination: PaginationDto?
) {
    val list: List<NotificationDto> get() = notifications ?: data ?: emptyList()
}

data class MarkNotificationsReadRequest(val notificationIds: List<String>)

data class NotifPreferencesDto(
    val emailEnabled: Boolean = true,
    val smsEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val bookingNotifications: Boolean = true,
    val paymentNotifications: Boolean = true,
    val reviewNotifications: Boolean = true,
    val marketingNotifications: Boolean = false,
    val dndEnabled: Boolean = false,
    val dndStartHour: Int = 22,
    val dndEndHour: Int = 8
)

data class UpdateNotifPreferencesRequest(
    val emailEnabled: Boolean,
    val smsEnabled: Boolean,
    val pushEnabled: Boolean,
    val bookingNotifications: Boolean,
    val paymentNotifications: Boolean,
    val reviewNotifications: Boolean,
    val marketingNotifications: Boolean,
    val dndEnabled: Boolean,
    val dndStartHour: Int,
    val dndEndHour: Int
)

// ── Disputes ──────────────────────────────────────────────────────────────────

data class DisputeDto(
    val id: String,
    val bookingId: String,
    val reportedBy: String,
    val disputeType: String,
    val subject: String,
    val description: String,
    val status: String,
    val priority: Int?,
    val resolution: String?,
    val refundAmount: Int?,
    val createdAt: String,
    val messages: List<DisputeMessageDto>?
) {
    val statusEnum: DisputeStatus get() = DisputeStatus.from(status)
}

enum class DisputeStatus(val raw: String, val displayName: String) {
    OPEN("OPEN", "Abierta"),
    IN_REVIEW("IN_REVIEW", "En revisión"),
    AWAITING_INFO("AWAITING_INFO", "Esperando info"),
    RESOLVED("RESOLVED", "Resuelta"),
    CLOSED("CLOSED", "Cerrada"),
    ESCALATED("ESCALATED", "Escalada");
    companion object { fun from(raw: String) = values().firstOrNull { it.raw == raw } ?: OPEN }
}

data class DisputeMessageDto(
    val id: String,
    val disputeId: String,
    val senderId: String,
    val senderRole: String,
    val message: String,
    val createdAt: String
)

data class DisputesResponse(
    val asReporter: List<DisputeDto>?,
    val asReported: List<DisputeDto>?,
    val disputes: List<DisputeDto>?
) {
    val all: List<DisputeDto> get() =
        ((asReporter ?: emptyList()) + (asReported ?: emptyList()) + (disputes ?: emptyList()))
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
}

data class CreateDisputeRequest(
    val bookingId: String,
    val disputeType: String,
    val subject: String,
    val description: String
)

data class AddDisputeMessageRequest(val message: String)

// ── Events ────────────────────────────────────────────────────────────────────

data class EventDto(
    val id: String,
    val code: String?,
    val clientId: String,
    val name: String,
    val description: String?,
    val location: String?,
    val notes: String?,
    val eventDate: String?,
    val status: String,
    val createdAt: String,
    val bookings: List<BookingDto>?
) {
    val totalCents: Int get() = bookings?.sumOf { it.totalPrice } ?: 0
    val formattedTotal: String get() = "Q${String.format("%,.2f", totalCents / 100.0)}"
}

data class EventsResponse(
    val events: List<EventDto>?,
    val data: List<EventDto>?
) {
    val list: List<EventDto> get() = events ?: data ?: emptyList()
}

data class CreateEventRequest(
    val name: String,
    val eventDate: String?,
    val location: String?,
    val notes: String?,
    val description: String?
)

// ── Favorites ─────────────────────────────────────────────────────────────────

// ── Coupons ───────────────────────────────────────────────────────────────────

data class CouponDto(
    val id: String,
    val code: String,
    val name: String? = null,
    val discountType: String,   // "PERCENTAGE" | "FIXED"
    val discountValue: Double,
    val minimumAmount: Double?,
    val expiresAt: String?,
    val isActive: Boolean,
    val description: String?,
    val maxUses: Int? = null,
    val maxUsesPerUser: Int? = null
) {
    val formattedDiscount: String get() = if (discountType == "PERCENTAGE")
        "${discountValue.toInt()}%" else "$${String.format("%.2f", discountValue)}"
    val isExpired: Boolean get() = expiresAt?.let {
        runCatching { java.time.Instant.parse(it).isBefore(java.time.Instant.now()) }.getOrElse { false }
    } ?: false
}

data class CouponValidateRequest(
    val code: String,
    val artistId: String,
    val serviceId: String,
    val bookingTotal: Int
)

data class CouponValidateResponse(
    val valid: Boolean,
    val discount: Int = 0,
    val error: String? = null
)

data class CouponsResponse(
    val coupons: List<CouponDto>?,
    val data: List<CouponDto>?
) {
    val list: List<CouponDto> get() = (coupons ?: data ?: emptyList())
        .filter { it.isActive && !it.isExpired }
}

// ── Favorites ─────────────────────────────────────────────────────────────────

data class FavoriteDto(
    val id: String,
    val entityType: String,
    val entityId: String,
    val notes: String?,
    val artist: ArtistDto?
)

data class FavoritesResponse(
    val favorites: List<FavoriteDto>?,
    val data: List<FavoriteDto>?
) {
    val list: List<FavoriteDto> get() = favorites ?: data ?: emptyList()
}

data class AddFavoriteRequest(
    val entityType: String = "ARTIST",
    val entityId: String,
    val notes: String? = null
)

data class FavoriteCheckResponse(val isFavorite: Boolean = false, val favoriteId: String?)

// ── Payments ──────────────────────────────────────────────────────────────────

data class PaymentDto(
    val id: String,
    val bookingId: String?,
    val amount: Int,
    val status: String,
    val description: String?,
    val createdAt: String
) {
    val formattedAmount: String get() = "$${String.format("%,.2f", amount / 100.0)}"
}

// El backend de pagos devuelve "pages" (no "totalPages") en su paginación
data class PaymentsPagination(
    val page: Int,
    val limit: Int? = null,
    val total: Int? = null,
    val pages: Int
) {
    val hasMore: Boolean get() = page < pages
}

data class PaymentsResponse(
    val payments: List<PaymentDto>?,
    val data: List<PaymentDto>?,
    val pagination: PaymentsPagination? = null
) {
    val list: List<PaymentDto> get() = payments ?: data ?: emptyList()
}

data class PaymentIntentRequest(
    val bookingId: String,
    val amount: Int? = null,
    val currency: String? = null,
    val countryCode: String? = null,
    val billingFirst: String? = null,
    val billingLast: String? = null
)

data class PaymentIntentDto(
    val id: String = "",
    val providerRef: String? = null,
    val redirectUrl: String? = null,
    val clientSecret: String? = null,
    val provider: String? = null,
    val status: String = "CREATED"
) {
    val isTilopay: Boolean get() = provider == "TILOPAY" || redirectUrl != null
}

data class PaymentIntentResponse(
    val paymentIntent: PaymentIntentDto? = null,
    val redirectUrl: String? = null,
    val clientSecret: String? = null,
    val provider: String? = null,
    val status: String? = null
) {
    val resolved: PaymentIntentDto? get() {
        paymentIntent?.let { return it }
        if (redirectUrl == null && clientSecret == null) return null
        return PaymentIntentDto(
            redirectUrl  = redirectUrl,
            clientSecret = clientSecret,
            provider     = provider,
            status       = status ?: "CREATED"
        )
    }
}

data class TilopayCallbackParams(
    val bookingId: String,
    val responseCode: String,
    val orderNumber: String,
    val amount: String,
    val auth: String?,
    val currency: String?,
    val orderHash: String?
) {
    val isApproved: Boolean get() = responseCode == "00"
}

data class TilopayConfirmRequest(
    val bookingId: String,
    val responseCode: String,
    val orderNumber: String,
    val amount: String,
    val auth: String? = null,
    val currency: String? = null,
    val orderHash: String? = null
)

data class TilopayConfirmResponse(
    val success: Boolean? = null,
    val responseCode: String? = null
)

data class PaymentMethodDto(
    val id: String,
    val brand: String?,
    val last4: String?,
    val expiryMonth: Int?,
    val expiryYear: Int?,
    val isDefault: Boolean = false
) {
    val displayBrand: String get() = brand?.replaceFirstChar { it.uppercase() } ?: "Tarjeta"
    val maskedNumber: String get() = last4?.let { "•••• $it" } ?: "—"
    val expiryDisplay: String get() = if (expiryMonth != null && expiryYear != null)
        "${expiryMonth.toString().padStart(2, '0')}/${expiryYear % 100}" else ""
}

data class PaymentMethodsResponse(
    val methods: List<PaymentMethodDto>?,
    val data: List<PaymentMethodDto>?,
    val paymentMethods: List<PaymentMethodDto>?
) {
    val list: List<PaymentMethodDto> get() = methods ?: paymentMethods ?: data ?: emptyList()
}

data class MyCreditsResponse(
    val totalAmount: Int = 0,
    val currency: String = "USD"
) {
    val formattedAmount: String get() = "$${String.format("%,.2f", totalAmount / 100.0)}"
}

data class AvatarUploadResponse(
    val avatarUrl: String?,
    val avatar: String?
) {
    val url: String? get() = avatarUrl ?: avatar
}

// ── Tickets y Eventos ─────────────────────────────────────────────────────────

data class TicketEvent(
    val id: String,
    val code: String,
    val artistId: String,
    val name: String,
    val description: String? = null,
    val venue: String,
    val address: String,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val eventDate: String,
    val doorsOpen: String? = null,
    val imageUrl: String? = null,
    val maxCapacity: Int,
    val status: String,
    val tiers: List<TicketTier> = emptyList(),
    val createdAt: String
) {
    val isPublished: Boolean get() = status == "PUBLICADO"
    val isSoldOut: Boolean   get() = status == "AGOTADO"
    val minPriceCents: Int   get() = tiers.minOfOrNull { it.priceCents } ?: 0
    val formattedMinPrice: String get() = "$${String.format("%,.2f", minPriceCents / 100.0)}"
}

data class TicketTier(
    val id: String,
    val ticketEventId: String,
    val name: String,
    val description: String? = null,
    val priceCents: Int,
    val currency: String = "USD",
    val totalQty: Int,
    val soldQty: Int
) {
    val available: Int    get() = totalQty - soldQty
    val isSoldOut: Boolean get() = available <= 0
    val formattedPrice: String get() = "$${String.format("%,.2f", priceCents / 100.0)}"
}

data class TicketPurchase(
    val id: String,
    val code: String,
    val ticketEventId: String,
    val tierId: String,
    val buyerId: String,
    val buyerEmail: String,
    val buyerName: String,
    val quantity: Int,
    val subtotalCents: Int,
    val discountCents: Int = 0,
    val totalCents: Int,
    val currency: String = "USD",
    val couponCode: String? = null,
    val status: String,
    val paidAt: String? = null,
    val checkedInAt: String? = null,
    val ticketEvent: TicketEvent? = null,
    val tier: TicketTier? = null,
    val createdAt: String
) {
    val isPaid: Boolean get() = status == "PAGADO"
    val isUpcoming: Boolean get() {
        val d = ticketEvent?.eventDate ?: return false
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .parse(d.take(19))
        }.getOrNull()?.after(java.util.Date()) == true
    }
    val formattedTotal: String get() = "$${String.format("%,.2f", totalCents / 100.0)}"
}

data class TicketEventsResponse(
    val events: List<TicketEvent>? = null,
    val data: List<TicketEvent>? = null,
    val pagination: PaginationDto? = null
) {
    val all: List<TicketEvent> get() = events ?: data ?: emptyList()
}

data class TicketPurchasesResponse(
    val purchases: List<TicketPurchase>? = null,
    val data: List<TicketPurchase>? = null
) {
    val all: List<TicketPurchase> get() = purchases ?: data ?: emptyList()
}

data class TicketPurchaseWrapper(
    val purchase: TicketPurchase? = null,
    val data: TicketPurchase? = null
) {
    val resolved: TicketPurchase? get() = purchase ?: data
}

data class TicketPurchaseCheckoutResponse(
    val purchase: TicketPurchase? = null,
    val purchaseId: String? = null,
    val redirectUrl: String? = null
)

// ── Ofertas del Día ───────────────────────────────────────────────────────────

data class ServiceDayOffer(
    val id: String,
    val serviceId: String,
    val artistId: String,
    val discountPercent: Int? = null,
    val discountAmount: Int? = null,
    val note: String? = null,
    val isActive: Boolean,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val createdAt: String
) {
    val badgeLabel: String? get() {
        return discountPercent?.let { "-$it%" }
            ?: discountAmount?.let { "-$${String.format("%,.2f", it / 100.0)}" }
    }
}

data class ServiceDayOffersResponse(
    val offers: List<ServiceDayOffer>? = null,
    val data: List<ServiceDayOffer>? = null
) {
    val all: List<ServiceDayOffer> get() = offers ?: data ?: emptyList()
}

// ── Colaboradores de Reserva ──────────────────────────────────────────────────

data class BookingCollaborator(
    val id: String,
    val bookingId: String,
    val artistId: String,
    val invitedBy: String,
    val role: String? = null,
    val status: String,
    val notes: String? = null,
    val invitedAt: String,
    val respondedAt: String? = null,
    val artistName: String? = null,
    val artistAvatar: String? = null
)

data class BookingCollaboratorsResponse(
    val collaborators: List<BookingCollaborator>? = null,
    val data: List<BookingCollaborator>? = null
) {
    val all: List<BookingCollaborator> get() = collaborators ?: data ?: emptyList()
}

// ── Google Calendar ───────────────────────────────────────────────────────────

data class GoogleCalendarStatusResponse(
    @com.google.gson.annotations.SerializedName("enabled") val connected: Boolean = false,
    val email: String? = null,
    val calendarEmail: String? = null
)
