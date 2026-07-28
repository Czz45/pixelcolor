package com.example.pixelcolor.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.PixelColorApp
import com.example.pixelcolor.ui.component.ColorPaletteBar
import com.example.pixelcolor.ui.component.PixelCanvasView
import com.example.pixelcolor.ui.theme.LocalAppTheme
import com.example.pixelcolor.ui.theme.FrostedGlassBox
import com.example.pixelcolor.ui.theme.PixelAccent
import com.example.pixelcolor.ui.theme.PixelBg
import com.example.pixelcolor.ui.theme.PixelGold
import com.example.pixelcolor.ui.theme.PixelMuted
import com.example.pixelcolor.ui.theme.PixelSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 进入画布时用于承载「从缩略图位置放大到全屏」的启动动画来源矩形与预览图。
 * 画廊卡片在点击跳转前写入自身在窗口中的坐标与画作缩略图，GameScreen 读取后立即清空，避免返回再次进入时重放。
 */
object GameLaunchRectHolder {
    var rect: Rect? = null
    var preview: android.graphics.Bitmap? = null
}

@Composable
fun GameScreen(navController: NavController, saveId: String) {
    val vm: GameViewModel = viewModel()
    val gameState by vm.gameState.collectAsState()
    // Use snapshotFlow to collect canvas state without triggering recomposition on every frame
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(vm.canvasScale.value, vm.canvasOffsetX.value, vm.canvasOffsetY.value) }
            .collect { (s, ox, oy) ->
                scale = s; offsetX = ox; offsetY = oy
            }
    }
    val jumpTarget by vm.jumpTarget.collectAsState()
    val colorSortMode by vm.colorSortMode.collectAsState()
    val colorSortReversed by vm.colorSortReversed.collectAsState()
    var areaFillMode by remember { mutableStateOf(false) }
    var brushSize by remember { mutableFloatStateOf(1f) }
    var isLoading by remember { mutableStateOf(true) }
    var showPalette by remember { mutableStateOf(true) }
    var freePaintMode by remember { mutableStateOf(false) }
    var autoMode by remember { mutableStateOf(false) }
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()

    // Navigate to the completion screen the moment the last cell is filled in THIS session.
    LaunchedEffect(Unit) {
        vm.justCompleted.collect { completed ->
            if (completed) {
                navController.navigate(Screen.Completion.create(vm.saveId ?: "", preview = false))
                vm.clearJustCompleted()
            }
        }
    }

    // 实时显示计时
    val displayTimeMs = gameState?.elapsedTimeMs ?: 0L

    LaunchedEffect(saveId) {
        val t0 = android.os.SystemClock.elapsedRealtime()
        isLoading = true
        PixelColorApp.logEntry("GameEntry", "=== 进入画布 saveId=$saveId ===")
        val t1 = android.os.SystemClock.elapsedRealtime()
        try {
            withContext(Dispatchers.Default) { vm.loadGame(saveId) }
        } catch (e: OutOfMemoryError) {
            // OOM during load is fatal — the canvas cannot be built, so record it as a crash
            PixelColorApp.logCrash(e)
            isLoading = false
            return@LaunchedEffect
        } catch (e: Exception) {
            // Any other failure (e.g. corrupt save → GameConfig/GameEngine throws) is a real
            // crash that previously went only to Logcat and left the screen stuck on "加载中".
            PixelColorApp.logCrash(e)
            isLoading = false
            return@LaunchedEffect
        }
        val t2 = android.os.SystemClock.elapsedRealtime()
        PixelColorApp.logEntry("GameEntry", "loadGame完成 +${t2-t1}ms total=${t2-t0}ms")
        val st = vm.gameState.value
        if (st != null) {
            PixelColorApp.logEntry("GameEntry", "画布: w=${st.canvas.width} h=${st.canvas.height} totalCells=${st.canvas.width*st.canvas.height} filledCount=${st.canvas.filledCount}")
        }
        vm.selectFirstColor()
        isLoading = false
        val t3 = android.os.SystemClock.elapsedRealtime()
        PixelColorApp.logEntry("GameEntry", "=== 画布加载完毕 total=${t3-t0}ms ===")
    }

    // 「启动应用」式入场动画：若从画廊缩略图点入，则从该缩略图位置矩形放大到全屏；否则轻微放大淡入。
    // 内容仅做 alpha 0->1 的透明到不透明淡入，避免反向缩放导致图层缓冲反复重分配而卡顿。
    val launchRect = remember { GameLaunchRectHolder.rect.also { GameLaunchRectHolder.rect = null } }
    val launchPreview = remember { GameLaunchRectHolder.preview.also { GameLaunchRectHolder.preview = null } }
    var overlayRect by remember { mutableStateOf<Rect?>(null) }
    val hasLaunch = launchRect != null
    val progress = remember { Animatable(if (hasLaunch) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (hasLaunch) progress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayRect = it.boundsInWindow() }
            .graphicsLayer {
                if (hasLaunch && overlayRect != null) {
                    val ov = overlayRect!!
                    val src = launchRect!!
                    scaleX = lerp(src.width / ov.width, 1f, progress.value)
                    scaleY = lerp(src.height / ov.height, 1f, progress.value)
                    translationX = lerp(src.left - ov.left, 0f, progress.value)
                    translationY = lerp(src.top - ov.top, 0f, progress.value)
                    transformOrigin = TransformOrigin(0f, 0f)
                    alpha = progress.value
                } else {
                    val s = lerp(0.92f, 1f, progress.value)
                    scaleX = s
                    scaleY = s
                    alpha = progress.value.coerceIn(0f, 1f)
                }
            }
            .background(theme.bg)
    ) {
        if (isLoading) {
            // 加载阶段复用与正式画布完全一致布局（同一 GameTopBar + 同尺寸画布区），
            // 预览图用 ContentScale.Fit 在画布区居中，落点 = 真实画布里画作的位置。
            LoadingPreviewContent(navController, launchPreview, true)
        } else {
            val state = gameState
            if (state != null) {
                val currentState = state
                val tMin = displayTimeMs / 60000
                val tSec = (displayTimeMs / 1000) % 60
                val timeText = if (tMin > 0) "${tMin}m${tSec}s" else "${tSec}s"
                Column(Modifier.fillMaxSize().background(theme.bg)) {
                    GameTopBar(navController, "${"%.4f".format(currentState.progress * 100)}%", timeText, true)

                    // Canvas area (takes all remaining space)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        val paletteColors = currentState.palette.colors.associate { it.code to Color(it.color.toInt()) }

                        // Pass selectedColorCode to PixelCanvasView for arrow logic
                        val selColor = currentState.selectedColorCode

                        PixelCanvasView(
                            canvas = currentState.canvas, palette = paletteColors, selectedColorCode = currentState.selectedColorCode,
                            scale = scale, offsetX = offsetX, offsetY = offsetY,
                            brushSize = brushSize,
                            freePaintMode = freePaintMode,
                            autoMode = autoMode,
                            onCellClick = { x, y -> if (areaFillMode) { vm.onAreaFill(x, y) } else vm.onCellClick(x, y) },
                            onFreeClick = { x, y -> vm.onFreeClick(x, y) },
                            onPaintLine = { line -> if (areaFillMode) { vm.onPaintLine(line) } else vm.onPaintLine(line) },
                            onPaintFast = { vm.paintCellsFast(it) },
                            onPaintCommit = { vm.commitPaint() },
                            onScaleChange = { vm.canvasScale.value = it },
                            onOffsetChange = { ox, oy -> vm.canvasOffsetX.value = ox; vm.canvasOffsetY.value = oy },
                            onColorSelected = { vm.onColorSelected(it) },
                            jumpTarget = jumpTarget,
                            onJumpHandled = { vm.clearJumpTarget() },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Area fill instruction tooltip
                        if (areaFillMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "点按要填充的区域",
                                    color = theme.gold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        // Free paint mode tooltip
                        if (freePaintMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = if (areaFillMode) 40.dp else 8.dp)
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "点按格子直接填色",
                                    color = theme.gold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        // Brush size tooltip
                        if (brushSize > 1f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = if (areaFillMode || freePaintMode) 40.dp else 8.dp)
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "长按滑动画画 | 笔刷 ${brushSize.toInt()}×${brushSize.toInt()}",
                                    color = theme.gold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Floating toolbar (horizontal, above palette)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.surface.copy(alpha = 0.9f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Palette toggle
                        FloatingPixelButton(
                            onClick = { showPalette = !showPalette },
                            active = showPalette,
                            label = "🎨"
                        )
                        // Area fill
                        FloatingPixelButton(
                            onClick = { areaFillMode = !areaFillMode },
                            active = areaFillMode,
                            label = "🪣"
                        )
                        // Free paint
                        FloatingPixelButton(
                            onClick = {
                                freePaintMode = !freePaintMode
                                if (freePaintMode) areaFillMode = false
                            },
                            active = freePaintMode,
                            label = "✏️"
                        )
                        // Brush size
                        FloatingPixelButton(
                            onClick = { brushSize = if (brushSize >= 5f) 1f else brushSize + 1f },
                            active = brushSize > 1f,
                            label = "${brushSize.toInt()}×"
                        )
                        // Auto mode
                        FloatingPixelButton(
                            onClick = { autoMode = !autoMode },
                            active = autoMode,
                            label = "⚡"
                        )
                        // Preview
                        FloatingPixelButton(
                            onClick = {
                                vm.saveCurrentState()
                                navController.navigate(Screen.Completion.create(vm.saveId ?: "", preview = true))
                            },
                            active = false,
                            label = "👁"
                        )
                    }

                    // Collapsible palette bar (animated)
                    AnimatedVisibility(
                        visible = showPalette,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            color = theme.surface,
                            tonalElevation = 0.dp
                        ) {
                            Column(Modifier.navigationBarsPadding()) {
                                ColorPaletteBar(
                                    palette = currentState.palette,
                                    selectedColorCode = currentState.selectedColorCode,
                                    onColorSelected = { vm.onColorSelected(it) },
                                    initialSortMode = colorSortMode,
                                    initialReversed = colorSortReversed,
                                    onSortModeChanged = { mode, reversed -> vm.onColorSortModeChanged(mode, reversed) }
                                )
                            }
                        }
                    }
                }

                // Completed works stay on GameScreen for viewing
            }
        }
        // 跨淡出遮罩：用与加载完全相同的布局盖在真实画布之上，进入画布后淡出 ~280ms，
        // 遮住画布位图异步生成期间的空白/闪跳；其预览落点与真实画作完全一致。
        if (launchPreview != null && !isLoading) {
            var overlayAlpha by remember { mutableFloatStateOf(1f) }
            LaunchedEffect(Unit) {
                val a = androidx.compose.animation.core.Animatable(1f)
                a.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                overlayAlpha = a.value
            }
            if (overlayAlpha > 0.001f) {
                Box(Modifier.fillMaxSize().zIndex(20f).graphicsLayer { alpha = overlayAlpha }) {
                    LoadingPreviewContent(navController, launchPreview, false)
                }
            }
        }
    }
}

