package com.example.pixelcolor.engine

class PixelCanvas(
    val width: Int,
    val height: Int,
    val cells: Array<IntArray>,
    val filledCells: Array<BooleanArray>,
    val fillOrder: Array<IntArray>? = null
) {
    init {
        require(width > 0 && height > 0) { "Canvas dimensions must be positive" }
        require(cells.size == height) { "cells rows must equal height" }
        require(cells.all { it.size == width }) { "each cells row must have width columns" }
        require(filledCells.size == height) { "filledCells rows must equal height" }
        require(filledCells.all { it.size == width }) { "each filledCells row must have width columns" }
    }

    val totalCells: Int get() = width * height
    val filledCount: Int get() {
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (filledCells[y][x] && !isTransparent(x, y)) count++
            }
        }
        return count
    }
    val progress: Float get() {
        val nonTransparent = totalCells - transparentCount
        return if (nonTransparent == 0) 0f else filledCount.toFloat() / nonTransparent
    }
    val transparentCount: Int get() {
        var count = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (cells[y][x] == 0) count++
        }
        return count
    }

    fun isFilled(x: Int, y: Int): Boolean {
        checkBounds(x, y)
        return filledCells[y][x]
    }

    fun isTransparent(x: Int, y: Int): Boolean {
        checkBounds(x, y)
        return cells[y][x] == 0
    }

    fun getCell(x: Int, y: Int): Int {
        checkBounds(x, y)
        return cells[y][x]
    }

    fun fillCell(x: Int, y: Int) {
        checkBounds(x, y)
        filledCells[y][x] = true
        fillOrder?.let { it[y][x] = fillCount }
    }

    private val fillCount: Int get() {
        var count = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (filledCells[y][x]) count++
        }
        return count
    }

    fun isCompleted(): Boolean {
        for (y in 0 until height) for (x in 0 until width) {
            if (cells[y][x] != 0 && !filledCells[y][x]) return false
        }
        return true
    }

    fun isInBounds(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height

    private fun checkBounds(x: Int, y: Int) {
        require(isInBounds(x, y)) { "Cell ($x, $y) out of bounds [${width}x${height}]" }
    }

    companion object {
        fun create(width: Int, height: Int, cellValues: (Int, Int) -> Int): PixelCanvas {
            val cells = Array(height) { y -> IntArray(width) { x -> cellValues(x, y) } }
            val filled = Array(height) { BooleanArray(width) { false } }
            val fillOrder = Array(height) { IntArray(width) { 0 } }
            return PixelCanvas(width, height, cells, filled, fillOrder)
        }
    }
}
