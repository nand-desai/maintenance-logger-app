package com.garage.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * Interactive Filament viewport. Drag to orbit, pinch to zoom; the car also spins gently.
 * Loads [modelAssetName] from app/src/main/assets and falls back to a friendly placeholder if the
 * asset is missing or fails to load.
 */
@Composable
fun Car3DView(modelAssetName: String = "vw_golf_mk7.glb", modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val box = modifier.fillMaxWidth().height(260.dp).clip(shape).background(Cream)

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 0.0f, y = 1.2f, z = 3.5f)
    }
    val childNodes = rememberNodes()

    val model = remember(modelAssetName) {
        runCatching {
            ModelNode(
                modelInstance = modelLoader.createModelInstance(assetFileLocation = modelAssetName),
                scaleToUnits = 1.5f,
            )
        }.getOrNull()
    }

    if (model == null) {
        Box(box, contentAlignment = Alignment.Center) {
            Text("Add $modelAssetName to app/src/main/assets to see your car in 3D", color = InkSoft)
        }
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
