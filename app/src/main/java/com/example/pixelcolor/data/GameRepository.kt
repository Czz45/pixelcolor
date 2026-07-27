package com.example.pixelcolor.data

import android.content.Context
import com.example.pixelcolor.data.model.*
import com.example.pixelcolor.engine.*
import com.google.gson.Gson
import org.json.JSONObject
import java.io.File
import java.util.UUID

class GameRepository(private val context: Context) {
    private val gson = Gson()
    private val savesDir: File
        get() = File(context.filesDir, "saves").also { it.mkdirs() }
    private val imagesDir: File
        get() = File(context.filesDir, "images").also { it.mkdirs() }

    fun save(state: GameState, config: GameConfig, saveId: String? = null, colorSortMode: Int = 0, colorSortReversed: Boolean = false): String {
        val id = saveId ?: UUID.randomUUID().toString()
        val canvas = state.canvas

        // Flat cell codes as List<Int> (Gson-compatible)
        val flatCells = mutableListOf<Int>()
        for (y in 0 until canvas.height) {
            for (x in 0 until canvas.width) {
                flatCells.add(canvas.getCell(x, y))
            }
        }

        // Filled cells as type-safe list with order
        val filledCells = mutableListOf<FilledCell>()
        for (y in 0 until canvas.height) {
            for (x in 0 until canvas.width) {
                if (canvas.isFilled(x, y)) {
                    val order = canvas.fillOrder?.getOrNull(y)?.getOrNull(x) ?: 0
                    filledCells.add(FilledCell(y, x, order))
                }
            }
        }
        // Sort by order to preserve painting sequence
        filledCells.sortBy { it.order }

        val saveData = SaveData(
            id = id,
            config = ConfigData(config.gridWidth, config.gridHeight, config.maxColors, config.sourceImageUri),
            cells = flatCells,
            palette = state.palette.colors.map {
                PaletteColorData(it.code, it.color, it.totalCount, it.remainingCount)
            },
            filledCells = filledCells,
            elapsedTimeMs = state.elapsedTimeMs,
            createdAt = java.time.Instant.now().toString(),
            colorSortMode = colorSortMode,
            colorSortReversed = colorSortReversed
        )
        val json = gson.toJson(saveData)
        File(savesDir, "$id.json").writeText(json)
        return id
    }

