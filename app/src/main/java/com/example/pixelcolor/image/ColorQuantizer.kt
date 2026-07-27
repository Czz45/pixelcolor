package com.example.pixelcolor.image

import com.example.pixelcolor.engine.ColorPalette
import com.example.pixelcolor.engine.PaletteColor
import kotlin.math.min
import kotlin.random.Random

object ColorQuantizer {

    private const val MAX_SAMPLE_SIZE = 10000 // Max pixels to feed into K-Means
    private const val MAX_ITERATIONS = 10

    fun quantize(pixels: LongArray, targetColors: Int): ColorPalette {
        if (pixels.isEmpty()) return ColorPalette(emptyList())
        val n = min(targetColors, 256)

        // Filter out transparent pixels — only quantize opaque ones
        val opaquePixels = pixels.filter { val alpha = (it shr 24) and 0xFF; alpha >= 128 }.toLongArray()
        if (opaquePixels.isEmpty()) return ColorPalette(emptyList())

        // For small inputs, check distinct colors first
        if (opaquePixels.size <= 1000) {
            val distinctColors = opaquePixels.distinct()
            if (distinctColors.size <= n) {
                return ColorPalette(distinctColors.mapIndexed { i, color ->
                    val count = opaquePixels.count { it == color }
                    PaletteColor(i + 1, color, count, count)
                })
            }
        }

        // Sample pixels for K-Means to keep it fast
        val sample = if (opaquePixels.size <= MAX_SAMPLE_SIZE) {
            opaquePixels
        } else {
            val step = opaquePixels.size / MAX_SAMPLE_SIZE
            LongArray(MAX_SAMPLE_SIZE) { i -> opaquePixels[i * step] }
        }

        val centroids = kMeansCentroids(sample, n)

        // Build palette from centroids
        val paletteColors = centroids.mapIndexed { i, color ->
            PaletteColor(i + 1, color, 0, 0)
        }
        val palette = ColorPalette(paletteColors)

        // Count total pixels per color (efficient single pass)
        val counts = IntArray(n)
        val colorList = centroids
        for (p in opaquePixels) {
            val nearest = findNearest(p, colorList)
            counts[nearest]++
        }

        return ColorPalette(
            centroids.mapIndexed { i, color ->
                PaletteColor(i + 1, color, counts[i], counts[i])
            }
        )
    }

