package com.tracecam.cv

/** A point in pixel coordinates (x right, y down). */
data class Point2D(val x: Double, val y: Double)

/** An integer pixel rectangle; [right] and [bottom] are exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(right >= left && bottom >= top) { "Invalid rect: $this" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Describes an analysis frame as delivered by the camera: the raw buffer size, the region
 * of it that matches what the preview shows ([crop]), and how many degrees clockwise the
 * buffer must be rotated to appear upright on screen.
 */
data class FrameGeometry(
    val bufferWidth: Int,
    val bufferHeight: Int,
    val crop: PixelRect,
    val rotationDegrees: Int,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotationDegrees must be 0, 90, 180 or 270, was $rotationDegrees"
        }
    }
}

/** Paper corners (ordered TL, TR, BR, BL) in buffer coordinates of [frame]. */
data class PaperDetection(val corners: List<Point2D>, val frame: FrameGeometry)
