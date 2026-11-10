package com.dahl404.penny937.export

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
    private val lzw = LzwEncoder()
    private var started = false
    private var closed = false

    /** Palette + compressed image data for one frame, ready to be written. */
    class EncodedFrame(val palette: IntArray, val blocks: ByteArray)

    private fun start() {
        if (started) return
        started = true
        out.write(HEADER)
        writeShort(width)
        writeShort(height)
        out.write(0x70) // no global color table, 8-bit colour resolution
        out.write(0)
        out.write(0)
        if (loop) {
            out.write(0x21)
            out.write(0xFF)
            out.write(0x0B)
            out.write(NETSCAPE)
            out.write(0x03)
            out.write(0x01)
            writeShort(0) // forever
            out.write(0)
        }
    }

    fun addFrame(argb: IntArray, delayCentis: Int) {
        require(argb.size >= width * height) { "Frame pixel buffer too small" }
        val quantized = quantizer.quantize(argb, width, height, 256)
        addEncodedFrame(
            EncodedFrame(quantized.palette, lzw.compress(quantized.indices)),
            delayCentis
        )
    }

    /**
     * Writes a frame that was already quantised and LZW-compressed, usually on a
     * worker thread: every frame of an export is independent, so the CPU work can be
     * spread while the byte stream stays strictly ordered.
     */
    fun addEncodedFrame(frame: EncodedFrame, delayCentis: Int) {
        start()
        val palette = frame.palette

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
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }

        out.write(8) // LZW minimum code size
        out.write(frame.blocks)
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

    private companion object {
        val HEADER = "GIF89a".toByteArray(Charsets.US_ASCII)
        val NETSCAPE = "NETSCAPE2.0".toByteArray(Charsets.US_ASCII)
    }
}

class QuantizedFrame(val indices: ByteArray, val palette: IntArray)

/**
 * Median-cut palette + Floyd–Steinberg error diffusion.
 *
 * Everything here works on primitive arrays: the palettes are built by counting-sort
 * over 5-bit channels (no boxing, no comparison sort per split) and the 32x32x32
 * nearest-colour table is filled from a k-d tree instead of testing all 256 palette
 * entries for every one of the 32768 bins.
 */
class FrameQuantizer {

    private val histogram = IntArray(32768)
    private val items = IntArray(32768)
    private val scratch = IntArray(32768)
    private val lut = IntArray(32768)
    private var palette = IntArray(256)

    // k-d tree over the palette, stored as parallel arrays.
    private val nodeIndex = IntArray(256)
    private val nodeAxis = IntArray(256)
    private val nodeLeft = IntArray(256)
    private val nodeRight = IntArray(256)
    private val nodeR = IntArray(256)
    private val nodeG = IntArray(256)
    private val nodeB = IntArray(256)
    private var nodeCount = 0
    private var bestIndex = 0
    private var bestDistance = Int.MAX_VALUE
    private var queryR = 0
    private var queryG = 0
    private var queryB = 0

