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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

    // 「启动应用」式入场动画：画布从略小放大并淡入
    val enter = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }

    Box(Modifier.graphicsLayer { scaleX = enter.value; scaleY = enter.value; alpha = ((enter.value - 0.9f) / 0.1f).coerceIn(0f, 1f) }.fillMaxSize().background(theme.bg)) {
        if (isLoading) {
            Box(Modifier.fillMaxSize().background(theme.bg).systemBarsPadding(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = theme.accent)
                    Spacer(Modifier.height(16.dp))
                    Text("加载中...", color = theme.muted, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            val state = gameState
            if (state != null) {
                val currentState = state
                Column(Modifier.fillMaxSize().background(theme.bg)) {
                    // Minimalist top bar — higher zIndex so canvas can't paint over it
                    FrostedGlassBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f)
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        tintColor = theme.bg,
                        blurRadius = 12.dp,
                        alpha = 0.8f
                    ) {
                        // Back button
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, "返回",
                                tint = theme.gold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // Progress text
                        Text(
                            "${"%.4f".format(currentState.progress * 100)}%",
                            color = theme.gold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        // Time display — right side
                        val tMin = displayTimeMs / 60000
                        val tSec = (displayTimeMs / 1000) % 60
                        Text(
                            if (tMin > 0) "${tMin}m${tSec}s" else "${tSec}s",
                            color = theme.muted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                        )
                    }

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
