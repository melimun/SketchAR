package com.tracecam.imaging

import android.graphics.Bitmap
import com.tracecam.cv.OutlineExtractor
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

/** Colour of outline strokes (RGBA). Near-black reads well over paper. */
private val LINE_COLOR = Scalar(20.0, 20.0, 20.0, 255.0)

/**
 * Builds a transparent bitmap containing only the edges of [source], drawn as dark lines.
 * CPU-heavy: call off the main thread. Returns null if OpenCV isn't available.
 */
fun createOutlineBitmap(
    source: Bitmap,
    extractor: OutlineExtractor = OutlineExtractor(),
): Bitmap? {
    if (!OpenCv.isLoaded) return null
    val argb = if (source.config == Bitmap.Config.ARGB_8888) {
        source
    } else {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    }
    val rgba = Mat()
    var edges: Mat? = null
    val output = Mat()
    try {
        Utils.bitmapToMat(argb, rgba)
        edges = extractor.extract(rgba)
        output.create(edges.rows(), edges.cols(), CvType.CV_8UC4)
        output.setTo(Scalar(0.0, 0.0, 0.0, 0.0))
        output.setTo(LINE_COLOR, edges)
        val bitmap = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(output, bitmap)
        return bitmap
    } finally {
        rgba.release()
        edges?.release()
        output.release()
        if (argb !== source) argb.recycle()
    }
}
