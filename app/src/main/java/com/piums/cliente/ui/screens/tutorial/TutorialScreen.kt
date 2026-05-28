package com.piums.cliente.ui.screens.tutorial

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.TourManager
import kotlinx.coroutines.launch

// ─── Data ─────────────────────────────────────────────────────────────────────

private data class TutorialStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val detail: String
)

private val STEPS = listOf(
    TutorialStep(
        Icons.Default.Search, "Explora artistas",
        "Busca por categoría, ciudad o nombre",
        "Usa la pestaña Explorar para encontrar el talento que necesitas. Filtra por especialidad, precio, calificación y más."
    ),
    TutorialStep(
        Icons.Default.Person, "Conoce el perfil",
        "Revisa bio, servicios, reseñas y portfolio",
        "Cada artista tiene un perfil completo con sus servicios disponibles, precios, calificaciones reales y ejemplos de su trabajo."
    ),
    TutorialStep(
        Icons.Default.CalendarMonth, "Reserva en segundos",
        "Selecciona servicio, fecha y hora",
        "Elige el servicio que necesitas, revisa los horarios disponibles y confirma tu reserva. El artista la revisará y aceptará."
    ),
    TutorialStep(
        Icons.Default.ChatBubbleOutline, "Coordina los detalles",
        "Chatea directo con el artista",
        "Una vez confirmada tu reserva, tienes acceso al chat para coordinar ubicación, temática o cualquier detalle especial."
    ),
    TutorialStep(
        Icons.Default.Star, "Califica al finalizar",
        "Tu opinión mejora la comunidad",
        "Después del evento, deja una reseña sobre el artista. Esto ayuda a otros clientes y reconoce el buen trabajo."
    )
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(
    onDone: () -> Unit,
    onBack: () -> Unit = onDone,
    tourManager: TourManager? = null
) {
    val pagerState  = rememberPagerState(pageCount = { STEPS.size })
    val coroutine   = rememberCoroutineScope()
    val isLast      = pagerState.currentPage == STEPS.lastIndex

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(0.5f))
            }
            TextButton(onClick = onDone) {
                Text("Omitir", color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            TutorialPage(step = STEPS[page], stepNumber = page + 1)
        }

        // Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(STEPS.size) { i ->
                    val isActive = i == pagerState.currentPage
                    val width by animateDpAsState(
                        targetValue = if (isActive) 20.dp else 6.dp,
                        animationSpec = spring(dampingRatio = 0.7f),
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .size(width, 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) PiumsOrange else MaterialTheme.colorScheme.outline.copy(0.25f)
                            )
                    )
                }
            }

            // Primary button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438))))
                    .clickable {
                        if (isLast) onDone()
                        else coroutine.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (isLast) "¡Empezar a usar Piums!" else "Siguiente",
                        color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        if (isLast) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                        null, tint = Color.White, modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tour interactivo — solo visible cuando el screen viene desde Profile
            if (tourManager != null && isLast) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            onDone()
                            tourManager.start()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, null,
                            tint = PiumsOrange, modifier = Modifier.size(18.dp))
                        Text("Iniciar tour interactivo",
                            color = PiumsOrange, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ─── Tutorial Page ────────────────────────────────────────────────────────────

@Composable
private fun TutorialPage(step: TutorialStep, stepNumber: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Step icon
        AnimatedVisibility(visible, enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn()) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(110.dp).clip(CircleShape)
                        .background(PiumsOrange.copy(0.12f))
                )
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                        .background(PiumsOrange.copy(0.20f))
                )
                Icon(step.icon, contentDescription = null,
                    tint = PiumsOrange, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Step number badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(PiumsOrange)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Paso $stepNumber de ${STEPS.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(visible, enter = fadeIn(tween(400, 200))) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(step.title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center)
                Text(step.description, style = MaterialTheme.typography.bodyLarge,
                    color = PiumsOrange, textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(step.detail, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                        textAlign = TextAlign.Center, lineHeight = 22.sp)
                }
            }
        }
    }
}
