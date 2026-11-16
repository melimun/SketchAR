package com.tracecam.ui.trace

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.tracecam.R
import com.tracecam.imaging.createOutlineBitmap
import com.tracecam.ui.hasCameraPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val NOT_FOUND_MESSAGE_MS = 3_000L

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
                    analyzer = viewModel.paperAnalyzer,
                    onError = { cameraError = true },
                )
            }
            if (imageUri != null) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                TraceOverlay(
                    imageUri = imageUri,
                    state = uiState,
                    onViewSize = viewModel::onViewSize,
                    onImageLoaded = viewModel::onImageLoaded,
                    onTransform = viewModel::onTransform,
                    modifier = Modifier.fillMaxSize(),
                )
                TraceControls(
                    state = uiState,
                    onAutoFit = viewModel::autoFit,
                    onLockedChange = viewModel::setLocked,
                    onToggleFlipped = viewModel::toggleFlipped,
                    onOutlineModeChange = viewModel::setOutlineMode,
                    onOpacityChange = viewModel::setOpacity,
                    onDismissMessage = viewModel::dismissAutoFitMessage,
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
 * Draws the picked image through [TraceUiState.overlayMatrix], which may include perspective
 * after Auto-fit. Unless locked, gestures anywhere on screen move (drag), resize (pinch) and
 * rotate (two-finger twist) it.
 */
@Composable
private fun TraceOverlay(
    imageUri: Uri,
    state: TraceUiState,
    onViewSize: (width: Int, height: Int) -> Unit,
    onImageLoaded: (width: Int, height: Int) -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Decode no larger than the screen's longest side so big photos are downsampled. Using
    // the longest side keeps the size stable across rotation.
    val maxDimension = LocalWindowInfo.current.containerSize
        .let { max(it.width, it.height) }
        .coerceAtLeast(1)
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    val currentOnImageLoaded by rememberUpdatedState(onImageLoaded)
    LaunchedEffect(imageUri, maxDimension) {
        val request = ImageRequest.Builder(context)
            .data(imageUri)
            .size(maxDimension)
            .allowHardware(false) // drawn with a perspective matrix on the canvas
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val loaded = (result as? SuccessResult)?.image?.toBitmap()
        bitmap = loaded
        loaded?.let { currentOnImageLoaded(it.width, it.height) }
    }

    // Edge-only version of the image, built in the background the first time it's needed.
    var outline by remember(bitmap) { mutableStateOf<Bitmap?>(null) }
    if (state.outlineMode) {
        LaunchedEffect(bitmap) {
            val source = bitmap ?: return@LaunchedEffect
            if (outline == null) {
                outline = withContext(Dispatchers.Default) { createOutlineBitmap(source) }
            }
        }
    }

    val currentOnTransform by rememberUpdatedState(onTransform)
    val gestures = if (state.locked) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                currentOnTransform(centroid, pan, zoom, rotation)
            }
        }
    }

    val drawMatrix = remember { Matrix() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Canvas(
        modifier = modifier
            .onSizeChanged { onViewSize(it.width, it.height) }
            .then(gestures),
    ) {
        val original = bitmap ?: return@Canvas
        // Fall back to the full image while the outline is still being generated.
        val image = if (state.outlineMode) outline ?: original else original
        val values = state.overlayMatrix ?: return@Canvas
        drawMatrix.setValues(values.toFloatArray())
        // Pre-transforms apply in reverse order: bitmap pixels → unit square → mirror → placement.
        if (state.flipped) drawMatrix.preScale(-1f, 1f, 0.5f, 0.5f)
        drawMatrix.preScale(1f / image.width, 1f / image.height)
        paint.alpha = (state.opacity * 255).roundToInt()
        drawIntoCanvas { it.nativeCanvas.drawBitmap(image, drawMatrix, paint) }
    }
}

@Composable
private fun TraceControls(
    state: TraceUiState,
    onAutoFit: () -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onToggleFlipped: () -> Unit,
    onOutlineModeChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.autoFitStatus == AutoFitStatus.NotFound) {
        LaunchedEffect(Unit) {
            delay(NOT_FOUND_MESSAGE_MS)
            onDismissMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.autoFitStatus == AutoFitStatus.NotFound) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.inverseSurface,
            ) {
                Text(
                    text = "Couldn't find the paper. Try placing the paper on a darker surface.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val detecting = state.autoFitStatus == AutoFitStatus.Detecting
                    FilledTonalButton(onClick = onAutoFit, enabled = !detecting) {
                        if (detecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("Detecting…", modifier = Modifier.padding(start = 8.dp))
                        } else {
                            Text("Auto-fit")
                        }
                    }
                    FilterChip(
                        selected = state.locked,
                        onClick = { onLockedChange(!state.locked) },
                        label = { Text(if (state.locked) "Locked" else "Lock") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(
                                    if (state.locked) R.drawable.ic_lock else R.drawable.ic_lock_open,
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                    FilterChip(
                        selected = state.flipped,
                        onClick = onToggleFlipped,
                        label = { Text("Flip") },
                    )
                    FilterChip(
                        selected = state.outlineMode,
                        onClick = { onOutlineModeChange(!state.outlineMode) },
                        label = { Text("Outline") },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Opacity", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = state.opacity,
                        onValueChange = onOpacityChange,
                        valueRange = TraceUiState.MIN_OPACITY..TraceUiState.MAX_OPACITY,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(state.opacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.widthIn(min = 40.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
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
