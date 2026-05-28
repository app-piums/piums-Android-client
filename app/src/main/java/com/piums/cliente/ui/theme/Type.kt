package com.piums.cliente.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PiumsTypography = Typography(
    headlineLarge  = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall  = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge     = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium    = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall     = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge      = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    bodyMedium     = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodySmall      = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelLarge     = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall     = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
)
