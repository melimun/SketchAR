package com.tracecam.ui.trace

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation

private const val TAG = "CameraPreview"

/**
 * Full-bleed back-camera preview. The camera is bound to the current lifecycle, so it
 * pauses when the app goes to the background and is released when this leaves composition.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
            onError(e)
            return@LaunchedEffect
        }
        try {
            awaitCancellation()
        } finally {
            provider.unbind(preview)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