    /**
     * Returns k representative colors from the sample using K-Means.
     */
    private fun kMeansCentroids(sample: LongArray, k: Int): List<Long> {
        // Initialize centroids: pick evenly-spaced pixels for good coverage
        val centroids = mutableListOf<Long>()
        if (sample.size <= k) {
            centroids.addAll(sample.toList())
            while (centroids.size < k) centroids.add(sample[0])
            return centroids.distinct()
        }
        val step = sample.size / k
        for (i in 0 until k) {
            centroids.add(sample[i * step])
        }

        // Pre-compute color components for speed
        val sampleRGB = Array(sample.size) { i ->
            Triple(
                ((sample[i] shr 16) and 0xFF).toInt(),
                ((sample[i] shr 8) and 0xFF).toInt(),
                (sample[i] and 0xFF).toInt()
            )
        }
        val centroidRGB = Array(k) { i ->
            Triple(
                ((centroids[i] shr 16) and 0xFF).toInt(),
                ((centroids[i] shr 8) and 0xFF).toInt(),
                (centroids[i] and 0xFF).toInt()
            )
        }

        val assignments = IntArray(sample.size)

        for (iter in 0 until MAX_ITERATIONS) {
            var changed = false

            // Assign each sample to nearest centroid
            for (i in sample.indices) {
                val (sr, sg, sb) = sampleRGB[i]
                var bestIdx = 0
                var bestDist = Int.MAX_VALUE
                for (j in 0 until k) {
                    val (cr, cg, cb) = centroidRGB[j]
                    val dr = sr - cr; val dg = sg - cg; val db = sb - cb
                    val dist = dr * dr + dg * dg + db * db // No sqrt needed for comparison
                    if (dist < bestDist) { bestDist = dist; bestIdx = j }
                }
                if (assignments[i] != bestIdx) { assignments[i] = bestIdx; changed = true }
            }

            if (!changed) break

            // Update centroids: accumulate per cluster
            val sumR = LongArray(k); val sumG = LongArray(k); val sumB = LongArray(k)
            val count = IntArray(k)
            for (i in sample.indices) {
                val c = assignments[i]
                val (sr, sg, sb) = sampleRGB[i]
                sumR[c] += sr; sumG[c] += sg; sumB[c] += sb
                count[c]++
            }
            for (j in 0 until k) {
                if (count[j] > 0) {
                    val r = (sumR[j] / count[j]).toInt()
                    val g = (sumG[j] / count[j]).toInt()
                    val b = (sumB[j] / count[j]).toInt()
                    centroidRGB[j] = Triple(r, g, b)
                    centroids[j] = (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                }
            }
        }

        return centroids
    }

    fun mapToPalette(pixelColor: Long, palette: ColorPalette): Int {
        val colors = palette.colors.map { it.color }
        return findNearest(pixelColor, colors) + 1
    }

    fun mapAllToPalette(pixels: LongArray, palette: ColorPalette): IntArray {
        val colorList = palette.colors.map { it.color }
        val k = colorList.size
        // Pre-compute RGB for palette colors once
        val paletteR = IntArray(k); val paletteG = IntArray(k); val paletteB = IntArray(k)
        for (j in 0 until k) {
            val c = colorList[j]
            paletteR[j] = ((c shr 16) and 0xFF).toInt()
            paletteG[j] = ((c shr 8) and 0xFF).toInt()
            paletteB[j] = (c and 0xFF).toInt()
        }

        // Parallel mapping for large arrays
        val result = IntArray(pixels.size)
        if (pixels.size > 5000) {
            val numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
            val chunkSize = (pixels.size + numThreads - 1) / numThreads
            val threads = (0 until numThreads).map { t ->
                Thread {
                    val start = t * chunkSize
                    val end = minOf(start + chunkSize, pixels.size)
                    for (i in start until end) {
                        val p = pixels[i]
                        val pr = ((p shr 16) and 0xFF).toInt()
                        val pg = ((p shr 8) and 0xFF).toInt()
                        val pb = (p and 0xFF).toInt()
                        var bestIdx = 0; var bestDist = Int.MAX_VALUE
                        for (j in 0 until k) {
                            val dr = pr - paletteR[j]; val dg = pg - paletteG[j]; val db = pb - paletteB[j]
                            val dist = dr * dr + dg * dg + db * db
                            if (dist < bestDist) { bestDist = dist; bestIdx = j }
                        }
                        result[i] = bestIdx + 1
                    }
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
        } else {
            for (i in pixels.indices) {
                val p = pixels[i]
                val pr = ((p shr 16) and 0xFF).toInt()
                val pg = ((p shr 8) and 0xFF).toInt()
                val pb = (p and 0xFF).toInt()
                var bestIdx = 0; var bestDist = Int.MAX_VALUE
                for (j in 0 until k) {
                    val dr = pr - paletteR[j]; val dg = pg - paletteG[j]; val db = pb - paletteB[j]
                    val dist = dr * dr + dg * dg + db * db
                    if (dist < bestDist) { bestDist = dist; bestIdx = j }
                }
                result[i] = bestIdx + 1
            }
        }
        return result
    }

    private fun findNearest(color: Long, candidates: List<Long>): Int {
        val r = ((color shr 16) and 0xFF).toInt()
        val g = ((color shr 8) and 0xFF).toInt()
        val b = (color and 0xFF).toInt()
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        for (i in candidates.indices) {
            val c = candidates[i]
            val cr = ((c shr 16) and 0xFF).toInt()
            val cg = ((c shr 8) and 0xFF).toInt()
            val cb = (c and 0xFF).toInt()
            val dr = r - cr; val dg = g - cg; val db = b - cb
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) { bestDist = dist; bestIdx = i }
        }
        return bestIdx
    }
}
