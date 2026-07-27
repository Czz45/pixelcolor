package com.example.pixelcolor.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameEngineTest {
    private lateinit var engine: GameEngine
    private lateinit var config: GameConfig

    @Before
    fun setUp() {
        // 3x3 grid, cells[y][x]:
        // [1, 1, 2]
        // [1, 2, 2]
        // [3, 3, 3]
        val cells = arrayOf(
            intArrayOf(1, 1, 2),
            intArrayOf(1, 2, 2),
            intArrayOf(3, 3, 3)
        )
        val filled = Array(3) { BooleanArray(3) { false } }
        val fillOrder = Array(3) { IntArray(3) { 0 } }
        val canvas = PixelCanvas(3, 3, cells, filled, fillOrder)
        val palette = ColorPalette(listOf(
            PaletteColor(1, 0xFFFF0000, 3, 3),
            PaletteColor(2, 0xFF00FF00, 3, 3),
            PaletteColor(3, 0xFF0000FF, 3, 3)
        ))
        val state = GameState(canvas, palette, selectedColorCode = 1, isCompleted = false, progress = 0f, elapsedTimeMs = 0)
        config = GameConfig(16, 16, 8, "test")
        engine = GameEngine(state, config)
    }

    @Test
    fun fillCell_matchingColor_fillsCorrectly() {
        val newState = engine.fillCell(0, 0) // cell(0,0) = 1, selected=1
        assertTrue(newState.canvas.isFilled(0, 0))
        assertEquals(2, newState.palette.getColor(1).remainingCount)
        assertFalse(newState.isCompleted)
    }

    @Test
    fun fillCell_wrongColor_doesNothing() {
        val newState = engine.fillCell(2, 0) // cell(2,0) = 2, selected=1
        assertSame(engine.currentState, newState)
        assertFalse(newState.canvas.isFilled(2, 0))
    }

    @Test
    fun fillCell_alreadyFilled_doesNothing() {
        engine.fillCell(0, 0)
        val stateAfter1 = engine.currentState
        val newState = engine.fillCell(0, 0)
        assertSame(stateAfter1, engine.currentState) // unchanged on 2nd call (no-op)
        assertTrue(newState.canvas.isFilled(0, 0))
        assertEquals(2, newState.palette.getColor(1).remainingCount)
    }

    @Test
    fun fillCell_outOfBounds_throwsException() {
        assertThrows(IllegalArgumentException::class.java) { engine.fillCell(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { engine.fillCell(0, 3) }
    }

    @Test
    fun selectColor_changesSelectedColor() {
        val newState = engine.selectColor(2)
        assertEquals(2, newState.selectedColorCode)
    }

    @Test
    fun selectColor_invalidCode_throwsException() {
        assertThrows(NoSuchElementException::class.java) { engine.selectColor(99) }
    }

    @Test
    fun useAreaFill_fillsConnectedRegion_4Directions() {
        engine.selectColor(1)
        val newState = engine.useAreaFill(0, 0)
        assertTrue(newState.canvas.isFilled(0, 0))
        assertTrue(newState.canvas.isFilled(1, 0))
        assertTrue(newState.canvas.isFilled(0, 1))
    }

    @Test
    fun isCompleted_allFilled_returnsTrue() {
        engine.selectColor(1)
        engine.fillCell(0, 0); engine.fillCell(1, 0); engine.fillCell(0, 1)
        engine.selectColor(2)
        engine.fillCell(2, 0); engine.fillCell(1, 1); engine.fillCell(2, 1)
        engine.selectColor(3)
        engine.fillCell(0, 2); engine.fillCell(1, 2); engine.fillCell(2, 2)
        assertTrue(engine.currentState.isCompleted)
    }

    @Test
    fun fillSwipePath_fillsMatchingCellsOnly() {
        engine.selectColor(1)
        val newState = engine.fillSwipePath(listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0)))
        assertTrue(newState.canvas.isFilled(0, 0))
        assertTrue(newState.canvas.isFilled(1, 0))
        assertFalse(newState.canvas.isFilled(2, 0)) // wrong color, skipped
    }

    @Test
    fun getColorCells_returnsAllCoordinatesForColor() {
        val cells = engine.getColorCells(3)
        assertEquals(setOf(Pair(0, 2), Pair(1, 2), Pair(2, 2)), cells.toSet())
    }

    @Test
    fun getColorRegions_findsConnectedComponents() {
        val regions = engine.getColorRegions(1)
        assertEquals(1, regions.size)
        assertEquals(3, regions[0].size)
    }
}
