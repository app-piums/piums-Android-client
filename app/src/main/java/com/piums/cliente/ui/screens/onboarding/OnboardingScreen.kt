package com.piums.cliente.ui.screens.onboarding

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.repository.AuthRepository
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val api: PiumsApiService,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    var selectedInterests     by mutableStateOf<Set<String>>(emptySet()); private set
    var selectedSubcategories by mutableStateOf<Map<String, Set<String>>>(emptyMap()); private set
    var isLoading             by mutableStateOf(false); private set

    var verFrontDone   by mutableStateOf(false); private set
    var verBackDone    by mutableStateOf(false); private set
    var verSelfieDone  by mutableStateOf(false); private set
    var isUploadingFront   by mutableStateOf(false)
    var isUploadingBack    by mutableStateOf(false)
    var isUploadingSelfie  by mutableStateOf(false)
    var verError by mutableStateOf<String?>(null)

    fun toggleInterest(interest: String) {
        selectedInterests = if (interest in selectedInterests)
            selectedInterests - interest
        else
            selectedInterests + interest
    }

    fun toggleSubcategory(category: String, sub: String) {
        val current = selectedSubcategories[category] ?: emptySet()
        selectedSubcategories = selectedSubcategories + (category to
            if (sub in current) current - sub else current + sub)
    }

    fun uploadDoc(folder: String, uri: Uri, ctx: Context) {
        viewModelScope.launch {
            verError = null
            when (folder) {
                "dpi_front" -> isUploadingFront  = true
                "dpi_back"  -> isUploadingBack   = true
                "selfie"    -> isUploadingSelfie = true
            }
            runCatching {
                val bytes = ImageUtils.compressedJpeg(ctx, uri)
                val body  = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part  = MultipartBody.Part.createFormData("file", "document.jpg", body)
                api.uploadDocument(folder = folder, file = part)
                when (folder) {
                    "dpi_front" -> verFrontDone  = true
                    "dpi_back"  -> verBackDone   = true
                    "selfie"    -> verSelfieDone = true
                }
            }.onFailure { verError = "Error al subir imagen. Intenta de nuevo." }
            when (folder) {
                "dpi_front" -> isUploadingFront  = false
                "dpi_back"  -> isUploadingBack   = false
                "selfie"    -> isUploadingSelfie = false
            }
        }
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            runCatching {
                val json = org.json.JSONArray(selectedInterests.toList()).toString()
                tokenStorage.savedInterests = json
            }
            runCatching { authRepository.completeOnboarding() }
            isLoading = false
            onDone()
        }
    }
}

// ─── Data ─────────────────────────────────────────────────────────────────────

private data class Interest(val label: String, val icon: ImageVector)

private val INTERESTS = listOf(
    Interest("Música en Vivo",          Icons.Default.MusicNote),
    Interest("DJs & Electrónica",       Icons.Default.Headphones),
    Interest("Fotografía",              Icons.Default.CameraAlt),
    Interest("Video & Contenido",       Icons.Default.Videocam),
    Interest("Producción Musical",      Icons.Default.Piano),
    Interest("Danza & Performance",     Icons.Default.SelfImprovement),
    Interest("Magia & Entretenimiento", Icons.Default.AutoFixHigh),
)

