package com.example.pixelcolor.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.pixelcolor.ui.theme.AppThemeColors
import com.example.pixelcolor.ui.theme.FrostedGlassBox
import com.example.pixelcolor.ui.theme.LocalAppTheme
import kotlinx.coroutines.CoroutineScope

/**
 * 首页「在线」统一入口：合并原「精选」(直接图源流) 与「在线」(网站浏览器) 两个入口。
 * 顶部以分段控件切换两个子页：
 *  - 精选：Safebooru / Danbooru / Yande.re 等二次元图源，无限滚动，点按直接进填色；
 *  - 网站：内置浏览器，可浏览/导入任意图源站点（花瓣网、Pixiv、Unsplash…）。
 */
@Composable
fun DiscoverTab(navController: NavController, scope: CoroutineScope) {
    val theme = LocalAppTheme.current
    var sub by remember { mutableIntStateOf(0) }
    val subs = listOf("精选", "网站")

    Column(Modifier.fillMaxSize().background(theme.bg)) {
        SegmentedControl(sub, subs, { sub = it }, theme)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (sub) {
                0 -> FeaturedTab(navController, scope)
                1 -> WebsitePickerContent(navController, LocalContext.current)
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    selectedIndex: Int,
    items: List<String>,
    onSelect: (Int) -> Unit,
    theme: AppThemeColors
) {
    FrostedGlassBox(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)),
        tintColor = theme.surface,
        blurRadius = 12.dp,
        alpha = 0.8f
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            items.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                val bgColor by animateColorAsState(
                    if (isSelected) theme.accent else Color.Transparent,
                    animationSpec = tween(150),
                    label = "seg"
                )
                val textColor = if (isSelected) Color.White else theme.muted
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(bgColor)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}
