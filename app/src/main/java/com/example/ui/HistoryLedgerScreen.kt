package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AbsenceRecord
import com.example.data.AbsenceTypes
import com.example.util.DateUtil
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryLedgerScreen(
    viewModel: AbsenceViewModel,
    modifier: Modifier = Modifier
) {
    val allAbsences by viewModel.allAbsences.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterType by remember { mutableStateOf<String?>(null) }
    var onlyJustified by remember { mutableStateOf(false) }

    // Dialog states
    var inspectedRecord by remember { mutableStateOf<AbsenceRecord?>(null) }
    var recordToModify by remember { mutableStateOf<AbsenceRecord?>(null) }
    var recordToDelete by remember { mutableStateOf<AbsenceRecord?>(null) }

    val filteredAbsences = remember(allAbsences, searchQuery, selectedFilterType, onlyJustified) {
        allAbsences.filter { record ->
            val matchesType = selectedFilterType == null || record.absenceType == selectedFilterType
            val matchesJustified = !onlyJustified || record.isJustified
            val matchesSearch = searchQuery.isBlank() ||
                    record.reason.contains(searchQuery, ignoreCase = true) ||
                    (record.medicalCertificateNote?.contains(searchQuery, ignoreCase = true) == true) ||
                    record.formattedDate.contains(searchQuery, ignoreCase = true)
            matchesType && matchesJustified && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ledger_search_field"),
            placeholder = { Text("Cerca per data, motivo...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Cerca")
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancella")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedFilterType == null && !onlyJustified,
                    onClick = {
                        selectedFilterType = null
                        onlyJustified = false
                    },
                    label = { Text("Tutte (${allAbsences.size})") },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            item {
                val justifiedCount = allAbsences.count { it.isJustified }
                FilterChip(
                    selected = onlyJustified,
                    onClick = { onlyJustified = !onlyJustified },
                    label = { Text("🏥 Certificato ($justifiedCount)") },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White
                    )
                )
            }
            items(AbsenceTypes.allTypes) { type ->
                val countForType = allAbsences.count { it.absenceType == type }
                val isSelected = selectedFilterType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilterType = if (isSelected) null else type },
                    label = { Text("${AbsenceTypes.getEmoji(type)} ${AbsenceTypes.getDisplayName(type)} ($countForType)") },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredAbsences.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Vuoto",
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (allAbsences.isEmpty()) "Nessuna assenza salvata."
                        else "Nessun risultato con i filtri attuali.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredAbsences, key = { it.id }) { record ->
                    // SWIPE TO DELETE INTEGRATION
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                recordToDelete = record
                                false // Keep item visible until confirmed in dialog
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color by animateColorAsState(
                                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                    Color(0xFFEF4444)
                                } else {
                                    Color(0xFFFEE2E2)
                                },
                                label = "swipeBg"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Elimina",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Elimina",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        },
                        content = {
                            ExpressiveLedgerItem(
                                record = record,
                                onInspect = { inspectedRecord = record }
                            )
                        }
                    )
                }
            }
        }
    }

    // INSPECT RECORD DIALOG
    inspectedRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { inspectedRecord = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${record.typeEmoji} ${record.typeDisplayName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow("Periodo", record.formattedDate)
                    if (record.isRange) {
                        DetailRow("Durata", "${record.daysCount} notti")
                    }
                    DetailRow(
                        "Certificato Medico",
                        if (record.isJustified) "Sì" else "No"
                    )
                    if (record.isJustified && !record.medicalCertificateNote.isNullOrBlank()) {
                        DetailRow("Dettagli Medico", record.medicalCertificateNote)
                    }
                    DetailRow("Note", if (record.reason.isNotBlank()) record.reason else "Nessuna nota")

                    if (record.isRange) {
                        Spacer(modifier = Modifier.height(6.dp))

                        // Button to EXPLODE interval into single days
                        OutlinedButton(
                            onClick = {
                                val targetId = record.id
                                inspectedRecord = null
                                viewModel.explodeInterval(targetId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.CallSplit, contentDescription = "Esplodi", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Esplodi in Date Singole", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // List of individual days with quick deletion
                        Text(
                            text = "Date nell'intervallo:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val daysList = remember(record) {
                            val list = mutableListOf<Long>()
                            val cal = Calendar.getInstance().apply { timeInMillis = record.dateMillis }
                            while (cal.timeInMillis <= record.endDateMillis) {
                                list.add(cal.timeInMillis)
                                cal.add(Calendar.DAY_OF_MONTH, 1)
                            }
                            list
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            daysList.forEach { dayMillis ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${DateUtil.formatFullDate(dayMillis)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    IconButton(
                                        onClick = {
                                            viewModel.removeSingleDayFromInterval(record.id, dayMillis) {
                                                inspectedRecord = null
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Rimuovi questo giorno",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedRecord = null }) {
                    Text("Chiudi")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            val target = inspectedRecord
                            inspectedRecord = null
                            recordToModify = target
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Modifica", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Modifica")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val target = inspectedRecord
                            inspectedRecord = null
                            recordToDelete = target
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Elimina", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Elimina")
                    }
                }
            }
        )
    }

    // FAST MODIFY RECORD DIALOG (With Expand/Shrink capability)
    recordToModify?.let { record ->
        var modifiedReason by remember { mutableStateOf(record.reason) }
        var modifiedType by remember { mutableStateOf(record.absenceType) }
        var modifiedJustified by remember { mutableStateOf(record.isJustified) }
        var modifiedMedNote by remember { mutableStateOf(record.medicalCertificateNote ?: "") }
        var modifiedStart by remember { mutableStateOf(record.dateMillis) }
        var modifiedEnd by remember { mutableStateOf(record.endDateMillis) }

        val currentNights = remember(modifiedStart, modifiedEnd) {
            (((modifiedEnd - modifiedStart) / (1000 * 60 * 60 * 24)).toInt() + 1).coerceAtLeast(1)
        }

        AlertDialog(
            onDismissRequest = { recordToModify = null },
            title = {
                Text("Modifica / Amplia Assenza", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val periodLabel = if (currentNights == 1) {
                        DateUtil.formatFullDate(modifiedStart)
                    } else {
                        "${DateUtil.formatShortDate(modifiedStart)} - ${DateUtil.formatShortDate(modifiedEnd)} ($currentNights notti)"
                    }
                    Text(
                        text = "Periodo: $periodLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Expand / Shrink buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Durata: $currentNights notti", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (modifiedEnd > modifiedStart) {
                                        val cal = Calendar.getInstance().apply {
                                            timeInMillis = modifiedEnd
                                            add(Calendar.DAY_OF_MONTH, -1)
                                        }
                                        modifiedEnd = cal.timeInMillis
                                    }
                                },
                                enabled = modifiedEnd > modifiedStart,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("- 1 Notte", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        timeInMillis = modifiedEnd
                                        add(Calendar.DAY_OF_MONTH, 1)
                                    }
                                    modifiedEnd = cal.timeInMillis
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("+ 1 Notte", fontSize = 11.sp)
                            }
                        }
                    }

                    // Type Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AbsenceTypes.allTypes.forEach { type ->
                            val isSel = modifiedType == type
                            FilterChip(
                                selected = isSel,
                                onClick = { modifiedType = type },
                                label = { Text(AbsenceTypes.getDisplayName(type), fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = modifiedReason,
                        onValueChange = { modifiedReason = it },
                        label = { Text("Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Certificato Medico", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = modifiedJustified, onCheckedChange = { modifiedJustified = it })
                    }

                    if (modifiedJustified) {
                        OutlinedTextField(
                            value = modifiedMedNote,
                            onValueChange = { modifiedMedNote = it },
                            label = { Text("Dettaglio Certificato") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAbsence(
                            recordId = record.id,
                            newStartDateMillis = modifiedStart,
                            newEndDateMillis = modifiedEnd,
                            newType = modifiedType,
                            newReason = modifiedReason,
                            isJustified = modifiedJustified,
                            medicalNote = modifiedMedNote.ifBlank { null },
                            onSuccess = { recordToModify = null }
                        )
                    }
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToModify = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    // FAST DELETE CONFIRMATION DIALOG (Triggered via click or swipe)
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = {
                Text("Elimina Assenza", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Vuoi eliminare ${record.formattedShortDate} (${record.typeDisplayName})?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAbsence(record.id) {
                            recordToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
fun ExpressiveLedgerItem(
    record: AbsenceRecord,
    onInspect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(AbsenceTypes.getColorHex(record.absenceType)).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = record.typeEmoji, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.typeDisplayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (record.isRange) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${record.daysCount} notti",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    if (record.isJustified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF2563EB).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Certificato",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = record.formattedDate,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (record.reason.isNotBlank()) {
                    Text(
                        text = record.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

