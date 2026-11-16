package com.tracecam.cv

import kotlin.math.atan2

/**
 * Orders the four corners of a convex quadrilateral as top-left, top-right, bottom-right,
 * bottom-left (clockwise on screen, where y grows downwards).
 *
 * Corners are sorted by angle around their centroid, which keeps the cyclic order correct
 * even for rotated or perspective-distorted shapes. The starting corner is the one closest
 * to the top-left (smallest x + y); ties, such as a square rotated 45°, go to the higher
 * (smaller y) corner.
 */
fun orderCorners(corners: List<Point2D>): List<Point2D> {
    require(corners.size == 4) { "Expected 4 corners, got ${corners.size}" }

    val cx = corners.sumOf { it.x } / 4
    val cy = corners.sumOf { it.y } / 4
    // With y pointing down, increasing atan2 sweeps clockwise on screen.
    val clockwise = corners.sortedBy { atan2(it.y - cy, it.x - cx) }

    val topLeft = clockwise.indices.minWith(
        compareBy<Int>({ clockwise[it].x + clockwise[it].y }, { clockwise[it].y }),
    )
    return List(4) { clockwise[(topLeft + it) % 4] }
}
