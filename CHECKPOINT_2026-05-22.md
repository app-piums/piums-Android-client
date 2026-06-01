# Checkpoint — ClientePiums Android · 2026-05-22

## Lo logrado en esta sesión

### 1. Fix: nombre artista en tarjeta de reserva ✅
- `BookingDto.resolvedArtistName` ahora prioriza `artistName` (campo raíz) sobre el nombre anidado en `artist.nombre`
- Orden correcto: `artistName ?: artist?.name ?: artist?.nombre`
- Antes: `artist?.resolvedName ?: artistName` — cuando backend devuelve `{ artist: { nombre: "ID···28d3b3" }, artistName: "DJ Amazing" }`, ganaba el nombre autogenerado
- Paridad con iOS: `acc203b`

### 2. Fix: PaymentsResponse — paginación y moneda ✅
- Nuevo `PaymentsPagination` con campo `pages: Int` (el backend de pagos devuelve `"pages"`, no `"totalPages"`)
- `PaymentsResponse` ahora incluye `pagination: PaymentsPagination?` y propiedad `hasMore`
- `PaymentDto.formattedAmount` corregido de `"Q..."` (quetzales) a `"$..."` (USD)
- Paridad con iOS: `5e01f8e`

### 3. Fix: PiumsSegmentedPicker — tab seleccionado expandido ✅
- Tab seleccionado: muestra ícono + texto (expandido)
- Tabs no seleccionados: muestran solo ícono — evita truncación con 5+ tabs
- Aplica en `MySpaceScreen` y cualquier uso de `PiumsSegmentedPicker`
- Paridad con iOS: `5e01f8e`

### 4. Feat: Certificaciones del artista en perfil ✅
- Nuevo `CertificationDto` en `ArtistDtos.kt`: id, artistId, name, issuer, issueDate, expiresAt, certificateUrl, issueYear
- `ArtistDto` añade campo `certifications: List<CertificationDto>?`
- `ArtistDetailResponse` añade `certifications` en raíz + `resolvedCertifications` (busca en nested o raíz)
- `ArtistProfileViewModel` carga certificaciones desde `artistDeferred.await()?.resolvedCertifications`
- Nueva sección "Certificaciones" en `ArtistProfileScreen` con ícono WorkspacePremium naranja, nombre, emisor·año, link al certificado
- La sección aparece entre Bio y Servicios (igual que iOS)
- Paridad con iOS: `61365bd`

### 5. Feat: Portafolio con soporte de video y visor fullscreen ✅
- `PortfolioItem` añade campo `type: String?` + computed properties:
  - `isVideo`: detecta por `type == "video"` o URLs de youtube.com/youtu.be
  - `youtubeVideoId`: extrae el ID del video de la URL
  - `youtubeThumbnailUrl`: genera URL de thumbnail de YouTube (mqdefault)
- `PortfolioGrid` actualizado:
  - Título ahora muestra contador: "Portafolio (N)"
  - Items de video muestran thumbnail de YouTube + overlay con ícono Play
  - Tap en video → abre YouTube en browser (URI handler)
  - Tap en imagen → abre visor fullscreen
- Visor fullscreen: `Dialog` fullscreen negro con `HorizontalPager` para swipe entre imágenes, botón X para cerrar
- Paridad con iOS: `355e7af` + serie de fixes `0512b92..0882f76`

---

## Archivos modificados

| Archivo | Cambio |
|---|---|
| `data/remote/dto/BookingDtos.kt` | `resolvedArtistName` — orden de prioridad corregido |
| `data/remote/dto/OtherDtos.kt` | `PaymentsPagination` nueva, `PaymentsResponse` + pagination, `PaymentDto` → USD |
| `ui/components/PiumsSegmentedPicker.kt` | Solo tab seleccionado muestra texto |
| `data/remote/dto/ArtistDtos.kt` | `CertificationDto` nueva, `ArtistDto` + certifications, `PortfolioItem` + type/isVideo/youtubeVideoId |
| `ui/screens/artist/ArtistProfileScreen.kt` | `certifications` en VM, `CertificationsSection`, `PortfolioGrid` con video + fullscreen |

---

## Pendientes conocidos
- **Tickets** en Mi Espacio: tab "Tickets" con TicketsScreen + MyTicketsScreen (documentado en ANDROID_CONTEXT.md §16.1)
- **Day Offers**: badge de descuento en tarjetas de servicio del artista
- **Google Calendar**: conectar desde ProfileScreen  
- **Reschedule**: ModifyDateSheet en BookingDetailScreen
- **Certificate pinning**: reemplazar `"sha256/REEMPLAZAR_CON_PIN_REAL_DEL_LEAF_CERT="` en `NetworkModule.kt`
