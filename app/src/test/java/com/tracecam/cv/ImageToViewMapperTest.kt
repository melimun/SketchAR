package com.tracecam.cv

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageToViewMapperTest {

    private fun assertPoint(expected: Point2D, actual: Point2D) {
        assertEquals("x", expected.x, actual.x, 1e-6)
        assertEquals("y", expected.y, actual.y, 1e-6)
    }

    private fun frame(w: Int, h: Int, rotation: Int, crop: PixelRect = PixelRect(0, 0, w, h)) =
        FrameGeometry(w, h, crop, rotation)

    @Test
    fun noRotationScalesUniformly() {
        val mapper = ImageToViewMapper(frame(640, 480, 0), 1280, 960)
        assertPoint(Point2D(20.0, 40.0), mapper.map(Point2D(10.0, 20.0)))
    }

    @Test
    fun rotation90MapsBufferCornersToRotatedViewCorners() {
        val mapper = ImageToViewMapper(frame(640, 480, 90), 480, 640)
        assertPoint(Point2D(480.0, 0.0), mapper.map(Point2D(0.0, 0.0)))   // buffer TL → view TR
        assertPoint(Point2D(480.0, 640.0), mapper.map(Point2D(640.0, 0.0))) // buffer TR → view BR
        assertPoint(Point2D(0.0, 640.0), mapper.map(Point2D(640.0, 480.0))) // buffer BR → view BL
    }

    @Test
    fun rotation180FlipsBothAxes() {
        val mapper = ImageToViewMapper(frame(640, 480, 180), 640, 480)
        assertPoint(Point2D(630.0, 470.0), mapper.map(Point2D(10.0, 10.0)))
    }

    @Test
    fun rotation270MapsBufferTopLeftToViewBottomLeft() {
        val mapper = ImageToViewMapper(frame(640, 480, 270), 480, 640)
        assertPoint(Point2D(0.0, 640.0), mapper.map(Point2D(0.0, 0.0)))
        assertPoint(Point2D(480.0, 0.0), mapper.map(Point2D(640.0, 480.0)))
    }

    @Test
    fun cropRectIsSubtractedBeforeMapping() {
        val crop = PixelRect(100, 0, 540, 480)
        val mapper = ImageToViewMapper(frame(640, 480, 0, crop), 440, 480)
        assertPoint(Point2D(0.0, 0.0), mapper.map(Point2D(100.0, 0.0)))
        assertPoint(Point2D(440.0, 480.0), mapper.map(Point2D(540.0, 480.0)))
    }

    @Test
    fun fillCenterCropsOverflowEvenly() {
        // 4:3 frame shown in a square view: scaled to 266.7 x 200, sides overflow by 33.3.
        val mapper = ImageToViewMapper(frame(400, 300, 0), 200, 200)
        assertPoint(Point2D(100.0, 100.0), mapper.map(Point2D(200.0, 150.0)))
        assertPoint(Point2D(-100.0 / 3, 0.0), mapper.map(Point2D(0.0, 0.0)))
    }
}