@Composable
private fun GameTopBar(
    navController: NavController,
    progressText: String,
    timeText: String,
    interactive: Boolean
) {
    val theme = LocalAppTheme.current
    FrostedGlassBox(
        modifier = Modifier.fillMaxWidth().zIndex(1f).statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
        tintColor = theme.bg, blurRadius = 12.dp, alpha = 0.8f
    ) {
        if (interactive) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.CenterStart).size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = theme.gold, modifier = Modifier.size(22.dp))
            }
        } else {
            Box(Modifier.align(Alignment.CenterStart).size(36.dp))
        }
        Text(progressText, color = theme.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.Center))
        Text(timeText, color = theme.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
    }
}

@Composable
private fun LoadingPreviewContent(navController: NavController, launchPreview: android.graphics.Bitmap?, interactive: Boolean) {
    val theme = LocalAppTheme.current
    Column(Modifier.fillMaxSize().background(theme.bg)) {
        GameTopBar(navController, "填色中…", "", interactive)
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            if (launchPreview != null) {
                Image(bitmap = launchPreview.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(color = theme.accent)
            }
        }
    }
}

@Composable
private fun FloatingPixelButton(
    onClick: () -> Unit,
    active: Boolean,
    label: String
) {
    val theme = LocalAppTheme.current
    FrostedGlassBox(
        modifier = Modifier
            .size(36.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        tintColor = if (active) theme.accent else theme.surface,
        blurRadius = 8.dp,
        alpha = if (active) 0.9f else 0.75f
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
    }
}
