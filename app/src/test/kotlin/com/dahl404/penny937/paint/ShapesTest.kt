package com.dahl404.penny937.paint

import com.dahl404.penny937.model.ShapeKind
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ShapesTest {

    private fun build(
        kind: ShapeKind,
        x0: Float, y0: Float, x1: Float, y1: Float
    ): Stroke = Shapes.build(kind, x0, y0, x1, y1, 0xFF000000.toInt(), 8f)!!

    private fun Stroke.bounds(): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until pointCount) {
            minX = minOf(minX, pointX(i))
            maxX = maxOf(maxX, pointX(i))
            minY = minOf(minY, pointY(i))
            maxY = maxOf(maxY, pointY(i))
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    @Test
    fun lineHasTwoPoints() {
        val line = build(ShapeKind.LINE, 10f, 20f, 110f, 90f)
        assertEquals(2, line.pointCount)
        assertEquals(Tool.PEN, line.tool)
        assertEquals(10f, line.pointX(0), 0.001f)
        assertEquals(90f, line.pointY(1), 0.001f)
    }

    @Test
    fun rectangleStaysInItsDragBounds() {
        val rect = build(ShapeKind.RECTANGLE, 120f, 40f, 20f, 200f)
        val b = rect.bounds()
        assertEquals(20f, b[0], 0.001f)
        assertEquals(40f, b[1], 0.001f)
        assertEquals(120f, b[2], 0.001f)
        assertEquals(200f, b[3], 0.001f)
        assertTrue(rect.pointCount >= 9)
    }

    @Test
    fun squareUsesTheLargerAxis() {
        val square = build(ShapeKind.SQUARE, 10f, 20f, 110f, 60f)
        val b = square.bounds()
        assertEquals(10f, b[0], 0.001f)
        assertEquals(20f, b[1], 0.001f)
        assertEquals(110f, b[2], 0.001f)
        assertEquals(120f, b[3], 0.001f)
    }

    @Test
    fun circleKeepsAspect() {
        val circle = build(ShapeKind.CIRCLE, 0f, 0f, 100f, 40f)
        val b = circle.bounds()
        assertEquals(0f, b[0], 0.01f)
        assertEquals(0f, b[1], 0.01f)
        assertEquals(100f, b[2], 0.01f)
        assertEquals(100f, b[3], 0.01f)
    }

    @Test
    fun ellipseUsesTheDragRectangle() {
        val ellipse = build(ShapeKind.ELLIPSE, 0f, 0f, 200f, 100f)
        val b = ellipse.bounds()
        assertEquals(0f, b[0], 0.01f)
        assertEquals(0f, b[1], 0.01f)
        assertEquals(200f, b[2], 0.01f)
        assertEquals(100f, b[3], 0.01f)
    }

    @Test
    fun allShapePointsCarryFullWidth() {
        for (kind in ShapeKind.entries) {
            val stroke = Shapes.build(kind, 0f, 0f, 240f, 180f, 0xFF112233.toInt(), 10f)
            assertNotNull(kind.name, stroke)
            for (i in 0 until stroke!!.pointCount) {
                assertTrue(kind.name, abs(stroke.pointPressure(i) - 1f) < 0.0001f)
            }
        }
    }

    @Test
    fun tinyDragsProduceNothing() {
        assertNull(Shapes.build(ShapeKind.LINE, 0f, 0f, 1f, 0f, 0, 8f))
        assertNull(Shapes.build(ShapeKind.RECTANGLE, 0f, 0f, 100f, 1f, 0, 8f))
        assertNull(Shapes.build(ShapeKind.ELLIPSE, 0f, 0f, 1f, 1f, 0, 8f))
        assertNotNull(Shapes.build(ShapeKind.SQUARE, 0f, 0f, 100f, 1f, 0, 8f))
    }
}
