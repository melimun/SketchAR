package com.tracecam.cv

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Turns an image into a line drawing of its edges, for tracing.
 *
 * Pipeline: downscale to at most [maxDimension] → grayscale → Gaussian blur → Canny with
 * thresholds derived from Otsu's method (so it adapts to each image's contrast) → dilate so
 * lines stay visible when scaled down on screen.
 *
 * Pure OpenCV/JVM code with no Android dependencies. The OpenCV native library must be loaded.
 */
class OutlineExtractor(
    private val maxDimension: Int = 1024,
    private val blurKernelSize: Int = 5,
    private val lineThickness: Int = 2,
) {
    /**
     * @param rgba an 8-bit, 4-channel RGBA image.
     * @return a single-channel 8-bit mask, 255 on edges and 0 elsewhere, possibly smaller
     *   than the input but with the same aspect ratio. The caller must release it.
     */
    fun extract(rgba: Mat): Mat {
        val small = Mat()
        val gray = Mat()
        val blurred = Mat()
        val otsuScratch = Mat()
        val edges = Mat()
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(lineThickness.toDouble(), lineThickness.toDouble()),
        )
        try {
            val scale = maxDimension.toDouble() / max(rgba.cols(), rgba.rows())
            if (scale < 1.0) {
                Imgproc.resize(rgba, small, Size(), scale, scale, Imgproc.INTER_AREA)
            } else {
                rgba.copyTo(small)
            }
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            val k = blurKernelSize.toDouble()
            Imgproc.GaussianBlur(gray, blurred, Size(k, k), 0.0)
            val otsu = Imgproc.threshold(
                blurred, otsuScratch, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU,
            )
            Imgproc.Canny(blurred, edges, otsu * 0.5, otsu)
            if (lineThickness > 1) Imgproc.dilate(edges, edges, kernel)
            return edges
        } catch (e: Throwable) {
            edges.release()
            throw e
        } finally {
            small.release()
            gray.release()
            blurred.release()
            otsuScratch.release()
            kernel.release()
        }
    }
}
