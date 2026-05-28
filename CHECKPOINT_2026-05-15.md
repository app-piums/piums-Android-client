# Checkpoint — ClientePiums Android · 2026-05-15

## Lo logrado en esta sesión

### 1. Avatar en HomeHeader navega al tab Perfil ✅
- `HomeScreen` recibe nuevo parámetro `onProfileClick: () -> Unit`
- `HomeHeader` recibe `onAvatarClick` y lo aplica con `.clickable` al `Box` del avatar
- `MainScaffold` conecta `onProfileClick` → `navigate(Tab.PROFILE.route)` con `launchSingleTop = true`
- Paridad con iOS: `0c7907f`

### 2. LoginRateLimiter persiste entre reinicios ✅
- `LoginRateLimiter` migrado de `SystemClock.elapsedRealtime()` (reseteaba al cerrar la app) a `System.currentTimeMillis()` con `SharedPreferences` (`piums_rate_limiter`)
- `failureCount` y `lockedUntilMs` persisten en `SharedPreferences`; `reset()` los limpia correctamente
- `AuthViewModel` ahora recibe `@ApplicationContext` por Hilt para construir `LoginRateLimiter`
- Paridad con iOS: `997df99`

### 3. Selector de tipo de evento en BookingFlow paso 2 ✅
- Nuevo enum `EventType` en `BookingDtos.kt` con 11 tipos: CUMPLEANOS, BODA, GRADUACION, QUINCEANERA, CORPORATIVO, CONCIERTO, FIESTA, BABY_SHOWER, BAUTIZO, ANIVERSARIO, OTRO
- `CreateBookingRequest` añade campo `val eventType: String? = null`
- `BookingViewModel` añade `selectedEventType: EventType?` y `toggleEventType()` (toggle — toca de nuevo para deseleccionar)
- `selectedEventType?.apiValue` se pasa en el payload de `createBooking`
- `EventTypePickerSection` — grid 3 columnas con iconos de Material Icons Extended, selección naranja, igual que iOS
- `eventTypeIcon()` — helper privado que mapea `EventType` → `ImageVector`
- Paridad con iOS: `b6ee199`

### 4. Filtrar artistas sin servicios en HomeScreen ✅
- `HomeViewModel.refresh()`: ahora filtra `.filter { it.servicesCount > 0 }` al asignar `recommendedArtists`
- Mismo filtro aplicado en el catch (fallback)
- `SearchScreen` ya lo tenía — queda paridad completa
- Paridad con iOS: `7cc5c96`

---

## Archivos modificados

| Archivo | Cambio |
|---|---|
| `ui/screens/home/HomeScreen.kt` | `onProfileClick`, `onAvatarClick` clickable, filtro `servicesCount > 0` |
| `ui/navigation/MainScaffold.kt` | `onProfileClick` conectado al tab Perfil |
| `utils/LoginRateLimiter.kt` | SharedPreferences, `System.currentTimeMillis()` |
| `ui/screens/auth/AuthViewModel.kt` | `@ApplicationContext`, `LoginRateLimiter(appContext)` |
| `data/remote/dto/BookingDtos.kt` | enum `EventType`, `CreateBookingRequest.eventType` |
| `ui/screens/booking/BookingScreen.kt` | `selectedEventType`, `toggleEventType`, `EventTypePickerSection`, `eventTypeIcon` |

### 5. Filtrar conversaciones CLOSED/CANCELLED del inbox ✅
- `InboxViewModel`: `.filter { it.status.uppercase() !in setOf("CLOSED", "CANCELLED") }` al cargar conversaciones
- Filtro defensivo mientras el backend ajusta cuándo crea el chat (solo al confirmar reserva)
- Paridad con iOS: `3e9aa24`

### 6. SearchByDateFiltersSheet completo ✅
- `ByDateSortOption` enum: RELEVANCE, RATING_DESC, PRICE_ASC, PRICE_DESC
- `ArtistSearchByDateViewModel` añade: `minPrice`, `maxPrice`, `minRating`, `selectedCity`, `isVerified`, `sortOption`, `hasActiveFilters`, `clearFilters()`, `citySuggestions`, `searchCity()`, `clearCitySuggestions()`
- `displayed` actualizado: filtra por precio, rating, ciudad, verificado; aplica sort client-side
- `SearchByDateFiltersSheet` reemplaza `CategoryPickerSheet` con secciones completas: Especialidad, Disponibilidad, Rango de precio, Calificación mínima (stars), Ciudad/zona (con sugerencias Nominatim), Ordenar por, Solo verificados, Limpiar filtros
- `FilterSection` helper composable
- Barra de filtros muestra resumen activo con fondo naranja tenue y botón X para limpiar todo
- Paridad con iOS: `a127320` + `6f1cd32`

---

## Archivos adicionales (pull iOS 2026-05-15)

| Archivo | Cambio |
|---|---|
| `ui/screens/inbox/InboxScreen.kt` | Filtro CLOSED/CANCELLED en conversaciones |
| `ui/screens/booking/ArtistSearchByDateScreen.kt` | ByDateSortOption, VM + filtros + SearchByDateFiltersSheet |

---

## Pendientes conocidos
- **Certificate pinning:** reemplazar `"sha256/REEMPLAZAR_CON_PIN_REAL_DEL_LEAF_CERT="` en `NetworkModule.kt`
- **Apple Sign-In:** no aplica en Android
- **Wallet / Tilopay:** flujo completo pendiente
