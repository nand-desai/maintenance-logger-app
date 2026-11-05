package com.garage.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.Reminder
import com.garage.tracker.data.Vehicle
import com.garage.tracker.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
fun RemindersScreen(
    reminders: List<Reminder>,
    vehicles: List<Vehicle>,
    onDelete: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    if (reminders.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.NotificationsActive, null, tint = Primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("No reminders yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Schedule one for an oil change, insurance renewal, MOT… anything.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val (upcoming, past) = reminders.partition { it.triggerAt > now }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(upcoming + past, key = { it.reminderId }) { r ->
            val isPast = r.triggerAt <= now
            val car = vehicles.firstOrNull { it.vehicleId == r.vehicleId }?.let { "${it.make} ${it.model} · " }.orEmpty()
            var open by rememberSaveable(r.reminderId) { mutableStateOf(false) }
            SoftCard(onClick = { open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontWeight = FontWeight.SemiBold, color = if (isPast) InkSoft else Ink)
                        if (r.note.isNotBlank()) Text(r.note, color = InkSoft, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            car + Instant.ofEpochMilli(r.triggerAt).atZone(ZoneId.systemDefault()).format(fmt),
                            if (isPast) CoralBg.copy(alpha = 0f) else MintBg, if (isPast) InkSoft else MintFg,
                        )
                    }
                    IconButton(onClick = { onDelete(r) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = InkSoft) }
                }
            }
        }
    }
}
