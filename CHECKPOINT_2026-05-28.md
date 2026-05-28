# Checkpoint — ClientePiums Android · 2026-05-28

## Lo logrado en esta sesión

### 1. Feat: Equipo adicional (Booking Collaborators) ✅
- Nuevo `BookingCollaborator` DTO en `OtherDtos.kt` con todos los campos (id, bookingId, artistId, role, status, artistName, artistAvatar…)
- `BookingCollaboratorsResponse` wrapper con `all` computed property
- Endpoint `GET /api/bookings/{id}/collaborators` en `PiumsApiService.kt`
- `BookingDetailViewModel` carga colaboradores en paralelo con la reserva (`load()`), filtra solo `status == "ACCEPTED"`
- Sección "Equipo adicional" en `BookingDetailScreen` (después de "Participantes"), solo visible si hay colaboradores aceptados
  - Avatar circular (AsyncImage o fallback icon naranja)
  - Nombre en bold + rol en gris
  - Divisores entre items
- Paridad con iOS: `MyBookingsView.swift` líneas 644–799

### 2. Fix: DeepLink routing para notificaciones nuevas ✅
- Nuevo `DeepLinkTarget.Coupons` en `DeepLinkManager.kt`
- Tipos RESCHEDULE (REQUEST / REQUESTED / APPROVED / REJECTED) → `DeepLinkTarget.Booking(entityId)`
- Tipos DISPUTE (OPENED / RESOLVED / MESSAGE) → `DeepLinkTarget.Inbox`
- Tipos COUPON (SENT / EXPIRING) + DISCOUNT → `DeepLinkTarget.Coupons`
- `MainScaffold.kt` maneja `DeepLinkTarget.Coupons` → `innerNav.navigate("coupons")`
- Paridad con iOS: `NotificationsView.swift` líneas 24–40

### 3. Fix: Iconos y colores en NotificationsScreen para tipos nuevos ✅
- `NotificationItem` ahora retorna par `(icon, accentColor)` por tipo
- RESCHEDULE_REQUEST / REQUESTED / APPROVED → `EditCalendar` morado (#8B5CF6)
- RESCHEDULE_REJECTED → `CalendarMonth` rojo (error)
- DISPUTE_OPENED / MESSAGE → `ReportProblem` rojo (error)
- DISPUTE_RESOLVED → `Gavel` teal (#14B8A6)
- COUPON_SENT / DISCOUNT → `LocalOffer` verde (#22C55E)
- COUPON_EXPIRING → `LocalOffer` naranja (#F59E0B)
- El `accentColor` se aplica al fondo del ícono, al borde y al tint cuando no leído
- Paridad con iOS: `NotificationsView.swift` líneas 184–223

---

## Archivos modificados

| Archivo | Cambio |
|---|---|
| `data/remote/dto/OtherDtos.kt` | `BookingCollaborator` + `BookingCollaboratorsResponse` nuevos |
| `data/remote/PiumsApiService.kt` | `getBookingCollaborators` endpoint |
| `ui/screens/booking/BookingDetailScreen.kt` | `collaborators` state en VM + sección "Equipo adicional" en UI |
| `utils/DeepLinkManager.kt` | `Coupons` target + RESCHEDULE/DISPUTE/COUPON routing |
| `ui/navigation/MainScaffold.kt` | Handler para `DeepLinkTarget.Coupons` |
| `ui/screens/notifications/NotificationsScreen.kt` | Iconos/colores por tipo de notificación |

---

## Pendientes conocidos
- **Certificate pinning**: reemplazar `"sha256/REEMPLAZAR_CON_PIN_REAL_DEL_LEAF_CERT="` en `NetworkModule.kt`
