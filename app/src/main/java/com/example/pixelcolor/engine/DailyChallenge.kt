package com.example.pixelcolor.engine

import java.time.LocalDate
import kotlin.random.Random

object DailyChallenge {
    private val gridSizes = listOf(32, 48, 64, 96, 128)

    fun generate(date: LocalDate, presetImageCount: Int): GameConfig {
        val seed = date.hashCode()
        val rng = Random(seed)
        val imageIndex = rng.nextInt(presetImageCount.coerceAtLeast(1))
        val gridSize = gridSizes[rng.nextInt(gridSizes.size)]
        val colors = rng.nextInt(10, 31)
        return GameConfig(
            gridWidth = gridSize, gridHeight = gridSize, maxColors = colors,
            sourceImageUri = "file:///android_asset/preset_images/${imageIndex}.png", aspectLocked = true
        )
    }
}
