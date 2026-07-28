package com.example.pixelcolor.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.pixelcolor.engine.GameConfig
import com.example.pixelcolor.engine.ColorPalette
import com.example.pixelcolor.engine.GameState
import com.example.pixelcolor.engine.PaletteColor
import com.example.pixelcolor.engine.PixelCanvas
import com.example.pixelcolor.image.ImageProcessor
import com.example.pixelcolor.image.ColorQuantizer
import com.example.pixelcolor.data.GameRepository
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.ui.theme.LocalAppTheme
import com.example.pixelcolor.ui.theme.PixelAccent
import com.example.pixelcolor.ui.theme.PixelBg
import com.example.pixelcolor.ui.theme.PixelGold
import com.example.pixelcolor.ui.theme.PixelMuted
import com.example.pixelcolor.ui.theme.PixelOnBg
import com.example.pixelcolor.ui.theme.PixelSuccess
import com.example.pixelcolor.ui.theme.PixelSurface
import com.example.pixelcolor.ui.theme.PixelSurfaceLight
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PixelPreviewScreen(navController: NavController, imageUri: String) {
    val context = LocalContext.current
    val repo = remember { GameRepository(context) }
    val scope = rememberCoroutineScope()
    val theme = LocalAppTheme.current
    val rawUri = remember(imageUri) { Uri.decode(imageUri) }

    // 「启动应用」式入场动画：内容从略小放大并淡入到全屏
    val enter = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }

    // Crop state (fractions 0..1)
    var cropX by remember { mutableFloatStateOf(0f) }
    var cropY by remember { mutableFloatStateOf(0f) }
    var cropW by remember { mutableFloatStateOf(1f) }
    var cropH by remember { mutableFloatStateOf(1f) }
    var showCrop by remember { mutableStateOf(false) }

    // Grid
    var gridW by remember { mutableFloatStateOf(64f) }
    var colorCount by remember { mutableFloatStateOf(20f) }

    // Processing
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var gameState by remember { mutableStateOf<GameState?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var processingJob by remember { mutableStateOf<Job?>(null) }

    val imageAspect = originalBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f
    val actualCropAspect = if (cropH > 0f) (cropW / cropH) * imageAspect else imageAspect
    val maxCells = 1048576f
    val rawGridW: Float; val rawGridH: Float
    if (actualCropAspect >= 1f) {
        rawGridW = gridW.coerceAtMost(kotlin.math.sqrt(maxCells * actualCropAspect))
                        rawGridH = (rawGridW / actualCropAspect).coerceIn(16f, 1024f)
    } else {
        rawGridH = gridW.coerceAtMost(kotlin.math.sqrt(maxCells / actualCropAspect))
                        rawGridW = (rawGridH * actualCropAspect).coerceIn(16f, 1024f)
    }
    val displayGridW = rawGridW; val displayGridH = rawGridH

    fun loadBitmap() {
        processingJob?.cancel()
        processingJob = scope.launch {
            isProcessing = true; errorMsg = null
            try {
                val bmp = withContext(Dispatchers.IO) {
                    if (rawUri.startsWith("file:///android_asset/")) {
                        val assetPath = rawUri.removePrefix("file:///android_asset/")
                        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
                    } else {
                        val uri = Uri.parse(rawUri)
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                }
                originalBitmap = bmp
                cropX = 0f; cropY = 0f; cropW = 1f; cropH = 1f
            } catch (e: kotlinx.coroutines.CancellationException) { }
            catch (e: Throwable) { errorMsg = "加载失败: ${e.message}"; e.printStackTrace() }
            finally { isProcessing = false }
        }
    }

    fun processCrop() {
        val bmp = originalBitmap ?: return
        processingJob?.cancel()
        processingJob = scope.launch {
            isProcessing = true; errorMsg = null
            try {
                val state = withContext(Dispatchers.IO) {
                    val x = (cropX * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                    val y = (cropY * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                    val cw = (cropW * bmp.width).toInt().coerceAtLeast(16)
                    val ch = (cropH * bmp.height).toInt().coerceAtLeast(16)
                    val cropRight = (x + cw).coerceAtMost(bmp.width)
                    val cropBottom = (y + ch).coerceAtMost(bmp.height)
                    val cropBitmap = Bitmap.createBitmap(bmp, x, y, cropRight - x, cropBottom - y)
                    val gw = displayGridW.toInt(); val gh = displayGridH.toInt()
                    // Nearest-neighbor scaling for crop
                    val scaled = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
                    for (y in 0 until gh) for (x in 0 until gw) {
                        val srcX = (x * cropBitmap.width / gw).coerceIn(0, cropBitmap.width - 1)
                        val srcY = (y * cropBitmap.height / gh).coerceIn(0, cropBitmap.height - 1)
                        scaled.setPixel(x, y, cropBitmap.getPixel(srcX, srcY))
                    }
                    val pixels = LongArray(gw * gh)
                    for (py in 0 until gh) for (px in 0 until gw) {
                        pixels[py * gw + px] = scaled.getPixel(px, py).toLong() and 0xFFFFFFFF
                    }
                    // Handle transparent pixels: filter them out, set code 0
                    val opaquePixels = mutableListOf<Long>()
                    val transparentSet = mutableSetOf<Int>()
                    for (py in 0 until gh) for (px in 0 until gw) {
                        val alpha = (scaled.getPixel(px, py) shr 24) and 0xFF
                        if (alpha < 128) {
                            transparentSet.add(py * gw + px)
                        } else {
                            opaquePixels.add(scaled.getPixel(px, py).toLong() and 0xFFFFFFFF)
                        }
                    }
                    val opaqueArr = opaquePixels.toLongArray()
                    val palette = ColorQuantizer.quantize(opaqueArr, colorCount.toInt())
                    val opaqueCodes = ColorQuantizer.mapAllToPalette(opaqueArr, palette)
                    var opaqueIdx = 0
                    val cells = Array(gh) { cy -> IntArray(gw) { cx ->
                        val idx = cy * gw + cx
                        if (idx in transparentSet) 0 else { opaqueCodes[opaqueIdx++].also { } }
                    } }
                    val filled = Array(gh) { cy -> BooleanArray(gw) { cx ->
                        (scaled.getPixel(cx, cy) shr 24) and 0xFF < 128
                    } }
                    val fillOrder = Array(gh) { IntArray(gw) { 0 } }
                    val canvas = PixelCanvas(gw, gh, cells, filled, fillOrder)
                    GameState(canvas, palette, palette.colors.first().code, false, 0f, 0)
                }
                gameState = state
                previewBitmap = generateGrayPreview(state)
            } catch (e: kotlinx.coroutines.CancellationException) { }
            catch (e: Throwable) { errorMsg = "处理失败: ${e.message}"; e.printStackTrace() }
            finally { isProcessing = false }
        }
    }

    fun directImport() {
        val bmp = originalBitmap ?: return
        scope.launch {
            isProcessing = true
            try {
                withContext(Dispatchers.IO) {
                    val maxDim = 1024
                    val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                        val tw = (bmp.width * maxDim.toFloat() / maxOf(bmp.width, bmp.height)).toInt().coerceAtLeast(1)
                        val th = (bmp.height * maxDim.toFloat() / maxOf(bmp.width, bmp.height)).toInt().coerceAtLeast(1)
                        // Nearest-neighbor scaling — preserves sharp edges
                        val result = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                        for (y in 0 until th) for (x in 0 until tw) {
                            val srcX = (x * bmp.width / tw).coerceIn(0, bmp.width - 1)
                            val srcY = (y * bmp.height / th).coerceIn(0, bmp.height - 1)
                            result.setPixel(x, y, bmp.getPixel(srcX, srcY))
                        }
                        result
                    } else bmp
                    val w = scaled.width; val h = scaled.height

                    // Collect unique colors with a cap to prevent huge palettes
                    val colorMap = mutableMapOf<Long, Int>() // color -> index
                    var nextCode = 1
                    val cells = Array(h) { IntArray(w) }
                    val filled = Array(h) { BooleanArray(w) }
                    val maxColors = 256

                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val pixel = scaled.getPixel(x, y)
                            val alpha = (pixel shr 24) and 0xFF
                            if (alpha < 128) {
                                cells[y][x] = 0 // transparent
                                filled[y][x] = true
                            } else {
                                filled[y][x] = false
                                val color = (pixel.toLong() and 0xFFFFFFFF)
                                val code = colorMap.getOrPut(color) {
                                    if (nextCode <= maxColors) nextCode++ else {
                                        var best = 1L; var bestDist = Long.MAX_VALUE
                                        for ((c, _) in colorMap) {
                                            val dr = ((color shr 16) and 0xFF) - ((c shr 16) and 0xFF)
                                            val dg = ((color shr 8) and 0xFF) - ((c shr 8) and 0xFF)
                                            val db = (color and 0xFF) - (c and 0xFF)
                                            val dist = dr * dr + dg * dg + db * db.toLong()
                                            if (dist < bestDist) { bestDist = dist; best = c }
                                        }
                                        colorMap[best] ?: 1
                                    }
                                }
                                cells[y][x] = code
                            }
                        }
                    }

                    val paletteColors = colorMap.entries.sortedBy { it.value }.mapIndexed { i, (c, _) ->
                        val cnt = cells.sumOf { row -> row.count { it == i + 1 } }
                        PaletteColor(i + 1, c, cnt, cnt)
                    }
                    val palette = ColorPalette(paletteColors)
                    val fillOrder = Array(h) { IntArray(w) { 0 } }
                    val canvas = PixelCanvas(w, h, cells, filled, fillOrder)
                    val state = GameState(canvas, palette, 1, false, 0f, 0)
                    val config = GameConfig(w, h, paletteColors.size, rawUri, false)
                    val id = repo.save(state, config)
                    withContext(Dispatchers.Main) { navController.navigate(Screen.Game.create(id)) }
                }
            } catch (e: Exception) { errorMsg = "导入失败: ${e.message}" }
            isProcessing = false
        }
    }

    fun startGame() {
        if (isProcessing || gameState == null) return
        scope.launch {
            isProcessing = true
            val state = gameState!!
            val config = GameConfig(displayGridW.toInt(), displayGridH.toInt(), colorCount.toInt(), rawUri, false)
            withContext(Dispatchers.IO) {
                val savedId = repo.save(state, config)
                try { repo.copySourceImage(rawUri, savedId) } catch (_: Exception) {}
                withContext(Dispatchers.Main) { navController.navigate(Screen.Game.create(savedId)) }
            }
            isProcessing = false
        }
    }

    LaunchedEffect(rawUri) { loadBitmap() }

    Column(Modifier.graphicsLayer { scaleX = enter.value; scaleY = enter.value; alpha = ((enter.value - 0.9f) / 0.1f).coerceIn(0f, 1f) }.fillMaxSize().background(theme.bg).systemBarsPadding()) {
        if (showCrop && originalBitmap != null) {
            // Crop page
            Box(Modifier.fillMaxSize().background(theme.bg)) {
                val cropMargin = 24.dp
                val bmp = originalBitmap!!
                val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()
                Box(Modifier.fillMaxSize().padding(top = 52.dp).padding(cropMargin), contentAlignment = Alignment.Center) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val availW = maxWidth; val availH = maxHeight
                        val displayW: androidx.compose.ui.unit.Dp
                        val displayH: androidx.compose.ui.unit.Dp
                        if (availW / availH > imgAspect) {
                            displayH = availH; displayW = availH * imgAspect
                        } else {
                            displayW = availW; displayH = availW / imgAspect
                        }
                        CropOverlay(
                            bitmap = bmp,
                            cropX = cropX, cropY = cropY, cropW = cropW, cropH = cropH,
                            onCropChange = { cx, cy, cw, ch -> cropX = cx; cropY = cy; cropW = cw; cropH = ch },
                            modifier = Modifier.size(displayW, displayH)
                        )
                    }
                }
                // Top bar
                    Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().background(theme.bg).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("拖角/边=改比例  拖中心=移动  双指=缩放", color = theme.muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .background(theme.surface, RoundedCornerShape(0.dp))
                                .clickable { cropX = 0f; cropY = 0f; cropW = 1f; cropH = 1f }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("重置", color = theme.onBg, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        Box(
                            Modifier
                                .background(theme.accent, RoundedCornerShape(0.dp))
                                .clickable { showCrop = false }
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) { Text("完成", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            // Settings page
            val screenH = LocalConfiguration.current.screenHeightDp.dp

            // Top bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(theme.bg)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = theme.gold, modifier = Modifier.size(22.dp))
                }
                Text(
                    "裁切 & 像素化",
                    color = theme.gold,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("原图", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.gold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                originalBitmap?.let { bmp ->
                    val imageMaxH = screenH * 0.45f
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = imageMaxH)
                            .border(1.dp, theme.muted)
                    )
                    Spacer(Modifier.height(8.dp))
                    // Buttons
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .background(theme.success, RoundedCornerShape(0.dp))
                                .clickable { directImport() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🎨 原生像素",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .background(theme.accent, RoundedCornerShape(0.dp))
                                .clickable { showCrop = true }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✂ 裁切",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } ?: Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    if (isProcessing) CircularProgressIndicator(color = theme.accent) else Text("加载中...", color = theme.muted, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(12.dp))

                // Grid settings
                Text(
                    "网格: ${displayGridW.toInt()}×${displayGridH.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = theme.onBg,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = gridW,
                    onValueChange = { gridW = it },
                    valueRange = 16f..1024f,
                    steps = 63,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.gold,
                        activeTrackColor = theme.accent,
                        inactiveTrackColor = theme.surface
                    )
                )
                Text(
                    if (actualCropAspect >= 1f) "高度 = 宽度 / ${"%.2f".format(actualCropAspect)} = ${displayGridH.toInt()}"
                    else "宽度 = 高度 × ${"%.2f".format(actualCropAspect)} = ${displayGridW.toInt()}",
                    fontSize = 10.sp,
                    color = theme.muted,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "颜色: ${colorCount.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = theme.onBg,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = colorCount,
                    onValueChange = { colorCount = it },
                    valueRange = 5f..256f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.gold,
                        activeTrackColor = theme.accent,
                        inactiveTrackColor = theme.surface
                    )
                )

                // Apply button
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(theme.accent, RoundedCornerShape(0.dp))
                        .clickable(enabled = !isProcessing && originalBitmap != null) { processCrop() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isProcessing) "处理中..." else "应用参数",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (errorMsg != null) {
                    Text(
                        errorMsg!!,
                        color = theme.danger,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (isProcessing) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = theme.accent
                    )
                }

                // Preview
                previewBitmap?.let { preview ->
                    Spacer(Modifier.height(12.dp))
                    Text("灰色预览", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.gold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().border(1.dp, theme.muted)
                    )
                    Spacer(Modifier.height(4.dp))
                    gameState?.let { state ->
                        Text(
                            "网格: ${state.canvas.width}×${state.canvas.height} | 颜色: ${state.palette.colors.size} 种",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = theme.onBg
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            state.palette.colors.take(24).forEach { c ->
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .background(Color(c.color.toInt()))
                                ) {
                                    val br = ((c.color.toInt() shr 16) and 0xFF) + ((c.color.toInt() shr 8) and 0xFF) + (c.color.toInt() and 0xFF)
                                    Text(
                                        "${c.code}",
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (br > 384) Color.Black else Color.White,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Start game button
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(theme.success, RoundedCornerShape(0.dp))
                            .clickable(enabled = !isProcessing) { startGame() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "开始填色！",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// --- Crop overlay (keep original logic) ---
@Composable
private fun CropOverlay(
    bitmap: Bitmap,
    cropX: Float, cropY: Float, cropW: Float, cropH: Float,
    onCropChange: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val cx = rememberUpdatedState(cropX); val cy = rememberUpdatedState(cropY)
    val cw = rememberUpdatedState(cropW); val ch = rememberUpdatedState(cropH)

    Box(
        modifier = modifier
            .onSizeChanged { viewSize = it }
            .pointerInput("crop") {
                val touchR = 36f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
                    val dx = down.position.x / vw; val dy = down.position.y / vh
                    val l = cx.value; val t = cy.value; val r = l + cw.value; val b = t + ch.value
                    val nearR = touchR / vw; val nearB = touchR / vh
                    val hitL = kotlin.math.abs(dx - l) < nearR; val hitR = kotlin.math.abs(dx - r) < nearR
                    val hitT = kotlin.math.abs(dy - t) < nearB; val hitB = kotlin.math.abs(dy - b) < nearB
                    val inX = true  // dx in (l, r) — always true for now
                    val inY = true
                    val op: Int = when {
                        hitL && hitT -> 2; hitR && hitT -> 3
                        hitL && hitB -> 4; hitR && hitB -> 5
                        hitL && inY -> 6; hitR && inY -> 7
                        hitT && inX -> 8; hitB && inX -> 9
                        inX && inY -> 1
                        else -> 0
                    }
                    if (op == 0) { down.consume(); return@awaitEachGesture }

                    do {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.isEmpty()) break
                        if (active.size >= 2) {
                            val z = event.calculateZoom()
                            val newW = (cw.value / z).coerceIn(0.1f, 1f - cx.value)
                            val newH = (ch.value / z).coerceIn(0.1f, 1f - cy.value)
                            onCropChange(cx.value, cy.value, newW, newH)
                            active.forEach { it.consume() }
                        } else {
                            val d = event.calculatePan()
                            val fdx = d.x / vw; val fdy = d.y / vh
                            val nx: Float; val ny: Float; val nw: Float; val nh: Float
                            when (op) {
                                1 -> { nx = (cx.value + fdx).coerceIn(0f, 1f - cw.value); ny = (cy.value + fdy).coerceIn(0f, 1f - ch.value); nw = cw.value; nh = ch.value }
                                2 -> { nx = (cx.value + fdx).coerceIn(0f, cx.value + cw.value - 0.1f); ny = (cy.value + fdy).coerceIn(0f, cy.value + ch.value - 0.1f); nw = cw.value + cx.value - nx; nh = ch.value + cy.value - ny }
                                3 -> { nx = cx.value; ny = (cy.value + fdy).coerceIn(0f, cy.value + ch.value - 0.1f); nw = (cw.value + fdx).coerceIn(0.1f, 1f - cx.value); nh = ch.value + cy.value - ny }
                                4 -> { nx = (cx.value + fdx).coerceIn(0f, cx.value + cw.value - 0.1f); ny = cy.value; nw = cw.value + cx.value - nx; nh = (ch.value + fdy).coerceIn(0.1f, 1f - cy.value) }
                                5 -> { nx = cx.value; ny = cy.value; nw = (cw.value + fdx).coerceIn(0.1f, 1f - cx.value); nh = (ch.value + fdy).coerceIn(0.1f, 1f - cy.value) }
                                6 -> { nx = (cx.value + fdx).coerceIn(0f, cx.value + cw.value - 0.1f); ny = cy.value; nw = cw.value + cx.value - nx; nh = ch.value }
                                7 -> { nx = cx.value; ny = cy.value; nw = (cw.value + fdx).coerceIn(0.1f, 1f - cx.value); nh = ch.value }
                                8 -> { nx = cx.value; ny = (cy.value + fdy).coerceIn(0f, cy.value + ch.value - 0.1f); nw = cw.value; nh = ch.value + cy.value - ny }
                                9 -> { nx = cx.value; ny = cy.value; nw = cw.value; nh = (ch.value + fdy).coerceIn(0.1f, 1f - cy.value) }
                                else -> { nx = cx.value; ny = cy.value; nw = cw.value; nh = ch.value }
                            }
                            onCropChange(nx, ny, nw, nh)
                            active.first().consume()
                        }
                    } while (true)
                }
            }
    ) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val vw = size.width; val vh = size.height
            val left = cropX * vw; val top = cropY * vh
            val cw = cropW * vw; val ch = cropH * vh

            clipRect(0f, 0f, vw, top) { drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(vw, vh)) }
            clipRect(0f, top + ch, vw, vh) { drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(vw, vh)) }
            clipRect(0f, top, left, top + ch) { drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(vw, vh)) }
            clipRect(left + cw, top, vw, top + ch) { drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(vw, vh)) }

            val rect = Rect(left, top, left + cw, top + ch)
            drawPath(Path().apply { addRect(rect) }, Color(0xFFFF6B35), style = Stroke(width = 3f))

            val handleR = 12f
            for (hx in listOf(left, left + cw)) {
                for (hy in listOf(top, top + ch)) {
                    drawCircle(com.example.pixelcolor.ui.theme.PixelGold, handleR, Offset(hx, hy))
                    drawCircle(Color.Black.copy(alpha = 0.3f), handleR, Offset(hx, hy), style = Stroke(width = 1f))
                }
            }

            val gW = 8; val gH = 8
            for (i in 1 until gW) { val gx = left + i * cw / gW; drawLine(Color.White.copy(alpha = 0.3f), Offset(gx, top), Offset(gx, top + ch), strokeWidth = 0.5f) }
            for (i in 1 until gH) { val gy = top + i * ch / gH; drawLine(Color.White.copy(alpha = 0.3f), Offset(left, gy), Offset(left + cw, gy), strokeWidth = 0.5f) }
        }
    }
}

private fun generateGrayPreview(state: GameState): Bitmap {
    val cw = state.canvas.width; val ch = state.canvas.height
    val cellSize = 8
    val w = cw * cellSize; val h = ch * cellSize
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val brightness = state.palette.colors.associate { c ->
        val r = (c.color.toInt() shr 16) and 0xFF
        val g = (c.color.toInt() shr 8) and 0xFF
        val b = c.color.toInt() and 0xFF
        c.code to ((0.299 * r + 0.587 * g + 0.114 * b).toInt())
    }
    for (y in 0 until ch) for (x in 0 until cw) {
        val code = state.canvas.getCell(x, y)
        val brt = brightness[code] ?: 128
        val gray = (0xFF shl 24) or (brt shl 16) or (brt shl 8) or brt
        for (dy in 0 until cellSize) for (dx in 0 until cellSize) bmp.setPixel(x * cellSize + dx, y * cellSize + dy, gray)
    }
    return bmp
}