    @Synchronized
    fun quantize(argb: IntArray, width: Int, height: Int, maxColors: Int): QuantizedFrame {
        val count = width * height
        java.util.Arrays.fill(histogram, 0)
        for (i in 0 until count) histogram[bin(argb[i])]++
        palette = medianCut(maxColors)
        buildLut()

        // Palette channels split out once: the diffusion loop runs per pixel.
        val palSize = palette.size
        val palR = IntArray(palSize)
        val palG = IntArray(palSize)
        val palB = IntArray(palSize)
        for (i in 0 until palSize) {
            val c = palette[i]
            palR[i] = (c shr 16) and 0xFF
            palG[i] = (c shr 8) and 0xFF
            palB[i] = c and 0xFF
        }

        val indices = ByteArray(count)
        val errCurrent = FloatArray(width * 3)
        val errNext = FloatArray(width * 3)
        var p = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = argb[p]
                val rf = ((c shr 16) and 0xFF) + errCurrent[x * 3]
                val gf = ((c shr 8) and 0xFF) + errCurrent[x * 3 + 1]
                val bf = (c and 0xFF) + errCurrent[x * 3 + 2]
                val r = if (rf < 0f) 0 else if (rf > 255f) 255 else rf.toInt()
                val g = if (gf < 0f) 0 else if (gf > 255f) 255 else gf.toInt()
                val b = if (bf < 0f) 0 else if (bf > 255f) 255 else bf.toInt()
                val idx = lut[((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)]
                indices[p] = idx.toByte()
                val er = rf - palR[idx]
                val eg = gf - palG[idx]
                val eb = bf - palB[idx]
                // Floyd–Steinberg: 7/16 right, 3/16 down-left, 5/16 down, 1/16 down-right.
                val c3 = x * 3
                errNext[c3] += er * 0.3125f
                errNext[c3 + 1] += eg * 0.3125f
                errNext[c3 + 2] += eb * 0.3125f
                if (x + 1 < width) {
                    val r3 = c3 + 3
                    errCurrent[r3] += er * 0.4375f
                    errCurrent[r3 + 1] += eg * 0.4375f
                    errCurrent[r3 + 2] += eb * 0.4375f
                    errNext[r3] += er * 0.0625f
                    errNext[r3 + 1] += eg * 0.0625f
                    errNext[r3 + 2] += eb * 0.0625f
                }
                if (x > 0) {
                    val l3 = c3 - 3
                    errNext[l3] += er * 0.1875f
                    errNext[l3 + 1] += eg * 0.1875f
                    errNext[l3 + 2] += eb * 0.1875f
                }
                p++
            }
            System.arraycopy(errNext, 0, errCurrent, 0, errCurrent.size)
            java.util.Arrays.fill(errNext, 0f)
        }
        return QuantizedFrame(indices, palette.copyOf(palette.size))
    }

    // ------------------------------------------------------------------ palette

    private fun medianCut(maxColors: Int): IntArray {
        var n = 0
        for (b in histogram.indices) if (histogram[b] > 0) items[n++] = b
        if (n == 0) return intArrayOf(0xFF000000.toInt())

        // Boxes are ranges [start, end) over the shared `items` array.
        val start = IntArray(maxColors)
        val end = IntArray(maxColors)
        val population = LongArray(maxColors)
        start[0] = 0
        end[0] = n
        var total = 0L
        for (i in 0 until n) total += histogram[items[i]]
        population[0] = total
        var boxes = 1

        while (boxes < maxColors) {
            var target = -1
            var bestPop = 0L
            for (i in 0 until boxes) {
                if (end[i] - start[i] > 1 && population[i] > bestPop) {
                    bestPop = population[i]
                    target = i
                }
            }
            if (target < 0) break

            val from = start[target]
            val to = end[target]
            var minR = 32; var maxR = 0
            var minG = 32; var maxG = 0
            var minB = 32; var maxB = 0
            for (i in from until to) {
                val b = items[i]
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
            val spanR = maxR - minR
            val spanG = maxG - minG
            val spanB = maxB - minB
            val channel = when {
                spanR >= spanG && spanR >= spanB -> 0
                spanG >= spanB -> 1
                else -> 2
            }
            val shift = when (channel) {
                0 -> 10
                1 -> 5
                else -> 0
            }

            // Counting sort the range by the 5-bit channel value.
            val buckets = IntArray(32)
            for (i in from until to) buckets[(items[i] shr shift) and 31]++
            var offset = from
            for (c in 0 until 32) {
                val c0 = buckets[c]
                buckets[c] = offset
                offset += c0
            }
            for (i in from until to) {
                val b = items[i]
                val c = (b shr shift) and 31
                scratch[buckets[c]++] = b
            }
            System.arraycopy(scratch, from, items, from, to - from)

            // Split where half of the population falls.
            var acc = 0L
            var splitAt = from + 1
            for (i in from until to) {
                acc += histogram[items[i]]
                if (acc * 2 >= population[target] && i + 1 < to) {
                    splitAt = i + 1
                    break
                }
            }
            if (splitAt <= from) splitAt = from + 1
            if (splitAt >= to) splitAt = to - 1

            var leftPop = 0L
            for (i in from until splitAt) leftPop += histogram[items[i]]
            start[target] = from
            end[target] = splitAt
            population[target] = leftPop
            start[boxes] = splitAt
            end[boxes] = to
            population[boxes] = bestPop - leftPop
            boxes++
        }

        val result = IntArray(boxes)
        for (box in 0 until boxes) {
            var r = 0L
            var g = 0L
            var b = 0L
            var totalCount = 0L
            for (i in start[box] until end[box]) {
                val binIndex = items[i]
                val n = histogram[binIndex].toLong()
                r += ((((binIndex shr 10) and 31) shl 3) or 4) * n
                g += ((((binIndex shr 5) and 31) shl 3) or 4) * n
                b += (((binIndex and 31) shl 3) or 4) * n
                totalCount += n
            }
            result[box] = if (totalCount == 0L) 0 else {
                val rr = (r / totalCount).toInt().coerceIn(0, 255)
                val gg = (g / totalCount).toInt().coerceIn(0, 255)
                val bb = (b / totalCount).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        return result
    }

    // ---------------------------------------------------------------------- LUT

    private fun buildLut() {
        buildTree()
        for (binIndex in 0 until 32768) {
            queryR = (((binIndex shr 10) and 31) shl 3) or 4
            queryG = (((binIndex shr 5) and 31) shl 3) or 4
            queryB = ((binIndex and 31) shl 3) or 4
            bestIndex = 0
            bestDistance = Int.MAX_VALUE
            search(0)
            lut[binIndex] = bestIndex
        }
    }

    private fun buildTree() {
        val size = palette.size
        nodeCount = 0
        if (size == 0) return
        val order = IntArray(size) { it }
        build(0, size, order, 0)
    }

    private fun build(from: Int, to: Int, order: IntArray, depth: Int): Int {
        if (from >= to) return -1
        val axis = depth % 3
        // Insertion sort on the slice: 256 entries at most, and it keeps this
        // dependency-free and allocation-light.
        for (i in from + 1 until to) {
            val v = order[i]
            val key = channel(palette[v], axis)
            var j = i - 1
            while (j >= from && channel(palette[order[j]], axis) > key) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = v
        }
        val mid = (from + to) / 2
        val node = nodeCount++
        val idx = order[mid]
        val c = palette[idx]
        nodeIndex[node] = idx
        nodeAxis[node] = axis
        nodeR[node] = (c shr 16) and 0xFF
        nodeG[node] = (c shr 8) and 0xFF
        nodeB[node] = c and 0xFF
        nodeLeft[node] = build(from, mid, order, depth + 1)
        nodeRight[node] = build(mid + 1, to, order, depth + 1)
        return node
    }

    private fun channel(color: Int, axis: Int): Int = when (axis) {
        0 -> (color shr 16) and 0xFF
        1 -> (color shr 8) and 0xFF
        else -> color and 0xFF
    }

    private fun queryChannel(axis: Int): Int = when (axis) {
        0 -> queryR
        1 -> queryG
        else -> queryB
    }

    private fun search(node: Int) {
        if (node < 0) return
        val dr = queryR - nodeR[node]
        val dg = queryG - nodeG[node]
        val db = queryB - nodeB[node]
        val d = dr * dr * 3 + dg * dg * 6 + db * db
        if (d < bestDistance) {
            bestDistance = d
            bestIndex = nodeIndex[node]
        }
        val axis = nodeAxis[node]
        val diff = queryChannel(axis) - channel(palette[nodeIndex[node]], axis)
        val near = if (diff <= 0) nodeLeft[node] else nodeRight[node]
        val far = if (diff <= 0) nodeRight[node] else nodeLeft[node]
        search(near)
        // The split plane's distance is a lower bound for the far subtree.
        val weight = when (axis) {
            0 -> 3
            1 -> 6
            else -> 1
        }
        if (diff * diff * weight < bestDistance) search(far)
    }

    private fun nearest(r: Int, g: Int, b: Int): Int {
        val binIndex = (((r shr 3) and 31) shl 10) or (((g shr 3) and 31) shl 5) or ((b shr 3) and 31)
        return lut[binIndex]
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
    // Open-addressed code dictionary: key = (prefix shl 8) or next-byte.
    private val keys = IntArray(1 shl 13)
    private val codes = IntArray(1 shl 13)

    fun compress(indices: ByteArray): ByteArray {
        val bitOut = SubBlockWriter()
        java.util.Arrays.fill(keys, -1)
        val clear = 256
        val eoi = 257
        var codeSize = 9
        var next = 258

        bitOut.writeCode(clear, codeSize)
        if (indices.isEmpty()) {
            bitOut.writeCode(eoi, codeSize)
            return bitOut.finish()
        }
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = find(key)
            if (found != 0) {
                prefix = found - 1
                continue
            }
            bitOut.writeCode(prefix, codeSize)
            if (next < 4096) {
                insert(key, next)
                next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                bitOut.writeCode(clear, codeSize)
                java.util.Arrays.fill(keys, -1)
                next = 258
                codeSize = 9
            }
            prefix = k
        }
        bitOut.writeCode(prefix, codeSize)
        bitOut.writeCode(eoi, codeSize)
        return bitOut.finish()
    }

    /** Returns code + 1, or 0 when absent. */
    private fun find(key: Int): Int {
        val mask = keys.size - 1
        var slot = (key * -1640531535) ushr 19 and mask
        while (true) {
            val existing = keys[slot]
            if (existing == -1) return 0
            if (existing == key) return codes[slot] + 1
            slot = (slot + 1) and mask
        }
    }

    private fun insert(key: Int, code: Int) {
        val mask = keys.size - 1
        var slot = (key * -1640531535) ushr 19 and mask
        while (keys[slot] != -1) slot = (slot + 1) and mask
        keys[slot] = key
        codes[slot] = code
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
