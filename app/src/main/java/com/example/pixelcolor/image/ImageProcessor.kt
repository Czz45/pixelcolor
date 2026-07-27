package com.example.pixelcolor.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.pixelcolor.engine.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageProcessor {

    fun processImageFromUri(
        context: Context,
        imageUri: Uri,
        config: GameConfig
    ): GameState {
        // Read entire stream into byte array (avoids needing to reopen stream)
        val bytes: ByteArray = if (imageUri.scheme == "file") {
            val file = File(imageUri.path ?: throw IllegalArgumentException("Invalid file URI"))
            if (!file.exists()) throw IllegalArgumentException("File not found: ${file.absolutePath}")
            file.readBytes()
        } else {
            context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Cannot open image: $imageUri")
        }

        // Decode bounds
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

        // Calculate sample size
        val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, config.gridWidth, config.gridHeight)

        // Decode with sample size
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // 2 bytes/pixel saves memory
        }
        var bitmap: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: throw IllegalArgumentException("Cannot decode image")

            // Scale to target grid size — nearest-neighbor for sharp edges
            val gw = config.gridWidth; val gh = config.gridHeight
            scaled = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
            for (y in 0 until gh) for (x in 0 until gw) {
                val srcX = (x * bitmap.width / gw).coerceIn(0, bitmap.width - 1)
                val srcY = (y * bitmap.height / gh).coerceIn(0, bitmap.height - 1)
                scaled!!.setPixel(x, y, bitmap.getPixel(srcX, srcY))
            }

            // Extract pixel colors, handle transparent
            val opaquePixels = mutableListOf<Long>()
            val transparentSet = mutableSetOf<Int>()
            for (y in 0 until config.gridHeight) {
                for (x in 0 until config.gridWidth) {
                    val px = scaled.getPixel(x, y)
                    val alpha = (px shr 24) and 0xFF
                    if (alpha < 128) transparentSet.add(y * config.gridWidth + x)
                    else opaquePixels.add(px.toLong() and 0xFFFFFFFF)
                }
            }

            // Color quantization (opaque only)
            val palette = ColorQuantizer.quantize(opaquePixels.toLongArray(), config.maxColors)
            val opaqueCodes = ColorQuantizer.mapAllToPalette(opaquePixels.toLongArray(), palette)

            // Build canvas
            var opaqueIdx = 0
            val cells = Array(config.gridHeight) { y ->
                IntArray(config.gridWidth) { x ->
                    if (y * config.gridWidth + x in transparentSet) 0 else opaqueCodes[opaqueIdx++]
                }
            }
            val filled = Array(config.gridHeight) { y ->
                BooleanArray(config.gridWidth) { x ->
                    val alpha = (scaled.getPixel(x, y) shr 24) and 0xFF
                    alpha < 128
                }
            }
            val fillOrder = Array(config.gridHeight) { IntArray(config.gridWidth) { 0 } }
            val canvas = PixelCanvas(config.gridWidth, config.gridHeight, cells, filled, fillOrder)

            return GameState(canvas, palette, palette.colors.first().code, false, 0f, 0)
        } finally {
            scaled?.recycle()
            bitmap?.recycle()
        }
    }

    fun processImage(imageFile: File, config: GameConfig): GameState {
        val bytes = imageFile.readBytes()
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, config.gridWidth, config.gridHeight)

        var bitmap: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: throw IllegalArgumentException("Cannot decode image: ${imageFile.name}")

            val gw2 = config.gridWidth; val gh2 = config.gridHeight
            scaled = Bitmap.createBitmap(gw2, gh2, Bitmap.Config.ARGB_8888)
            for (y in 0 until gh2) for (x in 0 until gw2) {
                val srcX = (x * bitmap.width / gw2).coerceIn(0, bitmap.width - 1)
                val srcY = (y * bitmap.height / gh2).coerceIn(0, bitmap.height - 1)
                scaled!!.setPixel(x, y, bitmap.getPixel(srcX, srcY))
            }

            val opaquePixels2 = mutableListOf<Long>()
            val transparentSet2 = mutableSetOf<Int>()
            for (y in 0 until config.gridHeight) {
                for (x in 0 until config.gridWidth) {
                    val px = scaled.getPixel(x, y)
                    val alpha = (px shr 24) and 0xFF
                    if (alpha < 128) transparentSet2.add(y * config.gridWidth + x)
                    else opaquePixels2.add(px.toLong() and 0xFFFFFFFF)
                }
            }
            val palette = ColorQuantizer.quantize(opaquePixels2.toLongArray(), config.maxColors)
            val opaqueCodes2 = ColorQuantizer.mapAllToPalette(opaquePixels2.toLongArray(), palette)
            var opaqueIdx2 = 0
            val cells = Array(config.gridHeight) { y ->
                IntArray(config.gridWidth) { x ->
                    if (y * config.gridWidth + x in transparentSet2) 0 else opaqueCodes2[opaqueIdx2++]
                }
            }
            val filled = Array(config.gridHeight) { y ->
                BooleanArray(config.gridWidth) { x ->
                    val alpha = (scaled.getPixel(x, y) shr 24) and 0xFF
                    alpha < 128
                }
            }
            val fillOrder = Array(config.gridHeight) { IntArray(config.gridWidth) { 0 } }
            val canvas = PixelCanvas(config.gridWidth, config.gridHeight, cells, filled, fillOrder)
            return GameState(canvas, palette, palette.colors.first().code, false, 0f, 0)
        } finally {
            scaled?.recycle()
            bitmap?.recycle()
        }
    }

    fun renderToBitmap(state: GameState, cellSizePx: Int = 20): Bitmap {
        val cw = state.canvas.width; val ch = state.canvas.height
        val w = cw * cellSizePx; val h = ch * cellSizePx
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until ch) {
            for (x in 0 until cw) {
                val color = if (state.canvas.isFilled(x, y)) {
                    state.palette.getColor(state.canvas.getCell(x, y)).color.toInt()
                } else 0xFF888888.toInt()
                for (dy in 0 until cellSizePx) {
                    for (dx in 0 until cellSizePx) {
                        bitmap.setPixel(x * cellSizePx + dx, y * cellSizePx + dy, color)
                    }
                }
            }
        }
        return bitmap
    }

    fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    private fun calculateSampleSize(imgW: Int, imgH: Int, targetW: Int, targetH: Int): Int {
        var sampleSize = 1
        while (imgW / sampleSize > targetW * 3 && imgH / sampleSize > targetH * 3) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }
}
