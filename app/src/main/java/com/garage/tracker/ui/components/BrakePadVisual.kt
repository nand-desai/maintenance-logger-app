package com.garage.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.BRAKE_NEW_MM

/**
 * A tiny side view of a brake pad: the metal backing plate sits on top of the friction material,
 * and the material gets thinner as the pad wears. The dashed outline is a brand-new pad for comparison.
 * The disc (rotor) is the bar underneath; a thin pad leaves the plate close to it.
 */
@Composable
fun BrakePadVisual(mm: Double?, color: Color, outline: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 64.dp, height = 46.dp)) {
        val w = size.width
        val h = size.height
        val rotorH = 5.dp.toPx()
        val plateH = 7.dp.toPx()
        val maxPad = 26.dp.toPx()
        val left = w * 0.12f
        val padW = w * 0.76f
        val corner = CornerRadius(3.dp.toPx())

        // disc
        drawRoundRect(Color(0xFF9AA0B4), Offset(0f, h - rotorH), Size(w, rotorH), CornerRadius(2.dp.toPx()))

        val padBottom = h - rotorH - 2.dp.toPx()
        // a new pad, dashed, for scale
        drawRoundRect(
            outline, Offset(left, padBottom - maxPad - plateH), Size(padW, maxPad + plateH), corner,
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
        )
        if (mm != null) {
            val padH = ((mm / BRAKE_NEW_MM).coerceIn(0.04, 1.0) * maxPad).toFloat()
            // friction material
            drawRoundRect(color, Offset(left, padBottom - padH), Size(padW, padH), corner)
            // backing plate
            drawRoundRect(Color(0xFF7A8094), Offset(left, padBottom - padH - plateH), Size(padW, plateH), corner)
        }
    }
}
