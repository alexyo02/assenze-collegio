package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CollegeSettings
import com.example.util.CivilHolidaysUtil
import com.example.util.DateUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupExportScreen(
    viewModel: AbsenceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val residencyStats by viewModel.residencyStats.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf<Uri?>(null) }

    // Direct Local Save Launchers (Storage Access Framework - Choose folder & filename)
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.savePdfToUri(uri) {}
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveCsvToUri(uri) {}
        }
    }

    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveJsonToUri(uri) {}
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            showImportConfirmDialog = uri
        }
    }

    val timeStamp = remember {
        SimpleDateFormat("yyyyMMdd", Locale.ITALIAN).format(Date())
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SALVA FILE & BACKUP LOCALE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Salva",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Salvataggio Locale & Backup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scegli dove salvare i file sul tuo dispositivo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // PDF Report
                    LocalSaveActionRow(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Salva Report PDF",
                        description = "Prospetto con presenze, assenze e percentuali.",
                        buttonLabel = "Salva PDF",
                        onAction = {
                            createPdfLauncher.launch("report_assenze_$timeStamp.pdf")
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // CSV Report
                    LocalSaveActionRow(
                        icon = Icons.Default.TableChart,
                        title = "Salva Foglio CSV",
                        description = "Compatibile con Excel e Fogli.",
                        buttonLabel = "Salva CSV",
                        onAction = {
                            createCsvLauncher.launch("tabella_assenze_$timeStamp.csv")
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // JSON Backup
                    LocalSaveActionRow(
                        icon = Icons.Default.FileUpload,
                        title = "Salva Backup Completo (.json)",
                        description = "Copia completa di assenze e impostazioni.",
                        buttonLabel = "Salva Backup",
                        onAction = {
                            createJsonLauncher.launch("backup_assenze_$timeStamp.json")
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Restore JSON
                    LocalSaveActionRow(
                        icon = Icons.Default.FileDownload,
                        title = "Ripristina da Backup (.json)",
                        description = "Reimporta da un file salvato in precedenza.",
                        buttonLabel = "Ripristina",
                        isOutlined = true,
                        onAction = {
                            importLauncher.launch("*/*")
                        }
                    )
                }
            }
        }

        // PERIOD & STUDENT PROFILE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profilo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Periodo & Dati Personali",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { showSettingsDialog = true }) {
                            Text("Modifica")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    CleanSettingsRow("Inizio Decorrenza", DateUtil.formatShortDate(settings.academicYearStartDate))
                    CleanSettingsRow("Fine Decorrenza", "30 Settembre")
                    CleanSettingsRow("Giorni Utili Totali", "${residencyStats.totalUsefulDaysInYear} giorni")
                    CleanSettingsRow("Soglia Minima", "> 56% (${residencyStats.minRequiredPresenceDays} giorni)")
                    CleanSettingsRow("Studente", settings.studentName)
                    CleanSettingsRow("Residenza", settings.collegeName)
                    CleanSettingsRow("Camera", settings.roomNumber)
                }
            }
        }
    }

    // RESTORE CONFIRMATION DIALOG
    showImportConfirmDialog?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = null },
            title = {
                Text("Ripristina Archivio", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Vuoi ripristinare le assenze dal file selezionato? I dati attuali verranno aggiornati con l'archivio.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedUri = uri
                        showImportConfirmDialog = null
                        viewModel.importDatabaseFromUri(selectedUri) { _, _ -> }
                    }
                ) {
                    Text("Conferma")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    // EDIT SETTINGS DIALOG
    if (showSettingsDialog) {
        var studentName by remember { mutableStateOf(settings.studentName) }
        var collegeName by remember { mutableStateOf(settings.collegeName) }
        var roomNumber by remember { mutableStateOf(settings.roomNumber) }
        var tempStartDate by remember { mutableStateOf(settings.academicYearStartDate) }
        var showDatePickerInSettings by remember { mutableStateOf(false) }

        val calculatedDays = remember(tempStartDate) {
            val end = CivilHolidaysUtil.getAcademicYearEndDate(tempStartDate)
            CivilHolidaysUtil.countUsefulDays(tempStartDate, end)
        }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text("Modifica Impostazioni", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePickerInSettings = true }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Inizio Decorrenza Giorni Utili:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = DateUtil.formatFullDate(tempStartDate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text("Cambia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Fine: 30 Settembre • Giorni utili: $calculatedDays gg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("Nome Studente") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = collegeName,
                        onValueChange = { collegeName = it },
                        label = { Text("Nome Residenza") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = roomNumber,
                        onValueChange = { roomNumber = it },
                        label = { Text("Camera") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveSettings(
                            settings.copy(
                                studentName = studentName.ifBlank { "Studente" },
                                collegeName = collegeName.ifBlank { "Residenza Universitaria" },
                                roomNumber = roomNumber.ifBlank { "Stanza 101" },
                                academicYearStartDate = tempStartDate
                            )
                        )
                        showSettingsDialog = false
                    }
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Annulla") }
            }
        )

        if (showDatePickerInSettings) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = tempStartDate)
            DatePickerDialog(
                onDismissRequest = { showDatePickerInSettings = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { tempStartDate = it }
                            showDatePickerInSettings = false
                        }
                    ) {
                        Text("Conferma")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerInSettings = false }) { Text("Annulla") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun LocalSaveActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    isOutlined: Boolean = false,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isOutlined) {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(buttonLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(buttonLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CleanSettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
