package com.garage.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.*
import com.garage.tracker.ui.components.VehiclePhoto
import com.garage.tracker.ui.theme.*
import java.io.File

@Composable
fun DashboardScreen(
    vehicle: Vehicle,
    vehicles: List<Vehicle>,
    schedules: List<MaintenanceSchedule>,
    dueSoonKm: Int,
    photoFile: File?,
    photoVersion: Int,
    onSelectVehicle: (String) -> Unit,
    onAddVehicle: () -> Unit,
    onEditOdometer: () -> Unit,
    onPickPhoto: () -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (MaintenanceSchedule) -> Unit,
    onMarkDone: (MaintenanceSchedule) -> Unit,
    onRemind: (MaintenanceSchedule) -> Unit,
    onDeleteSchedule: (MaintenanceSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { VehiclePhoto(photoFile, photoVersion, onPickPhoto) }
        item { GarageHeader(vehicle, vehicles, onSelectVehicle, onAddVehicle, onEditOdometer) }
        item {
            val h = overallHealth(schedules, vehicle.currentOdometer, dueSoonKm).style()
            Pill(if (schedules.isEmpty()) "No services tracked yet" else h.label,
                if (schedules.isEmpty()) androidx.compose.ui.graphics.Color(0xFFEEF0FA) else h.bg,
                if (schedules.isEmpty()) InkSoft else h.fg)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Upcoming maintenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onAddSchedule) { Icon(Icons.Default.Add, null); Text("Add") }
            }
        }
        if (schedules.isEmpty()) {
            item {
                SoftCard(onClick = onAddSchedule) {
                    Text("Add your first service", fontWeight = FontWeight.SemiBold)
                    Text("Oil change, tire rotation, brake fluid… Carlo will tell you when each one is due.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(schedules.sortedBy { it.kmRemaining(vehicle.currentOdometer) }, key = { it.scheduleId }) { s ->
            MaintenanceCard(
                s, vehicle.currentOdometer, dueSoonKm,
                onDone = { onMarkDone(s) }, onRemind = { onRemind(s) },
                onEdit = { onEditSchedule(s) }, onDelete = { onDeleteSchedule(s) },
            )
        }
    }
}

@Composable
private fun GarageHeader(
    vehicle: Vehicle, vehicles: List<Vehicle>,
    onSelect: (String) -> Unit, onAdd: () -> Unit, onEditOdometer: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(parseColor(vehicle.colorHex), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${vehicle.make} ${vehicle.model}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.clickable(onClick = onEditOdometer).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${vehicle.year} · ${"%,d".format(vehicle.currentOdometer)} km", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Edit, "Edit odometer", tint = Primary, modifier = Modifier.size(14.dp))
                }
            }
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Default.UnfoldMore, "Switch vehicle") }
                DropdownMenu(open, { open = false }) {
                    vehicles.forEach { v ->
                        DropdownMenuItem(text = { Text("${v.make} ${v.model}") }, onClick = { onSelect(v.vehicleId); open = false })
                    }
                    DropdownMenuItem(
                        text = { Text("Add vehicle") }, leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = { open = false; onAdd() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MaintenanceCard(
    s: MaintenanceSchedule, odometer: Int, dueSoonKm: Int,
    onDone: () -> Unit, onRemind: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    var open by rememberSaveable(s.scheduleId) { mutableStateOf(false) }
    val h = s.health(odometer, dueSoonKm).style()
    val left = s.kmRemaining(odometer)
    val dueAt = s.lastDoneOdometer + s.intervalKm

    SoftCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.serviceName, Modifier.weight(1f),
                style = if (open) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Pill(if (left < 0) "${"%,d".format(-left)} km overdue" else "${"%,d".format(left)} km left", h.bg, h.fg)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = InkSoft)
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { s.progress(odometer) },
            modifier = Modifier.fillMaxWidth().height(if (open) 14.dp else 8.dp),
            color = h.fg, trackColor = h.bg, strokeCap = StrokeCap.Round,
        )
        if (open) {
            Spacer(Modifier.height(16.dp))
            DetailRow("Service interval", "Every ${"%,d".format(s.intervalKm)} km")
            DetailRow("Last done at", "${"%,d".format(s.lastDoneOdometer)} km")
            DetailRow("Next due at", "${"%,d".format(dueAt)} km")
            DetailRow("Status", h.label)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDone, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Done now") }
                OutlinedButton(onClick = onRemind, modifier = Modifier.weight(1f)) { Icon(Icons.Default.NotificationAdd, null); Spacer(Modifier.width(6.dp)); Text("Remind me") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, null, tint = CoralFg); Spacer(Modifier.width(6.dp)); Text("Delete", color = CoralFg)
                }
            }
        }
    }
}
