package com.example.pixelcolor.engine

data class GameState(
    val canvas: PixelCanvas,
    val palette: ColorPalette,
    val selectedColorCode: Int,
    val isCompleted: Boolean,
    val progress: Float,
    val elapsedTimeMs: Long
)
