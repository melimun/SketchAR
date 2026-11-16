package com.tracecam.cv

import kotlin.math.max

/**
 * Maps points from an analysis frame's buffer coordinates to the coordinates of a view that
 * shows the same camera stream scaled with "fill center" (like PreviewView.FILL_CENTER).
 *
 * Steps: take the point relative to the frame's crop rect, rotate it upright by
 * [FrameGeometry.rotationDegrees] (clockwise), then scale it uniformly so the upright crop
 * covers the view, centring whatever overflows.
 */
class ImageToViewMapper(
    private val frame: FrameGeometry,
    viewWidth: Int,
    viewHeight: Int,
) {
    private val cropWidth = frame.crop.width.toDouble()
    private val cropHeight = frame.crop.height.toDouble()
    private val sideways = frame.rotationDegrees == 90 || frame.rotationDegrees == 270
    private val uprightWidth = if (sideways) cropHeight else cropWidth
    private val uprightHeight = if (sideways) cropWidth else cropHeight
    private val scale = max(viewWidth / uprightWidth, viewHeight / uprightHeight)
    private val offsetX = (viewWidth - uprightWidth * scale) / 2
    private val offsetY = (viewHeight - uprightHeight * scale) / 2

    fun map(point: Point2D): Point2D {
        val x = point.x - frame.crop.left
        val y = point.y - frame.crop.top
        val (ux, uy) = when (frame.rotationDegrees) {
            0 -> x to y
            90 -> (cropHeight - y) to x
            180 -> (cropWidth - x) to (cropHeight - y)
            else -> y to (cropWidth - x) // 270
        }
        return Point2D(ux * scale + offsetX, uy * scale + offsetY)
    }
}
