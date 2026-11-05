package com.garage.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.ExpenseLog
import com.garage.tracker.ui.theme.InkSoft

@Composable
fun WashScreen(
    washes: List<ExpenseLog>,
    onLog: () -> Unit,
    onEdit: (ExpenseLog) -> Unit,
    onDelete: (ExpenseLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(Color(0xFFDCEEFF), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LocalCarWash, null, tint = Color(0xFF2B6CB0), modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(washSummary(washes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (washes.isNotEmpty()) {
                            val total = washes.sumOf { it.cost }
                            Text("${washes.size} washes · ${money(total)} total · ${money(total / washes.size)} each", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onLog, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Log a wash") }
            }
        }
        items(washes, key = { it.logId }) { w ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.date, fontWeight = FontWeight.SemiBold)
                        Text(money(w.cost), color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onEdit(w) }) { Icon(Icons.Default.Edit, "Edit wash", tint = InkSoft) }
                    IconButton(onClick = { onDelete(w) }) { Icon(Icons.Default.DeleteOutline, "Delete wash", tint = InkSoft) }
                }
            }
        }
    }
}
