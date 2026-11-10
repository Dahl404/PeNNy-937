package com.dahl404.penny937.export

import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class GifEncoderTest {

    @Test
    fun encodesAReadableAnimation() {
        val w = 320
        val h = 200
        val dir = File("build/test-output").apply { mkdirs() }
        val file = File(dir, "animation.gif")
        FileOutputStream(file).use { fos ->
            val gif = GifWriter(fos, w, h)
            for (f in 0 until 8) {
                val px = IntArray(w * h)
                val cx = 50 + f * 28
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val dx = x - cx
                        val dy = y - 100
                        px[y * w + x] = when {
                            dx * dx + dy * dy < 45 * 45 -> 0xFF1D1D1F.toInt()
                            dx * dx + dy * dy < 55 * 55 -> 0xFF0A84FF.toInt()
                            else -> {
                                val r = x * 255 / w
                                val g = y * 255 / h
                                val b = f * 30
                                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            }
                        }
                    }
                }
                gif.addFrame(px, 8)
            }
            gif.close()
        }
        check(file.exists() && file.length() > 1000) { "GIF too small: ${file.length()}" }
        val header = file.readBytes().copyOfRange(0, 6).toString(Charsets.US_ASCII)
        check(header == "GIF89a") { "Bad header: $header" }
        println("Wrote ${file.absolutePath} (${file.length()} bytes)")
    }

    @Test
    fun quantizesFlatArtExactly() {
        val w = 64
        val h = 64
        val px = IntArray(w * h)
        for (i in px.indices) {
            px[i] = when (i % 3) {
                0 -> 0xFFFF0000.toInt()
                1 -> 0xFF00FF00.toInt()
                else -> 0xFF0000FF.toInt()
            }
        }
        val q = FrameQuantizer().quantize(px, w, h, 256)
        check(q.palette.size >= 3)
        val file = File("build/test-output").apply { mkdirs() }.resolve("flat.gif")
        FileOutputStream(file).use { fos ->
            val gif = GifWriter(fos, w, h)
            gif.addFrame(px, 10)
            gif.close()
        }
        check(file.length() > 100)
    }
}
