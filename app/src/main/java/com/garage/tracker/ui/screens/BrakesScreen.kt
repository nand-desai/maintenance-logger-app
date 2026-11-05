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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.*
import com.garage.tracker.ui.theme.*

/** Pad thickness colours: comfortable above 6 mm, getting low down to 3 mm, replace below that. */
private fun padColors(mm: Double): Triple<Color, Color, String> = when {
    mm >= 6.0 -> Triple(MintBg, MintFg, "Good")
    mm >= BRAKE_REPLACE_MM -> Triple(AmberBg, AmberFg, "Getting low")
    else -> Triple(CoralBg, CoralFg, "Replace soon")
}

@Composable
fun BrakesScreen(
    vehicle: Vehicle,
    entries: List<BrakeEntry>,
    onLog: (type: String) -> Unit,
    onEdit: (BrakeEntry) -> Unit,
    onDelete: (BrakeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SoftCard {
                Text("Brake pads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Replace around ${BRAKE_REPLACE_MM.toInt()} mm. New pads are about ${BRAKE_NEW_MM.toInt() - 2}–${BRAKE_NEW_MM.toInt()} mm.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                if (entries.none { it.frontMm != null || it.rearMm != null }) {
                    Text("No measurements yet. Measure the pad thickness (your mechanic can tell you) and log it here.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Gauge("Front pads", latestBrake(entries, true), true, entries, vehicle.currentOdometer)
                    Spacer(Modifier.height(16.dp))
                    Gauge("Rear pads", latestBrake(entries, false), false, entries, vehicle.currentOdometer)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onLog(BrakeType.MEASURED) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Straighten, null); Spacer(Modifier.width(6.dp)); Text("Log mm")
                    }
                    OutlinedButton(onClick = { onLog(BrakeType.REPLACED) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Build, null); Spacer(Modifier.width(6.dp)); Text("New pads")
                    }
                }
            }
        }
        item { Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (entries.isEmpty()) {
            item {
                SoftCard {
                    Text("Nothing logged yet", fontWeight = FontWeight.SemiBold)
                    Text("Log a measurement, or when new pads go on.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(entries, key = { it.entryId }) { e -> BrakeCard(e, { onEdit(e) }, { onDelete(e) }) }
    }
}

@Composable
private fun Gauge(label: String, latest: BrakeEntry?, front: Boolean, all: List<BrakeEntry>, odometer: Int) {
    val mm = latest?.let { if (front) it.frontMm else it.rearMm }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        if (mm == null) Text("—", color = InkSoft)
        else {
            val (bg, fg, word) = padColors(mm)
            Pill("%.1f mm · $word".format(mm), bg, fg)
        }
    }
    if (mm != null && latest != null) {
        val (bg, fg, _) = padColors(mm)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (mm / BRAKE_NEW_MM).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(14.dp), color = fg, trackColor = bg, strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(6.dp))
        val ago = (odometer - latest.odometer).coerceAtLeast(0)
        val left = brakeKmLeft(all, front)
        Text(
            "Measured ${latest.date}" + (if (ago > 0) " (${"%,d".format(ago)} km ago)" else "") +
                (left?.let { " · ≈ ${"%,d".format(it)} km until ${BRAKE_REPLACE_MM.toInt()} mm" } ?: ""),
            color = InkSoft, style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BrakeCard(e: BrakeEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by rememberSaveable(e.entryId) { mutableStateOf(false) }
    val summary = listOfNotNull(e.frontMm?.let { "Front %.1f".format(it) }, e.rearMm?.let { "Rear %.1f".format(it) }).joinToString(" · ")
    SoftCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (e.type == BrakeType.REPLACED) Icons.Default.Build else Icons.Default.Straighten, null, tint = Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (e.type == BrakeType.REPLACED) "New pads fitted" else "Pads measured", fontWeight = FontWeight.SemiBold)
                Text("${e.date} · ${"%,d".format(e.odometer)} km", color = InkSoft, style = MaterialTheme.typography.bodySmall)
            }
            Text(summary + if (summary.isNotEmpty()) " mm" else "", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = InkSoft)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            e.frontMm?.let { DetailRow("Front pads", "%.1f mm".format(it)) }
            e.rearMm?.let { DetailRow("Rear pads", "%.1f mm".format(it)) }
            if (e.cost > 0) DetailRow("Cost", money(e.cost))
            if (e.note.isNotBlank()) DetailRow("Note", e.note)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                TextButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = CoralFg); Spacer(Modifier.width(6.dp)); Text("Delete", color = CoralFg) }
            }
        }
    }
}
