package com.garage.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.*
import com.garage.tracker.ui.components.DonutChart
import com.garage.tracker.ui.components.Slice
import com.garage.tracker.ui.theme.*

@Composable
fun AnalyticsScreen(
    vehicle: Vehicle,
    logs: List<ExpenseLog>,
    insurance: RecurringExpense?,
    onInsurance: () -> Unit,
    onEdit: (ExpenseLog) -> Unit,
    onDelete: (ExpenseLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = summarize(logs, logs.minOfOrNull { it.odometer }, vehicle.currentOdometer)
    val slices = Categories.all.mapIndexed { i, c -> Slice(c, summary.byCategory[c] ?: 0.0, ChartColors[i % ChartColors.size]) }
    var totalOpen by rememberSaveable { mutableStateOf(false) }
    var donutOpen by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SoftCard(onClick = onInsurance) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Primary.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shield, null, tint = Primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Insurance premium", fontWeight = FontWeight.SemiBold)
                        Text(
                            insurance?.let {
                                "${money(it.amount)} every month on day ${it.dayOfMonth}" + if (it.active) " · next ${it.nextDue}" else " · paused"
                            } ?: "Add your monthly premium and Carlo logs it for you",
                            color = InkSoft, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(if (insurance == null) Icons.Default.Add else Icons.Default.Edit, null, tint = InkSoft)
                }
            }
        }
        item {
            SoftCard(onClick = { totalOpen = !totalOpen }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total cost of ownership", Modifier.weight(1f), color = InkSoft, style = MaterialTheme.typography.labelLarge)
                    Icon(if (totalOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = InkSoft)
                }
                Text(money(summary.lifetimeCost), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("Cost / km", summary.costPerKm?.let { money(it) } ?: "—")
                    Stat("Fuel use", summary.litresPer100Km?.let { "%.1f L/100 km".format(it) } ?: "—")
                }
                if (totalOpen) {
                    Spacer(Modifier.height(16.dp))
                    DetailRow("Entries logged", logs.size.toString())
                    DetailRow("Distance tracked", "${"%,d".format(vehicle.currentOdometer - (logs.minOfOrNull { it.odometer } ?: vehicle.currentOdometer))} km")
                    DetailRow("Fuel bought", "%.1f L".format(logs.sumOf { it.fuelLiters }))
                }
            }
        }
        item {
            SoftCard(onClick = { donutOpen = !donutOpen }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Where the money goes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Icon(if (donutOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = InkSoft)
                }
                Spacer(Modifier.height(16.dp))
                if (donutOpen) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DonutChart(slices, size = 240.dp, thickness = 34.dp) }
                    Spacer(Modifier.height(16.dp))
                    slices.forEach { s ->
                        val pct = if (summary.lifetimeCost > 0) s.value / summary.lifetimeCost * 100 else 0.0
                        Legend(s, "${s.label}  ${money(s.value)}  (${"%.0f".format(pct)}%)")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        DonutChart(slices)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slices.forEach { s -> Legend(s, "${s.label}  ${money(s.value, 0)}") }
                        }
                    }
                }
            }
        }
        item { Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (logs.isEmpty()) {
            item {
                SoftCard {
                    Text("No entries yet", fontWeight = FontWeight.SemiBold)
                    Text("Tap “Add entry” to log fuel, a service, insurance or an upgrade.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(logs, key = { it.logId }) { l -> ExpenseCard(l, onEdit, onDelete) }
    }
}

private fun expenseTitle(l: ExpenseLog) = when {
    l.category == Categories.OTHER && l.note.isNotBlank() -> l.note
    l.note.isNotBlank() -> "${l.category} · ${l.note}"
    else -> l.category
}

@Composable
private fun ExpenseCard(l: ExpenseLog, onEdit: (ExpenseLog) -> Unit, onDelete: (ExpenseLog) -> Unit) {
    var open by rememberSaveable(l.logId) { mutableStateOf(false) }
    SoftCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(expenseTitle(l) + " · ${money(l.cost)}", fontWeight = FontWeight.SemiBold, style = if (open) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                Text(l.date, color = InkSoft, style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = InkSoft)
        }
        if (open) {
            Spacer(Modifier.height(12.dp))
            if (l.category == Categories.OTHER) DetailRow("Category", "Other")
            if (l.note.isNotBlank()) DetailRow(if (l.category == Categories.OTHER) "What for" else "Note", l.note)
            DetailRow("Odometer", "${"%,d".format(l.odometer)} km")
            if (l.fuelLiters > 0) {
                DetailRow("Fuel", "%.1f L".format(l.fuelLiters))
                DetailRow("Price per litre", money(l.cost / l.fuelLiters, 3))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEdit(l) }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                TextButton(onClick = { onDelete(l) }) {
                    Icon(Icons.Default.DeleteOutline, null, tint = CoralFg); Spacer(Modifier.width(6.dp)); Text("Delete", color = CoralFg)
                }
            }
        }
    }
}

@Composable
private fun Legend(s: Slice, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.size(10.dp).background(s.color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, color = InkSoft, style = MaterialTheme.typography.bodySmall)
    }
}
