package com.example.pixelcolor.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pixelcolor.engine.ColorPalette
import com.example.pixelcolor.engine.PaletteColor
import com.example.pixelcolor.ui.theme.LocalAppTheme
import com.example.pixelcolor.ui.theme.FrostedGlassBox

enum class ColorSortMode { Gradient, Code, Total, Remaining }

@Composable
fun ColorPaletteBar(
    palette: ColorPalette,
    selectedColorCode: Int,
    onColorSelected: (Int) -> Unit,
    initialSortMode: Int = 0,
    initialReversed: Boolean = false,
    onSortModeChanged: (Int, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var sortMode by remember { mutableStateOf(ColorSortMode.entries[initialSortMode]) }
    var reversed by remember { mutableStateOf(initialReversed) }
    val activeColors = palette.colors.filter { it.remainingCount > 0 || it.code == selectedColorCode }
    val sorted = remember(activeColors, sortMode, reversed, selectedColorCode) {
        val list = when (sortMode) {
            ColorSortMode.Gradient -> activeColors.sortedBy { c ->
                val r = (c.color.toInt() shr 16) and 0xFF
                val g = (c.color.toInt() shr 8) and 0xFF
                val b = c.color.toInt() and 0xFF
                // 按感知亮度排序（暗→亮）
                (r * 299 + g * 587 + b * 114) / 10
            }
            ColorSortMode.Code -> activeColors.sortedBy { it.code }
            ColorSortMode.Total -> activeColors.sortedBy { it.totalCount }
            ColorSortMode.Remaining -> activeColors.sortedBy { it.remainingCount }
        }
        val base = if (reversed) list.reversed() else list
        // 选中的颜色始终排第一
        val selected = base.filter { it.code == selectedColorCode }
        val rest = base.filter { it.code != selectedColorCode }
        selected + rest
    }

    FrostedGlassBox(
        modifier = modifier.fillMaxWidth(),
        tintColor = LocalAppTheme.current.bg,
        blurRadius = 14.dp,
        alpha = 0.85f
    ) {
        Column(Modifier.fillMaxWidth()) {
            val theme = LocalAppTheme.current
            // Sort row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "排序:",
                    fontSize = 9.sp,
                    color = theme.muted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                PixelSortChip("🌈", sortMode == ColorSortMode.Gradient) {
                    if (sortMode == ColorSortMode.Gradient) { reversed = !reversed; onSortModeChanged(sortMode.ordinal, reversed) }
                    else { sortMode = ColorSortMode.Gradient; reversed = false; onSortModeChanged(0, false) }
                }
                PixelSortChip("色号", sortMode == ColorSortMode.Code) {
                    if (sortMode == ColorSortMode.Code) { reversed = !reversed; onSortModeChanged(sortMode.ordinal, reversed) }
                    else { sortMode = ColorSortMode.Code; reversed = false; onSortModeChanged(1, false) }
                }
                PixelSortChip("总数", sortMode == ColorSortMode.Total) {
                    if (sortMode == ColorSortMode.Total) { reversed = !reversed; onSortModeChanged(sortMode.ordinal, reversed) }
                    else { sortMode = ColorSortMode.Total; reversed = false; onSortModeChanged(2, false) }
                }
                PixelSortChip("剩余", sortMode == ColorSortMode.Remaining) {
                    if (sortMode == ColorSortMode.Remaining) { reversed = !reversed; onSortModeChanged(sortMode.ordinal, reversed) }
                    else { sortMode = ColorSortMode.Remaining; reversed = false; onSortModeChanged(3, false) }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (reversed) "▲" else "▼",
                    fontSize = 10.sp,
                    color = theme.gold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Color chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sorted.forEach { c ->
                    val isSelected = c.code == selectedColorCode
                    PixelColorChip(
                        colorCode = c.code,
                        colorInt = c.color.toInt(),
                        remainingCount = c.remainingCount,
                        isSelected = isSelected,
                        onClick = { onColorSelected(c.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelColorChip(
    colorCode: Int,
    colorInt: Int,
    remainingCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(if (isSelected) 58.dp else 50.dp)
            .height(if (isSelected) 62.dp else 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(colorInt))
            .then(
                if (isSelected) Modifier.border(3.dp, LocalAppTheme.current.gold, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, Color(0xFF444444).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val br = ((colorInt shr 16) and 0xFF) + ((colorInt shr 8) and 0xFF) + (colorInt and 0xFF)
        val textColor = if (br > 384) Color.Black else Color.White

        Text(
            "$colorCode",
            fontSize = if (isSelected) 16.sp else 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$remainingCount",
                fontSize = 9.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PixelSortChip(label: String, active: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) theme.accent.copy(alpha = 0.15f) else theme.surface
            )
            .border(
                1.dp,
                if (active) theme.accent else Color(0xFF444444).copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) theme.accent else theme.muted
        )
    }
}
