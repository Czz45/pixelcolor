package com.example.pixelcolor.image

import com.example.pixelcolor.engine.ColorPalette
import com.example.pixelcolor.engine.PaletteColor
import org.junit.Assert.*
import org.junit.Test

class ColorQuantizerTest {

    @Test
    fun quantize_singleColor_allSame() {
        val pixels = LongArray(100) { 0xFFFF0000 }
        val palette = ColorQuantizer.quantize(pixels, 5)
        assertEquals(1, palette.colors.size)
        assertEquals(0xFFFF0000, palette.colors[0].color)
        assertEquals(100, palette.colors[0].totalCount)
    }

    @Test
    fun quantize_twoColors_splitsCorrectly() {
        val pixels = LongArray(100).apply {
            for (i in 0 until 50) this[i] = 0xFFFF0000
            for (i in 50 until 100) this[i] = 0xFF0000FF
        }
        val palette = ColorQuantizer.quantize(pixels, 2)
        assertEquals(2, palette.colors.size)
        assertEquals(100, palette.colors.sumOf { it.totalCount })
    }

    @Test
    fun quantize_moreColorsThanPixels_returnsActualCount() {
        val pixels = LongArray(3) { (0xFF000000 + it).toLong() }
        val palette = ColorQuantizer.quantize(pixels, 128)
        assertTrue(palette.colors.size <= 3)
    }

    @Test
    fun quantize_emptyInput_returnsEmpty() {
        val pixels = LongArray(0)
        val palette = ColorQuantizer.quantize(pixels, 10)
        assertEquals(0, palette.colors.size)
    }

    @Test
    fun mapToPalette_mapsToClosestColor() {
        val palette = ColorPalette(listOf(
            PaletteColor(1, 0xFFFF0000, 0, 0),
            PaletteColor(2, 0xFF0000FF, 0, 0)
        ))
        val result = ColorQuantizer.mapToPalette(0xFFEE1111, palette)
        assertEquals(1, result)
        val result2 = ColorQuantizer.mapToPalette(0xFF0000EE, palette)
        assertEquals(2, result2)
    }
}
