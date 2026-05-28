package com.piums.cliente.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piums.cliente.ui.theme.PiumsOrange

data class SegmentedTab(val label: String, val icon: ImageVector, val badge: Int = 0)

/**
 * Reusable segmented picker matching iOS PiumsSegmentedPicker:
 * - All tabs inside one outer capsule (surfaceVariant bg)
 * - Selected tab: orange capsule bg + white text
 * - Unselected tab: transparent + onSurface text
 * - Each tab: icon + label, equal width
 */
@Composable
fun PiumsSegmentedPicker(
    tabs: List<SegmentedTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        tabs.forEachIndexed { idx, tab ->
            val selected = selectedIndex == idx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (selected) PiumsOrange else Color.Transparent)
                    .clickable { onTabSelected(idx) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(13.dp),
                        tint = if (selected) Color.White
                               else MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    // Solo el tab seleccionado muestra el texto — evita truncación con 5+ tabs
                    if (selected) {
                        Text(
                            text = tab.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    if (tab.badge > 0) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color.White.copy(0.35f) else PiumsOrange
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${tab.badge}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
