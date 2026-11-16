package com.tracecam.ui.trace

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.cos
import kotlin.math.sin

/**
 * Overlay settings for the trace screen. [offsetX]/[offsetY] are the image centre's
 * displacement from the screen centre in pixels; [rotation] is in degrees.
 */
data class TraceUiState(
    val opacity: Float = DEFAULT_OPACITY,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    companion object {
        const val DEFAULT_OPACITY = 0.5f
        const val MIN_OPACITY = 0.1f
        const val MAX_OPACITY = 0.9f
        const val MIN_SCALE = 0.2f
        const val MAX_SCALE = 8f
    }
}

class TraceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TraceUiState())
    val uiState: StateFlow<TraceUiState> = _uiState.asStateFlow()

    fun setOpacity(opacity: Float) {
        _uiState.update {
            it.copy(opacity = opacity.coerceIn(TraceUiState.MIN_OPACITY, TraceUiState.MAX_OPACITY))
        }
    }

    /**
     * Applies one step of a transform gesture. Zoom and rotation pivot around the gesture
     * [centroid] (screen coordinates), so the image stays under the user's fingers.
     */
    fun onTransform(
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        rotationDelta: Float,
        containerCenter: Offset,
    ) {
        _uiState.update { state ->
            val newScale = (state.scale * zoom)
                .coerceIn(TraceUiState.MIN_SCALE, TraceUiState.MAX_SCALE)
            val effectiveZoom = newScale / state.scale

            // Current image centre in screen coordinates, relative to the pivot.
            val imageCenter = containerCenter + Offset(state.offsetX, state.offsetY)
            val fromPivot = (imageCenter - centroid) * effectiveZoom
            val radians = Math.toRadians(rotationDelta.toDouble())
            val rotated = Offset(
                x = (fromPivot.x * cos(radians) - fromPivot.y * sin(radians)).toFloat(),
                y = (fromPivot.x * sin(radians) + fromPivot.y * cos(radians)).toFloat(),
            )
            val newCenter = centroid + rotated + pan - containerCenter

            state.copy(
                scale = newScale,
                rotation = (state.rotation + rotationDelta) % 360f,
                offsetX = newCenter.x,
                offsetY = newCenter.y,
            )
        }
    }
}
