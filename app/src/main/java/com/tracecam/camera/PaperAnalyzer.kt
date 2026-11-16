package com.tracecam.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.tracecam.cv.FrameGeometry
import com.tracecam.cv.PaperDetection
import com.tracecam.cv.PaperDetector
import com.tracecam.cv.PixelRect
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PaperAnalyzer"

/**
 * CameraX analyzer that runs [PaperDetector] on demand. Frames are dropped immediately
 * unless a detection has been requested, so it costs almost nothing while idle.
 * Runs on its own background [executor]; call [close] when no longer needed.
 */
class PaperAnalyzer(
    private val detector: PaperDetector = PaperDetector(),
) : ImageAnalysis.Analyzer {

    val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "paper-analyzer")
    }

    private class Request(val maxFrames: Int, val onResult: (PaperDetection?) -> Unit) {
        var framesTried = 0
    }

    private val pending = AtomicReference<Request?>(null)

    private val openCvLoaded: Boolean by lazy {
        OpenCVLoader.initLocal().also { if (!it) Log.e(TAG, "Failed to load OpenCV") }
    }

    /**
     * Tries up to [maxFrames] frames to find the paper, then calls [onResult] once on the
     * analysis thread with the detection, or null if none was found. Replaces any pending request.
     */
    fun requestDetection(maxFrames: Int = 15, onResult: (PaperDetection?) -> Unit) {
        pending.set(Request(maxFrames, onResult))
    }

    fun cancel() {
        pending.set(null)
    }

    fun close() {
        cancel()
        executor.shutdown()
    }

    override fun analyze(image: ImageProxy) {
        image.use { proxy ->
            val request = pending.get() ?: return
            val detection = try {
                detect(proxy)
            } catch (e: Exception) {
                Log.e(TAG, "Paper detection failed", e)
                null
            }
            request.framesTried++
            if (detection != null || request.framesTried >= request.maxFrames || !openCvLoaded) {
                if (pending.compareAndSet(request, null)) request.onResult(detection)
            }
        }
    }

    private fun detect(image: ImageProxy): PaperDetection? {
        if (!openCvLoaded) return null
        val crop = image.cropRect
        val frame = FrameGeometry(
            bufferWidth = image.width,
            bufferHeight = image.height,
            crop = PixelRect(crop.left, crop.top, crop.right, crop.bottom),
            rotationDegrees = image.imageInfo.rotationDegrees,
        )
        // Plane 0 of YUV_420_888 is the luminance channel, i.e. already grayscale.
        val luma = image.planes[0]
        val corners = detector.detect(
            gray = luma.buffer,
            width = image.width,
            height = image.height,
            rowStride = luma.rowStride,
            roi = frame.crop,
        ) ?: return null
        return PaperDetection(corners, frame)
    }
}
