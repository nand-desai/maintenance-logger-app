package com.garage.tracker.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garage.tracker.data.*
import com.garage.tracker.ui.theme.InkSoft
import com.garage.tracker.ui.theme.NeutralBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Gives the user a ready-made prompt to paste into ChatGPT or Gemini to generate a cartoon picture of their car. */
@Composable
fun AiImageDialog(vehicle: Vehicle, onDismiss: () -> Unit, onChoosePhoto: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val prompt = remember(vehicle) { carImagePrompt(vehicle) }
    val (bgName, _) = backgroundFor(vehicle.colorHex)
    var copied by remember { mutableStateOf(false) }

    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make a cartoon picture of your car") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "1. Copy the prompt below.\n2. Paste it into ChatGPT or Gemini and let it draw your car.\n3. Save the picture, then tap “I have my picture”. Carlo removes the $bgName background for you, right on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NeutralBg).padding(12.dp),
                ) { Text(prompt, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = { clipboard.setText(AnnotatedString(prompt)); copied = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "Copied" else "Copy prompt")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { open("https://chatgpt.com/") }, modifier = Modifier.weight(1f)) { Text("Open ChatGPT") }
                    OutlinedButton(onClick = { open("https://gemini.google.com/app") }, modifier = Modifier.weight(1f)) { Text("Open Gemini") }
                }
                Text(
                    "Tip: attach a real photo of your car in the chat for a closer likeness. The colour comes from the one you picked for this vehicle.",
                    style = MaterialTheme.typography.bodySmall, color = InkSoft,
                )
            }
        },
        confirmButton = { TextButton(onClick = onChoosePhoto) { Text("I have my picture") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Shown after a picture is picked. Lets the user cut out a flat background (on the phone) and
 * preview the result before saving.
 */
@Composable
fun PhotoPrepareDialog(bitmap: Bitmap, onUse: (removeBackground: Boolean, tolerance: Float) -> Unit, onCancel: () -> Unit) {
    val flat = remember(bitmap) { BackgroundRemover.looksFlat(bitmap) }
    var remove by remember(bitmap) { mutableStateOf(flat) }
    var tolerance by remember(bitmap) { mutableFloatStateOf(0.18f) }

    // a small copy keeps the live preview quick
    val small = remember(bitmap) {
        val scale = 500f / maxOf(bitmap.width, bitmap.height)
        if (scale >= 1f) bitmap else Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }
    val preview by produceState<ImageBitmap>(small.asImageBitmap(), small, remove, tolerance) {
        if (!remove) { value = small.asImageBitmap(); return@produceState }
        delay(150) // wait while the slider is still moving
        value = withContext(Dispatchers.Default) { BackgroundRemover.remove(small, tolerance).asImageBitmap() }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Use this picture") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(NeutralBg),
                    contentAlignment = Alignment.Center,
                ) { Image(preview, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Remove background", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (flat) "Looks like a plain background, so this should work well."
                            else "Works best on a plain, single-colour background.",
                            style = MaterialTheme.typography.bodySmall, color = InkSoft,
                        )
                    }
                    Switch(remove, { remove = it })
                }
                if (remove) {
                    Text("Strength", style = MaterialTheme.typography.labelMedium)
                    Slider(tolerance, { tolerance = it }, valueRange = 0.05f..0.45f)
                    Text("Raise it if bits of background are left; lower it if parts of the car disappear.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onUse(remove, tolerance) }) { Text("Use picture") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
