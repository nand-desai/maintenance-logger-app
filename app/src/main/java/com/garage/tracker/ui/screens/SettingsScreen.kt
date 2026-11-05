package com.garage.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.AppSettings
import com.garage.tracker.data.Vehicle
import com.garage.tracker.ui.components.VehiclePhoto
import com.garage.tracker.ui.theme.CoralFg
import com.garage.tracker.ui.theme.InkSoft
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

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
    onAddVehicle: () -> Unit,
    onEditVehicle: () -> Unit,
    onSetOdometer: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
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
            SoftCard {
                Text("Your data", fontWeight = FontWeight.SemiBold)
                Text("Everything is stored on this phone. Save a copy to Google Drive or anywhere else with Back up.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBackup, modifier = Modifier.weight(1f)) { Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(6.dp)); Text("Back up") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Import") }
                }
            }
        }

        item { Section("${vehicle.make} ${vehicle.model}", "Settings for this vehicle") }
        item {
            SoftCard {
                VehiclePhoto(photoFile, photoVersion, onPickPhoto, height = 150.dp)
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
