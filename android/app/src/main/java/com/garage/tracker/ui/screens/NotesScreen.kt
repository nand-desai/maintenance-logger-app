package com.garage.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.CarNote
import com.garage.tracker.data.NoteTags
import com.garage.tracker.ui.theme.*
import java.time.LocalDate

private fun tagIcon(tag: String): ImageVector = when (tag) {
    "Noise" -> Icons.Default.VolumeUp
    "Vibration" -> Icons.Default.Vibration
    "Leak" -> Icons.Default.WaterDrop
    "Smell" -> Icons.Default.Air
    "Warning light" -> Icons.Default.Warning
    "Handling" -> Icons.Default.Speed
    else -> Icons.Default.EditNote
}

/** Observations to bring up with the mechanic: strange noises, vibrations, leaks, warning lights... */
@Composable
fun NotesScreen(
    notes: List<CarNote>,
    onAdd: () -> Unit,
    onEdit: (CarNote) -> Unit,
    onToggle: (CarNote) -> Unit,
    onDelete: (CarNote) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = notes.filter { !it.done }
    val done = notes.filter { it.done }
    var showDone by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EditNote, null, tint = Primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("For your next mechanic visit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (open.isEmpty()) "Nothing to mention right now" else "${open.size} thing${if (open.size == 1) "" else "s"} to mention",
                            color = InkSoft, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (open.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share this list")
                    }
                }
            }
        }
        if (notes.isEmpty()) {
            item {
                SoftCard(onClick = onAdd) {
                    Text("Jot down anything odd", fontWeight = FontWeight.SemiBold)
                    Text(
                        "A noise when braking, a vibration at highway speed, a smell, a warning light. Show the list to your mechanic so nothing gets forgotten.",
                        color = InkSoft, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(open, key = { it.noteId }) { n -> NoteCard(n, onEdit, onToggle, onDelete) }
        if (done.isNotEmpty()) {
            item {
                TextButton(onClick = { showDone = !showDone }) {
                    Icon(if (showDone) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Already mentioned (${done.size})")
                }
            }
            if (showDone) items(done, key = { it.noteId }) { n -> NoteCard(n, onEdit, onToggle, onDelete) }
        }
    }
}

@Composable
private fun NoteCard(n: CarNote, onEdit: (CarNote) -> Unit, onToggle: (CarNote) -> Unit, onDelete: (CarNote) -> Unit) {
    var open by rememberSaveable(n.noteId) { mutableStateOf(false) }
    SoftCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(n.done, { onToggle(n) })
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(tagIcon(n.tag), null, tint = if (n.done) InkSoft else Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(n.tag, color = InkSoft, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("${n.date} · ${"%,d".format(n.odometer)} km", color = InkSoft, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    n.text,
                    color = if (n.done) InkSoft else Ink,
                    textDecoration = if (n.done) TextDecoration.LineThrough else null,
                    maxLines = if (open) Int.MAX_VALUE else 2,
                )
            }
        }
        if (open) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEdit(n) }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                TextButton(onClick = { onDelete(n) }) { Icon(Icons.Default.DeleteOutline, null, tint = CoralFg); Spacer(Modifier.width(6.dp)); Text("Delete", color = CoralFg) }
            }
        }
    }
}

/** Add or edit an observation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteDialog(
    initial: CarNote?,
    defaultOdometer: Int,
    onDismiss: () -> Unit,
    onSave: (tag: String, text: String, date: String, odometer: Int) -> Unit,
) {
    var tag by remember { mutableStateOf(initial?.tag ?: NoteTags.GENERAL) }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var date by remember { mutableStateOf(initial?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var odo by remember { mutableStateOf((initial?.odometer ?: defaultOdometer).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a note" else "Edit note") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoteTags.all.forEach { t -> FilterChip(tag == t, { tag = t }, label = { Text(t) }) }
                }
                OutlinedTextField(
                    text, { text = it }, label = { Text("What did you notice?") },
                    placeholder = { Text("e.g. squeaking when braking, mostly cold mornings") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                DateButton(date, { date = it })
                Field(odo, { odo = it }, "Odometer (km)", num)
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && odo.toIntOrNull() != null,
                onClick = { onSave(tag, text.trim(), date.toString(), odo.toInt()); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
