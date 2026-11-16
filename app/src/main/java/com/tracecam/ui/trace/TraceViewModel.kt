package com.tracecam.ui.trace

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.tracecam.camera.PaperAnalyzer
import com.tracecam.cv.ImageToViewMapper
import com.tracecam.cv.PaperDetection
import com.tracecam.cv.orderCorners
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.min

enum class AutoFitStatus { Idle, Detecting, NotFound }

/**
 * Everything the trace screen needs to draw the overlay.
 *
 * [overlayMatrix] holds the 9 values of a 3x3 matrix that maps the image, normalised to the
 * unit square (0,0)–(1,1), onto screen pixels. It can express perspective, which Auto-fit
 * needs to warp the image onto paper seen at an angle. It is null until the image and screen
 * sizes are known.
 */
data class TraceUiState(
    val opacity: Float = DEFAULT_OPACITY,
    val overlayMatrix: List<Float>? = null,
    val locked: Boolean = false,
    val autoFitStatus: AutoFitStatus = AutoFitStatus.Idle,
) {
    companion object {
        const val DEFAULT_OPACITY = 0.5f
        const val MIN_OPACITY = 0.1f
        const val MAX_OPACITY = 0.9f
    }
}

class TraceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TraceUiState())
    val uiState: StateFlow<TraceUiState> = _uiState.asStateFlow()

    /** Shared with the camera; lives as long as this ViewModel so it survives rotation. */
    val paperAnalyzer = PaperAnalyzer()

    @Volatile private var viewWidth = 0
    @Volatile private var viewHeight = 0
    private var imageAspectRatio: Float? = null // height / width

    fun setOpacity(opacity: Float) {
        _uiState.update {
            it.copy(opacity = opacity.coerceIn(TraceUiState.MIN_OPACITY, TraceUiState.MAX_OPACITY))
        }
    }

    fun setLocked(locked: Boolean) {
        _uiState.update { it.copy(locked = locked) }
    }

    /** Called when the overlay's size changes, e.g. after screen rotation. */
    fun onViewSize(width: Int, height: Int) {
        if (width == viewWidth && height == viewHeight) return
        val oldWidth = viewWidth
        val oldHeight = viewHeight
        viewWidth = width
        viewHeight = height
        if (oldWidth > 0 && oldHeight > 0) {
            // Keep the overlay in the same place relative to the screen centre.
            updateMatrix { postTranslate((width - oldWidth) / 2f, (height - oldHeight) / 2f) }
        }
        initMatrixIfReady()
    }

    fun onImageLoaded(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        imageAspectRatio = height.toFloat() / width
        initMatrixIfReady()
    }

    /**
     * Applies one step of a drag / pinch / twist gesture, pivoting around [centroid] so the
     * image stays under the user's fingers. Ignored while locked.
     */
    fun onTransform(centroid: Offset, pan: Offset, zoom: Float, rotationDegrees: Float) {
        if (_uiState.value.locked) return
        updateMatrix {
            val before = mappedArea(this)
            postScale(zoom, zoom, centroid.x, centroid.y)
            val area = mappedArea(this)
            val minSide = min(viewWidth, viewHeight) * MIN_SIZE_FRACTION
            val maxSide = max(viewWidth, viewHeight) * MAX_SIZE_FACTOR
            if (area < minSide * minSide && area < before || area > maxSide * maxSide && area > before) {
                postScale(1 / zoom, 1 / zoom, centroid.x, centroid.y) // undo: too small/large
            }
            postRotate(rotationDegrees, centroid.x, centroid.y)
            postTranslate(pan.x, pan.y)
        }
    }

    /** Looks for paper in the camera feed and, if found, warps the overlay onto it and locks. */
    fun autoFit() {
        if (_uiState.value.autoFitStatus == AutoFitStatus.Detecting) return
        if (viewWidth == 0 || viewHeight == 0) return
        _uiState.update { it.copy(autoFitStatus = AutoFitStatus.Detecting) }
        paperAnalyzer.requestDetection { detection -> onPaperDetected(detection) }
    }

    fun dismissAutoFitMessage() {
        _uiState.update {
            if (it.autoFitStatus == AutoFitStatus.NotFound) it.copy(autoFitStatus = AutoFitStatus.Idle) else it
        }
    }

    // Called on the analysis thread; StateFlow.update is thread-safe.
    private fun onPaperDetected(detection: PaperDetection?) {
        val matrix = detection?.let { warpOntoPaper(it) }
        _uiState.update {
            if (matrix == null) {
                it.copy(autoFitStatus = AutoFitStatus.NotFound)
            } else {
                it.copy(overlayMatrix = matrix.toList(), locked = true, autoFitStatus = AutoFitStatus.Idle)
            }
        }
    }

    private fun warpOntoPaper(detection: PaperDetection): Matrix? {
        val mapper = ImageToViewMapper(detection.frame, viewWidth, viewHeight)
        // The detector orders corners in camera-buffer space, which is usually rotated
        // relative to the screen, so re-order them in screen space.
        val screenCorners = orderCorners(detection.corners.map(mapper::map))
        val dst = FloatArray(8)
        screenCorners.forEachIndexed { i, p ->
            dst[i * 2] = p.x.toFloat()
            dst[i * 2 + 1] = p.y.toFloat()
        }
        val matrix = Matrix()
        return if (matrix.setPolyToPoly(UNIT_SQUARE, 0, dst, 0, 4)) matrix else null
    }

    private fun initMatrixIfReady() {
        val aspect = imageAspectRatio ?: return
        if (_uiState.value.overlayMatrix != null || viewWidth == 0 || viewHeight == 0) return
        // Default: fit the screen width, centred vertically.
        val height = viewWidth * aspect
        val top = (viewHeight - height) / 2f
        val matrix = Matrix().apply {
            setRectToRect(RectF(0f, 0f, 1f, 1f), RectF(0f, top, viewWidth.toFloat(), top + height), Matrix.ScaleToFit.FILL)
        }
        _uiState.update { if (it.overlayMatrix == null) it.copy(overlayMatrix = matrix.toList()) else it }
    }

    private inline fun updateMatrix(crossinline block: Matrix.() -> Unit) {
        _uiState.update { state ->
            val values = state.overlayMatrix ?: return@update state
            val matrix = Matrix().apply { setValues(values.toFloatArray()) }
            matrix.block()
            state.copy(overlayMatrix = matrix.toList())
        }
    }

    override fun onCleared() {
        paperAnalyzer.close()
    }

    private companion object {
        const val MIN_SIZE_FRACTION = 0.1f
        const val MAX_SIZE_FACTOR = 8f
        val UNIT_SQUARE = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f) // TL, TR, BR, BL

        fun Matrix.toList(): List<Float> = FloatArray(9).also(::getValues).toList()

        /** Area on screen of the unit square under [matrix] (shoelace formula). */
        fun mappedArea(matrix: Matrix): Float {
            val pts = UNIT_SQUARE.copyOf().also(matrix::mapPoints)
            var sum = 0f
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                sum += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
            }
            return kotlin.math.abs(sum) / 2f
        }
    }
}
