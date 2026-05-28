package com.piums.cliente.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.TourManager
import com.piums.cliente.utils.TourStep

private const val TAB_COUNT = 5

@Composable
fun TourOverlay(
    tourManager: TourManager,
    navBarHeightDp: Dp = 80.dp
) {
    val isActive    by tourManager.isActive.collectAsState()
    val currentStep by tourManager.currentStep.collectAsState()

    if (!isActive) return
    val step = tourManager.currentStepData ?: return

    val config    = LocalConfiguration.current
    val density   = LocalDensity.current
    val screenW   = config.screenWidthDp.dp
    val screenH   = config.screenHeightDp.dp
    val spotR     = 44.dp

    // Center of the highlighted tab in the nav bar
    val tabCenterX  = screenW * (step.tab + 0.5f) / TAB_COUNT.toFloat()
    val spotCenterY = screenH - navBarHeightDp / 2f

    val spotXPx = with(density) { tabCenterX.toPx() }
    val spotYPx = with(density) { spotCenterY.toPx() }
    val spotRPx = with(density) { spotR.toPx() }

    // Pulsing ring
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue  = 1.55f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    Box(Modifier.fillMaxSize()) {

        // ── Dark backdrop with spotlight cutout ───────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            drawCircle(
                color     = Color.Transparent,
                radius    = spotRPx * 1.15f,
                center    = Offset(spotXPx, spotYPx),
                blendMode = BlendMode.Clear
            )
        }

        // ── Pulsing + solid ring ──────────────────────────────────────────────
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color  = PiumsOrange.copy(alpha = 0.35f),
                radius = spotRPx * pulseScale,
                center = Offset(spotXPx, spotYPx),
                style  = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color  = PiumsOrange,
                radius = spotRPx * 1.15f,
                center = Offset(spotXPx, spotYPx),
                style  = Stroke(width = 2.5.dp.toPx())
            )
        }

        // ── Card + arrow above the nav bar ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = navBarHeightDp + 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState)
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    else
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                },
                label = "tour_card"
            ) { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    StepCard(
                        step         = step,
                        currentIndex = currentStep,
                        totalSteps   = tourManager.steps.size,
                        isLast       = tourManager.isLastStep,
                        onNext       = tourManager::next,
                        onPrevious   = tourManager::previous,
                        onClose      = tourManager::end
                    )

                    // Triangle arrow pointing at the highlighted tab
                    val cardColor = MaterialTheme.colorScheme.surface
                    val arrowOffsetX = tabCenterX - 16.dp - 10.dp  // 16dp horizontal padding + center of arrow
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.width(arrowOffsetX))
                        Canvas(Modifier.size(width = 20.dp, height = 11.dp)) {
                            val w = size.width
                            val h = size.height
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(w, 0f)
                                lineTo(w / 2f, h)
                                close()
                            }
                            drawPath(path, color = cardColor)
                        }
                    }
                }
            }
        }
    }
}

// ─── Step card ────────────────────────────────────────────────────────────────

@Composable
private fun StepCard(
    step: TourStep,
    currentIndex: Int,
    totalSteps: Int,
    isLast: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Step dots + skip
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            StepDots(total = totalSteps, current = currentIndex, color = step.color)

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Saltar",
                    tint     = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    modifier = Modifier.size(14.dp))
            }
        }

        // Icon + title
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(step.color.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(step.icon, null,
                    tint     = step.color,
                    modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Paso ${currentIndex + 1} de $totalSteps",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = step.color
                )
                Text(
                    step.title,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Description
        Text(
            step.description,
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.onSurface.copy(0.65f),
            lineHeight = 21.sp
        )

        // Tip box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(step.color.copy(0.09f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Icon(Icons.Default.Lightbulb, null,
                tint     = step.color,
                modifier = Modifier.size(14.dp).padding(top = 1.dp))
            Text(
                step.tip,
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                lineHeight = 17.sp
            )
        }

        // Navigation row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Dots (hidden placeholder when on first step, for alignment)
            if (currentIndex > 0) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, null,
                        tint     = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        modifier = Modifier.size(18.dp))
                }
            } else {
                Spacer(Modifier.width(38.dp))
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(step.color)
                    .clickable(onClick = onNext)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        if (isLast) "¡Listo!" else "Siguiente",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                    Icon(
                        if (isLast) Icons.Default.Check else Icons.Default.ArrowForward,
                        null,
                        tint     = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─── Step dots ────────────────────────────────────────────────────────────────

@Composable
private fun StepDots(total: Int, current: Int, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val active = i == current
            val width by animateDpAsState(
                targetValue  = if (active) 20.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.6f),
                label        = "dot_w"
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) color
                        else MaterialTheme.colorScheme.onSurface.copy(0.15f)
                    )
            )
        }
    }
}
