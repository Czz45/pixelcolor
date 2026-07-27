package com.example.pixelcolor.ui.screen

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.pixelcolor.data.GameRepository
import com.example.pixelcolor.data.SettingsStore
import com.example.pixelcolor.engine.Achievement
import com.example.pixelcolor.engine.Achievements
import com.example.pixelcolor.image.ImageProcessor
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
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CompletionScreen(navController: NavController, saveId: String, preview: Boolean = false) {
    val context = LocalContext.current
    val repo = remember { GameRepository(context) }
    val settingsStore = remember { SettingsStore(context) }
    val theme = LocalAppTheme.current
    val pair = remember { repo.load(saveId) }
    if (pair == null) { navController.popBackStack(); return }
    val (state, config) = pair
    val s = state; val c = config
    val saveData = remember { repo.loadSaveData(saveId) }

    val completedCount by settingsStore.completedCount.collectAsState(initial = 0)
    val dailyStreak by settingsStore.dailyStreak.collectAsState(initial = 0)
    val totalTimeMs by settingsStore.totalTimeMs.collectAsState(initial = 0L)
    val totalFilledCells by settingsStore.totalFilledCells.collectAsState(initial = 0L)

    var unlockedAchievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var showAchievementBanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!preview) {
            settingsStore.incrementCompleted()
            settingsStore.addPlayStats(s.elapsedTimeMs, saveData?.filledCells?.size ?: 0)
            val today = java.time.LocalDate.now().toString()
            settingsStore.updateDailyChallenge(today, completed = true)
        }
    }

    LaunchedEffect(completedCount, dailyStreak) {
        if (completedCount > 0) {
            val achievements = Achievements.check(c, completedCount, dailyStreak, totalTimeMs, totalFilledCells, s.elapsedTimeMs)
            if (achievements.isNotEmpty()) {
                unlockedAchievements = achievements
                showAchievementBanner = true
            }
        }
    }

    val totalMinutes = s.elapsedTimeMs / 60000
    val totalSeconds = (s.elapsedTimeMs / 1000) % 60
    val timeStr = if (totalMinutes > 0) "${totalMinutes}分${totalSeconds}秒" else "${totalSeconds}秒"
    val stars = when { s.elapsedTimeMs < 120_000 -> "⭐⭐⭐"; s.elapsedTimeMs < 300_000 -> "⭐⭐"; else -> "⭐" }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.bg)
            .systemBarsPadding()
    ) {
        // Top bar
        Box(
            Modifier
                .fillMaxWidth()
                .background(theme.bg)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = { navController.popBackStack(Screen.Home.route, inclusive = false) },
                modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = theme.gold, modifier = Modifier.size(22.dp))
            }
            Text(
                if (preview) "预览" else "完成！",
                color = theme.gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!preview) {
            // Big pixel star
            Box(
                Modifier
                    .size(80.dp)
                    .background(theme.surface, RoundedCornerShape(0.dp))
                    .border(2.dp, theme.gold, RoundedCornerShape(0.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⭐", fontSize = 40.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "恭喜完成！",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = theme.gold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Text(stars, fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "用时: $timeStr",
                fontSize = 16.sp,
                color = theme.onBg,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(20.dp))
            }

            // Painting process playback
            if (saveData != null) {
                var playbackIndex by remember { mutableIntStateOf(0) }
                var isPlaying by remember { mutableStateOf(preview) }
                var playbackSpeed by remember { mutableFloatStateOf(1f) }
                val filledCells = saveData.filledCells
                val totalCells = filledCells.size
                val speedOptions = listOf(1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f, 512f)

                if (totalCells > 0) {
                    // Playback canvas - scale to fit within 300dp max
                    val maxCanvasSize = 300.dp
                    val aspectRatio = c.gridWidth.toFloat() / c.gridHeight
                    val canvasWidth: androidx.compose.ui.unit.Dp
                    val canvasHeight: androidx.compose.ui.unit.Dp
                    if (aspectRatio > 1f) {
                        canvasWidth = maxCanvasSize
                        canvasHeight = maxCanvasSize / aspectRatio
                    } else {
                        canvasHeight = maxCanvasSize
                        canvasWidth = maxCanvasSize * aspectRatio
                    }

                    Canvas(
                        modifier = Modifier
                            .size(canvasWidth, canvasHeight)
                            .border(1.dp, theme.muted.copy(alpha = 0.3f))
                    ) {
                        val cellPx = size.width / c.gridWidth
                        drawRect(Color.White, Offset.Zero, Size(size.width, size.height))
                        val colorMap = saveData.palette.associate { it.code to Color(it.color.toInt()) }
                        for (i in 0 until playbackIndex.coerceAtMost(totalCells)) {
                            val cell = filledCells[i]
                            val color = colorMap[saveData.cells.getOrElse(cell.y * c.gridWidth + cell.x) { 1 }] ?: Color.Gray
                            drawRect(
                                color,
                                Offset(cell.x * cellPx, cell.y * cellPx),
                                Size(cellPx, cellPx)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Playback controls row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Rewind button
                        IconButton(onClick = {
                            playbackIndex = (playbackIndex - (totalCells / 20).coerceAtLeast(1)).coerceAtLeast(0)
                        }) {
                            Icon(
                                Icons.Default.FastRewind,
                                contentDescription = "快退",
                                tint = theme.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play/Pause button
                        IconButton(onClick = {
                            if (!isPlaying && playbackIndex >= totalCells) {
                                playbackIndex = 0
                            }
                            isPlaying = !isPlaying
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = theme.accent,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Fast forward button
                        IconButton(onClick = {
                            playbackIndex = (playbackIndex + (totalCells / 20).coerceAtLeast(1)).coerceAtMost(totalCells)
                        }) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = "快进",
                                tint = theme.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${playbackIndex.coerceAtMost(totalCells)}/$totalCells",
                            fontSize = 14.sp,
                            color = theme.onBg,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Draggable progress bar (Slider)
                    Slider(
                        value = playbackIndex.toFloat(),
                        onValueChange = { newValue ->
                            playbackIndex = newValue.toInt().coerceIn(0, totalCells)
                        },
                        onValueChangeFinished = {},
                        valueRange = 0f..totalCells.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = theme.accent,
                            activeTrackColor = theme.accent,
                            inactiveTrackColor = theme.surface,
                        )
                    )

                    // Speed control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "倍速:",
                            fontSize = 13.sp,
                            color = theme.muted,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(8.dp))
                        speedOptions.forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .background(
                                        if (isSelected) theme.accent else theme.surface,
                                        RoundedCornerShape(0.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) theme.accent else theme.muted.copy(alpha = 0.3f),
                                        RoundedCornerShape(0.dp)
                                    )
                                    .clickable { playbackSpeed = speed }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${speed.toInt()}x",
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else theme.onBg,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Playback coroutine
                LaunchedEffect(isPlaying, totalCells) {
                    if (isPlaying && totalCells > 0) {
                        while (playbackIndex < totalCells) {
                            // batch size grows with speed to avoid 1ms granularity limit
                            val stepsPerTick = playbackSpeed.toInt().coerceAtLeast(1)
                            playbackIndex = (playbackIndex + stepsPerTick).coerceAtMost(totalCells)
                            delay(50)
                        }
                        isPlaying = false
                    }
                }
            }

            // Achievement banner (settlement only)
            if (!preview) {
            if (showAchievementBanner && unlockedAchievements.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                Text(
                    "🏆 解锁成就",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.gold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(12.dp))
                unlockedAchievements.forEach { achievement ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = theme.surface,
                        shape = RoundedCornerShape(0.dp),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .border(1.dp, theme.accent, RoundedCornerShape(0.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⭐", fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    achievement.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = theme.onBg,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    achievement.description,
                                    fontSize = 12.sp,
                                    color = theme.muted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
            }

            if (!preview) {
            Spacer(Modifier.height(24.dp))

            // Share button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(theme.accent, RoundedCornerShape(0.dp))
                    .clickable {
                        val bitmap = ImageProcessor.renderToBitmap(s)
                        val file = File(context.cacheDir, "share_${saveId}.png")
                        ImageProcessor.saveBitmap(bitmap, file)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享作品"))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "📤 分享",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(8.dp))

            // Home button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(theme.bg, RoundedCornerShape(0.dp))
                    .border(2.dp, theme.gold, RoundedCornerShape(0.dp))
                    .clickable { navController.popBackStack(Screen.Home.route, inclusive = false) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🏠 返回首页",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.gold,
                    fontFamily = FontFamily.Monospace
                )
            }
            }
        }
    }
}
