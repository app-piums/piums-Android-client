package com.piums.cliente.data.remote

import com.piums.cliente.data.remote.dto.*
import retrofit2.http.*

interface PiumsApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register/client")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/auth/firebase")
    suspend fun loginWithFirebase(@Body body: FirebaseAuthRequest): AuthResponse

    @PATCH("api/auth/refresh")
    suspend fun refreshToken(@Body body: RefreshRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout()

    @GET("api/auth/me")
    suspend fun getMe(): MeResponse

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest)

    @PATCH("api/auth/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): MeResponse

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @POST("api/auth/complete-onboarding")
    suspend fun completeOnboarding()

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: String)

    @Multipart
    @POST("api/users/me/avatar")
    suspend fun uploadAvatar(@Part avatar: okhttp3.MultipartBody.Part): AvatarUploadResponse

    @Multipart
    @POST("api/users/documents/upload")
    suspend fun uploadDocument(
        @Query("folder") folder: String,
        @Part file: okhttp3.MultipartBody.Part
    )

    @GET("api/payments/methods")
    suspend fun getPaymentMethods(): PaymentMethodsResponse

    @DELETE("api/payments/methods/{id}")
    suspend fun deletePaymentMethod(@Path("id") id: String)

    @PATCH("api/payments/methods/{id}/default")
    suspend fun setDefaultPaymentMethod(@Path("id") id: String): PaymentMethodDto

    @GET("api/payments/credits/me")
    suspend fun getMyCredits(): MyCreditsResponse

    // ── Search & Artists ──────────────────────────────────────────────────────
    @GET("api/search/artists")
    suspend fun searchArtists(
        @Query("page")       page: Int = 1,
        @Query("limit")      limit: Int = 20,
        @Query("q")          q: String? = null,
        @Query("category")   specialty: String? = null,
        @Query("city")       city: String? = null,
        @Query("minPrice")   minPrice: Int? = null,
        @Query("maxPrice")   maxPrice: Int? = null,
        @Query("minRating")  minRating: Double? = null,
        @Query("isVerified") isVerified: Boolean? = null,
        @Query("sortBy")     sortBy: String? = null,
        @Query("sortOrder")  sortOrder: String? = null
    ): SearchArtistsResponse

    @GET("api/search/smart")
    suspend fun smartSearch(
        @Query("q")          q: String,
        @Query("page")       page: Int = 1,
        @Query("limit")      limit: Int = 20,
        @Query("lat")        lat: Double? = null,
        @Query("lng")        lng: Double? = null,
        @Query("city")       city: String? = null,
        @Query("specialty")  specialty: String? = null,
        @Query("minPrice")   minPrice: Int? = null,
        @Query("maxPrice")   maxPrice: Int? = null,
        @Query("minRating")  minRating: Double? = null,
        @Query("isVerified") isVerified: Boolean? = null
    ): SmartSearchResponse

    @GET("api/artists/{id}")
    suspend fun getArtist(@Path("id") id: String): ArtistDetailResponse

    @GET("api/artists/{id}/portfolio")
    suspend fun getArtistPortfolio(@Path("id") id: String): PortfolioResponse

    @GET("api/catalog/services")
    suspend fun getArtistServices(@Query("artistId") artistId: String): ArtistServicesResponse

    // ── Availability & Pricing ────────────────────────────────────────────────
    @GET("api/availability/calendar")
    suspend fun getAvailabilityCalendar(
        @Query("artistId") artistId: String,
        @Query("year")     year: Int,
        @Query("month")    month: Int
    ): AvailabilityCalendarResponse

    @GET("api/availability/time-slots")
    suspend fun getTimeSlots(
        @Query("artistId") artistId: String,
        @Query("date")     date: String
    ): TimeSlotsResponse

    @POST("api/catalog/pricing/calculate")
    suspend fun calculatePricing(@Body body: PricingCalculateRequest): PricingCalculateResponse

    // ── Bookings ──────────────────────────────────────────────────────────────
    @POST("api/bookings")
    suspend fun createBooking(@Body body: CreateBookingRequest): BookingDto

    @GET("api/bookings")
    suspend fun getBookings(
        @Query("page")   page: Int = 1,
        @Query("limit")  limit: Int = 20,
        @Query("status") status: String? = null
    ): BookingsListResponse

    @GET("api/bookings/{id}")
    suspend fun getBooking(@Path("id") id: String): BookingDto

    @POST("api/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: String)

    @POST("api/bookings/{id}/no-show")
    suspend fun reportNoShow(@Path("id") id: String): BookingDto

    @POST("api/bookings/{id}/reschedule")
    suspend fun rescheduleBooking(@Path("id") id: String, @Body body: RescheduleRequest): BookingDto

    // ── Reviews ───────────────────────────────────────────────────────────────
    @GET("api/reviews")
    suspend fun getReviews(
        @Query("artistId") artistId: String,
        @Query("page")     page: Int = 1,
        @Query("limit")    limit: Int = 10
    ): ReviewsResponse

    @POST("api/reviews")
    suspend fun createReview(@Body body: CreateReviewRequest): ReviewDto

    // ── Favorites ─────────────────────────────────────────────────────────────
    @GET("api/users/me/favorites")
    suspend fun getFavorites(
        @Query("page")       page: Int = 1,
        @Query("limit")      limit: Int = 50,
        @Query("entityType") entityType: String = "ARTIST"
    ): FavoritesResponse

    @POST("api/users/me/favorites")
    suspend fun addFavorite(@Body body: AddFavoriteRequest): FavoriteDto

    @DELETE("api/users/me/favorites/{id}")
    suspend fun removeFavorite(@Path("id") id: String)

    @GET("api/users/me/favorites/check")
    suspend fun checkFavorite(
        @Query("entityType") entityType: String = "ARTIST",
        @Query("entityId")   entityId: String
    ): FavoriteCheckResponse

    // ── Notifications ─────────────────────────────────────────────────────────
    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("page")  page: Int = 1,
        @Query("limit") limit: Int = 20
    ): NotificationsResponse

    @POST("api/notifications/read")
    suspend fun markNotificationsRead(@Body body: MarkNotificationsReadRequest)

    @POST("api/notifications/push-token")
    suspend fun registerPushToken(@Body body: FcmTokenRequest)

    @GET("api/notifications/preferences")
    suspend fun getNotifPreferences(): NotifPreferencesDto

    @PUT("api/notifications/preferences")
    suspend fun updateNotifPreferences(@Body body: UpdateNotifPreferencesRequest): NotifPreferencesDto

    // ── Disputes ──────────────────────────────────────────────────────────────
    @GET("api/disputes/me")
    suspend fun getDisputes(): DisputesResponse

    @POST("api/disputes")
    suspend fun createDispute(@Body body: CreateDisputeRequest): DisputeDto

    @GET("api/disputes/{id}")
    suspend fun getDispute(@Path("id") id: String): DisputeDto

    @POST("api/disputes/{id}/messages")
    suspend fun addDisputeMessage(
        @Path("id") id: String,
        @Body body: AddDisputeMessageRequest
    ): DisputeMessageDto

    // ── Events ────────────────────────────────────────────────────────────────
    @GET("api/events")
    suspend fun getEvents(): EventsResponse

    @POST("api/events")
    suspend fun createEvent(@Body body: CreateEventRequest): SingleEventResponse

    @GET("api/events/{id}")
    suspend fun getEvent(@Path("id") id: String): SingleEventResponse

    @PATCH("api/events/{id}")
    suspend fun updateEvent(@Path("id") id: String, @Body body: CreateEventRequest): SingleEventResponse

    @DELETE("api/events/{id}")
    suspend fun deleteEvent(@Path("id") id: String)

    @POST("api/events/{eventId}/bookings/{bookingId}")
    suspend fun linkBookingToEvent(
        @Path("eventId")   eventId: String,
        @Path("bookingId") bookingId: String
    )

    // ── Chat ──────────────────────────────────────────────────────────────────
    @GET("api/chat/conversations")
    suspend fun getConversations(
        @Query("page")  page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ConversationsResponse

    @GET("api/chat/conversations/{id}")
    suspend fun getConversation(@Path("id") id: String): ConversationDto

    @PATCH("api/chat/conversations/{id}/read")
    suspend fun markConversationRead(@Path("id") id: String)

    @GET("api/chat/messages/{conversationId}")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("page")          page: Int = 1,
        @Query("limit")         limit: Int = 50
    ): MessagesResponse

    @POST("api/chat/messages")
    suspend fun sendMessage(@Body body: SendMessageRequest): ChatMessageDto

    @GET("api/chat/messages/unread-count")
    suspend fun getUnreadCount(): UnreadCountResponse

    // ── Coupons ───────────────────────────────────────────────────────────────
    @GET("api/coupons/my")
    suspend fun getCoupons(): CouponsResponse

    @POST("api/coupons/validate")
    suspend fun validateCoupon(@Body body: CouponValidateRequest): CouponValidateResponse

    // ── Payments ──────────────────────────────────────────────────────────────
    @POST("api/payments/checkout")
    suspend fun createPaymentIntent(@Body body: PaymentIntentRequest): PaymentIntentResponse

    @POST("api/payments/tilopay/confirm")
    suspend fun confirmTilopayRedirect(@Body body: TilopayConfirmRequest): TilopayConfirmResponse

    @GET("api/payments")
    suspend fun getPayments(@Query("page") page: Int = 1): PaymentsResponse

    @GET("api/payments/{id}")
    suspend fun getPayment(@Path("id") id: String): PaymentDto

    // ── Tickets ───────────────────────────────────────────────────────────────
    @GET("api/ticket-events")
    suspend fun listTicketEvents(
        @Query("page")  page: Int  = 1,
        @Query("limit") limit: Int = 12
    ): TicketEventsResponse

    @POST("api/ticket-events/{eventId}/purchase")
    suspend fun purchaseTicket(
        @Path("eventId") eventId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): TicketPurchaseCheckoutResponse

    @GET("api/ticket-purchases/my")
    suspend fun myTicketPurchases(): TicketPurchasesResponse

    @GET("api/ticket-purchases/{id}")
    suspend fun getTicketPurchase(@Path("id") id: String): TicketPurchaseWrapper

    // ── Colaboradores ─────────────────────────────────────────────────────────
    @GET("api/bookings/{id}/collaborators")
    suspend fun getBookingCollaborators(@Path("id") id: String): BookingCollaboratorsResponse

    // ── Day Offers ────────────────────────────────────────────────────────────
    @GET("api/catalog/services/{serviceId}/day-offers/public")
    suspend fun getDayOffers(@Path("serviceId") serviceId: String): ServiceDayOffersResponse

    // ── Replacement flow ─────────────────────────────────────────────────────────
    @GET("api/bookings/{id}/replacement")
    suspend fun getReplacementSearch(@Path("id") id: String): ReplacementSearchDto

    @POST("api/bookings/{id}/replacement/accept")
    suspend fun acceptReplacement(@Path("id") id: String): retrofit2.Response<Unit>

    @POST("api/bookings/{id}/replacement/decline")
    suspend fun declineReplacement(@Path("id") id: String): retrofit2.Response<Unit>

    // ── Google Calendar ───────────────────────────────────────────────────────
    @GET("api/auth/google-calendar/status")
    suspend fun googleCalendarStatus(): GoogleCalendarStatusResponse

    @POST("api/auth/google-calendar/disconnect")
    suspend fun googleCalendarDisconnect(): retrofit2.Response<Unit>
}
