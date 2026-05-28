package com.piums.cliente.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.piums.cliente.ui.theme.PiumsOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalentPickerSheet(
    selectedTalentId: String?,
    onSelect: (Talent) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var expandedGroupId by remember { mutableStateOf(TALENT_GROUPS.firstOrNull()?.id) }

    val selectedTalent = remember(selectedTalentId) {
        TALENT_GROUPS.flatMap { g -> g.subCategories.flatMap { it.talents } }
            .firstOrNull { it.id == selectedTalentId }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "¿Cuál es tu superpoder creativo?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Elige un talento para encontrar al profesional perfecto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
                if (selectedTalent != null) {
                    TextButton(onClick = onClear) {
                        Text("Limpiar", color = PiumsOrange)
                    }
                }
            }

            // Selected badge
            AnimatedVisibility(visible = selectedTalent != null) {
                selectedTalent?.let { talent ->
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Seleccionado:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(PiumsOrange)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                talent.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Icon(
                                Icons.Default.Close, null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable(onClick = onClear),
                                tint = Color.White.copy(0.8f)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

            // Accordion list
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(TALENT_GROUPS, key = { it.id }) { group ->
                    val isExpanded = expandedGroupId == group.id
                    val hasSelected = group.subCategories.any { sub ->
                        sub.talents.any { it.id == selectedTalentId }
                    }

                    TalentGroupCard(
                        group = group,
                        isExpanded = isExpanded,
                        hasSelected = hasSelected,
                        selectedTalentId = selectedTalentId,
                        onToggle = {
                            expandedGroupId = if (isExpanded) null else group.id
                        },
                        onSelectTalent = { talent ->
                            onSelect(talent)
                            onDismiss()
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun TalentGroupCard(
    group: TalentGroup,
    isExpanded: Boolean,
    hasSelected: Boolean,
    selectedTalentId: String?,
    onToggle: () -> Unit,
    onSelectTalent: (Talent) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (hasSelected) 4.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (hasSelected) PiumsOrange
                            else PiumsOrange.copy(0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote, null,
                        modifier = Modifier.size(16.dp),
                        tint = if (hasSelected) Color.White else PiumsOrange
                    )
                }
                Text(
                    group.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hasSelected) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(PiumsOrange)
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    group.subCategories.forEach { sub ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                sub.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                sub.talents.forEach { talent ->
                                    val isSelected = talent.id == selectedTalentId
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50.dp))
                                            .background(
                                                if (isSelected) PiumsOrange
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { onSelectTalent(talent) }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            talent.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSelected) Color.White
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
