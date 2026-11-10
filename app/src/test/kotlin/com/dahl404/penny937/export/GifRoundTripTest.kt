package com.dahl404.penny937.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Node
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

/**
 * Encodes a GIF, then lets the JDK's independent GIF reader decode it. This proves
 * the header, palette, LZW stream and frame timing are all readable by software
 * other than this app.
 */
class GifRoundTripTest {

    private fun delayTime(metadata: javax.imageio.metadata.IIOMetadata): Int {
        val root = metadata.getAsTree("javax_imageio_gif_image_1.0")
        val node = find(root, "GraphicControlExtension") ?: return -1
        return (node as IIOMetadataNode).getAttribute("delayTime").toInt()
    }

    private fun find(node: Node, name: String): Node? {
        if (node.nodeName == name) return node
        var child = node.firstChild
        while (child != null) {
            find(child, name)?.let { return it }
            child = child.nextSibling
        }
        return null
    }

    private fun solid(argb: Int, w: Int, h: Int): IntArray = IntArray(w * h) { argb }

    @Test
    fun encodedAnimationDecodesWithTheSameFramesAndTiming() {
        val w = 96
        val h = 64
        val dir = File("build/test-output").apply { mkdirs() }
        val file = File(dir, "round-trip.gif")
        val colors = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        val delays = listOf(5, 10, 2)
        FileOutputStream(file).use { fos ->
            val gif = GifWriter(fos, w, h)
            for (i in colors.indices) gif.addFrame(solid(colors[i], w, h), delays[i])
            gif.close()
        }

        assertTrue("gif was written", file.length() > 100)
        val bytes = file.readBytes()
        assertEquals("GIF89a", bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII))

        ImageIO.createImageInputStream(file).use { input ->
            assertNotNull(input)
            val readers = ImageIO.getImageReaders(input)
            assertTrue("a GIF reader is available", readers.hasNext())
            val reader = readers.next()
            reader.setInput(input)
            assertEquals(w, reader.getWidth(0))
            assertEquals(h, reader.getHeight(0))
            assertEquals(colors.size, reader.getNumImages(true))

            for (i in colors.indices) {
                val image = reader.read(i)
                assertEquals(w, image.width)
                assertEquals(h, image.height)
                val argb = image.getRGB(w / 2, h / 2)
                val expected = colors[i]
                // Median-cut keeps a solid colour exact; compare channels with a
                // little slack so the test does not depend on palette ordering.
                assertEquals(
                    "frame $i red",
                    ((expected shr 16) and 0xFF).toDouble(),
                    ((argb shr 16) and 0xFF).toDouble(),
                    8.0
                )
                assertEquals(
                    "frame $i green",
                    ((expected shr 8) and 0xFF).toDouble(),
                    ((argb shr 8) and 0xFF).toDouble(),
                    8.0
                )
                assertEquals(
                    "frame $i blue",
                    (expected and 0xFF).toDouble(),
                    (argb and 0xFF).toDouble(),
                    8.0
                )
                assertEquals(
                    "frame $i delay (1/100s)",
                    delays[i].toDouble(),
                    delayTime(reader.getImageMetadata(i)).toDouble(),
                    0.0
                )
            }
            reader.dispose()
        }
    }

    @Test
    fun oneFrameStaysASingleImage() {
        val file = File("build/test-output").apply { mkdirs() }.resolve("single-round-trip.gif")
        FileOutputStream(file).use { fos ->
            val gif = GifWriter(fos, 32, 32)
            gif.addFrame(solid(0xFF112233.toInt(), 32, 32), 1)
            gif.close()
        }
        ImageIO.createImageInputStream(file).use { input ->
            val reader = ImageIO.getImageReaders(input).next()
            reader.setInput(input)
            assertEquals(1, reader.getNumImages(true))
            val argb = reader.read(0).getRGB(16, 16)
            // Palettes are quantised to 5 bits per channel, so allow one step.
            assertEquals(0x11.toDouble(), ((argb shr 16) and 0xFF).toDouble(), 8.0)
            assertEquals(0x22.toDouble(), ((argb shr 8) and 0xFF).toDouble(), 8.0)
            assertEquals(0x33.toDouble(), (argb and 0xFF).toDouble(), 8.0)
            reader.dispose()
        }
    }
}
