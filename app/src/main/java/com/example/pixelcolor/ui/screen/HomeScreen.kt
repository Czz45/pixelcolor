package com.example.pixelcolor.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.pixelcolor.data.GameRepository
import com.example.pixelcolor.data.SettingsStore
import com.example.pixelcolor.data.model.SaveData
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.ui.theme.AllThemes
import com.example.pixelcolor.ui.theme.FrostedGlassBox
import com.example.pixelcolor.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoItem(val id: String, val url: String, val thumb: String, val author: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { GameRepository(context) }
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val dailyStreak by settingsStore.dailyStreak.collectAsState(initial = 0)
    val completedCount by settingsStore.completedCount.collectAsState(initial = 0)
    val totalTimeMs by settingsStore.totalTimeMs.collectAsState(initial = 0L)
    val totalFilledCells by settingsStore.totalFilledCells.collectAsState(initial = 0L)
    val themeIndex by settingsStore.themeIndex.collectAsState(initial = 0)
    val theme = LocalAppTheme.current
    var showThemePicker by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf(false) }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("选择主题", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AllThemes.forEachIndexed { index, t ->
                        val isSelected = index == themeIndex
                        val bgColor by animateColorAsState(
                            if (isSelected) theme.accent.copy(alpha = 0.12f) else Color.Transparent, label = "bg"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bgColor)
                                .clickable { scope.launch { settingsStore.setThemeIndex(index) }; showThemePicker = false }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(t.accent))
                            Spacer(Modifier.width(14.dp))
                            Text(t.name, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = theme.onBg, modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(theme.accent), contentAlignment = Alignment.Center) {
                                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = theme.surface,
            shape = RoundedCornerShape(20.dp),
            titleContentColor = theme.onBg,
            textContentColor = theme.onBg
        )
    }

    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.scrollToPage(selectedTab)
        }
    }

    // Achievements dialog
    if (showAchievements) {
        val allUnlocked = com.example.pixelcolor.engine.Achievements.check(
            com.example.pixelcolor.engine.GameConfig(100, 100, 10, "", true),
            completedCount, dailyStreak, totalTimeMs, totalFilledCells
        ).map { it.id }.toSet()
        AlertDialog(
            onDismissRequest = { showAchievements = false },
            title = { Text("🏆 成就", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Stats
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("完成", "${completedCount}张", theme)
                        StatItem("时长", formatTime(totalTimeMs), theme)
                        StatItem("方块", formatCells(totalFilledCells), theme)
                        StatItem("连续", "${dailyStreak}天", theme)
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    // All achievements
                    com.example.pixelcolor.engine.Achievements.ALL.forEach { ach ->
                        val unlocked = ach.id in allUnlocked
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(
                                if (unlocked) theme.accent.copy(alpha = 0.08f) else Color.Transparent
                            ).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ach.icon, fontSize = 20.sp, modifier = Modifier.width(30.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ach.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (unlocked) theme.onBg else theme.muted)
                                Text(ach.description, fontSize = 10.sp, color = theme.muted)
                            }
                            if (unlocked) Text("✓", color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            else Text("🔒", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = theme.surface,
            shape = RoundedCornerShape(16.dp),
            titleContentColor = theme.onBg,
            textContentColor = theme.onBg
        )
    }

    Scaffold(
        topBar = {
            Box {
                // Frosted glass background behind the top bar
                Box(
                    Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .blur(12.dp)
                        .background(theme.bg.copy(alpha = 0.7f))
                )
                CenterAlignedTopAppBar(
                    title = { Text("PixelColor", fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        if (dailyStreak > 0) {
                            Box(Modifier.padding(end = 4.dp).clip(RoundedCornerShape(20.dp)).background(theme.accent.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text("🔥 $dailyStreak", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        IconButton(onClick = { showAchievements = true }) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(theme.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Text("🏆", fontSize = 16.sp)
                            }
                        }
                        IconButton(onClick = { showThemePicker = true }) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(theme.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Text("🎨", fontSize = 16.sp)
                            }
                        }
                        val hasCrashLogs = remember {
                            context.filesDir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".log") }?.isNotEmpty() == true
                        }
                        if (hasCrashLogs) {
                            IconButton(onClick = { navController.navigate(Screen.CrashLogs.route) }) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(theme.danger.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Text("!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.danger)
                                }
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                )
            }
        },
        containerColor = theme.bg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ModernTabRow(selectedTab, listOf("画廊", "精选"), { selectedTab = it }, theme, pagerState.currentPageOffsetFraction)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> GalleryTab(navController, context, repo, theme)
                    1 -> FeaturedTab(navController, scope)
                }
            }
        }
    }
}

@Composable
private fun ModernTabRow(selectedIndex: Int, tabs: List<String>, onTabSelected: (Int) -> Unit, theme: com.example.pixelcolor.ui.theme.AppThemeColors, pageOffsetFraction: Float = 0f) {
    FrostedGlassBox(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)),
        tintColor = theme.surface,
        blurRadius = 12.dp,
        alpha = 0.8f
    ) {
        Box(Modifier.fillMaxWidth().padding(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = index == selectedIndex
                    val bgColor by animateColorAsState(
                        if (isSelected) theme.accent else Color.Transparent,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "tab"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) Color.White else theme.muted,
                        animationSpec = tween(150),
                        label = "tabText"
                    )
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(bgColor)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, color = textColor)
                    }
                }
            }
        }
    }
}

private val thumbCache = mutableMapOf<String, android.graphics.Bitmap>()

