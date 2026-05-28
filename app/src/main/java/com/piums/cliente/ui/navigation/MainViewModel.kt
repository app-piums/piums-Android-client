package com.piums.cliente.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.notifications.NotificationsStore
import com.piums.cliente.utils.AuthEventBus
import com.piums.cliente.utils.ChatSocketManager
import com.piums.cliente.utils.DeepLinkManager
import com.piums.cliente.utils.TourManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val deepLinkManager: DeepLinkManager,
    val tourManager: TourManager,
    val socketManager: ChatSocketManager,
    val authEventBus: AuthEventBus,
    private val api: PiumsApiService,
    private val notificationsStore: NotificationsStore
) : ViewModel() {

    var unreadCount by mutableStateOf(0)
        private set

    init {
        loadUnreadCount()
        viewModelScope.launch { notificationsStore.refresh() }
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            runCatching { unreadCount = api.getUnreadCount().count }
        }
    }

    /** Equivalente a chatStore.setActive(active) de iOS — solo gestiona el WebSocket */
    fun setSocketActive(active: Boolean) {
        if (active) socketManager.connect() else socketManager.disconnect()
    }

    fun refreshNotifications() {
        loadUnreadCount()
        viewModelScope.launch { notificationsStore.refresh() }
    }
}
