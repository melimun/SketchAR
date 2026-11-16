package com.tracecam.ui.trace

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import com.tracecam.R
import com.tracecam.ui.hasCameraPermission
import kotlin.math.roundToInt

@Composable
fun TraceScreen(
    imageUri: Uri?,
    onBack: () -> Unit,
    viewModel: TraceViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    // Re-check on resume, e.g. after the user grants permission in system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasCameraPermission()
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        // After a second denial Android stops showing the dialog; send the user to settings.
        permanentlyDenied = !granted &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }

    KeepScreenOn()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            var cameraError by remember { mutableStateOf(false) }
            if (cameraError) {
                MessagePanel(message = "The camera couldn't be started on this device.")
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onError = { cameraError = true },
                )
            }
            if (imageUri != null) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                TraceOverlay(
                    imageUri = imageUri,
                    state = uiState,
                    onTransform = viewModel::onTransform,
                    modifier = Modifier.fillMaxSize(),
                )
                OpacitySlider(
                    opacity = uiState.opacity,
                    onOpacityChange = viewModel::setOpacity,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        } else {
            CameraPermissionRationale(
                permanentlyDenied = permanentlyDenied,
                onRetry = { requestPermission.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )
        }

        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
        }
    }
}

/**
 * The picked image, initially centred and scaled to the screen width. Gestures anywhere on
 * screen move (drag), resize (pinch) and rotate (two-finger twist) it.
 */
@Composable
private fun TraceOverlay(
    imageUri: Uri,
    state: TraceUiState,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float, center: Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTransform by rememberUpdatedState(onTransform)
    BoxWithConstraints(
        modifier = modifier.pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                val center = Offset(size.width / 2f, size.height / 2f)
                currentOnTransform(centroid, pan, zoom, rotation, center)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        // Decode at screen width so large photos are downsampled instead of loaded at full size.
        val request = remember(imageUri, widthPx) {
            ImageRequest.Builder(context)
                .data(imageUri)
                .size(Size(Dimension(widthPx), Dimension.Undefined))
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = "Image being traced",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = state.offsetX
                    translationY = state.offsetY
                    scaleX = state.scale
                    scaleY = state.scale
                    rotationZ = state.rotation
                    alpha = state.opacity
                },
        )
    }
}

@Composable
private fun OpacitySlider(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Opacity", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = opacity,
                onValueChange = onOpacityChange,
                valueRange = TraceUiState.MIN_OPACITY..TraceUiState.MAX_OPACITY,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(opacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(min = 40.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun CameraPermissionRationale(
    permanentlyDenied: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    MessagePanel(
        message = if (permanentlyDenied) {
            "SketchAR needs the camera to overlay your image on your paper. " +
                "Camera access is turned off. You can turn it on in Settings."
        } else {
            "SketchAR needs the camera to overlay your image on your paper so you can trace it."
        },
        actionLabel = if (permanentlyDenied) "Open Settings" else "Try Again",
        onAction = if (permanentlyDenied) onOpenSettings else onRetry,
    )
}

@Composable
private fun MessagePanel(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
