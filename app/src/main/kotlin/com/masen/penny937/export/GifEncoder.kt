package com.masen.penny937.export

import java.io.OutputStream

/**
 * A small, dependency-free GIF89a writer with per-frame palette quantization
 * (median cut) and Floyd–Steinberg dithering. Pure Kotlin so it can be tested off-device.
 */
class GifWriter(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    private val loop: Boolean = true
) {
    private val quantizer = FrameQuantizer()
    private var started = false
    private var closed = false

    private fun start() {
        if (started) return
        started = true
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(0x70) // no global color table, 8-bit colour resolution
        out.write(0)
        out.write(0)
        if (loop) {
            out.write(0x21)
            out.write(0xFF)
            out.write(0x0B)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(0x03)
            out.write(0x01)
            writeShort(0) // forever
            out.write(0)
        }
    }

    fun addFrame(argb: IntArray, delayCentis: Int) {
        require(argb.size >= width * height) { "Frame pixel buffer too small" }
        start()
        val quantized = quantizer.quantize(argb, width, height, 256)
        val palette = quantized.palette

        // Graphic control extension.
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04) // disposal = 1 (do not dispose), no transparency
        writeShort(delayCentis.coerceIn(1, 65535))
        out.write(0) // transparent index
        out.write(0)

        // Image descriptor with a local color table.
        out.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out.write(0x87) // local table, 256 entries

        for (i in 0 until 256) {
            val c = palette.getOrElse(i) { 0 }
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }

        out.write(8) // LZW minimum code size
        val blocks = LzwEncoder().compress(quantized.indices)
        out.write(blocks)
    }

    fun close() {
        if (closed) return
        if (!started) start()
        out.write(0x3B)
        out.flush()
        closed = true
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
}

class QuantizedFrame(val indices: ByteArray, val palette: IntArray)

/** Median-cut palette + Floyd–Steinberg error diffusion. */
class FrameQuantizer {
    private class Box(val items: MutableList<Int>, val population: Long)

    private var lut = IntArray(0)
    private lateinit var palette: IntArray

