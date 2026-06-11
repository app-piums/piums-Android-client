package com.piums.cliente.ui.screens.tutorial

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class HowItWorksFeature(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val tab: Int
)

private val FEATURES = listOf(
    HowItWorksFeature(Icons.Default.Home,     "Inicio",     Color(0xFFFF6A00), 0),
    HowItWorksFeature(Icons.Default.Search,   "Explorar",   Color(0xFF6366F1), 1),
    HowItWorksFeature(Icons.Default.GridView, "Mi Espacio", Color(0xFFEC4899), 2),
    HowItWorksFeature(Icons.Default.Message,  "Mensajes",   Color(0xFF3B82F6), 3),
    HowItWorksFeature(Icons.Default.Person,   "Perfil",     Color(0xFF8B5CF6), 4),
)

@Composable
fun HowItWorksScreen(
    onBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onStartTour: () -> Unit
) {
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }

    val iconScale by animateFloatAsState(
        targetValue   = if (animateIn) 1f else 0.7f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 220f),
        label         = "iconScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue   = if (animateIn) 1f else 0f,
        animationSpec = tween(500, delayMillis = 120),
        label         = "contentAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Close button
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
        }

        // Header icon
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(iconScale)) {
                Box(Modifier.size(96.dp).clip(CircleShape)
                    .background(Color(0xFFFF6B35).copy(0.12f)))
                Box(Modifier.size(72.dp).clip(CircleShape)
                    .background(Color(0xFFFF6B35).copy(0.18f)))
                Icon(Icons.Default.Lightbulb, null,
                    tint = Color(0xFFFF6B35), modifier = Modifier.size(32.dp))
            }
        }

        // Title + subtitle + time estimate
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(top = 20.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Tour guiado de Piums",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Conoce cada sección de la app en\n5 pasos diseñados para ti.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
                textAlign = TextAlign.Center
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(0.4f),
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Tiempo estimado: ~2 minutos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.4f)
                )
            }
        }

        // Feature grid
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .alpha(contentAlpha),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FEATURES.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { feature ->
                        FeatureCell(
                            feature  = feature,
                            onClick  = { onNavigateToTab(feature.tab) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // CTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 40.dp)
                .alpha(contentAlpha),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF6B35), Color(0xFFFF8438))))
                    .clickable { onStartTour() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Iniciar tour interactivo",
                        color = Color.White, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(Icons.Default.ArrowForward, null,
                        tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            TextButton(onClick = onBack) {
                Text("Omitir",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.45f))
            }

            Text(
                "Navega por la app real mientras aprendes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureCell(
    feature: HowItWorksFeature,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick      = onClick,
        modifier     = modifier,
        shape        = RoundedCornerShape(14.dp),
        color        = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(feature.color.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(feature.icon, null,
                    tint = feature.color, modifier = Modifier.size(18.dp))
            }
            Text(
                feature.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onBackground.copy(0.25f),
                modifier = Modifier.size(14.dp))
        }
    }
}
