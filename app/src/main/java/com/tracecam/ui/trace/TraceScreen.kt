package com.tracecam.ui.trace

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import com.tracecam.R
import com.tracecam.ui.hasCameraPermission

private const val OVERLAY_ALPHA = 0.5f

@Composable
fun TraceScreen(imageUri: Uri?, onBack: () -> Unit) {
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
                TraceOverlay(imageUri = imageUri, modifier = Modifier.fillMaxSize())
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

/** The picked image, semi-transparent, scaled to the screen width and centered vertically. */
@Composable
private fun TraceOverlay(imageUri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
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
            alpha = OVERLAY_ALPHA,
            modifier = Modifier.fillMaxWidth(),
        )
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
