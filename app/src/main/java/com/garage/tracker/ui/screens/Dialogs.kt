package com.garage.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.Categories
import com.garage.tracker.data.entryMpg
import java.time.LocalDate

private val num = KeyboardOptions(keyboardType = KeyboardType.Number)
private val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, kb: KeyboardOptions = KeyboardOptions.Default) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, keyboardOptions = kb, modifier = Modifier.fillMaxWidth())

@Composable
fun AddEntryDialog(
    defaultOdometer: Int,
    previousFuelOdometer: Int?,
    onDismiss: () -> Unit,
    onSave: (date: String, odometer: Int, category: String, cost: Double, gallons: Double) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var odo by remember { mutableStateOf(defaultOdometer.toString()) }
    var category by remember { mutableStateOf(Categories.FUEL) }
    var cost by remember { mutableStateOf("") }
    var gallons by remember { mutableStateOf("") }

    val validDate = runCatching { LocalDate.parse(date) }.isSuccess
    val odoV = odo.toIntOrNull()
    val costV = cost.toDoubleOrNull()
    val gal = gallons.toDoubleOrNull() ?: 0.0
    val mpg = if (category == Categories.FUEL && odoV != null) entryMpg(previousFuelOdometer, odoV, gal) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(date, { date = it }, "Date (YYYY-MM-DD)")
                Field(odo, { odo = it }, "Odometer (mi)", num)
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) { Text("Category: $category") }
                    DropdownMenu(menu, { menu = false }) {
                        Categories.all.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; menu = false }) }
                    }
                }
                Field(cost, { cost = it }, "Cost ($)", dec)
                if (category == Categories.FUEL) {
                    Field(gallons, { gallons = it }, "Fuel (gallons)", dec)
                    mpg?.let { Text("≈ %.1f MPG since last fill-up".format(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validDate && odoV != null && costV != null,
                onClick = { onSave(date, odoV!!, category, costV!!, if (category == Categories.FUEL) gal else 0.0); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddVehicleDialog(onDismiss: () -> Unit, onSave: (String, String, Int, Int, String) -> Unit) {
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var odo by remember { mutableStateOf("0") }
    var color by remember { mutableStateOf("#3B82F6") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(make, { make = it }, "Make")
                Field(model, { model = it }, "Model / trim")
                Field(year, { year = it }, "Year", num)
                Field(odo, { odo = it }, "Current odometer (mi)", num)
                Field(color, { color = it }, "Colour (hex)")
            }
        },
        confirmButton = {
            TextButton(
                enabled = make.isNotBlank() && model.isNotBlank() && year.toIntOrNull() != null && odo.toIntOrNull() != null,
                onClick = { onSave(make.trim(), model.trim(), year.toInt(), odo.toInt(), color.trim()); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddScheduleDialog(currentOdometer: Int, onDismiss: () -> Unit, onSave: (String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("5000") }
    var last by remember { mutableStateOf(currentOdometer.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add maintenance item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Service (e.g. Engine Oil & Filter)")
                Field(interval, { interval = it }, "Interval (mi)", num)
                Field(last, { last = it }, "Last done at (mi)", num)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (interval.toIntOrNull() ?: 0) > 0 && last.toIntOrNull() != null,
                onClick = { onSave(name.trim(), interval.toInt(), last.toInt()); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SpreadsheetIdDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var id by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Sheets backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste the ID from your spreadsheet URL (docs.google.com/spreadsheets/d/<ID>/edit).", style = MaterialTheme.typography.bodySmall)
                Field(id, { id = it }, "Spreadsheet ID")
            }
        },
        confirmButton = { TextButton(enabled = id.isNotBlank(), onClick = { onSave(id); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
