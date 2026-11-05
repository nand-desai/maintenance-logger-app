package com.garage.tracker.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.garage.tracker.data.Attachment
import com.garage.tracker.ui.theme.InkSoft
import com.garage.tracker.ui.theme.NeutralBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the app needs to show and open receipts. Provided once from MainActivity. */
class AttachmentActions(val fileFor: (Attachment) -> File, val open: (Attachment) -> Unit)

val LocalAttachments = staticCompositionLocalOf<List<Attachment>> { emptyList() }
val LocalAttachmentActions = staticCompositionLocalOf { AttachmentActions({ File("") }, {}) }

private fun prettySize(n: Long) = if (n > 1_000_000) "%.1f MB".format(n / 1_000_000.0) else "${maxOf(1L, n / 1000)} KB"

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "attachment"

/** A small thumbnail for pictures; a document icon for everything else. */
@Composable
private fun Thumb(source: Any, isImage: Boolean) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, source) {
        value = if (!isImage) null else withContext(Dispatchers.IO) {
            runCatching {
                fun open() = when (source) {
                    is File -> source.inputStream()
                    is Uri -> context.contentResolver.openInputStream(source)!!
                    else -> error("unsupported")
                }
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                open().use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (o.outWidth / s > 192 || o.outHeight / s > 192) s *= 2
                open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
            }.getOrNull()
        }
    }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(NeutralBg), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Description, null, tint = InkSoft)
    }
}

/** Little "paperclip 2" marker for list rows. */
@Composable
fun AttachmentCount(ownerId: String) {
    val n = LocalAttachments.current.count { it.ownerId == ownerId }
    if (n > 0) Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AttachFile, "Has attachments", tint = InkSoft, modifier = Modifier.size(14.dp))
        Text("$n", color = InkSoft, style = MaterialTheme.typography.labelSmall)
    }
}

/** The receipts on a record, shown in its expanded card. Tap one to open it. */
@Composable
fun AttachmentList(ownerId: String) {
    val items = LocalAttachments.current.filter { it.ownerId == ownerId }
    val actions = LocalAttachmentActions.current
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Receipts and files", style = MaterialTheme.typography.labelLarge, color = InkSoft)
        items.forEach { a ->
            Row(Modifier.fillMaxWidth().clickable { actions.open(a) }, verticalAlignment = Alignment.CenterVertically) {
                Thumb(actions.fileFor(a), a.mime.startsWith("image/"))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.fileName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("${prettySize(a.size)} · tap to open", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** What is being attached while a dialog is open. Nothing is saved until the dialog's Save. */
class AttachmentDraft(val ownerId: String?) {
    val pending: SnapshotStateList<Uri> = mutableStateListOf()
    val removed: SnapshotStateList<Attachment> = mutableStateListOf()
}

@Composable
fun rememberAttachmentDraft(ownerId: String?) = remember(ownerId) { AttachmentDraft(ownerId) }

/** "Receipts and files" section for a dialog: take a photo, or pick a photo / PDF. */
@Composable
fun AttachmentsField(draft: AttachmentDraft) {
    val context = LocalContext.current
    val actions = LocalAttachmentActions.current
    val existing = LocalAttachments.current.filter { a -> draft.ownerId != null && a.ownerId == draft.ownerId && a !in draft.removed }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> draft.pending.addAll(uris) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { draft.pending.add(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Receipts and files (optional)", style = MaterialTheme.typography.labelLarge)
        existing.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(actions.fileFor(a), a.mime.startsWith("image/"))
                Spacer(Modifier.width(12.dp))
                Text(a.fileName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { draft.removed.add(a) }) { Icon(Icons.Default.DeleteOutline, "Remove file", tint = InkSoft) }
            }
        }
        draft.pending.toList().forEach { uri ->
            val isImage = context.contentResolver.getType(uri)?.startsWith("image/") == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(uri, isImage)
                Spacer(Modifier.width(12.dp))
                Text(displayName(context, uri) + "  · new", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { draft.pending.remove(uri) }) { Icon(Icons.Default.DeleteOutline, "Remove file", tint = InkSoft) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File.createTempFile("receipt", ".jpg", dir)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraUri = uri
                    camera.launch(uri)
                },
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("Photo") }
            OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("File")
            }
        }
    }
}
