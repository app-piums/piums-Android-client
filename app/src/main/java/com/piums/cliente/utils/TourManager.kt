package com.piums.cliente.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import com.piums.cliente.ui.theme.PiumsOrange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class TourStep(
    val tab: Int,
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val description: String,
    val tip: String
)

@Singleton
class TourManager @Inject constructor() {

    private val _isActive    = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    val steps: List<TourStep> by lazy {
        listOf(
            TourStep(
                tab = 0,
                icon = Icons.Default.Home,
                color = PiumsOrange,
                title = "Panel Principal",
                description = "Aquí encuentras artistas destacados, tus próximas reservas y recomendaciones personalizadas para tu evento.",
                tip = "Desliza hacia abajo para actualizar tus reservas activas en tiempo real."
            ),
            TourStep(
                tab = 1,
                icon = Icons.Default.Search,
                color = Color(0xFF6366F1),
                title = "Explorar Artistas",
                description = "Busca artistas por especialidad, ciudad y precio. Filtra por disponibilidad y calificación para encontrar el perfil ideal.",
                tip = "Usa el filtro de fecha para ver únicamente artistas libres cuando lo necesitas."
            ),
            TourStep(
                tab = 1,
                icon = Icons.Default.EditCalendar,
                color = Color(0xFF10B981),
                title = "Buscar por Fecha y Lugar",
                description = "Ingresa la fecha de tu evento y tu ubicación para ver de inmediato qué artistas están disponibles cerca de ti.",
                tip = "Toca el botón + para acceder a la búsqueda por fecha desde cualquier pantalla."
            ),
            TourStep(
                tab = 2,
                icon = Icons.Default.GridView,
                color = Color(0xFFF59E0B),
                title = "Mi Espacio",
                description = "Tu centro de control: gestiona reservas activas y pasadas, revisa tus eventos programados y accede a tus favoritos para contratar rápido.",
                tip = "Cambia entre Reservas, Eventos y Favoritos con las pestañas de la parte superior."
            ),
            TourStep(
                tab = 3,
                icon = Icons.Default.Message,
                color = Color(0xFF3B82F6),
                title = "Mensajes",
                description = "Comunícate directamente con los artistas en tiempo real. Sabrás cuando el artista está escribiendo. Las conversaciones no leídas muestran un badge.",
                tip = "Escribe antes del evento para coordinar los detalles finales sin sorpresas."
            ),
            TourStep(
                tab = 4,
                icon = Icons.Default.Person,
                color = Color(0xFF8B5CF6),
                title = "Tu Perfil",
                description = "Gestiona tu cuenta, revisa tu historial de reseñas y personaliza tus preferencias. También puedes volver a ver este tour cuando quieras.",
                tip = "Desde Perfil también puedes acceder a este tutorial en cualquier momento."
            )
        )
    }

    val currentStepData: TourStep? get() {
        val i = _currentStep.value
        return if (i < steps.size) steps[i] else null
    }

    val isLastStep: Boolean get() = _currentStep.value == steps.lastIndex

    fun start() {
        _currentStep.value = 0
        _isActive.value = true
    }

    fun next() {
        if (isLastStep) end()
        else _currentStep.value++
    }

    fun previous() {
        if (_currentStep.value > 0) _currentStep.value--
    }

    fun end() {
        _isActive.value = false
        _currentStep.value = 0
    }
}
