package com.garage.tracker.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.garage.tracker.ui.theme.InkSoft
import com.garage.tracker.ui.theme.NeutralBg
import com.garage.tracker.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The vehicle's picture. Shows a friendly "add a photo" tile when there is none; tapping an existing
 * photo opens it full-screen. [version] changes whenever the file is replaced, which reloads it.
 */
@Composable
fun VehiclePhoto(
    file: File?,
    version: Int,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    onCreateWithAi: (() -> Unit)? = null,
) {
    val bitmap by produceState<ImageBitmap?>(null, file?.path, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                file?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    var fullScreen by remember { mutableStateOf(false) }
    val frame = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))
    val bmp = bitmap

    if (bmp == null) {
        Box(frame.background(NeutralBg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DirectionsCar, null, tint = Primary, modifier = Modifier.size(40.dp))
                Text("Add a photo of your car", color = InkSoft)
                FilledTonalButton(onClick = onPick) {
                    Icon(Icons.Default.AddAPhoto, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose photo")
                }
                if (onCreateWithAi != null) TextButton(onClick = onCreateWithAi) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Make a cartoon car with AI")
                }
            }
        }
    } else {
        // a cut-out car (transparent background) is shown whole; an ordinary photo fills the frame
        val cutout = bmp.hasAlpha
        Image(
            bmp, "Vehicle photo",
            frame.background(NeutralBg).clickable { fullScreen = true }.then(if (cutout) Modifier.padding(8.dp) else Modifier),
            contentScale = if (cutout) ContentScale.Fit else ContentScale.Crop,
        )
    }

    if (fullScreen && bmp != null) {
        Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { fullScreen = false },
                contentAlignment = Alignment.Center,
            ) {
                Image(bmp, "Vehicle photo", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                FilledTonalButton(
                    onClick = { fullScreen = false; onPick() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                ) { Text("Change photo") }
            }
        }
    }
}
