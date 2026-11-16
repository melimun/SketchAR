package com.tracecam.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CornerOrderingTest {

    private val tl = Point2D(10.0, 20.0)
    private val tr = Point2D(110.0, 20.0)
    private val br = Point2D(110.0, 220.0)
    private val bl = Point2D(10.0, 220.0)

    @Test
    fun alreadyOrderedRectangleIsUnchanged() {
        assertEquals(listOf(tl, tr, br, bl), orderCorners(listOf(tl, tr, br, bl)))
    }

    @Test
    fun everyPermutationOfARectangleIsOrdered() {
        for (permutation in permutations(listOf(tl, tr, br, bl))) {
            assertEquals("input $permutation", listOf(tl, tr, br, bl), orderCorners(permutation))
        }
    }

    @Test
    fun perspectiveTrapezoidIsOrdered() {
        // Paper seen at an angle: top edge shorter and tilted.
        val a = Point2D(140.0, 90.0)
        val b = Point2D(480.0, 110.0)
        val c = Point2D(590.0, 430.0)
        val d = Point2D(40.0, 400.0)
        for (permutation in permutations(listOf(a, b, c, d))) {
            assertEquals(listOf(a, b, c, d), orderCorners(permutation))
        }
    }

    @Test
    fun slightlyRotatedRectangleIsOrdered() {
        val angle = Math.toRadians(15.0)
        fun rotate(p: Point2D) = Point2D(
            300 + (p.x * cos(angle) - p.y * sin(angle)),
            300 + (p.x * sin(angle) + p.y * cos(angle)),
        )
        val expected = listOf(
            Point2D(-100.0, -150.0), Point2D(100.0, -150.0),
            Point2D(100.0, 150.0), Point2D(-100.0, 150.0),
        ).map(::rotate)
        assertEquals(expected, orderCorners(expected.reversed()))
        assertEquals(expected, orderCorners(listOf(expected[2], expected[0], expected[3], expected[1])))
    }

    @Test
    fun diamondTieBreaksToTheTopCorner() {
        val top = Point2D(5.0, 0.0)
        val right = Point2D(10.0, 5.0)
        val bottom = Point2D(5.0, 10.0)
        val left = Point2D(0.0, 5.0)
        assertEquals(listOf(top, right, bottom, left), orderCorners(listOf(left, bottom, right, top)))
    }

    @Test
    fun resultIsClockwiseOnScreen() {
        val ordered = orderCorners(listOf(br, tl, bl, tr))
        // Shoelace sum is positive for clockwise order when y points down.
        val sum = ordered.indices.sumOf { i ->
            val p = ordered[i]
            val q = ordered[(i + 1) % 4]
            p.x * q.y - q.x * p.y
        }
        assertTrue(sum > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongNumberOfCorners() {
        orderCorners(listOf(tl, tr, br))
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) {
            listOf(items)
        } else {
            items.flatMap { head -> permutations(items - head).map { listOf(head) + it } }
        }
}
