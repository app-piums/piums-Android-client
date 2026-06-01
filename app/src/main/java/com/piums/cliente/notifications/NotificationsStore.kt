package com.piums.cliente.notifications

import com.piums.cliente.data.remote.PiumsApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationsStore @Inject constructor(
    private val api: PiumsApiService
) {
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    fun setZero() { _unreadCount.value = 0 }

    fun setCount(count: Int) { _unreadCount.value = count }

    suspend fun refresh() {
        runCatching { api.getNotifications(page = 1, limit = 20) }
            .getOrNull()?.let { result ->
                _unreadCount.value = result.list.count { !it.isRead }
            }
    }
}