@Composable
private fun GalleryTab(navController: NavController, context: android.content.Context, repo: GameRepository, theme: com.example.pixelcolor.ui.theme.AppThemeColors) {
    var refresh by remember { mutableIntStateOf(0) }
    val saves = remember(refresh) { repo.listSaves() }
    LaunchedEffect(Unit) { refresh++ }
    val sorted = remember(saves) {
        saves.sortedWith(compareByDescending<SaveData> { val t = it.config.gridWidth * it.config.gridHeight; if (it.filledCells.size == t) 0 else 1 }.thenByDescending { it.createdAt })
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "__add_new__") {
            AddNewCard(theme) { navController.navigate(Screen.Gallery.route) }
        }
        if (sorted.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(theme.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Text("🎨", fontSize = 30.sp) }
                        Spacer(Modifier.height(12.dp))
                        Text("还没有作品", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = theme.onBg)
                        Spacer(Modifier.height(4.dp))
                        Text("点击 + 开始你的第一幅画", fontSize = 12.sp, color = theme.muted)
                    }
                }
            }
        } else {
            itemsIndexed(sorted, key = { _, s -> s.id }) { _, save ->
                ModernGalleryCard(save, navController, context, repo, { refresh++ }, theme)
            }
        }
    }
}

@Composable
private fun AddNewCard(theme: com.example.pixelcolor.ui.theme.AppThemeColors, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(Modifier.background(theme.surface)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+", fontSize = 36.sp, fontWeight = FontWeight.Light, color = theme.accent)
                    Spacer(Modifier.height(10.dp))
                    Text("添加图片", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.onBg)
                }
            }
            // Bottom text section (same padding as work cards)
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("新建作品", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.onBg, modifier = Modifier.weight(1f))
                    Text("→", fontSize = 14.sp, color = theme.accent, fontWeight = FontWeight.Bold)
                }
                Text("相册 / 拍照 / 在线", fontSize = 10.sp, color = theme.muted)
            }
        }
    }
}

@Composable
private fun ModernGalleryCard(save: SaveData, navController: NavController, context: android.content.Context, repo: GameRepository, onRefresh: () -> Unit, theme: com.example.pixelcolor.ui.theme.AppThemeColors) {
    val w = save.config.gridWidth; val h = save.config.gridHeight
    val filled = save.filledCells.size; val total = w * h; val done = filled >= total
    val progress = if (total > 0) filled * 100 / total else 0
    var showDelete by remember { mutableStateOf(false) }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除作品", fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要删除这幅作品吗？") },
            confirmButton = { TextButton(onClick = { repo.deleteSave(save.id); java.io.File(context.cacheDir, "thumb_${save.id}.png").delete(); thumbCache.remove(save.id); showDelete = false; onRefresh() }) { Text("删除", color = theme.danger, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
            shape = RoundedCornerShape(16.dp), containerColor = theme.surface
        )
    }

    var thumb by remember { mutableStateOf(thumbCache["${save.id}_${save.filledCells.size}"]) }
    LaunchedEffect("${save.id}_${save.filledCells.size}") {
        val key = "${save.id}_${save.filledCells.size}"
        if (thumbCache.containsKey(key)) { thumb = thumbCache[key]; return@LaunchedEffect }
        val cacheFile = java.io.File(context.cacheDir, "thumb_${save.id}.png")
        withContext(Dispatchers.IO) {
            val bmp = if (cacheFile.exists()) android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath) else {
                val cp = 4; val b = android.graphics.Bitmap.createBitmap(w * cp, h * cp, android.graphics.Bitmap.Config.ARGB_8888)
                val cm = save.palette.associate { it.code to it.color.toInt() }; val fs = save.filledCells.map { it.y * w + it.x }.toSet()
                for (y in 0 until h) for (x in 0 until w) {
                    val argb = if (y * w + x in fs) cm.getOrDefault(save.cells.getOrElse(y * w + x) { 1 }, 0xFF888888.toInt()) else 0xFFFFFFFF.toInt()
                    for (dy in 0 until cp) for (dx in 0 until cp) b.setPixel(x * cp + dx, y * cp + dy, argb)
                }
                cacheFile.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, it) }; b
            }
            thumbCache[key] = bmp; thumb = bmp
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable { navController.navigate(Screen.Game.create(save.id)) },
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(Modifier.background(theme.surface)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    thumb?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    Box(Modifier.padding(8.dp).clip(RoundedCornerShape(20.dp)).background(if (done) theme.success else Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(if (done) "✓ 完成" else "$progress%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showDelete = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Delete, "删除", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
                    }
                }
                // Progress bar strip
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color.Black.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(filled.toFloat() / total.coerceAtLeast(1)).background(if (done) theme.success else theme.accent))
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${w}×${h}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.onBg, modifier = Modifier.weight(1f))
                        val timeMin = save.elapsedTimeMs / 60000
                        val timeSec = (save.elapsedTimeMs / 1000) % 60
                        val timeStr = if (timeMin > 0) "${timeMin}分${timeSec}秒" else "${timeSec}秒"
                        Text("⏱$timeStr", fontSize = 11.sp, color = theme.muted)
                    }
                    Text("${filled}/${total}格", fontSize = 10.sp, color = theme.muted)
                }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, theme: com.example.pixelcolor.ui.theme.AppThemeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.accent, fontFamily = FontFamily.Monospace)
        Text(label, fontSize = 10.sp, color = theme.muted)
    }
}

private fun formatTime(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "${h}h${m}m" else "${m}m"
}

private fun formatCells(cells: Long): String {
    return if (cells >= 1_000_000) "${"%.1f".format(cells / 1_000_000f)}M"
    else if (cells >= 1_000) "${"%.1f".format(cells / 1_000f)}K"
    else "$cells"
}
