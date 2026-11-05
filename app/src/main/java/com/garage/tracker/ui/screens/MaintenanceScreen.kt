package com.garage.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.garage.tracker.ui.theme.*

@Composable
fun MaintenanceScreen(
    vehicle: Vehicle,
    schedules: List<MaintenanceSchedule>,
    dueSoonKm: Int,
    onAddSchedule: () -> Unit,
    onEditSchedule: (MaintenanceSchedule) -> Unit,
    onMarkDone: (MaintenanceSchedule) -> Unit,
    onRemind: (MaintenanceSchedule) -> Unit,
    onDeleteSchedule: (MaintenanceSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            val h = overallHealth(schedules, vehicle.currentOdometer, dueSoonKm).style()
            Pill(
                if (schedules.isEmpty()) "No services tracked yet" else h.label,
                if (schedules.isEmpty()) NeutralBg else h.bg,
                if (schedules.isEmpty()) InkSoft else h.fg,
            )
        }
        if (schedules.isEmpty()) {
            item {
                SoftCard(onClick = onAddSchedule) {
                    Text("Add your first service", fontWeight = FontWeight.SemiBold)
                    Text("Oil change, air filter, brake fluid… Carlo will tell you when each one is due.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
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

