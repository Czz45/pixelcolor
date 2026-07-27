package com.example.pixelcolor.engine

import java.util.LinkedList

class GameEngine(
    private var _state: GameState,
    private val config: GameConfig
) {
    val currentState: GameState get() = _state

    fun fillCell(x: Int, y: Int): GameState {
        val canvas = _state.canvas
        checkBounds(x, y)
        if (!canFill(x, y)) return _state
        doFill(x, y)
        return _state
    }

    /** 自由填色模式：点击任意未填格子，自动填对应颜色 */
    fun fillAnyCell(x: Int, y: Int): GameState {
        val canvas = _state.canvas
        if (!canvas.isInBounds(x, y)) return _state
        if (canvas.isFilled(x, y) || canvas.isTransparent(x, y)) return _state
        // 临时切换 selectedColorCode 为该格子的颜色
        val code = canvas.getCell(x, y)
        _state = _state.copy(selectedColorCode = code)
        doFill(x, y)
        return _state
    }

    fun fillSwipePath(path: List<Pair<Int, Int>>): GameState {
        for ((x, y) in path) {
            if (_state.canvas.isInBounds(x, y) && canFill(x, y)) {
                doFill(x, y)
            }
        }
        return _state
    }

    fun useAreaFill(x: Int, y: Int): GameState {
        val canvas = _state.canvas
        checkBounds(x, y)
        val targetCode = canvas.getCell(x, y)
        if (targetCode != _state.selectedColorCode || canvas.isTransparent(x, y)) return _state
        val region = bfsRegion(x, y, targetCode)
        var pal = _state.palette
        for ((rx, ry) in region) {
            if (!canvas.isFilled(rx, ry)) {
                canvas.fillCell(rx, ry)
                pal = pal.withDecrementedRemaining(targetCode)
            }
        }
        _state = _state.copy(palette = pal, progress = canvas.progress, isCompleted = canvas.isCompleted())
        return _state
    }

    /** Fill region step-by-step, returns list of partial states for UI updates */
    fun useAreaFillStepwise(x: Int, y: Int): List<GameState> {
        val canvas = _state.canvas
        checkBounds(x, y)
        val targetCode = canvas.getCell(x, y)
        if (targetCode != _state.selectedColorCode || canvas.isTransparent(x, y)) return emptyList()
        val region = bfsRegion(x, y, targetCode)
        val batchSize = 50
        var pal = _state.palette
        val results = mutableListOf<GameState>()
        for ((rx, ry) in region) {
            if (!canvas.isFilled(rx, ry)) {
                canvas.fillCell(rx, ry)
                pal = pal.withDecrementedRemaining(targetCode)
            }
        }
        _state = _state.copy(palette = pal, progress = canvas.progress, isCompleted = canvas.isCompleted())
        results.add(_state)
        return results
    }

    fun selectColor(code: Int): GameState {
        _state.palette.getColor(code) // throws if invalid
        _state = _state.copy(selectedColorCode = code)
        return _state
    }

    fun getColorCells(code: Int): List<Pair<Int, Int>> {
        val canvas = _state.canvas
        val result = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until canvas.height) {
            for (x in 0 until canvas.width) {
                if (canvas.getCell(x, y) == code) {
                    result.add(Pair(x, y))
                }
            }
        }
        return result
    }

    fun getColorRegions(code: Int): List<List<Pair<Int, Int>>> {
        val canvas = _state.canvas
        val visited = Array(canvas.height) { BooleanArray(canvas.width) { false } }
        val regions = mutableListOf<List<Pair<Int, Int>>>()

        for (y in 0 until canvas.height) {
            for (x in 0 until canvas.width) {
                if (canvas.getCell(x, y) == code && !visited[y][x]) {
                    regions.add(bfsRegion(x, y, code, visited))
                }
            }
        }
        return regions
    }

    private fun canFill(x: Int, y: Int): Boolean {
        val canvas = _state.canvas
        return canvas.getCell(x, y) == _state.selectedColorCode && !canvas.isFilled(x, y) && !canvas.isTransparent(x, y)
    }

    private fun doFill(x: Int, y: Int) {
        val canvas = _state.canvas
        val code = canvas.getCell(x, y)
        canvas.fillCell(x, y)
        val newPalette = _state.palette.withDecrementedRemaining(code)
        _state = _state.copy(
            palette = newPalette,
            progress = canvas.progress,
            isCompleted = canvas.isCompleted()
        )
    }

    private fun bfsRegion(startX: Int, startY: Int, code: Int, visited: Array<BooleanArray>? = null): List<Pair<Int, Int>> {
        val canvas = _state.canvas
        val vis = visited ?: Array(canvas.height) { BooleanArray(canvas.width) { false } }
        val result = mutableListOf<Pair<Int, Int>>()
        val queue = LinkedList<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))
        vis[startY][startX] = true
        val dirs = listOf(Pair(0, -1), Pair(1, 0), Pair(0, 1), Pair(-1, 0))
        val maxCells = 5000

        while (queue.isNotEmpty() && result.size < maxCells) {
            val (cx, cy) = queue.removeFirst()
            result.add(Pair(cx, cy))
            for ((dx, dy) in dirs) {
                val nx = cx + dx
                val ny = cy + dy
                if (canvas.isInBounds(nx, ny) && !vis[ny][nx] && canvas.getCell(nx, ny) == code) {
                    vis[ny][nx] = true
                    queue.add(Pair(nx, ny))
                }
            }
        }
        return result
    }

    private fun checkBounds(x: Int, y: Int) {
        if (!_state.canvas.isInBounds(x, y)) {
            throw IllegalArgumentException("Cell ($x, $y) out of bounds")
        }
    }
}
