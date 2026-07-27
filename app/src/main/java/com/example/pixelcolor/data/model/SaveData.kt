package com.example.pixelcolor.data.model

data class SaveData(
    val id: String,
    val config: ConfigData,
    val cells: List<Int>,              // Gson-compatible: List<Int> instead of IntArray
    val palette: List<PaletteColorData>,
    val filledCells: List<FilledCell>, // Gson-compatible: typed class instead of Pair
    val elapsedTimeMs: Long,
    val createdAt: String,
    val colorSortMode: Int = 0,        // 0=Gradient, 1=Code, 2=Total, 3=Remaining
    val colorSortReversed: Boolean = false
)

data class ConfigData(
    val gridWidth: Int,
    val gridHeight: Int,
    val maxColors: Int,
    val sourceImagePath: String
)

data class PaletteColorData(
    val code: Int,
    val color: Long,
    val total: Int,
    val remaining: Int
)

data class FilledCell(
    val y: Int,
    val x: Int,
    val order: Int = 0
)