private val SUBCATEGORIES: Map<String, List<String>> = mapOf(
    "Música en Vivo"          to listOf("Banda de Rock","Jazz & Blues","Pop Acústico","Cantautor","Clásica","Folklore & Regional"),
    "DJs & Electrónica"       to listOf("House & Tech","Reggaeton & Urban","Pop & Comercial","Hip-Hop & Trap","DJ para Bodas","Festival & Club"),
    "Fotografía"              to listOf("Eventos","Retratos","Editorial","Bodas","Producto","Urbana & Street"),
    "Video & Contenido"       to listOf("Clips Musicales","Bodas & Celebraciones","Redes Sociales","Documental","Comercial","Cortometraje"),
    "Producción Musical"      to listOf("Beat Making","Mezcla & Mastering","Grabación en Estudio","Composición","Arreglos","Jingle & Publicidad"),
    "Danza & Performance"     to listOf("Urbano & Hip-Hop","Ballet Clásico","Contemporáneo","Latino & Salsa","Folklore","Show & Entretenimiento"),
    "Magia & Entretenimiento" to listOf("Magia de Cerca","Gran Ilusionismo","Malabares","Acrobacia","Circo","Fuego & Pirotecnia"),
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val context       = LocalContext.current
    val pagerState    = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val isLast        = pagerState.currentPage == 3

    // Photo launchers for verification page
    val frontLauncher  = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.uploadDoc("dpi_front", it, context) }
    }
    val backLauncher   = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.uploadDoc("dpi_back", it, context) }
    }
    val selfieLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.uploadDoc("selfie", it, context) }
    }

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
                    0 -> WelcomePage()
                    1 -> InterestsPage(vm.selectedInterests, vm::toggleInterest)
                    2 -> RefinePage(vm.selectedInterests, vm.selectedSubcategories, vm::toggleSubcategory)
                    3 -> VerificationPage(
                        frontDone       = vm.verFrontDone,
                        backDone        = vm.verBackDone,
                        selfieDone      = vm.verSelfieDone,
                        isUploadingFront   = vm.isUploadingFront,
                        isUploadingBack    = vm.isUploadingBack,
                        isUploadingSelfie  = vm.isUploadingSelfie,
                        verError        = vm.verError,
                        onPickFront     = { frontLauncher.launch("image/*") },
                        onPickBack      = { backLauncher.launch("image/*") },
                        onPickSelfie    = { selfieLauncher.launch("image/*") },
                        onSkip          = { vm.complete(onDone) }
                    )
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
                    repeat(4) { i ->
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

                // CTA button — hidden on verification page (buttons are inside the page)
                if (!isLast) {
                    val buttonText = when (pagerState.currentPage) {
                        0    -> "Empecemos"
                        1    -> if (vm.selectedInterests.isEmpty()) "Continuar sin filtros" else "Continuar"
                        else -> "Continuar"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438))))
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment    = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(buttonText, color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Default.ArrowForward, null,
                                tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── Page 0: Welcome ──────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
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
                Icon(Icons.Default.Palette, null, tint = PiumsOrange, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        AnimatedVisibility(visible, enter = fadeIn(tween(400, 200))) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Bienvenido a Piums",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "El marketplace donde encuentras el talento creativo perfecto para tu evento.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(0.6f), textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

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
                        Icon(icon, null, tint = PiumsOrange, modifier = Modifier.size(18.dp))
                        Text(label, color = Color.White.copy(0.85f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Stats row
        AnimatedVisibility(visible, enter = fadeIn(tween(500, 600))) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("10K+ Artistas", "50K+ Reservas", "5.0 Promedio").forEach { stat ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stat, color = Color.White.copy(0.75f), fontSize = 12.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ─── Page 1: Interests ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsPage(
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
            Icon(Icons.Default.Search, null, tint = PiumsOrange, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "¿Qué tipo de talento buscas?",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Selecciona tus intereses para personalizar tu experiencia.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(0.6f), textAlign = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(24.dp))

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
                        .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(0.15f), RoundedCornerShape(20.dp))
                        .clickable { onToggle(interest.label) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(interest.icon, null,
                            tint     = if (isSelected) Color.White else Color.White.copy(0.65f),
                            modifier = Modifier.size(16.dp))
                        Text(
                            interest.label,
                            color      = if (isSelected) Color.White else Color.White.copy(0.75f),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            style      = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "${selected.size} seleccionado${if (selected.size != 1) "s" else ""}",
                color = PiumsOrange, style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// ─── Page 2: Refine ───────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefinePage(
    selectedInterests: Set<String>,
    selectedSubs: Map<String, Set<String>>,
    onToggle: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(PiumsOrange.copy(0.15f)))
            Icon(Icons.Default.Tune, null, tint = PiumsOrange, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Afina tus gustos",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Elige géneros y estilos específicos que más te inspiran.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(0.6f), textAlign = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(24.dp))

        if (selectedInterests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(0.05f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Vuelve al paso anterior y selecciona al menos un interés para afinar tus gustos.",
                    color      = Color.White.copy(0.4f),
                    style      = MaterialTheme.typography.bodyMedium,
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        } else {
            selectedInterests.forEach { category ->
                val subs     = SUBCATEGORIES[category] ?: return@forEach
                val selected = selectedSubs[category] ?: emptySet()

                Text(
                    category.uppercase(),
                    color      = Color.White.copy(0.45f),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    subs.forEach { sub ->
                        val isSel = sub in selected
                        val bg by animateColorAsState(
                            if (isSel) PiumsOrange else Color.White.copy(0.08f),
                            label = "sub_chip"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(bg)
                                .border(1.dp, if (isSel) Color.Transparent else Color.White.copy(0.12f), RoundedCornerShape(20.dp))
                                .clickable { onToggle(category, sub) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                sub,
                                color      = if (isSel) Color.White else Color.White.copy(0.7f),
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                style      = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ─── Page 3: Verification ─────────────────────────────────────────────────────

@Composable
private fun VerificationPage(
    frontDone: Boolean, backDone: Boolean, selfieDone: Boolean,
    isUploadingFront: Boolean, isUploadingBack: Boolean, isUploadingSelfie: Boolean,
    verError: String?,
    onPickFront: () -> Unit, onPickBack: () -> Unit, onPickSelfie: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(PiumsOrange.copy(0.15f)))
            Icon(Icons.Default.Shield, null, tint = PiumsOrange, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Verifica tu identidad",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Requerida para crear reservas. Puedes completarla después desde tu perfil.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(0.6f), textAlign = TextAlign.Center, lineHeight = 22.sp
        )

        Spacer(Modifier.height(20.dp))

        // Info banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PiumsOrange.copy(0.08f))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, null, tint = PiumsOrange, modifier = Modifier.size(18.dp))
            Text(
                "Sube el frente de tu DPI y una selfie sosteniéndolo. La verificación tarda menos de 24 horas.",
                color = Color.White.copy(0.75f), style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp, modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Upload cards
        VerDocCard(
            title        = "DPI — Frente",
            description  = "Foto clara del frente de tu documento",
            isDone       = frontDone,
            isUploading  = isUploadingFront,
            onClick      = onPickFront
        )
        Spacer(Modifier.height(10.dp))
        VerDocCard(
            title        = "DPI — Reverso",
            description  = "Foto del reverso (opcional)",
            isDone       = backDone,
            isUploading  = isUploadingBack,
            onClick      = onPickBack
        )
        Spacer(Modifier.height(10.dp))
        VerDocCard(
            title        = "Selfie con DPI",
            description  = "Una foto tuya sosteniendo tu documento",
            isDone       = selfieDone,
            isUploading  = isUploadingSelfie,
            onClick      = onPickSelfie
        )

        verError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))

        // Ir a la app button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(PiumsOrange, Color(0xFFFF8438))))
                .clickable(onClick = onSkip),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Ir a la app", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onSkip) {
            Text("Completar después",
                color = Color.White.copy(0.45f),
                style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun VerDocCard(
    title: String,
    description: String,
    isDone: Boolean,
    isUploading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.06f))
            .clickable(enabled = !isUploading, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isDone) Color(0xFF22C55E).copy(0.15f) else PiumsOrange.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Default.CheckCircle, null,
                    tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.Default.Upload, null,
                    tint = PiumsOrange, modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isDone) "Subido correctamente" else description,
                color = if (isDone) Color(0xFF22C55E) else Color.White.copy(0.5f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (isUploading) {
            CircularProgressIndicator(color = PiumsOrange,
                modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (isDone) Icons.Default.Edit else Icons.Default.ChevronRight,
                null,
                tint     = if (isDone) Color(0xFF22C55E) else Color.White.copy(0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
