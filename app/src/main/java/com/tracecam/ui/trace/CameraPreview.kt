package com.tracecam.ui.trace

import android.util.Log
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tracecam.camera.PaperAnalyzer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.util.Size as AndroidSize

private const val TAG = "CameraPreview"

/**
 * Full-bleed back-camera preview, plus an optional [analyzer] fed ~640x480 frames.
 * Both use cases share the preview's viewport, so analysis frames carry a crop rect that
 * matches exactly what is visible on screen. The camera is bound to the current lifecycle
 * and released when this leaves composition.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    analyzer: PaperAnalyzer? = null,
    onError: (Throwable) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(lifecycleOwner, analyzer) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        // The viewport depends on the view's size and display, so wait for layout.
        previewView.awaitLayout()

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = analyzer?.let {
            ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                AndroidSize(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .apply { previewView.display?.let { setTargetRotation(it.rotation) } }
                .build()
                .apply { setAnalyzer(it.executor, it) }
        }
        val useCases: List<UseCase> = listOfNotNull(preview, analysis)

        try {
            provider.unbindAll()
            val viewPort = previewView.viewPort
            if (viewPort != null) {
                val group = UseCaseGroup.Builder().setViewPort(viewPort)
                    .apply { useCases.forEach(::addUseCase) }
                    .build()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
            } else {
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray(),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
            onError(e)
            return@LaunchedEffect
        }
        try {
            awaitCancellation()
        } finally {
            analysis?.clearAnalyzer()
            provider.unbind(*useCases.toTypedArray())
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private suspend fun View.awaitLayout() = suspendCancellableCoroutine { continuation ->
    doOnLayout { if (continuation.isActive) continuation.resume(Unit) }
}
