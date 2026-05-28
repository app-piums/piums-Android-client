package com.piums.cliente.ui.screens.notifications

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.MarkNotificationsReadRequest
import com.piums.cliente.data.remote.dto.NotificationDto
import com.piums.cliente.notifications.NotificationsStore
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: PiumsApiService,
    private val notificationsStore: NotificationsStore
) : ViewModel() {

    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var page by mutableStateOf(1)
        private set
    var hasMore by mutableStateOf(false)
        private set

    init { load() }

    private fun load(reset: Boolean = true) {
        viewModelScope.launch {
            if (reset) { page = 1; isLoading = true }
            val result = runCatching { api.getNotifications(page = page, limit = 20) }.getOrNull()
            if (reset) {
                notifications = result?.list ?: emptyList()
                notificationsStore.setCount(notifications.count { !it.isRead })
            } else {
                notifications = notifications + (result?.list ?: emptyList())
            }
            hasMore = result?.pagination?.hasMore == true
            isLoading = false
        }
    }

    fun refresh() { load(reset = true) }

    fun loadMore() {
        if (!hasMore || isLoading) return
        page++
        load(reset = false)
    }

    fun markAllRead() {
        val unreadIds = notifications.filter { !it.isRead }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            runCatching { api.markNotificationsRead(MarkNotificationsReadRequest(unreadIds)) }
            notifications = notifications.map { it.copy(readAt = it.readAt ?: "now") }
            notificationsStore.setZero()
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    vm: NotificationsViewModel = hiltViewModel()
) {
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(vm.isLoading) { if (!vm.isLoading) isRefreshing = false }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Notificaciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (vm.notifications.any { !it.isRead }) {
                TextButton(onClick = vm::markAllRead) {
                    Text("Marcar todas", color = PiumsOrange,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { isRefreshing = true; vm.refresh() },
            modifier     = Modifier.fillMaxSize()
        ) {
        if (vm.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PiumsOrange)
            }
        } else if (vm.notifications.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.NotificationsNone, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(0.3f),
                    modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Sin notificaciones",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.notifications, key = { it.id }) { notif ->
                    NotificationItem(notif = notif)
                }
                if (vm.hasMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = vm::loadMore) {
                                Text("Cargar más", color = PiumsOrange)
                            }
                        }
                    }
                }
            }
        }
        } // end PullToRefreshBox
    }
}

@Composable
private fun NotificationItem(notif: NotificationDto) {
    val errorColor = MaterialTheme.colorScheme.error
    val (icon, accentColor) = when (notif.type) {
        "BOOKING_CONFIRMED"                      -> Icons.Default.CheckCircle  to PiumsOrange
        "BOOKING_CANCELLED"                      -> Icons.Default.Cancel        to errorColor
        "NEW_MESSAGE"                            -> Icons.Default.Message       to PiumsOrange
        "BOOKING_REMINDER"                       -> Icons.Default.AccessTime    to PiumsOrange
        "REVIEW_REQUEST"                         -> Icons.Default.Star          to androidx.compose.ui.graphics.Color(0xFFF59E0B)
        "RESCHEDULE_REQUEST",
        "RESCHEDULE_REQUESTED",
        "RESCHEDULE_APPROVED"                    -> Icons.Default.EditCalendar  to androidx.compose.ui.graphics.Color(0xFF8B5CF6)
        "RESCHEDULE_REJECTED"                    -> Icons.Default.CalendarMonth to errorColor
        "DISPUTE_OPENED",
        "DISPUTE_MESSAGE"                        -> Icons.Default.ReportProblem to errorColor
        "DISPUTE_RESOLVED"                       -> Icons.Default.Gavel         to androidx.compose.ui.graphics.Color(0xFF14B8A6)
        "COUPON_SENT",
        "DISCOUNT"                               -> Icons.Default.LocalOffer    to androidx.compose.ui.graphics.Color(0xFF22C55E)
        "COUPON_EXPIRING"                        -> Icons.Default.LocalOffer    to androidx.compose.ui.graphics.Color(0xFFF59E0B)
        else                                     -> Icons.Default.Notifications to PiumsOrange
    }
    val iconTint = if (!notif.isRead) accentColor else MaterialTheme.colorScheme.onSurface.copy(0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (!notif.isRead) accentColor.copy(0.05f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)
            )
            .border(1.dp,
                if (!notif.isRead) accentColor.copy(0.2f) else PiumsOrange.copy(0f),
                RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(if (!notif.isRead) accentColor.copy(0.15f)
                             else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(notif.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                if (!notif.isRead) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(PiumsOrange)
                        .padding(start = 4.dp))
                }
            }
            Text(notif.message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(notif.createdAt.take(10), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.35f))
        }
    }
}