    fun quantize(argb: IntArray, width: Int, height: Int, maxColors: Int): QuantizedFrame {
        val histogram = IntArray(32768)
        val count = width * height
        for (i in 0 until count) histogram[bin(argb[i])]++
        palette = medianCut(histogram, maxColors)
        buildLut()

        val indices = ByteArray(count)
        val errCurrent = FloatArray(width * 3)
        val errNext = FloatArray(width * 3)
        var p = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = argb[p]
                var r = ((c shr 16) and 0xFF) + errCurrent[x * 3]
                var g = ((c shr 8) and 0xFF) + errCurrent[x * 3 + 1]
                var b = (c and 0xFF) + errCurrent[x * 3 + 2]
                r = r.coerceIn(0f, 255f)
                g = g.coerceIn(0f, 255f)
                b = b.coerceIn(0f, 255f)
                val idx = nearest(r.toInt(), g.toInt(), b.toInt())
                indices[p] = idx.toByte()
                val pc = palette.getOrElse(idx) { 0 }
                val er = r - ((pc shr 16) and 0xFF)
                val eg = g - ((pc shr 8) and 0xFF)
                val eb = b - (pc and 0xFF)
                distribute(errCurrent, errNext, width, x, er, eg, eb)
                p++
            }
            System.arraycopy(errNext, 0, errCurrent, 0, errCurrent.size)
            java.util.Arrays.fill(errNext, 0f)
        }
        return QuantizedFrame(indices, palette)
    }

    private fun distribute(
        current: FloatArray,
        next: FloatArray,
        width: Int,
        x: Int,
        er: Float, eg: Float, eb: Float
    ) {
        if (x + 1 < width) {
            current[(x + 1) * 3] += er * 7f / 16f
            current[(x + 1) * 3 + 1] += eg * 7f / 16f
            current[(x + 1) * 3 + 2] += eb * 7f / 16f
        }
        if (x > 0) {
            next[(x - 1) * 3] += er * 3f / 16f
            next[(x - 1) * 3 + 1] += eg * 3f / 16f
            next[(x - 1) * 3 + 2] += eb * 3f / 16f
        }
        next[x * 3] += er * 5f / 16f
        next[x * 3 + 1] += eg * 5f / 16f
        next[x * 3 + 2] += eb * 5f / 16f
        if (x + 1 < width) {
            next[(x + 1) * 3] += er * 1f / 16f
            next[(x + 1) * 3 + 1] += eg * 1f / 16f
            next[(x + 1) * 3 + 2] += eb * 1f / 16f
        }
    }

    private fun buildLut() {
        lut = IntArray(32768)
        for (bin in 0 until 32768) {
            val r = (((bin shr 10) and 31) shl 3) or 4
            val g = (((bin shr 5) and 31) shl 3) or 4
            val b = ((bin and 31) shl 3) or 4
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (i in palette.indices) {
                val c = palette[i]
                val dr = r - ((c shr 16) and 0xFF)
                val dg = g - ((c shr 8) and 0xFF)
                val db = b - (c and 0xFF)
                val d = dr * dr * 3 + dg * dg * 6 + db * db
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            lut[bin] = best
        }
    }

    private fun nearest(r: Int, g: Int, b: Int): Int {
        val bin = (((r shr 3) and 31) shl 10) or (((g shr 3) and 31) shl 5) or ((b shr 3) and 31)
        return lut[bin]
    }

    private fun medianCut(histogram: IntArray, maxColors: Int): IntArray {
        val nonEmpty = ArrayList<Int>()
        for (i in histogram.indices) if (histogram[i] > 0) nonEmpty.add(i)
        if (nonEmpty.isEmpty()) return IntArray(1) { 0xFF000000.toInt() }

        var boxes = listOf(
            Box(nonEmpty.toMutableList(), nonEmpty.sumOf { histogram[it].toLong() })
        )

        while (boxes.size < maxColors) {
            val box = boxes.filter { it.items.size > 1 }.maxByOrNull { it.population } ?: break
            val items = box.items
            var minR = 32; var maxR = 0
            var minG = 32; var maxG = 0
            var minB = 32; var maxB = 0
            for (b in items) {
                val r = (b shr 10) and 31
                val g = (b shr 5) and 31
                val bl = b and 31
                if (r < minR) minR = r
                if (r > maxR) maxR = r
                if (g < minG) minG = g
                if (g > maxG) maxG = g
                if (bl < minB) minB = bl
                if (bl > maxB) maxB = bl
            }
            val channel = when {
                maxR - minR >= maxG - minG && maxR - minR >= maxB - minB -> 0
                maxG - minG >= maxB - minB -> 1
                else -> 2
            }
            val sorted = items.sortedBy { b ->
                when (channel) {
                    0 -> (b shr 10) and 31
                    1 -> (b shr 5) and 31
                    else -> b and 31
                }
            }
            var acc = 0L
            var splitAt = sorted.size / 2
            var found = false
            for (i in sorted.indices) {
                acc += histogram[sorted[i]]
                if (acc * 2 >= box.population && i + 1 < sorted.size) {
                    splitAt = i + 1
                    found = true
                    break
                }
            }
            if (!found) {
                if (sorted.size < 2) break
                splitAt = sorted.size / 2
            }
            val leftItems = sorted.subList(0, splitAt).toMutableList()
            val rightItems = sorted.subList(splitAt, sorted.size).toMutableList()
            val left = Box(leftItems, leftItems.sumOf { histogram[it].toLong() })
            val right = Box(rightItems, rightItems.sumOf { histogram[it].toLong() })
            val mutable = boxes.toMutableList()
            mutable.remove(box)
            mutable.add(left)
            mutable.add(right)
            boxes = mutable
        }

        val result = IntArray(boxes.size)
        boxes.forEachIndexed { index, box ->
            var r = 0L
            var g = 0L
            var b = 0L
            var total = 0L
            for (bin in box.items) {
                val n = histogram[bin].toLong()
                r += ((((bin shr 10) and 31) shl 3) or 4) * n
                g += ((((bin shr 5) and 31) shl 3) or 4) * n
                b += (((bin and 31) shl 3) or 4) * n
                total += n
            }
            result[index] = if (total == 0L) 0 else {
                val rr = (r / total).toInt().coerceIn(0, 255)
                val gg = (g / total).toInt().coerceIn(0, 255)
                val bb = (b / total).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        return result
    }

    private fun bin(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
    }
}

/** LZW compression for GIF, emitted as image data sub-blocks. */
class LzwEncoder {
    fun compress(indices: ByteArray): ByteArray {
        val bitOut = SubBlockWriter()
        val clear = 256
        val eoi = 257
        var codeSize = 9
        var next = 258
        val dict = HashMap<Int, Int>(8192)

        bitOut.writeCode(clear, codeSize)
        if (indices.isEmpty()) {
            bitOut.writeCode(eoi, codeSize)
            return bitOut.finish()
        }
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = dict[key]
            if (found != null) {
                prefix = found
                continue
            }
            bitOut.writeCode(prefix, codeSize)
            if (next < 4096) {
                dict[key] = next
                next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                bitOut.writeCode(clear, codeSize)
                dict.clear()
                next = 258
                codeSize = 9
            }
            prefix = k
        }
        bitOut.writeCode(prefix, codeSize)
        bitOut.writeCode(eoi, codeSize)
        return bitOut.finish()
    }
}

private class SubBlockWriter {
    private val out = java.io.ByteArrayOutputStream(1 shl 16)
    private val block = ByteArray(255)
    private var blockSize = 0
    private var bitBuffer = 0
    private var bitCount = 0

    fun writeCode(code: Int, size: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += size
        while (bitCount >= 8) {
            emitByte(bitBuffer and 0xFF)
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    private fun emitByte(b: Int) {
        block[blockSize++] = b.toByte()
        if (blockSize == 255) {
            out.write(255)
            out.write(block, 0, 255)
            blockSize = 0
        }
    }

    fun finish(): ByteArray {
        if (bitCount > 0) {
            emitByte(bitBuffer and 0xFF)
            bitBuffer = 0
            bitCount = 0
        }
        if (blockSize > 0) {
            out.write(blockSize)
            out.write(block, 0, blockSize)
            blockSize = 0
        }
        out.write(0)
        return out.toByteArray()
    }
}
