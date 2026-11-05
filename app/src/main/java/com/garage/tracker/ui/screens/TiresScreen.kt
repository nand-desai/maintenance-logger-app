package com.garage.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.*
import com.garage.tracker.ui.components.TireCarDiagram
import com.garage.tracker.ui.theme.*

data class SeasonStyle(val color: Color, val bg: Color, val fg: Color, val icon: ImageVector)

fun seasonStyle(season: String?) = when (season) {
    Seasons.SUMMER -> SeasonStyle(Color(0xFFFFB84D), AmberBg, AmberFg, Icons.Default.WbSunny)
    Seasons.WINTER -> SeasonStyle(Color(0xFF6BB8FF), Color(0xFFDCEEFF), Color(0xFF2B6CB0), Icons.Default.AcUnit)
    Seasons.ALL_SEASON -> SeasonStyle(Color(0xFF6FCF97), MintBg, MintFg, Icons.Default.Cloud)
    else -> SeasonStyle(Color(0xFFC9CEDD), Color(0xFFEEF0FA), InkSoft, Icons.Default.DirectionsCar)
}

private fun eventIcon(type: String) = when (type) {
    TireEventType.ROTATION -> Icons.Default.Autorenew
    TireEventType.BALANCING -> Icons.Default.Tune
    TireEventType.ALIGNMENT -> Icons.Default.Straighten
    TireEventType.PUNCTURE -> Icons.Default.ReportProblem
    else -> Icons.Default.SwapHoriz
}

