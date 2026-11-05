package com.garage.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.garage.tracker.StorageUsage
import com.garage.tracker.data.AppSettings
import kotlinx.coroutines.launch
import com.garage.tracker.data.RecurringExpense
import com.garage.tracker.data.Vehicle
import com.garage.tracker.ui.components.VehiclePhoto
import com.garage.tracker.ui.theme.CoralFg
import com.garage.tracker.ui.theme.InkSoft
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vehicle: Vehicle,
    settings: AppSettings,
    photoFile: File?,
    photoVersion: Int,
    onDueSoonChange: (Int) -> Unit,
    onReminderHourChange: (Int) -> Unit,
    onBackup: () -> Unit,
    onImport: () -> Unit,
    onThemeChange: (String) -> Unit,
    onChooseBackupFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onStopAutoBackup: () -> Unit,
    loadStorage: suspend () -> StorageUsage,
    onClearTemp: suspend () -> Long,
    onAddVehicle: () -> Unit,
    onEditVehicle: () -> Unit,
    onSetOdometer: () -> Unit,
    onPickPhoto: () -> Unit,
    onCreateWithAi: () -> Unit,
    onRemovePhoto: () -> Unit,
    insurance: RecurringExpense?,
    onInsurance: () -> Unit,
    onDeleteVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Section("App settings", "Apply to every vehicle") }
        item {
            SoftCard {
                Text("Appearance", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val modes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (key, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == key, onClick = { onThemeChange(key) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label) }
                    }
                }
            }
        }
        item {
            SoftCard {
                Text("Due-soon warning", fontWeight = FontWeight.SemiBold)
                Text("Services turn amber within ${"%,d".format(settings.dueSoonKm)} km of being due", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.dueSoonKm.toFloat(),
                    onValueChange = { onDueSoonChange((it / 500f).roundToInt() * 500) },
                    valueRange = 500f..5000f, steps = 8,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Default reminder time", fontWeight = FontWeight.SemiBold)
                Text(
                    "New reminders start at ${LocalTime.of(settings.reminderHour, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}",
                    color = InkSoft, style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = settings.reminderHour.toFloat(),
                    onValueChange = { onReminderHourChange(it.roundToInt()) },
                    valueRange = 0f..23f, steps = 22,
                )
            }
        }
        item {
            val context = LocalContext.current
            val folderName = settings.backupUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() }
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Automatic backup", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (settings.backupUri == null)
                        "Pick a folder (for example Documents) and Carlo saves everything there after every change: your vehicles, history, photos and settings. It stays on your phone when Carlo is uninstalled, and you can restore it from the welcome screen after reinstalling."
                    else "Saving to ${folderName ?: "your chosen folder"}" +
                        (if (settings.lastBackup > 0) " · last saved ${DateUtils.getRelativeTimeSpanString(settings.lastBackup, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}" else ""),
                    color = InkSoft, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                if (settings.backupUri == null) {
                    Button(onClick = onChooseBackupFolder, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose backup folder") }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBackupNow, modifier = Modifier.weight(1f)) { Text("Back up now") }
                        OutlinedButton(onClick = onChooseBackupFolder, modifier = Modifier.weight(1f)) { Text("Change folder") }
                    }
                    TextButton(onClick = onStopAutoBackup) { Text("Turn off automatic backup") }
                }
            }
        }
        item {
            var refresh by remember { mutableIntStateOf(0) }
            val usage by produceState<StorageUsage?>(null, refresh) { value = loadStorage() }
            val scope = rememberCoroutineScope()
            SoftCard {
                Text("Storage", fontWeight = FontWeight.SemiBold)
                usage?.let { u ->
                    DetailRow("Receipts and files", "${u.receiptCount} · ${formatBytes(u.receiptBytes)}")
                    DetailRow("Vehicle photos", formatBytes(u.photoBytes))
                    DetailRow("Temporary files", formatBytes(u.tempBytes))
                    Text(
                        "Pictures are shrunk when you add them, and deleting an entry deletes its receipts. Temporary files (the zip made for sharing, camera shots) clear themselves after a day.",
                        color = InkSoft, style = MaterialTheme.typography.bodySmall,
                    )
                    if (u.tempBytes > 0) TextButton(onClick = { scope.launch { onClearTemp(); refresh++ } }) { Text("Clear temporary files now") }
                }
            }
        }
        item {
            SoftCard {
                Text("Your data", fontWeight = FontWeight.SemiBold)
                Text("Everything is stored on this phone. Back up saves one zip with your data, vehicle photos and receipts; save it to Google Drive or anywhere else.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBackup, modifier = Modifier.weight(1f)) { Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(6.dp)); Text("Back up (zip)") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Import") }
                }
            }
        }

        item { Section("${vehicle.make} ${vehicle.model}", "Settings for this vehicle") }
        item {
            SoftCard {
                VehiclePhoto(photoFile, photoVersion, onPickPhoto, height = 150.dp)
                TextButton(onClick = onCreateWithAi) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Make a cartoon car with AI") }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickPhoto, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Photo, null); Spacer(Modifier.width(6.dp)); Text(if (photoFile?.exists() == true) "Change photo" else "Add photo") }
                    if (photoFile?.exists() == true) {
                        OutlinedButton(onClick = onRemovePhoto, modifier = Modifier.weight(1f)) { Text("Remove photo") }
                    }
                }
            }
        }
        item {
            SoftCard {
                SettingRow(Icons.Default.Edit, "Edit details", "${vehicle.year} ${vehicle.make} ${vehicle.model}", onEditVehicle)
                SettingRow(Icons.Default.Speed, "Set odometer", "${"%,d".format(vehicle.currentOdometer)} km", onSetOdometer)
                SettingRow(
                    Icons.Default.Payments, "Monthly insurance",
                    insurance?.let { "${money(it.amount)} on day ${it.dayOfMonth}" + if (it.active) " · next ${it.nextDue}" else " · paused" }
                        ?: "Not set up. Tap to add it automatically each month",
                    onInsurance,
                )
                SettingRow(Icons.Default.Add, "Add another vehicle", "Switch between cars from the Garage tab", onAddVehicle)
            }
        }
        item {
            OutlinedButton(
                onClick = onDeleteVehicle, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralFg),
            ) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Delete this vehicle") }
        }
        item {
            Text("Carlo · km, litres, L/100 km · Canadian dollars", color = InkSoft, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = InkSoft, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, color = InkSoft) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = InkSoft) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

private fun formatBytes(n: Long) = if (n >= 1_000_000) "%.1f MB".format(n / 1_000_000.0) else "${n / 1000} KB"
