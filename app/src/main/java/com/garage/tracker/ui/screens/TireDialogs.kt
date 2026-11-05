package com.garage.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton({ show = true }, modifier.fillMaxWidth()) {
        Icon(Icons.Default.CalendarToday, null)
        Spacer(Modifier.width(8.dp))
        Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }
    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ show = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** Add a tire set, or edit one when [initial] is given. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TireSetDialog(
    initial: TireSet?,
    isFirst: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, season: String, brand: String, size: String, kmBefore: Int, mountNow: Boolean) -> Unit,
) {
    var season by remember { mutableStateOf(initial?.season) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var brand by remember { mutableStateOf(initial?.brand ?: "") }
    var size by remember { mutableStateOf(initial?.size ?: "") }
    var km by remember { mutableStateOf(initial?.kmBefore?.takeIf { it > 0 }?.toString() ?: "") }
    var mountNow by remember { mutableStateOf(isFirst) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add tire set" else "Edit tire set") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Season", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Seasons.all.forEach { s ->
                        val st = seasonStyle(s)
                        FilterChip(
                            selected = season == s, onClick = { season = s },
                            label = { Text(s) }, leadingIcon = { Icon(st.icon, null, tint = st.fg) },
                        )
                    }
                }
                Field(name, { name = it }, "Name (e.g. Winter set)")
                Field(brand, { brand = it }, "Brand / model (optional)")
                Field(size, { size = it }, "Size (e.g. 225/45 R17, optional)")
                Field(km, { km = it }, "Km already on these tires (optional)", num)
                if (initial == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Put these on the car now", Modifier.weight(1f))
                        Switch(mountNow, { mountNow = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = season != null && name.isNotBlank() && (km.isBlank() || km.toIntOrNull() != null),
                onClick = { onSave(name.trim(), season!!, brand.trim(), size.trim(), km.toIntOrNull() ?: 0, mountNow); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MountDialog(set: TireSet, currentOdometer: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var odo by remember { mutableStateOf(currentOdometer.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Put on ${set.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The set on the car now comes off, and Carlo keeps count of the km on each set.", style = MaterialTheme.typography.bodyMedium)
                Field(odo, { odo = it }, "Odometer right now (km)", num)
            }
        },
        confirmButton = {
            TextButton(enabled = odo.toIntOrNull() != null, onClick = { onConfirm(odo.toInt()); onDismiss() }) { Text("Mount") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Log a rotation, balancing, alignment or puncture. Nothing is pre-selected unless the caller knows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TireEventDialog(
    initialType: String?,
    initialWheel: String?,
    defaultOdometer: Int,
    onDismiss: () -> Unit,
    onSave: (type: String, date: String, odometer: Int, wheel: String?, note: String) -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    var wheel by remember { mutableStateOf(initialWheel) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var odo by remember { mutableStateOf(defaultOdometer.toString()) }
    var note by remember { mutableStateOf("") }
    val isPuncture = type == TireEventType.PUNCTURE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log tire event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What happened?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TireEventType.loggable.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t) }) }
                }
                if (isPuncture) {
                    Text("Which wheel?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        wheelNames.forEach { (id, label) -> FilterChip(wheel == id, { wheel = id }, label = { Text(label) }) }
                    }
                }
                DateButton(date, { date = it })
                Field(odo, { odo = it }, "Odometer (km)", num)
                Field(note, { note = it }, "Note (optional)")
            }
        },
        confirmButton = {
            TextButton(
                enabled = type != null && odo.toIntOrNull() != null && (!isPuncture || wheel != null),
                onClick = { onSave(type!!, date.toString(), odo.toInt(), if (isPuncture) wheel else null, note.trim()); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
