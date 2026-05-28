package com.piums.cliente.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthEventBus @Inject constructor() {
    private val _logoutRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutRequired: SharedFlow<Unit> = _logoutRequired.asSharedFlow()

    fun emitLogoutRequired() { _logoutRequired.tryEmit(Unit) }
}
