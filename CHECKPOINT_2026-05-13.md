# Checkpoint — ClientePiums Android · 2026-05-13

## Estado general
Paridad con iOS actualizada tras el pull del 13 mayo.

---

## Lo logrado en esta sesión

### 1. NotificationsStore ✅
- Nuevo singleton (`@Singleton`) en `notifications/NotificationsStore.kt`
- `unreadCount: StateFlow<Int>` global accesible desde cualquier VM
- `refresh()` — fetch página 1, cuenta no-leídas
- `setZero()` / `setCount(n)` — reset inmediato sin red
- `MainViewModel.init {}` llama `refresh()` al arranque de la app

### 2. Badge campana en HomeScreen ✅
- `HomeViewModel` inyecta `NotificationsStore`, expone `notifUnreadCount: StateFlow<Int>`
- `HomeHeader` acepta `notifUnreadCount` y muestra `BadgedBox` naranja en la campana
- Paridad exacta con `HomeView.swift`

### 3. NotificationsViewModel sincroniza badge ✅
- Tras cargar primera página: `notificationsStore.setCount(unread)`
- `markAllRead()`: llama `notificationsStore.setZero()` — badge se apaga sin esperar fetch

### 4. Deep link de chat por push ✅
- `ChatSocketManager.pendingDeepLinkConversationId: StateFlow<String?>` nuevo
- `PiumsFcmService.onMessageReceived`: extrae `conversationId`/`chatId` del payload FCM
  - NEW_MESSAGE → llama `chatSocketManager.setPendingConversationId(id)`
  - Otros tipos → `notificationsStore.refresh()` para actualizar badge
- `MainActivity.handleIntent()`: lee `EXTRA_CONVERSATION_ID` del intent de tap y llama `setPendingConversationId`
- `PiumsClienteApp`: nueva constante `EXTRA_CONVERSATION_ID = "conversation_id"`

### 5. InboxScreen consume deep link de conversación ✅
- `InboxViewModel.openConversationById(id)`: busca en lista cargada primero, si no hace GET al backend
- `InboxScreen` `LaunchedEffect(Unit)`: colecta `pendingDeepLinkConversationId` y abre la conversación automáticamente
- Funciona tanto al abrir la app desde push (app cerrada) como cuando la pantalla ya está activa

---

## Archivos modificados/creados

### Nuevos
- `notifications/NotificationsStore.kt`
- `CHECKPOINT_2026-05-13.md`

### Modificados
- `ui/navigation/MainViewModel.kt` — inyecta NotificationsStore, refresh en init
- `ui/screens/home/HomeScreen.kt` — badge campana via NotificationsStore
- `ui/screens/notifications/NotificationsScreen.kt` — VM sincroniza badge
- `utils/ChatSocketManager.kt` — pendingDeepLinkConversationId
- `notifications/PiumsFcmService.kt` — inyecta stores, maneja conversationId
- `MainActivity.kt` — inyecta ChatSocketManager, lee conversationId del intent
- `PiumsClienteApp.kt` — EXTRA_CONVERSATION_ID
- `ui/screens/inbox/InboxScreen.kt` — openConversationById + consumo de deep link

---

## Pendientes conocidos
- Certificate pinning: reemplazar pin placeholder en `NetworkModule.kt`
- Wallet / pagos: flujo Tilopay completo pendiente
- Apple Sign-In: no aplica en Android
