package com.example.pixelcolor.engine

data class PaletteColor(
    val code: Int,
    val color: Long,
    val totalCount: Int,
    val remainingCount: Int
)

data class ColorPalette(
    val colors: List<PaletteColor>
) {
    init {
        val codes = colors.map { it.code }
        require(codes.size == codes.distinct().size) { "Color codes must be unique" }
    }

    fun getColor(code: Int): PaletteColor =
        colors.first { it.code == code }

    fun withDecrementedRemaining(code: Int): ColorPalette {
        val newColors = colors.map { c ->
            if (c.code == code && c.remainingCount > 0) {
                c.copy(remainingCount = c.remainingCount - 1)
            } else c
        }
        return copy(colors = newColors)
    }

    fun allRemainingZero(): Boolean = colors.all { it.remainingCount == 0 }
}
