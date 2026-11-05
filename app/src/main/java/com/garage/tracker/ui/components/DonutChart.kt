package com.garage.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Slice(val label: String, val value: Double, val color: Color)

@Composable
fun DonutChart(slices: List<Slice>, size: Dp = 160.dp, thickness: Dp = 26.dp, trackColor: Color = Color(0xFFEEF0FA)) {
    val total = slices.sumOf { it.value }
    Canvas(Modifier.size(size)) {
        val stroke = thickness.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (total <= 0) return@Canvas
        var start = -90f
        val gap = if (slices.count { it.value > 0 } > 1) 3f else 0f
        slices.filter { it.value > 0 }.forEach { s ->
            val sweep = (s.value / total * 360).toFloat()
            drawArc(
                s.color, start, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}
