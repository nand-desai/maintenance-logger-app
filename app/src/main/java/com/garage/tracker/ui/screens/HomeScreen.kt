package com.garage.tracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.*
import com.garage.tracker.ui.components.VehiclePhoto
import com.garage.tracker.ui.theme.*
import java.io.File

/** The landing hub: the car, what needs attention, and a tile for every section. */
@Composable
fun HomeScreen(
    vehicle: Vehicle,
    vehicles: List<Vehicle>,
    schedules: List<MaintenanceSchedule>,
    logs: List<ExpenseLog>,
    tireSets: List<TireSet>,
    tireEvents: List<TireEvent>,
    brakes: List<BrakeEntry>,
    reminders: List<Reminder>,
    dueSoonKm: Int,
    photoFile: File?,
    photoVersion: Int,
    onSelectVehicle: (String) -> Unit,
    onAddVehicle: () -> Unit,
    onEditOdometer: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpen: (String) -> Unit,
    backupOn: Boolean,
    onSetupBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alerts = buildAlerts(vehicle, schedules, dueSoonKm, tireSets, tireEvents, brakes)
    val tiles = buildTiles(vehicle, schedules, logs, dueSoonKm, tireSets, tireEvents, brakes, reminders)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { VehiclePhoto(photoFile, photoVersion, onPickPhoto) }
        item(span = { GridItemSpan(maxLineSpan) }) { GarageHeader(vehicle, vehicles, onSelectVehicle, onAddVehicle, onEditOdometer) }
        item(span = { GridItemSpan(maxLineSpan) }) { AttentionBanner(alerts, onOpen) }
        items(tiles, key = { it.id }) { tile -> HomeTile(tile) { onOpen(tile.id) } }
        if (!backupOn) item(span = { GridItemSpan(maxLineSpan) }) {
            SoftCard(onClick = onSetupBackup) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null, tint = Primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Keep your data safe", fontWeight = FontWeight.SemiBold)
                        Text("Pick a backup folder so it survives reinstalling Carlo.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = InkSoft)
                }
            }
        }
    }
}

@Composable
private fun AttentionBanner(alerts: List<Alert>, onOpen: (String) -> Unit) {
    val top = alerts.firstOrNull()
    val (bg, fg) = levelColors(top?.level ?: Level.Good)
    val animBg by animateColorAsState(bg, tween(400), label = "bannerBg")
    val animFg by animateColorAsState(fg, tween(400), label = "bannerFg")
    Card(
        onClick = { top?.let { onOpen(it.target) } },
        enabled = top != null,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = animBg, disabledContainerColor = animBg),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(animFg.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (top == null) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = animFg)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(top?.text ?: "Everything looks good", fontWeight = FontWeight.SemiBold, color = animFg)
                Text(
                    when {
                        top == null -> "Nothing needs attention right now"
                        alerts.size > 1 -> "+${alerts.size - 1} more · tap to take a look"
                        else -> "Tap to open"
                    },
                    style = MaterialTheme.typography.bodySmall, color = animFg.copy(alpha = 0.8f),
                )
            }
            if (top != null) Icon(Icons.Default.ChevronRight, null, tint = animFg)
        }
    }
}

/** A section tile. It dips slightly while pressed; there is no entrance animation. */
@Composable
private fun HomeTile(tile: TileInfo, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    val (_, statusColor) = levelColors(tile.level)

    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().heightIn(min = 138.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(44.dp).background(tile.accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(tile.icon, null, tint = tile.accent)
                }
                Spacer(Modifier.weight(1f))
                if (tile.level != Level.Neutral) Box(Modifier.padding(top = 4.dp).size(10.dp).background(statusColor, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Column {
                Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    tile.status, style = MaterialTheme.typography.bodySmall,
                    color = if (tile.level == Level.Neutral) InkSoft else statusColor,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
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

