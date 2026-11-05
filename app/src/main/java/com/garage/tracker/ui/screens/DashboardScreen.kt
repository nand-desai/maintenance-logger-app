package com.garage.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.*
import com.garage.tracker.ui.components.Car3DView
import com.garage.tracker.ui.theme.*

@Composable
fun DashboardScreen(
    vehicle: Vehicle?,
    vehicles: List<Vehicle>,
    schedules: List<MaintenanceSchedule>,
    onSelectVehicle: (String) -> Unit,
    onAddVehicle: () -> Unit,
    onBump: (Int) -> Unit,
    onAddSchedule: () -> Unit,
    onMarkDone: (MaintenanceSchedule) -> Unit,
    onRemind: (MaintenanceSchedule) -> Unit,
    modelFile: java.io.File?,
    modelVersion: Int,
    onPickModel: () -> Unit,
    onDeleteSchedule: (MaintenanceSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { GarageHeader(vehicle, vehicles, onSelectVehicle, onAddVehicle, onBump) }
        item { Car3DView(modelFile, modelVersion, onPickModel) }
        if (vehicle != null) {
            item {
                val h = overallHealth(schedules, vehicle.currentOdometer).style()
                Pill(h.label, h.bg, h.fg)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Upcoming maintenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onAddSchedule) { Icon(Icons.Default.Add, null); Text("Add") }
                }
            }
            items(schedules.sortedBy { it.kmRemaining(vehicle.currentOdometer) }, key = { it.scheduleId }) { s ->
                MaintenanceCard(s, vehicle.currentOdometer, { onMarkDone(s) }, { onRemind(s) }, { onDeleteSchedule(s) })
            }
        }
    }
}

@Composable
private fun GarageHeader(
    vehicle: Vehicle?, vehicles: List<Vehicle>,
    onSelect: (String) -> Unit, onAdd: () -> Unit, onBump: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(vehicle?.colorHex?.let(::parseColor) ?: Primary, CircleShape))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                Column(Modifier.clickableNoRipple { open = true }) {
                    Text(
                        vehicle?.let { "${it.make} ${it.model}" } ?: "No vehicle yet",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        vehicle?.let { "${it.year} · ${"%,d".format(it.currentOdometer)} km" } ?: "Tap + to add one",
                        color = InkSoft, style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
            IconButton(onClick = { open = true }) { Icon(Icons.Default.ArrowDropDown, "Switch vehicle") }
            if (vehicle != null) {
                FilledTonalButton(onClick = { onBump(100) }) { Text("+100 km") }
            }
        }
    }
}

@Composable
private fun MaintenanceCard(s: MaintenanceSchedule, odometer: Int, onDone: () -> Unit, onRemind: () -> Unit, onDelete: () -> Unit) {
    val h = s.health(odometer).style()
    val left = s.kmRemaining(odometer)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.serviceName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Pill(
                if (left < 0) "${"%,d".format(-left)} km overdue" else "${"%,d".format(left)} km left",
                h.bg, h.fg,
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { s.progress(odometer) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = h.fg, trackColor = h.bg, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Every ${"%,d".format(s.intervalKm)} km · last at ${"%,d".format(s.lastDoneOdometer)}", Modifier.weight(1f), color = InkSoft, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDone) { Text("Mark done") }
            IconButton(onClick = onRemind) { Icon(Icons.Default.NotificationAdd, "Remind me", tint = Primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = InkSoft) }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClick)
}
