package com.example.pixelcolor.engine

data class GameConfig(
    val gridWidth: Int,
    val gridHeight: Int,
    val maxColors: Int,
    val sourceImageUri: String,
    val aspectLocked: Boolean = true
) {
    init {
        require(gridWidth in 16..1024) { "gridWidth must be 16~1024, got $gridWidth" }
        require(gridHeight in 16..1024) { "gridHeight must be 16~1024, got $gridHeight" }
        require(maxColors in 5..256) { "maxColors must be 5~256, got $maxColors" }
    }
}
