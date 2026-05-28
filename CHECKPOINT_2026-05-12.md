# Checkpoint — ClientePiums Android · 2026-05-12

## Estado general
App del cliente final (`com.piums.cliente`) — paridad iOS alcanzada en todas las pantallas principales.

---

## Lo logrado en esta sesión

### 1. HowItWorksScreen ✅
- Nueva pantalla accesible desde Perfil → "¿Cómo funciona?"
- Reemplaza el link que antes abría `TutorialScreen`
- Header animado con spring scale + fade
- Grid de 8 features con navegación directa a cada tab
- Botón "Iniciar tour interactivo" que lanza el `TourManager`
- Ruta `how-it-works` registrada en `MainScaffold`

### 2. EventLocationPickerSheet ✅
- Bottom sheet que aparece al tocar un día en el calendario de HomeScreen
- Mapa OSM con crosshair centrado + botón GPS
- Geocodificación inversa (Nominatim) con debounce 600ms
- Búsqueda de texto con sugerencias, debounce 400ms
- Flag `suppressGeocode` para proteger el nombre seleccionado por texto (2s)
- Al confirmar navega a `ArtistSearchByDateScreen` con fecha y coordenadas pre-llenadas

### 3. ArtistSearchByDateScreen — parámetros iniciales ✅
- Acepta `initialDate`, `initialLat`, `initialLng` opcionales
- `setLocation(lat, lng, label)` en ViewModel para aplicar ubicación y re-ordenar artistas por distancia
- Date strip expandida de 14 → 30 días
- Ruta `search-by-date?date={date}&lat={lat}&lng={lng}` con query args opcionales en `MainScaffold`

### 4. Splash — corrección de escala ✅
- **Antes:** `TextureView` con `VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING` → logo estirado/enorme
- **Ahora:** `PlayerView` (media3-ui) con `RESIZE_MODE_FIT` + `@UnstableApi` → mismo comportamiento que la app artista
- Agregado `media3-ui` al catálogo de versiones y `build.gradle.kts`

### 5. InboxScreen — fix posición conversaciones ✅
- **Problema:** `HorizontalPager` tiene `verticalAlignment = Alignment.CenterVertically` por defecto; con pocas conversaciones el contenido quedaba centrado verticalmente (al fondo)
- **Fix:** `verticalAlignment = Alignment.Top` en el `HorizontalPager`

### 6. Build errors corregidos ✅
| Archivo | Error | Fix |
|---|---|---|
| `MySpaceScreen.kt` | `Unresolved reference 'LazyRow'` | Import agregado |
| `MySpaceScreen.kt` | `component1()/component2() ambiguous` | Destructuring explícito con `.first`/`.second` |
| `TourManager.kt` | `Unresolved reference 'EditCalendar'` | Import `filled.EditCalendar` agregado |
| `AuthScreen.kt` | `Unresolved reference 'MiddleEllipsis'` | → `TextOverflow.Ellipsis` |
| `AuthScreen.kt` | `Unresolved reference 'shadow'` (×2) | Import `ui.draw.shadow` agregado |

### 7. Revisión de backend ✅
- `HttpLoggingInterceptor` restringido a `DEBUG` only (evita fuga de tokens en producción)
- `retryOnConnectionFailure = false` (evitaba duplicar POSTs de bookings/pagos)
- URL de refresh en `TokenAuthenticator` migrada a `BuildConfig.BASE_URL`
- Pendiente (conocido): reemplazar pin placeholder de certificado SSL antes de release

### 8. AuthScreen — paridad iOS completa ✅
**Flujo multi-paso (nuevo):**
- **Paso 1 — Email:** campo de correo + botón "Continuar" con flecha (habilitado solo si el email es válido) + atajo social + link de registro
- **Paso 2 — Password:** botón back circular naranja + email del usuario visible arriba + campo contraseña + "¿Olvidaste tu contraseña?"
- **Paso 3 — Social:** Google (funcional) · Facebook "Próximamente" · TikTok "Próximamente" — todos en filas full-width con ícono izquierda

**Visuales igualados a iOS:**
- Logo `piums_logo.png` (copiado desde el asset iOS `piums-logo@3x.png`) en lugar del ícono genérico
- Glow: un solo círculo grande centrado al tope (antes 2 blobs)
- Texto: "¡Bienvenido a Piums!" / "El artista perfecto para tu próximo evento"
- Divisor línea-punto-línea (estilo iOS)
- Botón "Continuar" con gradiente naranja oscuro y flecha `ArrowForward`
- Botones sociales con `BorderStroke` + badge "Próximamente" + opacity 55%
- Transición animada entre pasos con `AnimatedContent` + `slideInHorizontally`

---

## Archivos modificados/creados

### Nuevos
- `ui/screens/tutorial/HowItWorksScreen.kt`
- `ui/components/EventLocationPickerSheet.kt`
- `res/drawable/piums_logo.png`

### Modificados
- `ui/navigation/MainScaffold.kt`
- `ui/screens/home/HomeScreen.kt`
- `ui/screens/booking/ArtistSearchByDateScreen.kt`
- `ui/screens/auth/AuthScreen.kt`
- `ui/screens/splash/SplashScreen.kt`
- `ui/screens/inbox/InboxScreen.kt`
- `ui/screens/myspace/MySpaceScreen.kt`
- `utils/TourManager.kt`
- `di/NetworkModule.kt`
- `data/remote/TokenAuthenticator.kt`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

---

## Pendientes conocidos
- **Certificate pinning:** reemplazar `"sha256/REEMPLAZAR_CON_PIN_REAL_DEL_LEAF_CERT="` en `NetworkModule.kt` con el pin real del cert de `backend.piums.io` antes de subir a producción
- **Apple Sign-In:** iOS lo tiene; Android no aplica (no existe en Android)
- **Wallet / pagos avanzados:** implementación básica presente, pendiente flujo Tilopay completo según backend
