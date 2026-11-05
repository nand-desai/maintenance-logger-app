package com.garage.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    vehicle: Vehicle?,
    logs: List<ExpenseLog>,
    onDelete: (ExpenseLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (vehicle == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Add a vehicle first", color = InkSoft) }
        return
    }
    val summary = summarize(logs, logs.minOfOrNull { it.odometer }, vehicle.currentOdometer)
    val slices = Categories.all.mapIndexed { i, c -> Slice(c, summary.byCategory[c] ?: 0.0, ChartColors[i % ChartColors.size]) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SoftCard {
                Text("Total cost of ownership", color = InkSoft, style = MaterialTheme.typography.labelLarge)
                Text("$%,.2f".format(summary.lifetimeCost), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("Cost / mile", summary.costPerMile?.let { "$%.2f".format(it) } ?: "—")
                    Stat("Fuel economy", summary.mpg?.let { "%.1f MPG".format(it) } ?: "—")
                }
            }
        }
        item {
            SoftCard {
                Text("Where the money goes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    DonutChart(slices)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slices.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(s.color, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text("${s.label}  $%,.0f".format(s.value), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        item { Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(logs, key = { it.logId }) { l ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${l.category} · $%,.2f".format(l.cost), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${l.date} · ${"%,d".format(l.odometer)} mi" + if (l.fuelGallons > 0) " · %.1f gal".format(l.fuelGallons) else "",
                            color = InkSoft, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { onDelete(l) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = InkSoft) }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, color = InkSoft, style = MaterialTheme.typography.bodySmall)
    }
}
