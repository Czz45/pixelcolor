package com.example.pixelcolor.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.pixelcolor.engine.PixelCanvas
import com.example.pixelcolor.ui.theme.FrostedGlassBox
import com.example.pixelcolor.ui.theme.LocalAppTheme

@Composable
fun PixelCanvasView(
    canvas: PixelCanvas,
    palette: Map<Int, Color>,
    selectedColorCode: Int,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    brushSize: Float = 1f,
    freePaintMode: Boolean = false,
    autoMode: Boolean = false,
    onCellClick: (Int, Int) -> Unit,
    onFreeClick: (Int, Int) -> Unit = { _, _ -> },
    onPaintLine: (List<Pair<Int, Int>>) -> Unit = {},
    onPaintFast: (List<Pair<Int, Int>>) -> Unit = {},
    onPaintCommit: () -> Unit = {},
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onColorSelected: (Int) -> Unit = {},
    onMiniMapDoubleTap: () -> Unit = {},
    jumpTarget: Pair<Int, Int>? = null,
    onJumpHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val w = canvas.width; val h = canvas.height
    val totalCells = w * h
    val canvasAspect = w.toFloat() / h.toFloat()
    val isDark = LocalAppTheme.current.isDark
    val unfilledColor = if (isDark) Color(0xFF1A1A1A) else Color.White
    val selectedHighlight = if (isDark) Color(0xFF333333) else Color(0xFFCCCCCC)

    var internalScale by remember { mutableFloatStateOf(scale) }
    var internalOffX by remember { mutableFloatStateOf(offsetX) }
    var internalOffY by remember { mutableFloatStateOf(offsetY) }
    var isDragging by remember { mutableStateOf(false) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // Sync external parameters when not dragging
    LaunchedEffect(scale, offsetX, offsetY) {
        if (!isDragging) {
            internalScale = scale
            internalOffX = offsetX
            internalOffY = offsetY
        }
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // Clamp offset so canvas doesn't leave the viewport
    fun clampOffset(offX: Float, offY: Float, scl: Float): Pair<Float, Float> {
        val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
        if (vw <= 0 || vh <= 0) return Pair(offX, offY)
        val dw: Float; val dh: Float
        if (vw / vh > canvasAspect) { dh = vh; dw = vh * canvasAspect } else { dw = vw; dh = vw / canvasAspect }
        val padX = (vw - dw) / 2f; val padY = (vh - dh) / 2f
        val canvasW = dw * scl; val canvasH = dh * scl
        // Keep at least some canvas visible
        val minX = -(padX + canvasW - 40f); val maxX = vw - padX - 40f
        val minY = -(padY + canvasH - 40f); val maxY = vh - padY - 40f
        return Pair(offX.coerceIn(minX, maxX), offY.coerceIn(minY, maxY))
    }

    // Use internal values directly for rendering — no animation delay
    val renderScale = internalScale
    val renderOffX = internalOffX
    val renderOffY = internalOffY

    // Throttle offset updates to reduce recomposition during drag
    var lastOffsetUpdateTime = remember { 0L }
    var pendingOffsetX by remember { mutableFloatStateOf(offsetX) }
    var pendingOffsetY by remember { mutableFloatStateOf(offsetY) }

    // Mini bitmap: async to avoid blocking main thread on large canvases
    var miniBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fullBmp by remember { mutableStateOf<Bitmap?>(null) }
    var fullPxPerCell by remember { mutableStateOf(1) }
    // 自增以触发 Canvas 重绘：增量 patch 完位图后让 onDraw 重新执行，涂格立即可见
    var bitmapEpoch by remember { mutableStateOf(0) }
    val useBitmap = totalCells > 5000
    // 待 patch 的涂格缓冲（普通 List，故意不做 Compose state —— 否则每涂一格触发一次重组本身就会卡）
    val pendingPaint = remember { mutableListOf<Pair<Int, Int>>() }
    // canvas/palette 每次涂绘可能是新实例；patch 循环用 LaunchedEffect(Unit) 只挂载一次，
    // 必须用 rememberUpdatedState 才能每帧拿到最新的 canvas/palette，否则会 patch 到旧数据。
    val canvasRef = rememberUpdatedState(canvas)
    val paletteRef = rememberUpdatedState(palette)
    val selectedColorCodeRef = rememberUpdatedState(selectedColorCode)

    // 结构/首次位图生成（绝不逐格触发）：涂格的即时反馈由下方增量 patch 循环处理，
    // 因此再也不会因为涂一笔就全画布重扫 + 4MB 重分配 → 涂抹延迟/抖动的根源已消除。
    LaunchedEffect(isDark, selectedColorCode) {
        // Wait for first frame to render before allocating any bitmaps
        delay(50)
        withContext(Dispatchers.IO) {
            val runtime = Runtime.getRuntime()
            val freeMemMB = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
            com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "开始bitmap生成 freeMem=${freeMemMB}MB totalCells=$totalCells")

            // 1. Generate mini bitmap first (small, quick)
            // 关键修复：把原生 Bitmap 长边限制在安全上限内，避免大画布触发系统强杀进程
            // （系统显存上限与 JVM 堆无关，超限会直接 kill 进程且不抛异常 → 不进闪退日志）
            val t0 = android.os.SystemClock.elapsedRealtime()
            try {
                val longSide = maxOf(w, h)
                val miniScale = if (longSide <= MAX_BITMAP_DIM) {
                    if (totalCells >= 40000) 1f else 2f
                } else {
                    MAX_BITMAP_DIM.toFloat() / longSide
                }
                val miniMw = maxOf(2, (w * miniScale).toInt())
                val miniMh = maxOf(2, (h * miniScale).toInt())
                val newMini = generateMiniBitmap(canvasRef.value, paletteRef.value, miniMw, miniMh, w, h, isDark)
                miniBitmap = newMini
                com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "miniBitmap ${newMini.width}x${newMini.height} +${android.os.SystemClock.elapsedRealtime()-t0}ms")
            } catch (t: Throwable) {
                // 任何异常都记入闪退日志（原先只抓 OutOfMemoryError，其它异常/系统强杀会被漏掉）
                com.example.pixelcolor.PixelColorApp.logCrash(t)
                System.gc()
            }

            // 2. GC and wait before full bitmap
            System.gc()
            delay(100)

            // 3. Generate full bitmap (large, needs more memory)
            if (useBitmap) {
                val t1 = android.os.SystemClock.elapsedRealtime()
                val freshMem = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
                val m = minOf(512f, maxOf(w, h) * 4f)
                val pxPerCell = (m / maxOf(w, h)).toInt().coerceAtLeast(1)
                val estimatedMB = w * pxPerCell.toLong() * h * pxPerCell * 4 / (1024 * 1024)
                com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "fullBitmap估算 ${estimatedMB}MB free=${freshMem}MB pxPerCell=$pxPerCell")
                val fullLong = maxOf(w * pxPerCell, h * pxPerCell)
                if (estimatedMB < freshMem / 3 && fullLong <= MAX_BITMAP_DIM) {
                    try {
                        val newFull = generateFullBitmap(canvasRef.value, paletteRef.value, selectedColorCode, pxPerCell, w, h, isDark)
                        fullBmp = newFull
                        fullPxPerCell = pxPerCell
                        com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "fullBitmap ${newFull.width}x${newFull.height} +${android.os.SystemClock.elapsedRealtime()-t1}ms")
                    } catch (t: Throwable) {
                        // 任何异常都记入闪退日志，并回退到逐格绘制
                        com.example.pixelcolor.PixelColorApp.logCrash(t)
                        fullBmp = null
                        System.gc()
                    }
                } else {
                    com.example.pixelcolor.PixelColorApp.logEntry("GameEntry", "内存不足跳过fullBitmap")
                }
            }
        }
    }

    // 增量 patch 循环：把「涂的格子」直接写进现有 Bitmap（主线程、几十格/次、零全扫），
    // 让涂抹在 ~1 帧内可见，彻底摆脱「每格全量重算」带来的延迟。
    LaunchedEffect(Unit) {
        while (true) {
            delay(32)
            if (pendingPaint.isNotEmpty()) {
                val cells = pendingPaint.toList()
                pendingPaint.clear()
                val cv = canvasRef.value
                val pal = paletteRef.value
                val curMini = miniBitmap
                if (curMini != null) {
                    for ((cx, cy) in cells) patchMini(curMini, cx, cy, w, h, cv, pal, selectedColorCodeRef.value, isDark)
                }
                val curFull = fullBmp
                val pc = fullPxPerCell
                if (curFull != null) {
                    for ((cx, cy) in cells) patchFull(curFull, cx, cy, w, h, pc, cv, pal, selectedColorCodeRef.value, isDark)
                }
                bitmapEpoch++
            }
        }
    }

    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(viewSize) { if (viewSize.width > 0 && !ready) { delay(16); ready = true } }
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    // Mini-map double-tap overview state
    var miniIsOverview by remember { mutableStateOf(false) }

    /** Compute bitmap draw size that fits within viewSize preserving aspect ratio */
    fun drawArea(): Pair<Float, Float> {
        val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
        if (vw <= 0 || vh <= 0) return Pair(0f, 0f)
        return if (vw / vh > canvasAspect) Pair(vh * canvasAspect, vh)
        else Pair(vw, vw / canvasAspect)
    }

    // Jump to target cell with smooth animation
    LaunchedEffect(jumpTarget) {
        val (cx, cy) = jumpTarget ?: return@LaunchedEffect
        val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
        if (vw <= 0 || vh <= 0) return@LaunchedEffect
        val (drawW, drawH) = drawArea()
        if (drawW <= 0 || drawH <= 0) return@LaunchedEffect
        val padX = (vw - drawW) / 2f; val padY = (vh - drawH) / 2f
        internalScale = maxOf(internalScale, 4f)
        val rawOffX = vw / 2f - (cx + 0.5f) / w * drawW * internalScale - padX
        val rawOffY = vh / 2f - (cy + 0.5f) / h * drawH * internalScale - padY
        val (jcx, jcy) = clampOffset(rawOffX, rawOffY, internalScale)
        // Smooth animation to target
        val anim = androidx.compose.animation.core.Animatable(internalOffX)
        val animY = androidx.compose.animation.core.Animatable(internalOffY)
        kotlinx.coroutines.coroutineScope {
            launch {
                anim.animateTo(jcx, androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) {
                    internalOffX = value
                }
            }
            launch {
                animY.animateTo(jcy, androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) {
                    internalOffY = value
                }
            }
        }
        onScaleChange(internalScale)
        onOffsetChange(internalOffX, internalOffY)
        onJumpHandled()
    }

    // Auto mode: smooth scroll to arrow target when no visible targets
    LaunchedEffect(autoMode, selectedColorCode) {
        if (!autoMode) return@LaunchedEffect
        while (autoMode) {
            kotlinx.coroutines.delay(500)
            if (isUserInteracting) continue // skip while user is touching
            val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
            if (vw <= 0 || vh <= 0) continue
            val (drawW, drawH) = drawArea()
            if (drawW <= 0 || drawH <= 0) continue
            val padX = (vw - drawW) / 2f; val padY = (vh - drawH) / 2f
            // 自动模式：仅屏幕中央正方形内不追踪；目标一旦移出中央正方形（即使在屏幕内）即平滑追踪
            val deadHalf = minOf(vw, vh) * 0.25f
            val cx0 = vw / 2f - deadHalf; val cx1 = vw / 2f + deadHalf
            val cy0 = vh / 2f - deadHalf; val cy1 = vh / 2f + deadHalf
            // 1) 中央正方形内是否存在可涂目标（精确扫描该区域，作为"不追踪"死区）
            val cellL = ((cx0 - padX - internalOffX) / (drawW * internalScale / w)).toInt().coerceIn(0, w - 1)
            val cellR = ((cx1 - padX - internalOffX) / (drawW * internalScale / w)).toInt().coerceIn(0, w - 1)
            val cellT = ((cy0 - padY - internalOffY) / (drawH * internalScale / h)).toInt().coerceIn(0, h - 1)
            val cellB = ((cy1 - padY - internalOffY) / (drawH * internalScale / h)).toInt().coerceIn(0, h - 1)
            var hasVisible = false
            for (y in cellT..cellB) for (x in cellL..cellR) {
                if (!canvas.isFilled(x, y) && canvas.getCell(x, y) == selectedColorCode) {
                    hasVisible = true; break
                }
                if (hasVisible) break
            }
            if (hasVisible) continue
            // 2) 中央正方形外（即使在屏幕内）找离中心最近的目标并追踪
            val centerX = vw / 2f; val centerY = vh / 2f
            var bestDist = Float.MAX_VALUE; var bestX = -1; var bestY = -1
            val totalCells = w.toLong() * h
            val step = if (totalCells > 20000L) maxOf(1, kotlin.math.sqrt(totalCells / 20000.0).toInt()) else 1
            for (y in 0 until h step step) for (x in 0 until w step step) {
                if (!canvas.isFilled(x, y) && canvas.getCell(x, y) == selectedColorCode) {
                    val sx = padX + (x + 0.5f) / w * drawW * internalScale + internalOffX
                    val sy = padY + (y + 0.5f) / h * drawH * internalScale + internalOffY
                    if (sx in cx0..cx1 && sy in cy0..cy1) continue
                    val dx2 = sx - centerX; val dy2 = sy - centerY
                    val d = dx2 * dx2 + dy2 * dy2
                    if (d < bestDist) { bestDist = d; bestX = x; bestY = y }
                }
            }
            if (bestX < 0) continue
            // Smooth scroll to target (slow-fast-slow)
            val rawOffX = vw / 2f - (bestX + 0.5f) / w * drawW * internalScale - padX
            val rawOffY = vh / 2f - (bestY + 0.5f) / h * drawH * internalScale - padY
            val (targetOffX, targetOffY) = clampOffset(rawOffX, rawOffY, internalScale)
            val startOffX = internalOffX; val startOffY = internalOffY
            val steps = 35
            for (i in 1..steps) {
                if (!autoMode) break
                val t = i.toFloat() / steps
                // slow-fast-slow: ease in-out cubic
                val eased = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
                internalOffX = startOffX + (targetOffX - startOffX) * eased
                internalOffY = startOffY + (targetOffY - startOffY) * eased
                kotlinx.coroutines.delay(16)
            }
            onOffsetChange(internalOffX, internalOffY)
        }
    }

    fun toCell(px: Float, py: Float): Pair<Int, Int> {
        val (drawW, drawH) = drawArea()
        if (drawW <= 0 || drawH <= 0) return Pair(-1, -1)
        val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
        val padX = (vw - drawW) / 2f; val padY = (vh - drawH) / 2f
        val bx = ((px - padX - internalOffX) / internalScale) / drawW * w
        val by = ((py - padY - internalOffY) / internalScale) / drawH * h
        return Pair(bx.toInt(), by.toInt())
    }

    // Viewport bitmap cache for large canvases (avoids 100K+ drawRect calls)
    var lastTapTime = 0L; var lastTapCell = Pair(-1, -1)

    // Arrow pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "arrow")
    val arrowPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "arrowPulse"
    )

    // 重绘依赖读取：bitmapEpoch 变化时触发本组合体重组，使下方 Canvas.onDraw 重新执行，
    // 反映增量 patch 后的最新位图（onDraw 内读状态不会触发重组，必须在组合体里读）。
    val redrawTrigger = bitmapEpoch
    if (redrawTrigger < 0) { /* 仅用于建立重组依赖，永不触发 */ }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(brushSize) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isUserInteracting = true
                        val start = down.position; val startTime = System.currentTimeMillis()
                        var mode = 0; var prev = start
                        var wasMultiTouch = false; var paintPrev = start

                        do {
                            val event = awaitPointerEvent()
                            val active = event.changes.filter { it.pressed }
                            if (active.isEmpty()) {
                                // Finger lifted — sync state to ViewModel now
                                when (mode) {
                                    0 -> {
                                        val (cx, cy) = toCell(start.x, start.y)
                                        if (cx in 0 until w && cy in 0 until h) {
                                            if (freePaintMode) {
                                                onFreeClick(cx, cy)
                                            } else {
                                                val now = System.currentTimeMillis()
                                                val sameCell = cx == lastTapCell.first && cy == lastTapCell.second
                                                if (sameCell && now - lastTapTime < 400) {
                                                    onColorSelected(canvas.getCell(cx, cy))
                                                    lastTapTime = 0L
                                                } else {
                                                    // Apply brush size
                                                    val size = brushSize.toInt()
                                                    val half = size / 2
                                                    for (dy in -half..half) {
                                                        for (dx in -half..half) {
                                                            val bx = cx + dx; val by = cy + dy
                                                            if (bx in 0 until w && by in 0 until h) {
                                                                onCellClick(bx, by)
                                                                pendingPaint.add(bx to by)
                                                            }
                                                        }
                                                    }
                                                    lastTapTime = now; lastTapCell = Pair(cx, cy)
                                                }
                                            }
                                        }
                                    }
                                    1 -> { onPaintCommit() } // 拖拽涂色结束，统一提交一次状态（避免每帧 emit 导致全屏重组延迟）
                                    2 -> {
                                        // Drag ended — sync offset once
                                        onOffsetChange(internalOffX, internalOffY)
                                    }
                                }
                                isDragging = false
                                isUserInteracting = false
                                break
                            }
                            if (active.size >= 2) {
                                isDragging = true
                                val z = event.calculateZoom(); val p = event.calculatePan()
                                val centroid = active.map { it.position }.let { pts ->
                                    Offset(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size,
                                           pts.sumOf { it.y.toDouble() }.toFloat() / pts.size)
                                }
                                val newScale = (internalScale * z).coerceIn(0.3f, 64f)
                                val (dw, dh) = drawArea()
                                val vx = viewSize.width.toFloat(); val vy = viewSize.height.toFloat()
                                val px = (vx - dw) / 2f; val py = (vy - dh) / 2f
                                val ratio = newScale / internalScale
                                val newOffX = centroid.x - px - (centroid.x - px - internalOffX) * ratio + p.x
                                val newOffY = centroid.y - py - (centroid.y - py - internalOffY) * ratio + p.y
                                val (cx, cy) = clampOffset(newOffX, newOffY, newScale)
                                internalOffX = cx; internalOffY = cy; internalScale = newScale
                                // Sync during pinch (multi-touch is slower, safe to update)
                                onScaleChange(internalScale); onOffsetChange(internalOffX, internalOffY)
                                mode = 0; wasMultiTouch = true
                                active.forEach { it.consume() }
                            } else {
                                val pos = active.first().position
                                if (wasMultiTouch) { prev = pos; paintPrev = pos; wasMultiTouch = false; active.first().consume(); continue }
                                val perFrameDx = pos.x - prev.x; val perFrameDy = pos.y - prev.y
                                val totalDx = pos.x - start.x; val totalDy = pos.y - start.y
                                val isDrag = abs(totalDx) > touchSlop || abs(totalDy) > touchSlop
                                when (mode) {
                                    0 -> {
                                        if (isDrag) {
                                            mode = 2; isDragging = true
                                            val (cx, cy) = clampOffset(internalOffX + perFrameDx, internalOffY + perFrameDy, internalScale)
                                            internalOffX = cx; internalOffY = cy
                                        } else if (System.currentTimeMillis() - startTime >= 180L) {
                                            mode = 1
                                            val (cx, cy) = toCell(pos.x, pos.y)
                                            // Apply brush size on first paint point
                                            val size = brushSize.toInt()
                                            val half = size / 2
                                            for (bdy in -half..half) {
                                                for (bdx in -half..half) {
                                                    val bx = cx + bdx; val by = cy + bdy
                                                    if (bx in 0 until w && by in 0 until h) {
                                                        onCellClick(bx, by)
                                                        pendingPaint.add(bx to by)
                                                    }
                                                }
                                            }
                                            paintPrev = pos
                                        }
                                    }
                                    1 -> {
                                        val (cx0, cy0) = toCell(paintPrev.x, paintPrev.y)
                                        val (cx1, cy1) = toCell(pos.x, pos.y)
                                        if (cx0 != cx1 || cy0 != cy1) {
                                            val size = brushSize.toInt()
                                            val half = size / 2
                                            val line = mutableListOf<Pair<Int, Int>>()
                                            var x = cx0; var y = cy0
                                            val dx = abs(cx1 - cx0); val dy = -(cy1 - cy0).let { if (it < 0) -it else it }
                                            val sx = if (cx0 < cx1) 1 else -1; val sy = if (cy0 < cy1) 1 else -1
                                            var err = dx + dy
                                            while (true) {
                                                // Apply brush size to each point on the line
                                                for (bdy in -half..half) {
                                                    for (bdx in -half..half) {
                                                        val bx = x + bdx; val by = y + bdy
                                                        if (bx in 0 until w && by in 0 until h) {
                                                            line.add(Pair(bx, by))
                                                        }
                                                    }
                                                }
                                                if (x == cx1 && y == cy1) break
                                                val e2 = 2 * err
                                                if (e2 >= dy) { err += dy; x += sx }
                                                if (e2 <= dx) { err += dx; y += sy }
                                            }
                                            if (line.isNotEmpty()) { onPaintFast(line); pendingPaint.addAll(line) }
                                            paintPrev = pos
                                        }
                                    }
                                    2 -> {
                                        if (isDrag) {
                                            val (cx, cy) = clampOffset(internalOffX + perFrameDx, internalOffY + perFrameDy, internalScale)
                                            internalOffX = cx; internalOffY = cy
                                            // NO onOffsetChange during drag — sync on finger lift
                                        }
                                    }
                                }
                                if (isDrag) prev = pos
                                active.first().consume()
                            }
                        } while (true)
                    }
                }
        ) {
            val (drawW, drawH) = drawArea()
            if (drawW <= 0 || drawH <= 0) return@Canvas
            val padX = (size.width - drawW) / 2f; val padY = (size.height - drawH) / 2f
            val cellW = drawW / w; val cellH = drawH / h

            val invScale = 1f / renderScale
            val contentLeft   = -(padX + renderOffX) * invScale
            val contentRight  = (size.width - padX - renderOffX) * invScale
            val contentTop    = -(padY + renderOffY) * invScale
            val contentBottom = (size.height - padY - renderOffY) * invScale
            val visX0 = ((contentLeft / cellW).toInt() - 1).coerceIn(0, w - 1)
            val visX1 = ((contentRight / cellW).toInt() + 1).coerceIn(0, w - 1)
            val visY0 = ((contentTop / cellH).toInt() - 1).coerceIn(0, h - 1)
            val visY1 = ((contentBottom / cellH).toInt() + 1).coerceIn(0, h - 1)
            withTransform({
                translate(padX + renderOffX, padY + renderOffY)
                scale(renderScale, renderScale, Offset.Zero)
            }) {
                if (!ready) return@withTransform
                // Use bitmap rendering when cells are too small or too many
                val screenCellW = cellW * renderScale
                val screenCellH = cellH * renderScale
                val visibleCells = (visX1 - visX0 + 1) * (visY1 - visY0 + 1)
                // 渲染策略（关键：主线程严禁在大画布上逐格 drawRect，否则 DisplayList 指令过多 → 主线程卡死 → ANR）
                // - 位图就绪（大画布优先 fullBmp，退化用 miniBitmap）：主线程只做 1 条 drawImage 指令
                // - 小画布（totalCells<=5000，不生成位图）：逐格渲染可见范围，格数≤5000，主线程安全
                // - 大画布但需要位图却尚未就绪：先画纯背景占位（1 条指令），等协程把位图生成好后下一帧自动切 drawImage
                val bmp = if (useBitmap && (fullBmp != null || miniBitmap != null)) (fullBmp ?: miniBitmap) else null
                when {
                    bmp != null -> {
                        // 让位图严格对齐网格：位图像素 (px,py) → 局部坐标 (px*cellW*w/bw, py*cellH*h/bh)，
                        // 与网格线 x*cellW 共用同一坐标基准，消除 drawImage 用 roundToInt 拉伸整数尺寸时
                        // 引入的比例误差——高倍率下色块/数字/网格线错位（1024 画布放大时尤其明显）。
                        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
                        withTransform({ scale(cellW * w / bw, cellH * h / bh, Offset.Zero) }) {
                            drawImage(bmp.asImageBitmap(), dstSize = IntSize(bmp.width, bmp.height), filterQuality = FilterQuality.None)
                        }
                    }
                    !useBitmap -> {
                        // 小画布：直接逐格绘制可见范围（循环次数有上限，主线程安全）
                        for (y in visY0..visY1) {
                            for (x in visX0..visX1) {
                                val filled = canvas.isFilled(x, y); val code = canvas.getCell(x, y)
                                val bg = when {
                                    canvas.isTransparent(x, y) -> Color.Transparent
                                    filled -> palette[code] ?: Color.Gray
                                    code == selectedColorCode -> selectedHighlight
                                    else -> unfilledColor
                                }
                                drawRect(bg, Offset(x * cellW, y * cellH), Size(cellW, cellH))
                            }
                        }
                    }
                    else -> {
                        // 大画布但需要位图却尚未就绪：绝不可逐格 drawRect 全画布（会卡死主线程 → ANR）。
                        // 先用纯背景占位，位图就绪后下一帧自动切换到 drawImage。
                        drawRect(if (isDark) Color(0xFF1A1A1A) else unfilledColor, Offset.Zero, Size(drawW, drawH))
                    }
                }

                // Grid lines — drawn whenever cells are large enough to need guidance
                // (screenCell > 16f), INCLUDING during zoom/pinch/pan/paint, so lines and the
                // per-cell numbers appear together while interacting (user request). Per-cell
                // drawLine is cheap, so drawing during a gesture does not reintroduce the old
                // mid-zoom stutter — that came from per-cell drawText, which stays bounded by the
                // existing visible-cell cap in the number block below. Fully-transparent areas show
                // NO grid lines; boundaries to painted cells stay visible.
                if (minOf(screenCellW, screenCellH) > 16f) {
                    val gridAlpha = ((minOf(screenCellW, screenCellH) - 8f) / 40f).coerceIn(0.05f, 0.3f)
                    val gridColor = if (isDark) Color.White.copy(alpha = gridAlpha) else Color.Black.copy(alpha = gridAlpha)
                    val lw = 1f / renderScale
                    for (x in visX0..visX1 + 1) {
                        for (y in visY0..visY1) {
                            val leftTrans = if (x - 1 >= 0) canvas.isTransparent(x - 1, y) else true
                            val rightTrans = if (x < w) canvas.isTransparent(x, y) else true
                            if (!(leftTrans && rightTrans)) {
                                drawLine(gridColor, Offset(x * cellW, y * cellH), Offset(x * cellW, (y + 1) * cellH), strokeWidth = lw)
                            }
                        }
                    }
                    for (y in visY0..visY1 + 1) {
                        for (x in visX0..visX1) {
                            val topTrans = if (y - 1 >= 0) canvas.isTransparent(x, y - 1) else true
                            val botTrans = if (y < h) canvas.isTransparent(x, y) else true
                            if (!(topTrans && botTrans)) {
                                drawLine(gridColor, Offset(x * cellW, y * cellH), Offset((x + 1) * cellW, y * cellH), strokeWidth = lw)
                            }
                        }
                    }
                }

            } // end withTransform

            // Text drawn OUTSIDE the scale transform — screen coordinates
            val screenCellW2 = drawW * renderScale / w
            val screenCellH2 = drawH * renderScale / h
            val visibleCells2 = (visX1 - visX0 + 1) * (visY1 - visY0 + 1)
            // Skip text when cells are tiny or too many visible
            if (minOf(screenCellW2, screenCellH2) > 16f && visibleCells2 < 1500) {
                val textColor = if (isDark) Color.White else Color.Black
                val textStyle = TextStyle(fontSize = (minOf(screenCellW2, screenCellH2) * 0.35f / density).sp, color = textColor)
                for (y in visY0..visY1) {
                    for (x in visX0..visX1) {
                        if (!canvas.isFilled(x, y) && canvas.getCell(x, y) != 0) {
                            val sx = padX + x * drawW / w * renderScale + renderOffX
                            val sy = padY + y * drawH / h * renderScale + renderOffY
                            val text = "${canvas.getCell(x, y)}"
                            val layout = textMeasurer.measure(text, textStyle)
                            drawText(layout, topLeft = Offset(
                                sx + (screenCellW2 - layout.size.width) / 2f,
                                sy + (screenCellH2 - layout.size.height) / 2f))
                        }
                    }
                }
            }

            // Direction arrow — navigation style
            run arrow@{
                val (dw4, dh4) = drawArea()
                if (dw4 <= 0 || dh4 <= 0) return@arrow
                val pX4 = (size.width - dw4) / 2f
                val pY4 = (size.height - dh4) / 2f
                val marginX = dw4 * 0.2f; val marginY = dh4 * 0.2f
                val cellLeft = (((-renderOffX - pX4 + marginX) / (dw4 * renderScale / w))).toInt().coerceIn(0, w - 1)
                val cellRight = (((size.width - pX4 - marginX - renderOffX) / (dw4 * renderScale / w))).toInt().coerceIn(0, w - 1)
                val cellTop = (((-renderOffY - pY4 + marginY) / (dh4 * renderScale / h))).toInt().coerceIn(0, h - 1)
                val cellBottom = (((size.height - pY4 - marginY - renderOffY) / (dh4 * renderScale / h))).toInt().coerceIn(0, h - 1)

                var hasVisibleTarget = false
                for (y in cellTop..cellBottom) for (x in cellLeft..cellRight) {
                    if (!canvas.isFilled(x, y) && canvas.getCell(x, y) == selectedColorCode) {
                        hasVisibleTarget = true; break
                    }
                }
                if (hasVisibleTarget) return@arrow

                val centerX = size.width / 2f; val centerY = size.height / 2f
                var bestAngle = 0f; var bestDist = Float.MAX_VALUE; var foundArrow = false
                // 全画布搜索最近的未完成目标格；对超大画布用步长采样，避免每帧 O(w*h) 卡死主线程
                val totalCells = w.toLong() * h
                val step = if (totalCells > 20000L) maxOf(1, kotlin.math.sqrt(totalCells / 20000.0).toInt()) else 1
                for (y in 0 until h step step) for (x in 0 until w step step) {
                    if (!canvas.isFilled(x, y) && canvas.getCell(x, y) == selectedColorCode) {
                        val sx = pX4 + (x + 0.5f) / w * dw4 * renderScale + renderOffX
                        val sy = pY4 + (y + 0.5f) / h * dh4 * renderScale + renderOffY
                        if (sx > 0 && sx < size.width && sy > 0 && sy < size.height) continue
                        val dx2 = sx - centerX; val dy2 = sy - centerY
                        val d = kotlin.math.sqrt(dx2 * dx2 + dy2 * dy2)
                        if (d < bestDist) { bestDist = d; bestAngle = kotlin.math.atan2(dy2, dx2); foundArrow = true }
                    }
                }
                if (!foundArrow) return@arrow

                val radius = minOf(centerX, centerY) - 30f
                val tipX = centerX + kotlin.math.cos(bestAngle) * radius
                val tipY = centerY + kotlin.math.sin(bestAngle) * radius
                val len = 42f; val halfW = 14f
                val bx = tipX - kotlin.math.cos(bestAngle) * len
                val by = tipY - kotlin.math.sin(bestAngle) * len
                val perpX = -kotlin.math.sin(bestAngle)
                val perpY = kotlin.math.cos(bestAngle)
                val l = Offset(bx + perpX * halfW, by + perpY * halfW)
                val r = Offset(bx - perpX * halfW, by - perpY * halfW)
                val tip = Offset(tipX, tipY)

                // Glow circle
                drawCircle(Color.Black.copy(alpha = 0.25f * arrowPulse), 28f * arrowPulse, Offset(tipX + 3f, tipY + 3f))
                drawCircle(Color.Red.copy(alpha = 0.3f * arrowPulse), 24f * arrowPulse, tip)
                // Arrow body
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(r.x, r.y); close() },
                    color = Color.Red.copy(alpha = arrowPulse))
            }
        }

        miniBitmap?.asImageBitmap()?.let { mini ->
            var miniLastTapTime by remember { mutableLongStateOf(0L) }
            FrostedGlassBox(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (miniIsOverview) 2.dp else 1.dp,
                        color = if (miniIsOverview) Color.Red.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .pointerInput("miniTap") {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tapTime = System.currentTimeMillis()
                            val isDoubleTap = tapTime - miniLastTapTime < 300
                            miniLastTapTime = tapTime

                            if (isDoubleTap) {
                                // Toggle overview mode — only affects mini-map, not main canvas
                                miniIsOverview = !miniIsOverview
                                down.consume()
                            }
                            // Single tap / drag — NOT consumed, passes through to canvas
                        }
                    },
                tintColor = Color.Black,
                blurRadius = 10.dp,
                alpha = 0.5f
            ) {
                // Animated mini-map scale
                // Normal: 0.5-2x; near max zoom: 4x for clarity
                val isNearMaxZoom = renderScale > 50f
                val targetMiniScale = if (isNearMaxZoom) {
                    4f
                } else {
                    (renderScale / 4f + 0.5f).coerceIn(0.5f, 2f)
                }
                val miniScale by animateFloatAsState(
                    targetValue = targetMiniScale,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "miniScale"
                )
                val useZoomedMini = if (miniIsOverview) false else miniScale > 1.2f

                // Draw mini-map image
                if (useZoomedMini) {
                    // Show viewport-centered region
                    val (dw3, dh3) = drawArea()
                    val pX3 = (viewSize.width.toFloat() - dw3) / 2f
                    val pY3 = (viewSize.height.toFloat() - dh3) / 2f
                    val centerGx = ((-internalOffX - pX3 + dw3 / 2f) / (dw3 * internalScale / w)).coerceIn(0f, w.toFloat())
                    val centerGy = ((-internalOffY - pY3 + dh3 / 2f) / (dh3 * internalScale / h)).coerceIn(0f, h.toFloat())
                    val visibleW = w / miniScale
                    val visibleH = h / miniScale
                    val cropX0 = (centerGx - visibleW / 2f).coerceIn(0f, (w - visibleW).coerceAtLeast(0f))
                    val cropY0 = (centerGy - visibleH / 2f).coerceIn(0f, (h - visibleH).coerceAtLeast(0f))
                    // Draw cropped region
                    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                        val s = size.width.coerceAtMost(size.height)
                        val canvasAspect = w.toFloat() / h.toFloat()
                        val imgW: Float; val imgH: Float
                        if (s / s > canvasAspect) { imgH = s; imgW = s * canvasAspect } else { imgW = s; imgH = s / canvasAspect }
                        val imgOffX = (size.width - imgW) / 2f
                        val imgOffY = (size.height - imgH) / 2f
                        drawImage(
                            mini,
                            srcOffset = IntOffset((cropX0 / w * mini.width).toInt(), (cropY0 / h * mini.height).toInt()),
                            srcSize = IntSize((visibleW / w * mini.width).toInt().coerceAtLeast(1), (visibleH / h * mini.height).toInt().coerceAtLeast(1)),
                            dstOffset = IntOffset(imgOffX.toInt(), imgOffY.toInt()),
                            dstSize = IntSize(imgW.toInt(), imgH.toInt()),
                            filterQuality = FilterQuality.None
                        )
                    }
                } else {
                    Image(
                        bitmap = mini, contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None
                    )
                }
                // Viewport rect overlay
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    val s = size.width.coerceAtMost(size.height)
                    val canvasAspect = w.toFloat() / h.toFloat()
                    val imgW: Float; val imgH: Float
                    if (s / s > canvasAspect) { imgH = s; imgW = s * canvasAspect } else { imgW = s; imgH = s / canvasAspect }
                    val imgOffX = (size.width - imgW) / 2f
                    val imgOffY = (size.height - imgH) / 2f

                    val (dw3, dh3) = drawArea()
                    if (dw3 > 0 && dh3 > 0) {
                        val pX3 = (viewSize.width.toFloat() - dw3) / 2f
                        val pY3 = (viewSize.height.toFloat() - dh3) / 2f
                        // Viewport position in grid coordinates
                        val gx0 = ((-internalOffX - pX3) / (dw3 * internalScale / w)).coerceIn(0f, w.toFloat())
                        val gy0 = ((-internalOffY - pY3) / (dh3 * internalScale / h)).coerceIn(0f, h.toFloat())
                        val gx1 = (((viewSize.width.toFloat() - pX3) - internalOffX) / (dw3 * internalScale / w)).coerceIn(0f, w.toFloat())
                        val gy1 = (((viewSize.height.toFloat() - pY3) - internalOffY) / (dh3 * internalScale / h)).coerceIn(0f, h.toFloat())

                        if (useZoomedMini) {
                            // When zoomed, mini-map shows cropped region — map rect to cropped coords
                            val centerGx = ((-internalOffX - pX3 + dw3 / 2f) / (dw3 * internalScale / w)).coerceIn(0f, w.toFloat())
                            val centerGy = ((-internalOffY - pY3 + dh3 / 2f) / (dh3 * internalScale / h)).coerceIn(0f, h.toFloat())
                            val visibleW = w / miniScale
                            val visibleH = h / miniScale
                            val cropX0 = (centerGx - visibleW / 2f).coerceIn(0f, (w - visibleW).coerceAtLeast(0f))
                            val cropY0 = (centerGy - visibleH / 2f).coerceIn(0f, (h - visibleH).coerceAtLeast(0f))
                            // Map viewport to cropped coordinates (0..1 within cropped area)
                            val relX0 = ((gx0 - cropX0) / visibleW).coerceIn(0f, 1f)
                            val relY0 = ((gy0 - cropY0) / visibleH).coerceIn(0f, 1f)
                            val relX1 = ((gx1 - cropX0) / visibleW).coerceIn(0f, 1f)
                            val relY1 = ((gy1 - cropY0) / visibleH).coerceIn(0f, 1f)
                            val rx0 = imgOffX + relX0 * imgW
                            val ry0 = imgOffY + relY0 * imgH
                            val rx1 = imgOffX + relX1 * imgW
                            val ry1 = imgOffY + relY1 * imgH
                            val rectW = (rx1 - rx0).coerceAtLeast(3f)
                            val rectH = (ry1 - ry0).coerceAtLeast(3f)
                            drawRect(Color.Red.copy(alpha = 0.9f), Offset(rx0, ry0), Size(rectW, rectH), style = Stroke(width = 2f))
                        } else {
                            // Full mini-map — direct mapping
                            val rx0 = imgOffX + (gx0 / w) * imgW
                            val ry0 = imgOffY + (gy0 / h) * imgH
                            val rx1 = imgOffX + (gx1 / w) * imgW
                            val ry1 = imgOffY + (gy1 / h) * imgH
                            val rectW = (rx1 - rx0).coerceAtLeast(3f)
                            val rectH = (ry1 - ry0).coerceAtLeast(3f)
                            drawRect(Color.Red.copy(alpha = 0.9f), Offset(rx0, ry0), Size(rectW, rectH), style = Stroke(width = 2f))
                        }
                    }
                }
            }
        }
    }
}

