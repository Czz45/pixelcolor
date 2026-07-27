package com.example.pixelcolor.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Theme data class ──
data class AppThemeColors(
    val name: String,
    val bg: Color,
    val surface: Color,
    val surfaceLight: Color,
    val accent: Color,
    val gold: Color,
    val gridLine: Color,
    val muted: Color,
    val danger: Color,
    val success: Color,
    val onBg: Color,
    val white: Color,
    val isDark: Boolean
)

val LocalAppTheme = staticCompositionLocalOf { DarkTheme }

// ── Dark (AMOLED) ──
val DarkTheme = AppThemeColors(
    name = "深色",
    bg = Color(0xFF000000),           // 纯黑 AMOLED
    surface = Color(0xFF111111),       // 卡片深灰
    surfaceLight = Color(0xFF1A1A1A),  // 表面浅灰
    accent = Color(0xFFFF6B35),        // 橙色强调
    gold = Color(0xFFFFD700),          // 金色标题
    gridLine = Color(0xFF222222),      // 网格线
    muted = Color(0xFF777777),         // 灰色辅助
    danger = Color(0xFFE74C3C),        // 红色删除
    success = Color(0xFF4CAF50),       // 绿色完成
    onBg = Color(0xFFEEEEEE),          // 白色文字
    white = Color(0xFFF8F8F8),         // 通用白
    isDark = true
)

// ── Pink (kawaii, white base) ──
val PinkTheme = AppThemeColors(
    name = "粉色",
    bg = Color(0xFFFFFBFC),           // 极淡粉白底
    surface = Color(0xFFFFFFFF),       // 纯白卡片
    surfaceLight = Color(0xFFFFF5F7),  // 极浅粉表面
    accent = Color(0xFFFF69B4),        // Hot Pink
    gold = Color(0xFFE8527A),          // 玫红标题
    gridLine = Color(0xFFF0E0E5),      // 淡粉网格
    muted = Color(0xFFAA8890),         // 玫灰辅助
    danger = Color(0xFFFF4466),        // 珊瑚红
    success = Color(0xFF5CB85C),       // 柔绿
    onBg = Color(0xFF2D2028),          // 深棕文字
    white = Color(0xFFFFFBFC),         // 暖白
    isDark = false
)

// ── White (clean, white base) ──
val WhiteTheme = AppThemeColors(
    name = "白色",
    bg = Color(0xFFF7F7F7),           // 浅灰白底
    surface = Color(0xFFFFFFFF),       // 纯白卡片
    surfaceLight = Color(0xFFFFFFFF),  // 白表面
    accent = Color(0xFF2196F3),        // 蓝色强调
    gold = Color(0xFF1976D2),          // 深蓝标题
    gridLine = Color(0xFFE8E8E8),      // 浅灰网格
    muted = Color(0xFF999999),         // 灰色辅助
    danger = Color(0xFFE53935),        // 红色删除
    success = Color(0xFF4CAF50),       // 绿色完成
    onBg = Color(0xFF212121),          // 深灰文字
    white = Color(0xFFFFFFFF),         // 白
    isDark = false
)

// ── Blue (ocean, white base) ──
val BlueTheme = AppThemeColors(
    name = "蓝色",
    bg = Color(0xFFF5F8FC),           // 极淡蓝白底
    surface = Color(0xFFFFFFFF),       // 纯白卡片
    surfaceLight = Color(0xFFF0F5FA),  // 极浅蓝表面
    accent = Color(0xFF00BCD4),        // 青色强调
    gold = Color(0xFF0097A7),          // 深青标题
    gridLine = Color(0xFFE0ECF4),      // 淡蓝网格
    muted = Color(0xFF78909C),         // 蓝灰辅助
    danger = Color(0xFFEF5350),        // 红色删除
    success = Color(0xFF66BB6A),       // 绿色完成
    onBg = Color(0xFF1A2332),          // 深蓝文字
    white = Color(0xFFF8FBFF),         // 冷白
    isDark = false
)

val AllThemes = listOf(DarkTheme, PinkTheme, WhiteTheme, BlueTheme)

// ── Backward-compatible aliases (default = Dark) ──
val PixelBg = DarkTheme.bg
val PixelSurface = DarkTheme.surface
val PixelSurfaceLight = DarkTheme.surfaceLight
val PixelAccent = DarkTheme.accent
val PixelGold = DarkTheme.gold
val PixelGridLine = DarkTheme.gridLine
val PixelMuted = DarkTheme.muted
val PixelDanger = DarkTheme.danger
val PixelSuccess = DarkTheme.success
val PixelOnBg = DarkTheme.onBg
val PixelWhite = DarkTheme.white

val CanvasBg = DarkTheme.bg
val CellUnfilled = Color(0xFF444444)
val CellHighlight = DarkTheme.gold
val PaletteSelected = Color.White
