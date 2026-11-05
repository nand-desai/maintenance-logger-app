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
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(initialTitle: String = "", initialNote: String = "", onDismiss: () -> Unit, onSave: (title: String, note: String, triggerAt: Long) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf(initialNote) }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val whenMillis = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val inFuture = whenMillis > System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(title, { title = it }, "What for? (e.g. Oil change)")
                Field(note, { note = it }, "Note (optional)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ pickDate = true }, Modifier.weight(1f)) { Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
                    OutlinedButton({ pickTime = true }, Modifier.weight(1f)) { Text(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) }
                }
                if (!inFuture) Text("Pick a time in the future", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && inFuture, onClick = { onSave(title.trim(), note.trim(), whenMillis); onDismiss() }) { Text("Schedule") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton({ time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}
