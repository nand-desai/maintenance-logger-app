package com.garage.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.garage.tracker.ui.theme.Cream
import com.garage.tracker.ui.theme.InkSoft
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Interactive Filament viewport. Drag to orbit, pinch to zoom; the car also spins gently.
 * Shows the user's own `.glb` ([modelFile]) or a friendly placeholder with a button to add one.
 * [version] changes whenever the file is replaced, which forces a reload.
 */
@Composable
fun Car3DView(
    modelFile: File?,
    version: Int,
    onPickModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val box = modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)).background(Cream)
    val hasFile = remember(modelFile?.path, version) { modelFile?.exists() == true }

    if (!hasFile) {
        Placeholder(box, "Add a 3D model of your car", "Pick a .glb file", onPickModel)
        return
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 0.0f, y = 1.2f, z = 3.5f)
    }
    val childNodes = rememberNodes()

    // Read the file off the main thread into a direct buffer for Filament.
    val buffer by produceState<ByteBuffer?>(null, modelFile!!.path, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = modelFile.readBytes()
                ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }
            }.getOrNull()
        }
    }
    if (buffer == null) {
        Placeholder(box, "Loading 3D model…", null, null)
        return
    }

    val model = remember(buffer) {
        runCatching {
            ModelNode(modelInstance = modelLoader.createModelInstance(buffer!!), scaleToUnits = 1.5f)
        }.getOrNull()
    }
    if (model == null) {
        Placeholder(box, "Couldn't open that model", "Choose another .glb", onPickModel)
        return
    }

    remember(model) { childNodes.clear(); childNodes.add(model) }

    Scene(
        modifier = box,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        cameraManipulator = rememberCameraManipulator(
            orbitHomePosition = cameraNode.worldPosition,
            targetPosition = model.worldPosition,
        ),
        childNodes = childNodes,
        isOpaque = false,
        onFrame = {
            model.rotation = model.rotation.copy(y = model.rotation.y + 0.3f)
        },
    )
}

@Composable
private fun Placeholder(modifier: Modifier, text: String, action: String?, onAction: (() -> Unit)?) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, color = InkSoft)
            if (action != null && onAction != null) FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}
