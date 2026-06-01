package com.piums.cliente.ui.theme

import com.piums.cliente.data.local.TokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(private val tokenStorage: TokenStorage) {

    // -1 = follow system, 0 = force light, 1 = force dark
    private val _override = MutableStateFlow(tokenStorage.darkModeOverride)
    val override: StateFlow<Int> = _override.asStateFlow()

    /** true = dark, false = light (system is never followed) */
    val forcedDark: Boolean get() = _override.value != 0

    fun setForceDark(dark: Boolean) {
        val v = if (dark) 1 else 0
        tokenStorage.darkModeOverride = v
        _override.value = v
    }
}
