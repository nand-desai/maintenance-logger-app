package com.garage.tracker.ui.screens

import android.net.Uri
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

private fun parseDate(s: String?): LocalDate =
    s?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()

/** Swap to another set (e.g. summer to winter) with the date, odometer and what it cost. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwapDialog(
    candidates: List<TireSet>,
    preselected: TireSet?,
    currentOdometer: Int,
    onDismiss: () -> Unit,
    onConfirm: (target: TireSet, date: String, odometer: Int, cost: Double, note: String) -> Unit,
) {
    var target by remember { mutableStateOf(preselected ?: candidates.singleOrNull()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var odo by remember { mutableStateOf(currentOdometer.toString()) }
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap tires") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Put on", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { c ->
                        val st = seasonStyle(c.season)
                        FilterChip(target?.setId == c.setId, { target = c }, label = { Text(c.name) }, leadingIcon = { Icon(st.icon, null, tint = st.fg) })
                    }
                }
                Text("The set on the car now comes off, and a swap counts as a tire rotation.", style = MaterialTheme.typography.bodySmall)
                DateButton(date, { date = it })
                Field(odo, { odo = it }, "Odometer (km)", num)
                Field(cost, { cost = it }, "Cost of the swap (CAD, optional)", dec)
                Field(note, { note = it }, "Note (optional)")
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null && odo.toIntOrNull() != null && (cost.isBlank() || cost.toNumber() != null),
                onClick = { onConfirm(target!!, date.toString(), odo.toInt(), cost.toNumber() ?: 0.0, note.trim()); onDismiss() },
            ) { Text("Swap") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Log (or edit) a rotation, balancing, alignment, puncture or tread-depth measurement. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TireEventDialog(
    initial: TireEvent?,
    initialType: String?,
    initialWheel: String?,
    defaultOdometer: Int,
    onDismiss: () -> Unit,
    onSave: (type: String, date: String, odometer: Int, wheel: String?, note: String, cost: Double, fl: Double?, fr: Double?, rl: Double?, rr: Double?, files: List<Uri>, removed: List<Attachment>) -> Unit,
) {
    val draft = rememberAttachmentDraft(initial?.eventId)
    var type by remember { mutableStateOf(initial?.type ?: initialType) }
    var wheel by remember { mutableStateOf(initial?.wheel ?: initialWheel) }
    var date by remember { mutableStateOf(parseDate(initial?.date)) }
    var odo by remember { mutableStateOf((initial?.odometer ?: defaultOdometer).toString()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var cost by remember { mutableStateOf(initial?.cost?.takeIf { it > 0 }?.toString() ?: "") }
    val tread = remember {
        mutableStateMapOf(
            "FL" to (initial?.treadFl?.toString() ?: initialWheel?.let { "" } ?: ""),
            "FR" to (initial?.treadFr?.toString() ?: ""),
            "RL" to (initial?.treadRl?.toString() ?: ""),
            "RR" to (initial?.treadRr?.toString() ?: ""),
        )
    }
    val isSwap = type == TireEventType.SWAP
    val isPuncture = type == TireEventType.PUNCTURE
    val isTread = type == TireEventType.TREAD
    val readings = wheelNames.keys.map { tread[it]?.toNumber() }
    val treadOk = tread.values.all { it.isBlank() || it.toNumber() != null } && readings.any { it != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log tire event" else "Edit tire event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSwap) {
                    Text("Tire swap", style = MaterialTheme.typography.titleSmall)
                } else {
                    Text("What happened?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TireEventType.loggable.forEach { t ->
                            FilterChip(type == t, { type = t }, label = { Text(if (t == TireEventType.TREAD) "Tread depth" else t) })
                        }
                    }
                }
                if (isPuncture) {
                    Text("Which wheel?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        wheelNames.forEach { (id, label) -> FilterChip(wheel == id, { wheel = id }, label = { Text(label) }) }
                    }
                }
                DateButton(date, { date = it })
                Field(odo, { odo = it }, "Odometer (km)", num)
                if (isTread) {
                    Text("Tread depth in mm (fill in what you measured)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Field(tread["FL"] ?: "", { tread["FL"] = it }, "Front left", dec) }
                        Box(Modifier.weight(1f)) { Field(tread["FR"] ?: "", { tread["FR"] = it }, "Front right", dec) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Field(tread["RL"] ?: "", { tread["RL"] = it }, "Rear left", dec) }
                        Box(Modifier.weight(1f)) { Field(tread["RR"] ?: "", { tread["RR"] = it }, "Rear right", dec) }
                    }
                } else {
                    Field(cost, { cost = it }, "Cost (CAD, optional)", dec)
                }
                Field(note, { note = it }, "Note (optional)")
                if (!isTread) AttachmentsField(draft)
            }
        },
        confirmButton = {
            TextButton(
                enabled = type != null && odo.toIntOrNull() != null &&
                    (!isPuncture || wheel != null) && (!isTread || treadOk) &&
                    (isTread || cost.isBlank() || cost.toNumber() != null),
                onClick = {
                    onSave(
                        type!!, date.toString(), odo.toInt(), if (isPuncture) wheel else null, note.trim(),
                        if (isTread) 0.0 else cost.toNumber() ?: 0.0,
                        if (isTread) tread["FL"]?.toNumber() else null, if (isTread) tread["FR"]?.toNumber() else null,
                        if (isTread) tread["RL"]?.toNumber() else null, if (isTread) tread["RR"]?.toNumber() else null,
                        draft.pending.toList(), draft.removed.toList(),
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Log (or edit) a brake pad measurement or replacement. Thickness is per axle, in mm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrakeDialog(
    initial: BrakeEntry?,
    initialType: String?,
    defaultOdometer: Int,
    onDismiss: () -> Unit,
    onSave: (type: String, date: String, odometer: Int, frontMm: Double?, rearMm: Double?, cost: Double, note: String, files: List<Uri>, removed: List<Attachment>) -> Unit,
) {
    val draft = rememberAttachmentDraft(initial?.entryId)
    var type by remember { mutableStateOf(initial?.type ?: initialType) }
    var date by remember { mutableStateOf(parseDate(initial?.date)) }
    var odo by remember { mutableStateOf((initial?.odometer ?: defaultOdometer).toString()) }
    var front by remember { mutableStateOf(initial?.frontMm?.toString() ?: "") }
    var rear by remember { mutableStateOf(initial?.rearMm?.toString() ?: "") }
    var cost by remember { mutableStateOf(initial?.cost?.takeIf { it > 0 }?.toString() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val f = front.toNumber(); val r = rear.toNumber()
    val mmOk = (front.isBlank() || f != null) && (rear.isBlank() || r != null) && (f != null || r != null) &&
        (f ?: 0.0) in 0.0..30.0 && (r ?: 0.0) in 0.0..30.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log brake pads" else "Edit brake entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(BrakeType.MEASURED to "Measured", BrakeType.REPLACED to "Replaced (new pads)").forEach { (t, label) ->
                        FilterChip(type == t, { type = t }, label = { Text(label) })
                    }
                }
                DateButton(date, { date = it })
                Field(odo, { odo = it }, "Odometer (km)", num)
                Text("Pad thickness in mm (fill in what you measured)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Field(front, { front = it }, "Front", dec) }
                    Box(Modifier.weight(1f)) { Field(rear, { rear = it }, "Rear", dec) }
                }
                Field(cost, { cost = it }, "Cost (CAD, optional)", dec)
                Field(note, { note = it }, "Note (optional)")
                AttachmentsField(draft)
            }
        },
        confirmButton = {
            TextButton(
                enabled = type != null && odo.toIntOrNull() != null && mmOk && (cost.isBlank() || cost.toNumber() != null),
                onClick = { onSave(type!!, date.toString(), odo.toInt(), f, r, cost.toNumber() ?: 0.0, note.trim(), draft.pending.toList(), draft.removed.toList()); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