@Composable
fun TiresScreen(
    vehicle: Vehicle,
    sets: List<TireSet>,
    events: List<TireEvent>,
    onAddSet: () -> Unit,
    onEditSet: (TireSet) -> Unit,
    onDeleteSet: (TireSet) -> Unit,
    onMountSet: (TireSet) -> Unit,
    onLogEvent: (type: String?, wheel: String?) -> Unit,
    onDeleteEvent: (TireEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mounted = sets.firstOrNull { it.mounted }
    val punctures = events.filter { it.type == TireEventType.PUNCTURE && it.wheel != null && it.setId == mounted?.setId }
    var selectedSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var wheelDialog by remember { mutableStateOf<String?>(null) }
    val selected = sets.firstOrNull { it.setId == selectedSetId }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { CurrentTiresCard(vehicle, mounted, punctures, hasSets = sets.isNotEmpty(), onAddSet = onAddSet, onWheel = { wheelDialog = it }) }

        item { Text("Your tire sets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sets.forEach { s ->
                    val st = seasonStyle(s.season)
                    FilterChip(
                        selected = s.setId == selectedSetId,
                        onClick = { selectedSetId = if (selectedSetId == s.setId) null else s.setId },
                        label = { Text(if (s.mounted) "${s.name} · on car" else s.name) },
                        leadingIcon = { Icon(st.icon, null, tint = st.fg) },
                    )
                }
                AssistChip(onClick = onAddSet, label = { Text("Add set") }, leadingIcon = { Icon(Icons.Default.Add, null) })
            }
        }
        selected?.let { s ->
            item { SetDetailCard(s, vehicle.currentOdometer, onMount = { onMountSet(s) }, onEdit = { onEditSet(s) }, onDelete = { onDeleteSet(s) }) }
        }

        item { Text("Tire care", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(TireEventType.ROTATION, TireEventType.BALANCING, TireEventType.ALIGNMENT).forEach { t ->
                    CareTile(t, events.latest(t), vehicle.currentOdometer, Modifier.weight(1f)) { onLogEvent(t, null) }
                }
            }
        }

        item { Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (events.isEmpty()) {
            item {
                SoftCard {
                    Text("Nothing logged yet", fontWeight = FontWeight.SemiBold)
                    Text("Tap a care tile or a wheel, or use “Log tire event”.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(events, key = { it.eventId }) { e -> EventCard(e, sets.firstOrNull { it.setId == e.setId }, onDeleteEvent) }
    }

    wheelDialog?.let { w ->
        val here = punctures.filter { it.wheel == w }
        AlertDialog(
            onDismissRequest = { wheelDialog = null },
            title = { Text(wheelNames[w] ?: w) },
            text = {
                if (here.isEmpty()) Text("No punctures logged on this wheel for the current set.")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    here.forEach { e ->
                        Column {
                            Text("${e.date} · ${"%,d".format(e.odometer)} km", fontWeight = FontWeight.SemiBold)
                            if (e.note.isNotBlank()) Text(e.note, color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ wheelDialog = null; onLogEvent(TireEventType.PUNCTURE, w) }) { Text("Log puncture here") } },
            dismissButton = { TextButton({ wheelDialog = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun CurrentTiresCard(
    vehicle: Vehicle, mounted: TireSet?, punctures: List<TireEvent>, hasSets: Boolean,
    onAddSet: () -> Unit, onWheel: (String) -> Unit,
) {
    val st = seasonStyle(mounted?.season)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("On the car now", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (mounted != null) Pill(mounted.season, st.bg, st.fg)
        }
        if (mounted != null) {
            Spacer(Modifier.height(4.dp))
            Text(mounted.name, fontWeight = FontWeight.SemiBold)
            val line = listOf(mounted.brand, mounted.size).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotEmpty()) Text(line, color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            Text("${"%,d".format(mounted.kmDriven(vehicle.currentOdometer))} km driven on this set", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                if (hasSets) "No set is mounted. Open a set below and tap “Mount on car”." else "Add your tire sets to track what's on the car.",
                color = InkSoft, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TireCarDiagram(
                bodyColor = parseColor(vehicle.colorHex).copy(alpha = 0.85f),
                wheelColor = st.color,
                centerIcon = if (mounted != null) st.icon else null,
                punctures = punctures.groupingBy { it.wheel!! }.eachCount(),
                onWheelClick = onWheel,
            )
        }
        Text("Tap a wheel to see its punctures or log a new one", Modifier.fillMaxWidth(), color = InkSoft, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        if (!hasSets) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add your first tire set") }
        }
    }
}

@Composable
private fun SetDetailCard(s: TireSet, odometer: Int, onMount: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val st = seasonStyle(s.season)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Pill(s.season, st.bg, st.fg)
        }
        Spacer(Modifier.height(8.dp))
        if (s.brand.isNotBlank()) DetailRow("Brand / model", s.brand)
        if (s.size.isNotBlank()) DetailRow("Size", s.size)
        DetailRow("Driven on this set", "${"%,d".format(s.kmDriven(odometer))} km")
        DetailRow("Status", if (s.mounted) "On the car" else "In storage")
        Spacer(Modifier.height(8.dp))
        if (!s.mounted) {
            Button(onClick = onMount, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.SwapHoriz, null); Spacer(Modifier.width(8.dp)); Text("Mount on car") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
            TextButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = CoralFg); Spacer(Modifier.width(6.dp)); Text("Delete", color = CoralFg) }
        }
    }
}

@Composable
private fun CareTile(type: String, last: TireEvent?, odometer: Int, modifier: Modifier, onClick: () -> Unit) {
    val since = last?.let { (odometer - it.odometer).coerceAtLeast(0) }
    val ratio = since?.let { it.toFloat() / careIntervalKm(type) }
    val (bg, fg) = when {
        ratio == null -> Color(0xFFEEF0FA) to InkSoft
        ratio < 0.8f -> MintBg to MintFg
        ratio < 1f -> AmberBg to AmberFg
        else -> CoralBg to CoralFg
    }
    SoftCard(modifier, onClick = onClick) {
        Box(Modifier.size(40.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(eventIcon(type), null, tint = fg)
        }
        Spacer(Modifier.height(8.dp))
        Text(type, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        Text(since?.let { "${"%,d".format(it)} km ago" } ?: "Not logged", color = fg, style = MaterialTheme.typography.bodySmall)
        if (ratio != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
                color = fg, trackColor = bg, strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun EventCard(e: TireEvent, set: TireSet?, onDelete: (TireEvent) -> Unit) {
    var open by rememberSaveable(e.eventId) { mutableStateOf(false) }
    val title = if (e.type == TireEventType.PUNCTURE && e.wheel != null) "Puncture · ${wheelNames[e.wheel] ?: e.wheel}" else e.type
    SoftCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(eventIcon(e.type), null, tint = Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text("${e.date} · ${"%,d".format(e.odometer)} km", color = InkSoft, style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = InkSoft)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            set?.let { DetailRow("Tire set", it.name) }
            if (e.note.isNotBlank()) DetailRow("Note", e.note)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onDelete(e) }) { Icon(Icons.Default.DeleteOutline, null, tint = CoralFg); Spacer(Modifier.width(6.dp)); Text("Delete", color = CoralFg) }
            }
        }
    }
}
