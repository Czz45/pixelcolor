package com.example.pixelcolor.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.pixelcolor.data.GameRepository
import com.example.pixelcolor.data.model.SaveData
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.ui.theme.LocalAppTheme
import com.example.pixelcolor.ui.theme.PixelBg
import com.example.pixelcolor.ui.theme.PixelDanger
import com.example.pixelcolor.ui.theme.PixelGold
import com.example.pixelcolor.ui.theme.PixelMuted
import com.example.pixelcolor.ui.theme.PixelOnBg
import com.example.pixelcolor.ui.theme.PixelSuccess
import com.example.pixelcolor.ui.theme.PixelSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MyWorksScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { GameRepository(context) }
    val theme = LocalAppTheme.current

    var refreshKey by remember { mutableIntStateOf(0) }
    val saves = remember(refreshKey) { repo.listSaves() }
    LaunchedEffect(Unit) { refreshKey++ }
    val sorted = remember(saves) {
        saves.sortedWith(
            compareByDescending<SaveData> {
                val total = it.config.gridWidth * it.config.gridHeight
                val filled = it.filledCells.size
                if (filled == total) 0 else 1
            }.thenByDescending { it.createdAt })
    }

    var thumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(sorted) {
        withContext(Dispatchers.IO) {
            val map = mutableMapOf<String, Bitmap>()
            for (s in sorted) {
                if (!thumbnails.containsKey(s.id)) {
                    map[s.id] = renderThumbnail(s, 200)
                }
            }
            thumbnails = thumbnails + map
        }
    }

    // Confirm dialog state
    var pendingDelete by remember { mutableStateOf<SaveData?>(null) }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("确认删除", fontFamily = FontFamily.Monospace) },
            text = { Text("确定要删除这幅作品吗？此操作不可撤销。", fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { repo.deleteSave(it.id) }
                    pendingDelete = null
                    refreshKey++
                }) {
                    Text("删除", color = PixelDanger, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = theme.surface,
            titleContentColor = theme.onBg,
            textContentColor = theme.muted
        )
    }

    Column(Modifier.fillMaxSize().background(theme.bg).systemBarsPadding()) {
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
                    "我的作品",
                    color = theme.gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有作品，开始你的第一幅画吧！",
                    color = theme.muted,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            // Calculate card height: each card is square thumbnail + info bar
            val density = LocalDensity.current
            val spacingPx = with(density) { 8.dp.toPx() }
            val paddingPx = with(density) { 8.dp.toPx() }
            // We'll compute cell width in onSizeChanged

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { it.id }) { save ->
                    WorkCard(
                        save = save,
                        thumbnail = thumbnails[save.id],
                        onClick = { navController.navigate(Screen.Game.create(save.id)) },
                        onDelete = { pendingDelete = save }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkCard(
    save: SaveData,
    thumbnail: Bitmap?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalAppTheme.current
    val w = save.config.gridWidth
    val h = save.config.gridHeight
    val filled = save.filledCells.size
    val total = w * h
    val isDone = filled >= total
    val progress = if (total > 0) filled * 100 / total else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        // Square thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(theme.surface)
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Progress badge — top left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(
                        if (isDone) theme.success else Color.Black.copy(alpha = 0.7f),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (isDone) "✓" else "$progress%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Delete button — top right, black circle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete, "删除",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Progress bar strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color.Black.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(filled.toFloat() / total.coerceAtLeast(1))
                    .background(if (isDone) theme.success else theme.accent)
            )
        }

        // Info below
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            val timeMin = save.elapsedTimeMs / 60000
            val timeSec = (save.elapsedTimeMs / 1000) % 60
            val timeStr = if (timeMin > 0) "${timeMin}m${timeSec}s" else "${timeSec}s"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${w}×${h}", fontSize = 9.sp, color = theme.onBg, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text("⏱$timeStr", fontSize = 9.sp, color = theme.muted, fontFamily = FontFamily.Monospace)
            }
            Text("${filled}/${total}格", fontSize = 9.sp, color = theme.muted, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun renderThumbnail(save: SaveData, size: Int): Bitmap {
    val w = save.config.gridWidth
    val h = save.config.gridHeight
    val cellPx = (size.toFloat() / maxOf(w, h)).toInt().coerceAtLeast(1)
    val bw = w * cellPx
    val bh = h * cellPx
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)

    val colorMap = save.palette.associate { it.code to it.color.toInt() }
    val filledSet = save.filledCells.map { it.y * w + it.x }.toSet()

    for (y in 0 until h) {
        for (x in 0 until w) {
            val idx = y * w + x
            val code = if (idx < save.cells.size) save.cells[idx] else 1
            val argb = if (idx in filledSet) {
                colorMap.getOrDefault(code, 0xFF888888.toInt())
            } else {
                0xFFFFFFFF.toInt()
            }
            val px = x * cellPx
            val py = y * cellPx
            for (dy in 0 until cellPx) {
                for (dx in 0 until cellPx) {
                    if (px + dx < bw && py + dy < bh) {
                        bmp.setPixel(px + dx, py + dy, argb)
                    }
                }
            }
        }
    }
    return bmp
}
