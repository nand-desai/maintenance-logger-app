package com.garage.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import com.garage.tracker.data.ExpenseLog
import com.garage.tracker.data.MaintenanceRecord
import com.garage.tracker.data.MaintenanceSchedule
import com.garage.tracker.data.RecurringExpense
import com.garage.tracker.data.Vehicle
import com.garage.tracker.data.Categories
import com.garage.tracker.data.entryLitresPer100Km
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal val num = KeyboardOptions(keyboardType = KeyboardType.Number)
internal val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

@Composable
internal fun Field(value: String, onChange: (String) -> Unit, label: String, kb: KeyboardOptions = KeyboardOptions.Default) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, keyboardOptions = kb, modifier = Modifier.fillMaxWidth())

private fun parseDate(s: String?): LocalDate =
    s?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()

/** Accepts "6,5" as well as "6.5". */
internal fun String.toNumber(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** Add an expense, or edit one when [initial] is given. */
@Composable
fun AddEntryDialog(
    initial: ExpenseLog?,
    defaultOdometer: Int,
    previousFuelOdometer: Int?,
    onDismiss: () -> Unit,
    onSave: (date: String, odometer: Int, category: String, cost: Double, litres: Double, note: String) -> Unit,
) {
    var date by remember { mutableStateOf(parseDate(initial?.date)) }
    var odo by remember { mutableStateOf((initial?.odometer ?: defaultOdometer).toString()) }
    var category by remember { mutableStateOf(initial?.category ?: Categories.FUEL) }
    var cost by remember { mutableStateOf(initial?.cost?.toString() ?: "") }
    var litres by remember { mutableStateOf(initial?.fuelLiters?.takeIf { it > 0 }?.toString() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val odoV = odo.toIntOrNull()
    val costV = cost.toNumber()
    val fuel = litres.toNumber() ?: 0.0
    val consumption = if (category == Categories.FUEL && odoV != null) entryLitresPer100Km(previousFuelOdometer, odoV, fuel) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add entry" else "Edit entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(date, { date = it })
                Field(odo, { odo = it }, "Odometer (km)", num)
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) { Text("Category: $category") }
                    DropdownMenu(menu, { menu = false }) {
                        Categories.all.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; menu = false }) }
                    }
                }
                if (category == Categories.OTHER) Field(note, { note = it }, "What was it for? (e.g. remote starter)")
                Field(cost, { cost = it }, "Cost (CAD)", dec)
                if (category == Categories.FUEL) {
                    Field(litres, { litres = it }, "Fuel (litres)", dec)
                    consumption?.let { Text("≈ %.1f L/100 km since last fill-up".format(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = odoV != null && costV != null && (category != Categories.OTHER || note.isNotBlank()),
                onClick = {
                    onSave(
                        date.toString(), odoV!!, category, costV!!, if (category == Categories.FUEL) fuel else 0.0,
                        // keep the description of a service/tire/brake expense; "Other" uses what was typed
                        if (category == Categories.OTHER) note.trim() else if (initial?.category != Categories.OTHER) initial?.note.orEmpty() else "",
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

private val vehicleColors = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#6B7280")

private fun Color.toHex() = "#%06X".format(0xFFFFFF and toArgb())

/** Add a vehicle, or edit one when [initial] is given. Nothing is pre-filled for a new vehicle. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun VehicleDialog(
    initial: Vehicle?,
    savedColors: List<String>,
    onSaveColor: (String) -> Unit,
    onRemoveColor: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (make: String, model: String, year: Int, odometer: Int, color: String) -> Unit,
) {
    var make by remember { mutableStateOf(initial?.make ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var year by remember { mutableStateOf(initial?.year?.toString() ?: "") }
    var odo by remember { mutableStateOf(initial?.currentOdometer?.toString() ?: "") }
    var color by remember { mutableStateOf(initial?.colorHex ?: vehicleColors.first()) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add vehicle" else "Edit vehicle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(make, { make = it }, "Make (e.g. Toyota)")
                Field(model, { model = it }, "Model / trim (e.g. Corolla SE)")
                Field(year, { year = it }, "Year", num)
                Field(odo, { odo = it }, "Current odometer (km)", num)
                Text("Colour tag", style = MaterialTheme.typography.labelLarge)
                val palette = (vehicleColors + savedColors).distinctBy { it.uppercase() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    palette.forEach { hex ->
                        val isSaved = savedColors.any { it.equals(hex, true) } && vehicleColors.none { it.equals(hex, true) }
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(parseColor(hex))
                                .border(if (hex.equals(color, true)) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .combinedClickable(onClick = { color = hex }, onLongClick = { if (isSaved) onRemoveColor(hex) }),
                        )
                    }
                    if (palette.none { it.equals(color, true) }) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(parseColor(color)).border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
                    }
                    // "pick your own", always last
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                            .clickable { picking = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, "Pick your own colour", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                if (savedColors.isNotEmpty()) {
                    Text("Long-press a saved colour to remove it.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = make.isNotBlank() && model.isNotBlank() && year.toIntOrNull() != null && odo.toIntOrNull() != null,
                onClick = { onSave(make.trim(), model.trim(), year.toInt(), odo.toInt(), color); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        CustomColorDialog(
            initialHex = color,
            onDismiss = { picking = false },
            onUse = { hex, save -> color = hex; if (save) onSaveColor(hex); picking = false },
        )
    }
}

/** Hue / saturation / brightness sliders with a live preview. Optionally saves the colour to "my colours". */
@Composable
fun CustomColorDialog(initialHex: String, onDismiss: () -> Unit, onUse: (hex: String, save: Boolean) -> Unit) {
    val start = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(parseColor(initialHex).toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1].coerceAtLeast(0.2f)) }
    var value by remember { mutableFloatStateOf(start[2].coerceAtLeast(0.3f)) }
    var save by remember { mutableStateOf(true) }
    val picked = Color.hsv(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick your own colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(picked))
                    Spacer(Modifier.width(16.dp))
                    Text(picked.toHex(), style = MaterialTheme.typography.titleMedium)
                }
                Text("Colour", style = MaterialTheme.typography.labelMedium)
                Slider(hue, { hue = it }, valueRange = 0f..360f)
                Text("Richness", style = MaterialTheme.typography.labelMedium)
                Slider(sat, { sat = it }, valueRange = 0f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value, { value = it }, valueRange = 0f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(save, { save = it })
                    Text("Save to my colours")
                }
            }
        },
        confirmButton = { TextButton({ onUse(picked.toHex(), save) }) { Text("Use colour") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SetOdometerDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update odometer") },
        text = { Field(value, { value = it }, "Odometer (km)", num) },
        confirmButton = {
            TextButton(enabled = value.toIntOrNull() != null, onClick = { onSave(value.toInt()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Date and price: that's all a car wash needs. Pass [initial] to edit one. */
@Composable
fun LogWashDialog(initial: ExpenseLog?, onDismiss: () -> Unit, onSave: (date: String, cost: Double) -> Unit) {
    var date by remember { mutableStateOf(parseDate(initial?.date)) }
    var cost by remember { mutableStateOf(initial?.cost?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log a car wash" else "Edit car wash") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(date, { date = it })
                Field(cost, { cost = it }, "Cost (CAD, 0 if free)", dec)
            }
        },
        confirmButton = {
            TextButton(enabled = cost.toNumber() != null, onClick = { onSave(date.toString(), cost.toNumber()!!); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Monthly insurance that adds itself to the expenses on the same day every month. */
@Composable
fun InsurancePlanDialog(
    existing: RecurringExpense?,
    onDismiss: () -> Unit,
    onSave: (amount: Double, firstDate: LocalDate, active: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(parseDate(existing?.nextDue)) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly insurance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(amount, { amount = it }, "Monthly payment (CAD)", dec)
                Text(if (existing == null) "First payment date" else "Next payment date", style = MaterialTheme.typography.labelLarge)
                DateButton(date, { date = it })
                Text(
                    "Carlo adds this to your expenses on the same day each month, including any payments from that date up to today.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add automatically", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
                if (existing != null) {
                    TextButton(onClick = { onRemove(); onDismiss() }) { Text("Remove monthly insurance") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = (amount.toNumber() ?: 0.0) > 0, onClick = { onSave(amount.toNumber()!!, date, active); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Mark a service as done: date, odometer, cost and notes. A cost is added to Expenses automatically. */
@Composable
fun ServiceDoneDialog(
    schedule: MaintenanceSchedule,
    initial: MaintenanceRecord?,
    defaultOdometer: Int,
    onDismiss: () -> Unit,
    onSave: (date: String, odometer: Int, cost: Double, note: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var date by remember { mutableStateOf(parseDate(initial?.date)) }
    var odo by remember { mutableStateOf((initial?.odometer ?: defaultOdometer).toString()) }
    var cost by remember { mutableStateOf(initial?.cost?.takeIf { it > 0 }?.toString() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Done: ${schedule.serviceName}" else "Edit: ${schedule.serviceName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(date, { date = it })
                Field(odo, { odo = it }, "Odometer (km)", num)
                Field(cost, { cost = it }, "Cost (CAD, optional)", dec)
                OutlinedTextField(
                    note, { note = it }, label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. synthetic 5W-30, which garage, what the mechanic said") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Text("A cost is added to your Expenses automatically.", style = MaterialTheme.typography.bodySmall)
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete this record") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = odo.toIntOrNull() != null && (cost.isBlank() || cost.toNumber() != null),
                onClick = { onSave(date.toString(), odo.toInt(), cost.toNumber() ?: 0.0, note.trim()); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

private val serviceSuggestions = listOf(
    "Engine oil & filter" to 10000, "Brake fluid" to 40000,
    "Air filter" to 30000, "Coolant" to 60000, "Cabin filter" to 20000,
)

/** Add a maintenance item, or edit one when [initial] is given. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleDialog(currentOdometer: Int, initial: MaintenanceSchedule?, onDismiss: () -> Unit, onSave: (String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf(initial?.serviceName ?: "") }
    var interval by remember { mutableStateOf(initial?.intervalKm?.toString() ?: "") }
    var last by remember { mutableStateOf((initial?.lastDoneOdometer ?: currentOdometer).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add maintenance item" else "Edit maintenance item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Text("Quick pick", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        serviceSuggestions.forEach { (n, km) ->
                            SuggestionChip(onClick = { name = n; interval = km.toString() }, label = { Text(n) })
                        }
                    }
                }
                Field(name, { name = it }, "Service (e.g. Engine oil & filter)")
                Field(interval, { interval = it }, "Repeat every (km)", num)
                Field(last, { last = it }, "Last done at (km)", num)
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
fun AddReminderDialog(defaultHour: Int, initialTitle: String = "", initialNote: String = "", onDismiss: () -> Unit, onSave: (title: String, note: String, triggerAt: Long) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf(initialNote) }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(LocalTime.of(defaultHour, 0)) }
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
