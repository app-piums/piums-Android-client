package com.piums.cliente.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.piums.cliente.data.location.LocationSuggestion
import com.piums.cliente.ui.theme.PiumsOrange

@Composable
fun LocationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<LocationSuggestion>,
    onSuggestionSelected: (LocationSuggestion) -> Unit,
    hasCoordinates: Boolean = false,
    isLocatingGPS: Boolean = false,
    onUseMyLocation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box {
            OutlinedTextField(
                value         = value,
                onValueChange = onValueChange,
                label         = { Text("Dirección del evento") },
                placeholder   = { Text("ej. Zona 10, Ciudad de Guatemala") },
                leadingIcon   = {
                    Icon(
                        imageVector = if (hasCoordinates) Icons.Default.LocationOn else Icons.Default.Search,
                        contentDescription = null,
                        tint = if (hasCoordinates) PiumsOrange
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                },
                trailingIcon  = if (value.isNotEmpty()) ({
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Close, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp))
                    }
                }) else null,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = PiumsOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            DropdownMenu(
                expanded         = suggestions.isNotEmpty(),
                onDismissRequest = {}
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = {
                            Text(suggestion.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2)
                        },
                        onClick = { onSuggestionSelected(suggestion) },
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, null,
                                tint = PiumsOrange.copy(alpha = 0.7f))
                        }
                    )
                }
            }
        }

        if (onUseMyLocation != null) {
            TextButton(
                onClick  = onUseMyLocation,
                enabled  = !isLocatingGPS,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLocatingGPS) {
                    CircularProgressIndicator(
                        color       = PiumsOrange,
                        modifier    = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Obteniendo ubicación...",
                        color = PiumsOrange,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Icon(Icons.Default.MyLocation, null,
                        tint     = PiumsOrange,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Usar mi ubicación",
                        color = PiumsOrange,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
