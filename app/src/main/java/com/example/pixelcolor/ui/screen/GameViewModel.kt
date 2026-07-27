package com.example.pixelcolor.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixelcolor.data.GameRepository
import com.example.pixelcolor.engine.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = GameRepository(application)
    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState
    private val _config = MutableStateFlow<GameConfig?>(null)
    val config: StateFlow<GameConfig?> = _config
    private var engine: GameEngine? = null
    private var timerJob: Job? = null
    var saveId: String? = null
        private set

    val canvasScale = MutableStateFlow(1f)
    val canvasOffsetX = MutableStateFlow(0f)
    val canvasOffsetY = MutableStateFlow(0f)
    private val _jumpTarget = MutableStateFlow<Pair<Int, Int>?>(null)
    val jumpTarget: StateFlow<Pair<Int, Int>?> = _jumpTarget
    private val _colorSortMode = MutableStateFlow(0)
    val colorSortMode: StateFlow<Int> = _colorSortMode
    private val _colorSortReversed = MutableStateFlow(false)
    val colorSortReversed: StateFlow<Boolean> = _colorSortReversed
    private val _justCompleted = MutableStateFlow(false)
    val justCompleted: StateFlow<Boolean> = _justCompleted
    fun clearJustCompleted() { _justCompleted.value = false }
    private var userHasSelectedColor = false

    fun loadGame(id: String) {
        saveId = id
        val t0 = android.os.SystemClock.elapsedRealtime()
        com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "GameViewModel.loadGame 开始 id=$id")
        val result = repo.load(id)
        val t1 = android.os.SystemClock.elapsedRealtime()
        if (result == null) {
            com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "GameViewModel.loadGame 失败 load返回null +${t1-t0}ms")
            return
        }
        val (state, cfg, sortInfo) = result
        com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "repo.load完成 +${t1-t0}ms canvas=${state.canvas.width}x${state.canvas.height} filled=${state.canvas.filledCount}")
        _config.value = cfg; _gameState.value = state
        _colorSortMode.value = sortInfo.first
        _colorSortReversed.value = sortInfo.second
        engine = GameEngine(state, cfg)
        val t2 = android.os.SystemClock.elapsedRealtime()
        com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "GameEngine创建完成 +${t2-t1}ms total=${t2-t0}ms")
        startTimer()
    }

    fun onColorSortModeChanged(mode: Int, reversed: Boolean) {
        _colorSortMode.value = mode
        _colorSortReversed.value = reversed
        autoSaveAsync()
    }

    fun saveCurrentState() {
        val state = _gameState.value ?: return
        val cfg = _config.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repo.save(state, cfg, saveId, _colorSortMode.value, _colorSortReversed.value)
            } catch (_: Exception) {}
        }
    }

    /** 引擎状态变更时保留计时器的累计时间 */
    private fun syncEngineState() {
        val cur = _gameState.value?.elapsedTimeMs ?: 0L
        _gameState.value = engine!!.currentState.copy(elapsedTimeMs = cur)
    }

    fun onCellClick(x: Int, y: Int) {
        val eng = engine ?: return
        val wasCompleted = eng.currentState.isCompleted
        eng.fillCell(x, y)
        syncEngineState()
        autoSaveAsync()
        if (eng.currentState.isCompleted) { stopTimer(); if (!wasCompleted) _justCompleted.value = true }
        else autoAdvanceColor()
    }

    /** 自由填色：点击任意格子自动填对应颜色 */
    fun onFreeClick(x: Int, y: Int) {
        val eng = engine ?: return
        val wasCompleted = eng.currentState.isCompleted
        eng.fillAnyCell(x, y)
        syncEngineState()
        autoSaveAsync()
        if (eng.currentState.isCompleted) { stopTimer(); if (!wasCompleted) _justCompleted.value = true }
    }

    fun onPaintLine(cells: List<Pair<Int, Int>>) {
        val eng = engine ?: return
        val wasCompleted = eng.currentState.isCompleted
        var changed = false
        for ((x, y) in cells) {
            if (!eng.currentState.canvas.isFilled(x, y)) {
                eng.fillCell(x, y)
                changed = true
            }
        }
        if (changed) {
            syncEngineState()
            autoSaveAsync()
            if (eng.currentState.isCompleted) { stopTimer(); if (!wasCompleted) _justCompleted.value = true }
            else autoAdvanceColor()
        }
    }

    /** 拖拽涂色高频路径：只填引擎、不 emit 状态、不存盘、不切色。
     *  视觉反馈由 PixelCanvasView 的增量 patch 循环负责；抬手时由 commitPaint 统一提交，
     *  避免每帧 syncEngineState 触发 GameScreen 全屏重组 → 连续涂色（尤其 5x 笔刷）延迟。 */
    fun paintCellsFast(cells: List<Pair<Int, Int>>) {
        val eng = engine ?: return
        for ((x, y) in cells) {
            if (!eng.currentState.canvas.isFilled(x, y)) eng.fillCell(x, y)
        }
    }

    /** 拖拽结束（抬手）时一次性提交：同步状态、存盘、切色、判定完成。 */
    fun commitPaint() {
        val eng = engine ?: return
        val wasCompleted = eng.currentState.isCompleted
        syncEngineState()
        autoSaveAsync()
        if (eng.currentState.isCompleted) { stopTimer(); if (!wasCompleted) _justCompleted.value = true }
        else autoAdvanceColor()
    }

    /** 当前颜色剩余为0时，按当前排序顺序跳到下一个 */
    private fun autoAdvanceColor() {
        if (!userHasSelectedColor) return
        val state = _gameState.value ?: return
        val cur = state.selectedColorCode
        val curColor = state.palette.colors.firstOrNull { it.code == cur } ?: return
        if (curColor.remainingCount > 0) return
        val sorted = getSortedColors(state)
        val idx = sorted.indexOfFirst { it.code == cur }
        // 找当前颜色之后的下一个有剩余的，没有则从头找
        val next = sorted.subList(idx + 1, sorted.size).firstOrNull { it.remainingCount > 0 }
            ?: sorted.firstOrNull { it.remainingCount > 0 }
            ?: return
        onColorSelected(next.code)
    }

    /** 进画布时自动选中排序后的第一个颜色 */
    fun selectFirstColor() {
        val state = _gameState.value ?: return
        val sorted = getSortedColors(state)
        val first = sorted.firstOrNull { it.remainingCount > 0 } ?: return
        userHasSelectedColor = true
        onColorSelected(first.code)
    }

    /** 按当前排序模式返回颜色列表 */
    private fun getSortedColors(state: GameState): List<PaletteColor> {
        val mode = _colorSortMode.value
        val rev = _colorSortReversed.value
        val list = when (mode) {
            0 -> state.palette.colors.sortedBy {
                val r = (it.color.toInt() shr 16) and 0xFF
                val g = (it.color.toInt() shr 8) and 0xFF
                val b = it.color.toInt() and 0xFF
                (r * 299 + g * 587 + b * 114) / 1000
            }
            1 -> state.palette.colors.sortedBy { it.code }
            2 -> state.palette.colors.sortedBy { it.totalCount }
            3 -> state.palette.colors.sortedBy { it.remainingCount }
            else -> state.palette.colors
        }
        return if (rev) list.reversed() else list
    }

    private var saveJob: kotlinx.coroutines.Job? = null
    private fun autoSaveAsync() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(800)
                val state = _gameState.value ?: return@launch
                val cfg = _config.value ?: return@launch
                repo.save(state, cfg, saveId, _colorSortMode.value, _colorSortReversed.value)
            // Save thumbnail
            try {
                val id = saveId ?: return@launch
                val cw = state.canvas.width; val ch = state.canvas.height
                val scale = if (cw * ch >= 40000) 1f else 2f
                val mw = (cw * scale).toInt().coerceIn(2, 1024); val mh = (ch * scale).toInt().coerceIn(2, 1024)
                val bmp = android.graphics.Bitmap.createBitmap(mw, mh, android.graphics.Bitmap.Config.ARGB_8888)
                for (y in 0 until ch) for (x in 0 until cw) {
                    val color = state.palette.colors.firstOrNull { it.code == state.canvas.getCell(x, y) }
                    val argb = if (state.canvas.isFilled(x, y) && color != null) color.color.toInt() else 0xFFFFFFFF.toInt()
                    val px = (x * scale).toInt(); val py = (y * scale).toInt()
                    for (dy in 0 until scale.toInt()) for (dx in 0 until scale.toInt()) bmp.setPixel(px + dx, py + dy, argb)
                }
                val file = java.io.File(getApplication<android.app.Application>().cacheDir, "thumb_$id.png")
                file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, it) }
                bmp.recycle()
            } catch (_: Exception) {}
            } catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) com.example.pixelcolor.PixelColorApp.logError("AutoSave", "auto save failed", e) }
        }
    }

    private var timerSaveJob: kotlinx.coroutines.Job? = null
    private fun timerSaveAsync() {
        timerSaveJob?.cancel()
        timerSaveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(2000)
                val state = _gameState.value ?: return@launch
                val cfg = _config.value ?: return@launch
                repo.save(state, cfg, saveId, _colorSortMode.value, _colorSortReversed.value)
            } catch (_: Exception) {}
        }
    }

    fun onColorSelected(code: Int) {
        val eng = engine ?: return
        if (code == 0) return // skip transparent
        userHasSelectedColor = true
        if (code == eng.currentState.selectedColorCode) {
            // Re-tap selected color → jump to random cell with this color
            val st = eng.currentState
            val matching = mutableListOf<Pair<Int, Int>>()
            for (y in 0 until st.canvas.height) for (x in 0 until st.canvas.width) {
                if (!st.canvas.isFilled(x, y) && st.canvas.getCell(x, y) == code) matching.add(Pair(x, y))
            }
            if (matching.isNotEmpty()) _jumpTarget.value = matching.random()
            return
        }
        eng.selectColor(code); syncEngineState()
    }

    fun clearJumpTarget() { _jumpTarget.value = null }

    fun jumpToCell(x: Int, y: Int) {
        _jumpTarget.value = Pair(x, y)
    }

    fun onAreaFill(x: Int, y: Int) {
        val eng = engine ?: return
        val state = eng.currentState
        val canvas = state.canvas
        val targetCode = canvas.getCell(x, y)
        if (targetCode != state.selectedColorCode || canvas.isTransparent(x, y)) return
        val cells = mutableListOf<Pair<Int, Int>>()
        val visited = Array(canvas.height) { BooleanArray(canvas.width) }
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.addLast(Pair(x, y)); visited[y][x] = true
        val dirs = intArrayOf(0, -1, 1, 0, 0, 1, -1, 0)
        while (queue.isNotEmpty() && cells.size < 5000) {
            val (cx, cy) = queue.removeFirst()
            cells.add(Pair(cx, cy))
            for (i in 0 until 4) {
                val nx = cx + dirs[i * 2]; val ny = cy + dirs[i * 2 + 1]
                if (nx in 0 until canvas.width && ny in 0 until canvas.height && !visited[ny][nx] && canvas.getCell(nx, ny) == targetCode) {
                    visited[ny][nx] = true; queue.addLast(Pair(nx, ny))
                }
            }
        }
        // Sort by distance from click point — fill outward
        cells.sortBy { kotlin.math.abs(it.first - x) + kotlin.math.abs(it.second - y) }

        viewModelScope.launch {
            try {
                val batchSize = 30
                var idx = 0
                while (idx < cells.size) {
                    val end = minOf(idx + batchSize, cells.size)
                    for (i in idx until end) {
                        val (bx, by) = cells[i]
                        if (canvas.isInBounds(bx, by) && !canvas.isFilled(bx, by)) {
                            eng.fillCell(bx, by)
                        }
                    }
                    syncEngineState()
                    idx = end
                    kotlinx.coroutines.delay(1)
                }
            } catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) com.example.pixelcolor.PixelColorApp.logError("AreaFill", "area fill failed", e) }
            autoSaveAsync()
            if (eng.currentState.isCompleted) { stopTimer(); _justCompleted.value = true }
            else autoAdvanceColor()
        }
    }

    fun onSwipeFill(path: List<Pair<Int, Int>>) {
        val eng = engine ?: return
        _gameState.value = eng.fillSwipePath(path); autoSaveAsync()
        autoAdvanceColor()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(com.example.pixelcolor.PixelColorApp.crashHandler) {
            var tickCount = 0
            while (true) {
                delay(1000)
                val current = _gameState.value
                if (current == null) continue  // 等待 loadGame 设置状态，不退出
                _gameState.value = current.copy(elapsedTimeMs = current.elapsedTimeMs + 1000)
                tickCount++
                if (tickCount % 10 == 0) timerSaveAsync()
            }
        }
    }

    private fun stopTimer() { timerJob?.cancel() }

    override fun onCleared() {
        timerJob?.cancel()
        timerSaveJob?.cancel()
        // 同步保存，不能用 viewModelScope（已取消）
        try {
            val state = _gameState.value ?: return
            val cfg = _config.value ?: return
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                repo.save(state, cfg, saveId, _colorSortMode.value, _colorSortReversed.value)
            }
        } catch (_: Exception) {}
    }
}