    fun load(saveId: String): Triple<GameState, GameConfig, Pair<Int, Boolean>>? {
        return try {
            val t0 = android.os.SystemClock.elapsedRealtime()
            val file = File(savesDir, "$saveId.json")
            if (!file.exists()) return null
            val fileSize = file.length()
            val json = file.readText()
            val t1 = android.os.SystemClock.elapsedRealtime()
            // Fast path: manual parse for large files to avoid Gson boxing overhead
            val saveData: SaveData
            if (fileSize > 50_000) {
                val obj = JSONObject(json)
                val configObj = obj.getJSONObject("config")
                val config = ConfigData(configObj.getInt("gridWidth"), configObj.getInt("gridHeight"), configObj.getInt("maxColors"), configObj.optString("sourceImagePath", ""))
                // Parse cells directly as IntArray — avoids 40000+ boxed Integer objects
                val cellsArr = obj.getJSONArray("cells")
                val cells = IntArray(cellsArr.length()) { cellsArr.getInt(it) }
                // Parse palette
                val palArr = obj.getJSONArray("palette")
                val palette = (0 until palArr.length()).map { i ->
                    val p = palArr.getJSONObject(i)
                    PaletteColorData(p.getInt("code"), p.getLong("color"), p.getInt("total"), p.getInt("remaining"))
                }
                // Parse filledCells
                val fcArr = obj.getJSONArray("filledCells")
                val filledCells = (0 until fcArr.length()).map { i ->
                    val fc = fcArr.getJSONObject(i)
                    FilledCell(fc.getInt("y"), fc.getInt("x"), fc.optInt("order", 0))
                }
                saveData = SaveData(obj.optString("id", ""), config, cellsArr.toString().let { _ -> cells.toList() }, palette, filledCells, obj.optLong("elapsedTimeMs", 0), obj.optString("createdAt", ""), obj.optInt("colorSortMode", 0), obj.optBoolean("colorSortReversed", false))
                val t2 = android.os.SystemClock.elapsedRealtime()
                com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "fast parse: ${fileSize}bytes +${t2-t1}ms cells=${cells.size} filled=${filledCells.size}")
            } else {
                saveData = gson.fromJson(json, SaveData::class.java)
                val t2 = android.os.SystemClock.elapsedRealtime()
                com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "gson parse: ${fileSize}bytes +${t2-t1}ms")
            }
            val (state, config) = restoreFromSave(saveData)
            val t3 = android.os.SystemClock.elapsedRealtime()
            com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "restoreFromSave ${config.gridWidth}x${config.gridHeight} +${t3-t1}ms total=${t3-t0}ms")
            Triple(state, config, Pair(saveData.colorSortMode, saveData.colorSortReversed))
        } catch (e: Exception) {
            com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "repo.load异常: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun loadSaveData(saveId: String): SaveData? {
        return try {
            val file = File(savesDir, "$saveId.json")
            if (!file.exists()) return null
            val json = file.readText()
            gson.fromJson(json, SaveData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listSaves(): List<SaveData> {
        return savesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { try { gson.fromJson(it.readText(), SaveData::class.java) } catch (e: Exception) { null } }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun deleteSave(saveId: String) {
        File(savesDir, "$saveId.json").delete()
    }

    private fun restoreFromSave(data: SaveData): Pair<GameState, GameConfig> {
        val w = data.config.gridWidth
        val h = data.config.gridHeight
        val cells = Array(h) { y ->
            IntArray(w) { x ->
                val idx = y * w + x
                if (idx < data.cells.size) data.cells[idx] else 1
            }
        }
        val filled = Array(h) { BooleanArray(w) { false } }
        val fillOrder = Array(h) { IntArray(w) { 0 } }
        for (fc in data.filledCells) {
            if (fc.y in 0 until h && fc.x in 0 until w) {
                filled[fc.y][fc.x] = true
                fillOrder[fc.y][fc.x] = fc.order
            }
        }
        val canvas = PixelCanvas(w, h, cells, filled, fillOrder)
        val palColors = data.palette.map { PaletteColor(it.code, it.color, it.total, it.remaining) }
        // Fix old saves: if counts are zero, compute from canvas
        if (palColors.all { it.totalCount == 0 }) {
            val total = IntArray(palColors.size)
            val remaining = IntArray(palColors.size)
            for (y in 0 until h) for (x in 0 until w) {
                val code = cells[y][x]
                if (code in 1..palColors.size) { total[code-1]++; if (!filled[y][x]) remaining[code-1]++ }
            }
            val fixed = palColors.mapIndexed { i, c -> c.copy(totalCount = total[i], remainingCount = remaining[i]) }
            return Pair(GameState(canvas, ColorPalette(fixed), fixed.firstOrNull()?.code ?: 1, canvas.isCompleted(), canvas.progress, data.elapsedTimeMs), GameConfig(w, h, data.config.maxColors, data.config.sourceImagePath, true))
        }
        val palette = ColorPalette(palColors)
        val config = GameConfig(w, h, data.config.maxColors, data.config.sourceImagePath, true)
        val state = GameState(canvas, palette, palette.colors.firstOrNull()?.code ?: 1, canvas.isCompleted(), canvas.progress, data.elapsedTimeMs)
        return state to config
    }

    fun copySourceImage(sourceUri: String, saveId: String): String {
        val src = File(sourceUri.removePrefix("file://"))
        val dst = File(imagesDir, "${saveId}_original.png")
        if (src.exists() && !dst.exists()) src.copyTo(dst, overwrite = true)
        return dst.absolutePath
    }
}
