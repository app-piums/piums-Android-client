package com.piums.cliente.ui.screens.reviews

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.CreateReviewRequest
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val api: PiumsApiService,
    savedState: SavedStateHandle
) : ViewModel() {

    private val bookingId: String = checkNotNull(savedState["bookingId"])
    private var artistId: String? = null

    var rating by mutableStateOf(0)
        private set
    var comment by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isSuccess by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            runCatching { api.getBooking(bookingId) }.onSuccess { artistId = it.artistId }
        }
    }

    fun onRatingChange(value: Int)  { rating  = value }
    fun onCommentChange(value: String) { comment = value }

    val canSubmit: Boolean get() = rating > 0 && !isLoading

    fun submit() {
        if (!canSubmit) return
        val aId = artistId ?: return
        viewModelScope.launch {
            isLoading = true
            error = null
            runCatching {
                api.createReview(CreateReviewRequest(
                    artistId  = aId,
                    bookingId = bookingId,
                    rating    = rating,
                    comment   = comment.trim().ifBlank { null }
                ))
            }.onSuccess {
                isSuccess = true
            }.onFailure {
                error = it.toUserMessage()
            }
            isLoading = false
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    vm: ReviewViewModel = hiltViewModel()
) {
    if (vm.isSuccess) {
        ReviewSuccessScreen(onDone = onBack)
        return
    }

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
                "Dejar reseña",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.StarRate,
                    contentDescription = null,
                    tint = PiumsOrange,
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    "¿Cómo fue tu experiencia?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Tu reseña ayuda a otros clientes a elegir al artista ideal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
                    textAlign = TextAlign.Center
                )
            }

            // Star rating
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Calificación", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        val filled = i <= vm.rating
                        val scale by animateFloatAsState(
                            targetValue = if (filled) 1.15f else 1f,
                            animationSpec = spring(dampingRatio = 0.4f),
                            label = "star_scale"
                        )
                        Icon(
                            imageVector = if (filled) Icons.Default.Star else Icons.Default.StarOutline,
                            contentDescription = null,
                            tint = if (filled) Color(0xFFFBBC04) else MaterialTheme.colorScheme.onSurface.copy(0.25f),
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .clickable { vm.onRatingChange(i) }
                        )
                    }
                }

                if (vm.rating > 0) {
                    Text(
                        ratingLabel(vm.rating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PiumsOrange,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Comment
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Comentario (opcional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    BasicTextField(
                        value = vm.comment,
                        onValueChange = vm::onCommentChange,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (vm.comment.isEmpty()) {
                        Text(
                            "Cuéntanos tu experiencia con el artista...",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.35f)
                        )
                    }
                }
            }

            // Error
            vm.error?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Submit button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (vm.canSubmit) PiumsOrange
                        else MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                    .clickable(enabled = vm.canSubmit, onClick = vm::submit),
                contentAlignment = Alignment.Center
            ) {
                if (vm.isLoading) {
                    CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Publicar reseña", color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// ─── Success ──────────────────────────────────────────────────────────────────

@Composable
private fun ReviewSuccessScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Star, null,
            tint = Color(0xFFFBBC04), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(24.dp))
        Text("¡Gracias por tu reseña!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Tu opinión ayuda a la comunidad de Piums.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PiumsOrange)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Text("Listo", color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun ratingLabel(rating: Int) = when (rating) {
    1 -> "Muy malo"
    2 -> "Regular"
    3 -> "Bueno"
    4 -> "Muy bueno"
    5 -> "¡Excelente!"
    else -> ""
}

// ─── Internal alias ──────────────────────────────────────────────────────────

@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    textStyle: androidx.compose.ui.text.TextStyle
) = androidx.compose.foundation.text.BasicTextField(
    value = value, onValueChange = onValueChange,
    modifier = modifier, textStyle = textStyle
)
