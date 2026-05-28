package com.piums.cliente.data.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuthCallbackManager @Inject constructor() {

    private val _callbacks = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val callbacks = _callbacks.asSharedFlow()

    fun dispatch(jwt: String) {
        _callbacks.tryEmit(jwt)
    }
}
