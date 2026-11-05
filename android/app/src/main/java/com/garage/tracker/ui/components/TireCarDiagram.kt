package com.garage.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garage.tracker.ui.theme.CoralFg

private val WheelInk = Color(0xFF1F2430)

private data class WheelSpot(val id: String, val x: Int, val y: Int)

private val spots = listOf(
    WheelSpot("FL", 44, 28), WheelSpot("FR", 190, 28),
    WheelSpot("RL", 44, 158), WheelSpot("RR", 190, 158),
)

/**
 * Top-down car with four tappable wheels. Wheels take the colour of the season of the mounted set,
 * a red badge shows how many punctures a wheel has had, and the latest tread depth (mm) is printed on each wheel. Front of the car is at the top.
 */
@Composable
fun TireCarDiagram(
    bodyColor: Color,
    wheelColor: Color,
    centerIcon: ImageVector?,
    punctures: Map<String, Int>,
    tread: Map<String, Double>,
    onWheelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(width = 260.dp, height = 240.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val bodyW = 110.dp.toPx()
            val bodyH = 210.dp.toPx()
            val left = (size.width - bodyW) / 2
            val top = 15.dp.toPx()
            drawRoundRect(bodyColor, Offset(left, top), Size(bodyW, bodyH), CornerRadius(42.dp.toPx()))
            val glass = Color.White.copy(alpha = 0.75f)
            // windscreen
            drawRoundRect(glass, Offset(left + 14.dp.toPx(), top + 48.dp.toPx()), Size(bodyW - 28.dp.toPx(), 36.dp.toPx()), CornerRadius(12.dp.toPx()))
            // roof
            drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(left + 14.dp.toPx(), top + 92.dp.toPx()), Size(bodyW - 28.dp.toPx(), 48.dp.toPx()), CornerRadius(10.dp.toPx()))
            // rear window
            drawRoundRect(glass, Offset(left + 16.dp.toPx(), top + bodyH - 70.dp.toPx()), Size(bodyW - 32.dp.toPx(), 26.dp.toPx()), CornerRadius(10.dp.toPx()))
        }
        if (centerIcon != null) {
            Icon(centerIcon, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(34.dp))
        }
        spots.forEach { spot ->
            // a 48 dp wide touch target around the 26 dp wheel, so it is easy to hit with a thumb
            Box(
                Modifier.offset((spot.x - 11).dp, (spot.y - 3).dp).size(width = 48.dp, height = 60.dp)
                    .clickable { onWheelClick(spot.id) },
                contentAlignment = Alignment.Center,
            ) {
            Box(Modifier.size(width = 26.dp, height = 54.dp)) {
                Box(
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(wheelColor)
                        .border(2.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(spot.id, fontSize = 10.sp, color = WheelInk.copy(alpha = 0.75f), fontWeight = FontWeight.Bold)
                        tread[spot.id]?.let { Text("%.1f".format(it), fontSize = 10.sp, color = WheelInk, fontWeight = FontWeight.Bold) }
                    }
                }
                val n = punctures[spot.id] ?: 0
                if (n > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(20.dp).background(CoralFg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
            }
        }
    }
}
