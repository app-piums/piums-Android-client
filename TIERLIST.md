# PiumsCliente Android — Tierlist de Avance

> Última actualización: 2026-04-29
> Estado general: 17 / 17 fases completadas

---

## ✅ S — SHIPPED (en producción / listo)

_Vacío_

---

## 🔨 A — DONE (implementado, pendiente de QA)

| # | Fase | Archivos clave |
|---|------|----------------|
| 1 | **Scaffold** | `settings.gradle.kts`, `libs.versions.toml`, `app/build.gradle.kts`, `PiumsClienteApp`, `MainActivity`, `PiumsTheme` (Color/Type, light+dark), `TokenStorage`, `AuthInterceptor`, `NetworkModule`, `NavGraph`, `MainScaffold` (5 tabs + FAB), `ConnectivityObserver` (offline banner), `PiumsFcmService`, `PiumsFormatter`, `SplashScreen`, 5 placeholders de tabs |
| 2 | **Auth** | `AuthViewModel`, `AuthScreen` (LoginSheet+RegisterSheet+ForgotSheet en un solo composable), fondo dark animado con glow naranja, card deslizante, `PiumsAuthField` (borde naranja al focus), `PiumsGradientButton` (shadow naranja), Google Sign-In (Firebase+CredentialManager), `PasswordStrengthBar` (5 cápsulas), checkbox términos, `AuthRepository`, todos los DTOs (`AuthDtos`, `ArtistDtos`, `BookingDtos`, `ChatDtos`, `OtherDtos`), `PiumsApiService` (52 endpoints) |
| 3 | **Onboarding** | `OnboardingViewModel`, `OnboardingScreen` (HorizontalPager 3 págs), `WelcomePage` (scaleIn emoji + feature pills), `InterestsPage` (12 chips FlowRow con toggle animado), `ReadyPage` (círculos concéntricos + 3 pasos), dots de progreso, botón Skip |
| 4 | **Home** | `HomeViewModel` (getMe + getBookings + searchArtists en paralelo), `HomeScreen`, `HomeHeader` (saludo + avatar iniciales), `MiniCalendar` (LazyRow mes actual, dots naranjas en fechas con reservas, scroll a hoy), `PromoBanner` (gradiente naranja), `ArtistCard` (160dp, rating, precio, badge verificado), `ArtistCardSkeleton` (shimmer infinito) |
| 5 | **Search / Explorar** | `SearchViewModel` (debounce 400ms, smartSearch/searchArtists según query, paginación), `SearchScreen`, `SearchBar` (OutlinedTextField + botón filtros), `FilterChipsRow` (chips activos), `CategoryGrid` (3×4 grid categorías), `ArtistResultCard` (2-col, ciudad, rating, reseñas), `FilterSheet` (ModalBottomSheet: specialty/city/rating slider/verified/sort chips) |
| 6 | **Perfil del Artista** | `ArtistProfileViewModel` (paralelo: artist+services+reviews+portfolio+favCheck), `ArtistProfileScreen`, `ProfileHeader` (avatar iniciales+border, verified badge, back, favorito toggle animado), `StatsRow` (rating/reseñas/reservas/servicios), `BioSection` (expandible), `ServiceCard` (seleccionable con animación border naranja), `ReviewItem` (avatar+stars), `PortfolioGrid` (chunked 3-col), FAB "Contratar", `MainScaffold` refactorizado con nested NavController |
| 7 | **Booking Flow** | `BookingViewModel` (SavedStateHandle artistId, steps 0-3, availability calendar, time slots, pricing calculate), `BookingScreen` (step indicator 4 barras), `ServiceStep`, `DateStep` (calendario mensual con celdas clickables, blocked dates en rojo), `DetailsStep` (location+notes+numDays counter), `ConfirmStep` (resumen+precio+confirm button), `BookingSuccessScreen` (concentric circles verde + código reserva) |
| 8 | **Mi Espacio** | `MySpaceViewModel` (bookings+events+favorites en paralelo), `MySpaceScreen` (HorizontalPager 3 tabs), `BookingsTab` (filter chips por status, BookingCard con cancel dialog), `EventsTab` (EventCard con delete, FAB crear, CreateEventDialog), `FavoritesTab` (FavoriteCard con remove + navigate al perfil) |
| 9 | **Inbox** | `InboxViewModel` (conversations+disputes, openConversation, sendMessage optimista), `InboxScreen` (HorizontalPager 2 tabs), `ConversationsTab` (lista con unread count), `ChatScreen` (MessageBubble, input bar + send, LazyColumn auto-scroll), `DisputesTab` (DisputeCard+status), `CreateDisputeDialog`, `MessageBubble` (isOwn via senderId==myUserId) |
| 10 | **Notificaciones** | `NotificationsViewModel` (paginado, markAllRead), `NotificationsScreen` (top bar con "Marcar todas", NotificationItem con ícono por tipo, unread indicator dot), routes en MainScaffold |
| 11 | **Reseñas** | Formulario integrado en BookingCard (canReview) vía MySpaceScreen — pendiente dialog independiente |
| 12 | **Pagos** | `PaymentsViewModel`, `PaymentsScreen` (LazyColumn de PaymentCard con status coloreado), route desde ProfileScreen |
| 13 | **Tutorial** | `TutorialScreen` (HorizontalPager 5 pasos: emojis animados + detail card), dots de progreso, botón "Omitir", route desde ProfileScreen |
| 14 | **Perfil / Ajustes** | `ProfileViewModel` (editName, changePassword, logout), `ProfileScreen` (header gradiente naranja, avatar con edición inline, ProfileItem rows, PasswordField con toggle visibility), routes a payments/disputes/tutorial |
| 15 | **Eventos** | CRUD en Mi Espacio: `EventsTab` (EventCard con delete, FAB, `CreateEventDialog`), `linkBookingToEvent` endpoint disponible |
| 16 | **Smart Search + Geoloc** | `LocationHelper` (FusedLocationProviderClient, `getLastKnownLocation`/`getCurrentLocation`), "Cerca de mí" toggle en SearchScreen con `RequestMultiplePermissions`, lat/lng pasado a `smartSearch` |
| 17 | **Polish** | `DeepLinkManager` (singleton SharedFlow), `PiumsFcmService` muestra notificación local con PendingIntent, `MainActivity` `onNewIntent` despacha al DeepLinkManager, `MainScaffold` colecta deep links y navega, `MainViewModel` carga `unreadCount` para badge INBOX, canal de notificación en `PiumsClienteApp`, `ic_notification.xml` drawable |

---

## 🔄 B — IN PROGRESS (en construcción activa)

_Vacío_

---

## 📋 C — NEXT (listo para empezar)

| # | Fase | Descripción |
|---|------|-------------|

---

## 🗂️ D — BACKLOG (definido, no iniciado)

| # | Fase | Descripción |
|---|------|-------------|

---

## ❌ F — BLOQUEADO

_Vacío_

---

## Notas de quirks críticos

- `role = "cliente"` (minúscula) en Firebase auth → evita error 400
- `ConversationDto`: backend usa `participant1Id`/`participant2Id`, NO `userId`/`artistId` — ya aplicado con `@SerializedName`
- `ChatMessageDto`: backend envía `status: "SENT"|"DELIVERED"|"READ"`, no `read: Boolean`; no hay `senderType` — determinar isOwn comparando `senderId` con `tokenStorage.userId`
- `bookingId` obligatorio al crear reseña
- Backend devuelve precios mezclados Int/Double — usar TypeAdapter tolerante en Gson (ya configurado con `setLenient()`)
- Zero mock data → siempre empty state + retry en fallos de API
- Base URL prod: `https://api.piums.com/` (distinto al artista `piums.com/api`)
