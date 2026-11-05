package com.garage.tracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.Health
import com.garage.tracker.ui.theme.*

/** White rounded card. Pass [onClick] to make it tappable; its content animates when it grows. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val colors = CardDefaults.cardColors(containerColor = Surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp).animateContentSize(), content = content)
    }
    if (onClick == null) {
        Card(modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation, content = inner)
    } else {
        Card(onClick, modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation, content = inner)
    }
}

data class HealthStyle(val label: String, val bg: Color, val fg: Color)

@Composable
fun Health.style() = when (this) {
    Health.Healthy -> HealthStyle("All services healthy", MintBg, MintFg)
    Health.DueSoon -> HealthStyle("Service due soon", AmberBg, AmberFg)
    Health.Overdue -> HealthStyle("Service overdue", CoralBg, CoralFg)
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** "Label ........ value" line used in expanded cards. */
@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = InkSoft, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(BrandBlue)

private val cad: java.text.NumberFormat =
    java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "CA"))

/** Canadian dollars, e.g. $1,234.56 */
fun money(amount: Double, decimals: Int = 2): String =
    (cad.clone() as java.text.NumberFormat).apply {
        minimumFractionDigits = decimals; maximumFractionDigits = decimals
    }.format(amount)
