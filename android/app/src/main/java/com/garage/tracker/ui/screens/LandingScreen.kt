package com.garage.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garage.tracker.ui.theme.InkSoft
import com.garage.tracker.ui.theme.Primary

/** Shown while the garage is empty: a warm welcome and a clear first step. */
@Composable
fun LandingScreen(onAdd: () -> Unit, onImport: () -> Unit, onRestoreFolder: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(112.dp).background(Brush.linearGradient(listOf(Color(0xFF4F9DFF), Color(0xFF9B5CF6))), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.DirectionsCar, null, tint = Color.White, modifier = Modifier.size(56.dp)) }

        Spacer(Modifier.height(24.dp))
        Text("Welcome to Carlo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Keep every car's services, costs and reminders in one friendly place. Your data stays on your phone.",
            color = InkSoft, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(28.dp))
        Feature(Icons.Default.Build, "Track services and see what's due")
        Feature(Icons.Default.DonutLarge, "Log fuel and costs, see where the money goes")
        Feature(Icons.Default.Notifications, "Get reminders before things are overdue")

        Spacer(Modifier.height(32.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add your first vehicle")
        }
        OutlinedButton(onClick = onRestoreFolder, modifier = Modifier.fillMaxWidth()) { Text("Restore from my backup folder") }
        TextButton(onClick = onImport) { Text("Restore from a backup file") }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