private fun generateViewportBmp(canvas: PixelCanvas, palette: Map<Int, Color>, selectedColorCode: Int, x0: Int, y0: Int, vw: Int, vh: Int, pxPerCell: Int, isDark: Boolean = false): Bitmap {
    val unfilledArgb = if (isDark) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
    val selectedArgb = if (isDark) 0xFF333333.toInt() else 0xFFCCCCCC.toInt()
    val bw = vw * pxPerCell; val bh = vh * pxPerCell; val pxs = IntArray(bw * bh)
    for (vy in 0 until vh) { val cy = y0 + vy; val by = vy * pxPerCell
        for (vx in 0 until vw) { val cx = x0 + vx
            val filled = canvas.isFilled(cx, cy); val code = canvas.getCell(cx, cy)
            val argb = when { filled -> { val c = palette[code] ?: Color.Gray; android.graphics.Color.argb((c.alpha*255).toInt(), (c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt()) }; code == selectedColorCode -> selectedArgb; else -> unfilledArgb }
            val bx = vx * pxPerCell; for (dy in 0 until pxPerCell) { val r = (by + dy) * bw; var col = bx; repeat(pxPerCell) { pxs[r + col] = argb; col++ } }
        }
    }
    val im = Bitmap.createBitmap(pxs, bw, bh, Bitmap.Config.ARGB_8888); val bm = im.copy(Bitmap.Config.ARGB_8888, true); im.recycle()
    val g = AndroidCanvas(bm); val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val p = Paint().apply { color = textColor; textSize = pxPerCell * 0.4f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    for (vy in 0 until vh) { val cy = y0 + vy; for (vx in 0 until vw) { val cx = x0 + vx; if (!canvas.isFilled(cx, cy)) g.drawText("${canvas.getCell(cx, cy)}", vx * pxPerCell + pxPerCell / 2f, vy * pxPerCell + pxPerCell / 2f + pxPerCell * 0.12f, p) } }
    return bm
}

private fun generateFullBitmap(canvas: PixelCanvas, palette: Map<Int, Color>, selectedColorCode: Int, pxPerCell: Int, w: Int, h: Int, isDark: Boolean = false): Bitmap {
    val unfilledArgb = if (isDark) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
    val selectedArgb = if (isDark) 0xFF333333.toInt() else 0xFFCCCCCC.toInt()
    val bw = w * pxPerCell; val bh = h * pxPerCell
    val pxs = IntArray(bw * bh)
    for (y in 0 until h) { val by = y * pxPerCell
        for (x in 0 until w) {
            val filled = canvas.isFilled(x, y); val code = canvas.getCell(x, y)
            val argb = when {
                canvas.isTransparent(x, y) -> 0 // fully transparent
                filled -> { val c = palette[code] ?: Color.Gray; android.graphics.Color.argb((c.alpha*255).toInt(), (c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt()) }
                code == selectedColorCode -> selectedArgb
                else -> unfilledArgb
            }
            val bx = x * pxPerCell
            for (dy in 0 until pxPerCell) { val r = (by + dy) * bw; var col = bx; repeat(pxPerCell) { pxs[r + col] = argb; col++ } }
        }
    }
    val im = Bitmap.createBitmap(pxs, bw, bh, Bitmap.Config.ARGB_8888); val bm = im.copy(Bitmap.Config.ARGB_8888, true); im.recycle()
    return bm
}

private const val MAX_BITMAP_DIM = 1024

private fun generateMiniBitmap(canvas: PixelCanvas, palette: Map<Int, Color>, mw: Int, mh: Int, w: Int, h: Int, isDark: Boolean = false): Bitmap {
    val bgArgb = if (isDark) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
    // 先把尺寸夹到安全上限，杜绝 IntArray 溢出与超大原生 Bitmap 触发系统强杀
    val safeMw = mw.coerceIn(2, MAX_BITMAP_DIM); val safeMh = mh.coerceIn(2, MAX_BITMAP_DIM)
    val pxs = IntArray(safeMw * safeMh) { bgArgb }
    // 按目标尺寸对画布做最近邻降采样（避免 scale<1 时 sc=0 画不出内容）
    for (py in 0 until safeMh) {
        val cy = (py * h / safeMh).coerceIn(0, h - 1)
        for (px in 0 until safeMw) {
            val cx = (px * w / safeMw).coerceIn(0, w - 1)
            if (canvas.isTransparent(cx, cy)) continue
            if (canvas.isFilled(cx, cy)) {
                val color = palette[canvas.getCell(cx, cy)]
                val argb = color?.let { android.graphics.Color.argb((it.alpha*255).toInt(), (it.red*255).toInt(), (it.green*255).toInt(), (it.blue*255).toInt()) } ?: 0xFF888888.toInt()
                pxs[py * safeMw + px] = argb
            }
        }
    }
    val im = Bitmap.createBitmap(pxs, safeMw, safeMh, Bitmap.Config.ARGB_8888); val bm = im.copy(Bitmap.Config.ARGB_8888, true); im.recycle()
    return bm
}

/** 单格 ARGB 颜色，逻辑与 generateFullBitmap 完全一致，保证增量 patch 与全量重算结果相同。 */
private fun cellArgb(canvas: PixelCanvas, palette: Map<Int, Color>, selectedColorCode: Int, x: Int, y: Int, isDark: Boolean): Int {
    if (canvas.isTransparent(x, y)) return 0
    val filled = canvas.isFilled(x, y); val code = canvas.getCell(x, y)
    return when {
        filled -> { val c = palette[code] ?: Color.Gray; android.graphics.Color.argb((c.alpha*255).toInt(), (c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt()) }
        code == selectedColorCode -> if (isDark) 0xFF333333.toInt() else 0xFFCCCCCC.toInt()
        else -> if (isDark) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
    }
}

/** 增量修补 mini 位图（降采样）：涂一格只改 1 个像素，无需重扫整画布。 */
private fun patchMini(bmp: Bitmap, cx: Int, cy: Int, w: Int, h: Int, canvas: PixelCanvas, palette: Map<Int, Color>, selectedColorCode: Int, isDark: Boolean) {
    val px = (cx * bmp.width / w).coerceIn(0, bmp.width - 1)
    val py = (cy * bmp.height / h).coerceIn(0, bmp.height - 1)
    bmp.setPixel(px, py, cellArgb(canvas, palette, selectedColorCode, cx, cy, isDark))
}

/** 增量修补 full 位图：涂一格只改 pxPerCell×pxPerCell 个像素。 */
private fun patchFull(bmp: Bitmap, cx: Int, cy: Int, w: Int, h: Int, pxPerCell: Int, canvas: PixelCanvas, palette: Map<Int, Color>, selectedColorCode: Int, isDark: Boolean) {
    val c = cellArgb(canvas, palette, selectedColorCode, cx, cy, isDark)
    val bx = cx * pxPerCell; val by = cy * pxPerCell
    for (dy in 0 until pxPerCell) {
        for (dx in 0 until pxPerCell) {
            bmp.setPixel(bx + dx, by + dy, c)
        }
    }
}
