package com.piums.cliente.ui.screens.biometric

import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piums.cliente.ui.theme.PageBackground
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.BiometricHelper

@Composable
fun BiometricGateScreen(
    onSuccess:  () -> Unit,
    onFallback: () -> Unit
) {
    val activity = LocalContext.current as FragmentActivity
    val helper   = remember { BiometricHelper(activity) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun launch() {
        errorMsg = null
        helper.authenticate(
            onSuccess = onSuccess,
            onError   = { msg ->
                val userCancelled = msg.contains("cancel", ignoreCase = true) ||
                    msg.contains("dismiss", ignoreCase = true)
                if (!userCancelled) errorMsg = msg
            }
        )
    }

    LaunchedEffect(Unit) { launch() }

    Box(
        modifier          = Modifier.fillMaxSize().background(PageBackground),
        contentAlignment  = Alignment.Center
    ) {
        Column(
            modifier                = Modifier.padding(horizontal = 40.dp),
            horizontalAlignment     = Alignment.CenterHorizontally,
            verticalArrangement     = Arrangement.spacedBy(24.dp)
        ) {
            // Icon
            Box(
                modifier         = Modifier.size(120.dp).clip(CircleShape)
                    .background(PiumsOrange.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint     = PiumsOrange,
                    modifier = Modifier.size(64.dp)
                )
            }

            // Title + subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Verificación biométrica",
                    fontWeight  = FontWeight.ExtraBold,
                    fontSize    = 22.sp,
                    color       = Color.White
                )
                Text(
                    "Usa tu huella digital para acceder a Piums",
                    fontSize    = 14.sp,
                    color       = Color.White.copy(alpha = 0.55f),
                    textAlign   = TextAlign.Center,
                    lineHeight  = 20.sp
                )
            }

            // Error message
            AnimatedVisibility(visible = errorMsg != null, enter = fadeIn(), exit = fadeOut()) {
                errorMsg?.let {
                    Text(
                        it,
                        fontSize  = 13.sp,
                        color     = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Retry button
            Button(
                onClick  = ::launch,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
            ) {
                Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Usar huella digital", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            // Fallback
            TextButton(onClick = onFallback) {
                Text("Usar contraseña", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
            }
        }
    }
}
