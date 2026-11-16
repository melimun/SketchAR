package com.tracecam.cv

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Finds a sheet of paper in a grayscale image: blur → Canny edges → contours → polygon
 * approximation → the largest convex quadrilateral covering at least [minAreaFraction] of
 * the searched region.
 *
 * Pure OpenCV/JVM code with no Android dependencies. The OpenCV native library must be
 * loaded before calling [detect].
 */
class PaperDetector(
    private val minAreaFraction: Double = 0.1,
    private val blurKernelSize: Int = 5,
    private val cannyLowThreshold: Double = 50.0,
    private val cannyHighThreshold: Double = 150.0,
    private val approxEpsilonFraction: Double = 0.02,
) {
    /**
     * Detects paper in an 8-bit grayscale buffer (e.g. the Y plane of a YUV frame).
     * Only the [roi] region is searched. Returns corners ordered TL, TR, BR, BL in full
     * buffer coordinates, or null if no suitable shape is found.
     */
    fun detect(
        gray: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        roi: PixelRect = PixelRect(0, 0, width, height),
    ): List<Point2D>? {
        val full = wrapGray(gray, width, height, rowStride)
        val region = full.submat(roi.top, roi.bottom, roi.left, roi.right)
        try {
            return detect(region)?.map { Point2D(it.x + roi.left, it.y + roi.top) }
        } finally {
            region.release()
            full.release()
        }
    }

    /** Detects paper in a single-channel 8-bit [gray] image; coordinates are relative to it. */
    fun detect(gray: Mat): List<Point2D>? {
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val contours = ArrayList<MatOfPoint>()
        try {
            val k = blurKernelSize.toDouble()
            Imgproc.GaussianBlur(gray, blurred, Size(k, k), 0.0)
            Imgproc.Canny(blurred, edges, cannyLowThreshold, cannyHighThreshold)
            // Close small gaps in the paper outline so it forms a single contour.
            Imgproc.dilate(edges, edges, kernel)
            Imgproc.findContours(
                edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val minArea = minAreaFraction * gray.rows() * gray.cols()
            var best: List<Point2D>? = null
            var bestArea = minArea
            for (contour in contours) {
                val quad = approximateQuad(contour) ?: continue
                val area = abs(Geometry.contourArea(quad))
                if (area > bestArea && isConvex(quad)) {
                    best = quad.toArray().map { Point2D(it.x, it.y) }
                    bestArea = area
                }
                quad.release()
            }
            return best?.let(::orderCorners)
        } finally {
            contours.forEach { it.release() }
            blurred.release()
            edges.release()
            hierarchy.release()
            kernel.release()
        }
    }

    /** Simplifies [contour] to a polygon; returns it only if it has exactly four vertices. */
    private fun approximateQuad(contour: MatOfPoint): MatOfPoint2f? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approx = MatOfPoint2f()
        try {
            val perimeter = Geometry.arcLength(curve, true)
            Geometry.approxPolyDP(curve, approx, approxEpsilonFraction * perimeter, true)
        } finally {
            curve.release()
        }
        if (approx.total() == 4L) return approx
        approx.release()
        return null
    }

    private fun isConvex(quad: MatOfPoint2f): Boolean {
        val points = MatOfPoint(*quad.toArray())
        try {
            return Geometry.isContourConvex(points)
        } finally {
            points.release()
        }
    }

    private fun wrapGray(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int): Mat {
        // Zero-copy when the buffer covers every full row; otherwise copy row by row
        // (some devices omit the padding after the last row).
        if (buffer.isDirect && buffer.capacity() >= rowStride * height) {
            return Mat(height, width, CvType.CV_8UC1, buffer, rowStride.toLong())
        }
        val mat = Mat(height, width, CvType.CV_8UC1)
        val row = ByteArray(width)
        val source = buffer.duplicate()
        for (y in 0 until height) {
            source.position(y * rowStride)
            source.get(row, 0, width)
            mat.put(y, 0, row)
        }
        return mat
    }
}
