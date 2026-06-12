package com.piums.cliente.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piums.cliente.ui.theme.PiumsOrange
import java.time.YearMonth

private val MONTH_NAMES = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun",
                                 "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

/** Permite saltar directamente a un mes/año sin navegar mes a mes. */
@Composable
fun MonthYearPickerDialog(
    current: YearMonth,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit
) {
    var year by remember { mutableIntStateOf(current.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { year-- }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Año anterior",
                         tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
                Text("$year", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = { year++ }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Año siguiente",
                         tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (col in 0 until 3) {
                            val m = row * 3 + col + 1
                            val isCurrent = m == current.monthValue && year == current.year
                            Text(
                                text = MONTH_NAMES[m - 1],
                                color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) PiumsOrange else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onSelect(YearMonth.of(year, m)) }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(YearMonth.now()) }) {
                Text("Hoy", color = PiumsOrange, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
    )
}
