package com.piums.cliente.ui.screens.onboarding

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.repository.AuthRepository
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    var selectedInterests by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun toggleInterest(interest: String) {
        selectedInterests = if (interest in selectedInterests)
            selectedInterests - interest
        else
            selectedInterests + interest
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            runCatching { authRepository.completeOnboarding() }
            isLoading = false
            onDone()
        }
    }
}

// ─── Data ─────────────────────────────────────────────────────────────────────

private data class OnboardingPage(
    val step: Int,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

private val PAGES = listOf(
    OnboardingPage(0, Icons.Default.Palette, "Bienvenido a Piums",
        "El marketplace donde encuentras el talento creativo perfecto para tu evento."),
    OnboardingPage(1, Icons.Default.Search, "¿Qué tipo de talento buscas?",
        "Selecciona tus intereses para personalizar tu experiencia."),
    OnboardingPage(2, Icons.Default.Celebration, "¡Listo para comenzar!",
        "Explora artistas, reserva en segundos y disfruta del mejor talento cerca de ti.")
)

private data class Interest(val label: String, val icon: ImageVector)

private val INTERESTS = listOf(
    Interest("Música",       Icons.Default.MusicNote),
    Interest("DJ",           Icons.Default.Headphones),
    Interest("Fotografía",   Icons.Default.CameraAlt),
    Interest("Baile",        Icons.Default.SelfImprovement),
    Interest("Maquillaje",   Icons.Default.Brush),
    Interest("Tatuajes",     Icons.Default.Create),
    Interest("Iluminación",  Icons.Default.Lightbulb),
    Interest("Bodas",        Icons.Default.Favorite),
    Interest("Quinceañeras", Icons.Default.AutoAwesome),
    Interest("Corporativo",  Icons.Default.Business),
    Interest("Barbería",     Icons.Default.ContentCut),
    Interest("Shows",        Icons.Default.Celebration),
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val coroutineScope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == PAGES.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(
                listOf(Color(0xFF0F0A08), Color(0xFF1A1008), Color(0xFF0F0A08))
            ))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Skip button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = { vm.complete(onDone) }) {
                    Text("Omitir", color = Color.White.copy(0.5f),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Pager
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> WelcomePage(PAGES[0])
                    1 -> InterestsPage(PAGES[1], vm.selectedInterests, vm::toggleInterest)
                    2 -> ReadyPage(PAGES[2])
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(PAGES.size) { i ->
                        val isActive = i == pagerState.currentPage
                        val width by animateDpAsState(
                            targetValue   = if (isActive) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.7f),
                            label         = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .size(width, 8.dp)
                                .clip(CircleShape)
                                .background(if (isActive) PiumsOrange else Color.White.copy(0.25f))
                        )
                    }
                }

                // CTA button
                val buttonText = when (pagerState.currentPage) {
                    0    -> "Empecemos"
                    1    -> if (vm.selectedInterests.isEmpty()) "Continuar sin filtros" else "Continuar"
                    else -> "¡Empezar ahora!"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438)))
                        )
                        .clickable(enabled = !vm.isLoading) {
                            if (isLast) {
                                vm.complete(onDone)
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (vm.isLoading) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(buttonText, color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge)
                            Icon(
                                if (isLast) Icons.Default.Check else Icons.Default.ArrowForward,
                                null, tint = Color.White, modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Step 1: Welcome ──────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(page: OnboardingPage) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible, enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn()) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).clip(CircleShape).background(PiumsOrange.copy(0.12f)))
                Box(Modifier.size(86.dp).clip(CircleShape).background(PiumsOrange.copy(0.20f)))
                Icon(page.icon, contentDescription = null,
                    tint = PiumsOrange, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        AnimatedVisibility(visible, enter = fadeIn(tween(400, 200))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(page.title, style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center)
                Text(page.subtitle, style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(0.6f), textAlign = TextAlign.Center,
                    lineHeight = 24.sp)
            }
        }

        Spacer(Modifier.height(40.dp))

        // Feature pills
        AnimatedVisibility(visible, enter = fadeIn(tween(500, 400))) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Icons.Default.VerifiedUser to "Artistas verificados",
                    Icons.Default.FlashOn      to "Reserva en segundos",
                    Icons.Default.Chat         to "Chat directo"
                ).forEach { (icon, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(0.06f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null,
                            tint = PiumsOrange, modifier = Modifier.size(18.dp))
                        Text(label, color = Color.White.copy(0.85f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ─── Step 2: Interests ────────────────────────────────────────────────────────

@Composable
private fun InterestsPage(
    page: OnboardingPage,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(PiumsOrange.copy(0.15f)))
            Icon(page.icon, contentDescription = null,
                tint = PiumsOrange, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall,
            color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(page.subtitle, style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(0.6f), textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(24.dp))

        // Interests grid — FlowRow
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp)
        ) {
            INTERESTS.forEach { interest ->
                val isSelected = interest.label in selected
                val bgColor by animateColorAsState(
                    if (isSelected) PiumsOrange else Color.White.copy(0.08f),
                    label = "chip_bg"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgColor)
                        .border(
                            1.dp,
                            if (isSelected) Color.Transparent else Color.White.copy(0.15f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onToggle(interest.label) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(interest.icon, contentDescription = null,
                            tint = if (isSelected) Color.White else Color.White.copy(0.65f),
                            modifier = Modifier.size(16.dp))
                        Text(interest.label,
                            color      = if (isSelected) Color.White else Color.White.copy(0.75f),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            style      = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("${selected.size} seleccionado${if (selected.size != 1) "s" else ""}",
                color = PiumsOrange, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ─── Step 3: Ready ────────────────────────────────────────────────────────────

@Composable
private fun ReadyPage(page: OnboardingPage) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible, enter = scaleIn(spring(dampingRatio = 0.55f)) + fadeIn()) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(140.dp).clip(CircleShape)
                    .background(PiumsOrange.copy(0.15f)))
                Box(Modifier.size(100.dp).clip(CircleShape)
                    .background(PiumsOrange.copy(0.25f)))
                Icon(page.icon, contentDescription = null,
                    tint = PiumsOrange, modifier = Modifier.size(52.dp))
            }
        }
        Spacer(Modifier.height(36.dp))
        AnimatedVisibility(visible, enter = fadeIn(tween(400, 300))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(page.title, style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center)
                Text(page.subtitle, style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(0.6f), textAlign = TextAlign.Center,
                    lineHeight = 24.sp)

                Spacer(Modifier.height(12.dp))

                // Steps summary
                listOf(
                    "Explora" to "Busca artistas por categoría, ciudad y precio",
                    "Reserva" to "Elige fecha y hora disponible en segundos",
                    "Disfruta" to "Coordina los detalles y califica al finalizar"
                ).forEachIndexed { idx, (step, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(PiumsOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${idx + 1}", color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp)
                        }
                        Column {
                            Text(step, color = Color.White, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(desc, color = Color.White.copy(0.55f),
                                style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
