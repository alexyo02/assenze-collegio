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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.CollegeSettings
import com.example.util.CivilHolidaysUtil
import com.example.util.DateUtil
import java.io.File
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
    val monthlyBackups by viewModel.monthlyBackups.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf<Uri?>(null) }
    var showRestoreMonthlyConfirmDialog by remember { mutableStateOf<MonthlyBackupItem?>(null) }

    // Security PIN dialog triggers
    var showPinToEditSettings by remember { mutableStateOf(false) }
    var showPinToDisableLock by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showSetInitialPinDialog by remember { mutableStateOf(false) }

    // File to export from monthly backup list
    var fileToExportUri by remember { mutableStateOf<File?>(null) }

    // Direct Local Save Launchers (Storage Access Framework)
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
            val sourceFile = fileToExportUri
            if (sourceFile != null) {
                // Copy monthly backup file into destination uri
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    fileToExportUri = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                viewModel.saveJsonToUri(uri) {}
            }
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
        // 1. SALVA FILE & BACKUP LOCALE
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
                                text = "Salvataggio Locale & Esportazione",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Salva direttamente nella memoria del tuo telefono",
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
                        description = "Prospetto ufficiale con assenze, percentuali e date.",
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
                        description = "Tabella compatibile con Excel e Google Fogli.",
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
                        description = "Archivio di sicurezza di tutte le assenze e note.",
                        buttonLabel = "Salva Backup",
                        onAction = {
                            fileToExportUri = null
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

        // 2. PIANIFICAZIONE BACKUP AUTOMATICO MENSILE
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventRepeat,
                                    contentDescription = "Backup Mensile",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Backup Automatico Mensile",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pianifica un backup all'inizio di ogni mese",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = settings.autoMonthlyBackupEnabled,
                            onCheckedChange = { viewModel.setAutoMonthlyBackupEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settings.lastMonthlyBackupMonth.isNotBlank()) {
                                "Ultimo backup: ${settings.lastMonthlyBackupMonth}"
                            } else {
                                "Nessun backup mensile recente"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { viewModel.performMonthlyBackupNow() },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Esegui Ora", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (monthlyBackups.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Snapshot Mensili Archiviati:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            monthlyBackups.forEach { backupItem ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = backupItem.displayTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${backupItem.recordCount} assenze archiviate",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    fileToExportUri = backupItem.file
                                                    createJsonLauncher.launch("backup_${backupItem.monthKey}.json")
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = "Esporta", modifier = Modifier.size(14.dp))
                                            }

                                            Button(
                                                onClick = { showRestoreMonthlyConfirmDialog = backupItem },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Ripristina", fontSize = 11.sp)
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

        // 3. LUCCHETTO DI SICUREZZA (PIN) PER MODIFICHE E CANCELLAZIONI
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (settings.isSecurityLockEnabled) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (settings.isSecurityLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Lucchetto",
                                    tint = if (settings.isSecurityLockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Lucchetto di Sicurezza",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Protegge data inizio, stanza e rimozione assenze",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = settings.isSecurityLockEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    showSetInitialPinDialog = true
                                } else {
                                    showPinToDisableLock = true
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settings.isSecurityLockEnabled) "Stato: Attivo 🔒 (Richiede PIN)" else "Stato: Disattivato 🔓",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (settings.isSecurityLockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (settings.isSecurityLockEnabled) {
                            TextButton(onClick = { showChangePinDialog = true }) {
                                Text("Cambia PIN")
                            }
                        }
                    }
                }
            }
        }

        // 4. PERIOD & STUDENT PROFILE
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
                        TextButton(onClick = {
                            if (settings.isSecurityLockEnabled) {
                                showPinToEditSettings = true
                            } else {
                                showSettingsDialog = true
                            }
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (settings.isSecurityLockEnabled) {
                                    Icon(Icons.Default.Lock, contentDescription = "Bloccato", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Modifica")
                            }
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

        // 5. VERSION CONTROL & PACKAGE INFO
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Versione",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Versione Applicazione",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CleanSettingsRow("Versione", "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
                    CleanSettingsRow("ID Pacchetto", BuildConfig.APPLICATION_ID)
                    CleanSettingsRow("Stato Versione", "Aggiornata (Version Control Attivo)")
                }
            }
        }
    }

    // PIN PROMPT TO EDIT SETTINGS (Start Date & Room)
    if (showPinToEditSettings) {
        SecurityPinDialog(
            title = "Autenticazione Richiesta",
            description = "Inserisci il PIN di sicurezza per modificare la data di decorrenza e la camera.",
            onDismiss = { showPinToEditSettings = false },
            onVerifyPin = { viewModel.verifySecurityPin(it) },
            onSuccess = {
                showPinToEditSettings = false
                showSettingsDialog = true
            }
        )
    }

    // PIN PROMPT TO DISABLE SECURITY LOCK
    if (showPinToDisableLock) {
        SecurityPinDialog(
            title = "Disattivazione Lucchetto",
            description = "Inserisci il PIN per disattivare la protezione di sicurezza.",
            onDismiss = { showPinToDisableLock = false },
            onVerifyPin = { viewModel.verifySecurityPin(it) },
            onSuccess = {
                showPinToDisableLock = false
                viewModel.setSecurityLockEnabled(false)
            }
        )
    }

    // SET INITIAL PIN OR ENABLE LOCK
    if (showSetInitialPinDialog) {
        var newPin by remember { mutableStateOf(settings.securityPin) }
        AlertDialog(
            onDismissRequest = { showSetInitialPinDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = "Lucchetto", tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Attiva Lucchetto di Sicurezza", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Imposta o conferma il PIN a 4 cifre per proteggere data inizio, camera e cancellazioni.")
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("PIN (4 cifre)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length == 4) {
                            viewModel.updateSecurityPin(newPin)
                            viewModel.setSecurityLockEnabled(true)
                            showSetInitialPinDialog = false
                        }
                    },
                    enabled = newPin.length == 4
                ) {
                    Text("Attiva")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetInitialPinDialog = false }) { Text("Annulla") }
            }
        )
    }

    // CHANGE PIN DIALOG
    if (showChangePinDialog) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var isOldPinWrong by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Cambia PIN di Sicurezza", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                oldPin = it
                                isOldPinWrong = false
                            }
                        },
                        label = { Text("PIN Attuale") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = isOldPinWrong,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("Nuovo PIN (4 cifre)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!viewModel.verifySecurityPin(oldPin)) {
                            isOldPinWrong = true
                        } else if (newPin.length == 4) {
                            viewModel.updateSecurityPin(newPin)
                            showChangePinDialog = false
                        }
                    },
                    enabled = oldPin.length == 4 && newPin.length == 4
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) { Text("Annulla") }
            }
        )
    }

    // RESTORE MONTHLY CONFIRMATION DIALOG
    showRestoreMonthlyConfirmDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showRestoreMonthlyConfirmDialog = null },
            icon = { Icon(Icons.Default.Restore, contentDescription = "Ripristina", tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Ripristina Snapshot Mensile", fontWeight = FontWeight.Bold) },
            text = {
                Text("Vuoi ripristinare il backup di ${item.displayTitle} (${item.recordCount} assenze)? I dati attuali verranno sostituiti con questo snapshot.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = item
                        showRestoreMonthlyConfirmDialog = null
                        viewModel.restoreMonthlyBackup(target) {}
                    }
                ) {
                    Text("Conferma Ripristino")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreMonthlyConfirmDialog = null }) { Text("Annulla") }
            }
        )
    }

    // RESTORE IMPORT CONFIRMATION DIALOG
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
