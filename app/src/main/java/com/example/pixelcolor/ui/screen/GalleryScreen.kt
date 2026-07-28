package com.example.pixelcolor.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.ui.theme.FrostedGlassBox
import com.example.pixelcolor.ui.theme.LocalAppTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * 「选择图片」：仅保留本地来源（相册 / 拍照）。
 * 在线图源已合并到首页「在线」入口（含「精选」图源流与「网站」浏览器），避免重复入口。
 */
@Composable
fun GalleryScreen(navController: NavController) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("相册", "拍照")

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                navController.navigate(Screen.PixelPreview.create(Uri.encode(it.toString())))
            } catch (e: Exception) {
                Toast.makeText(context, "无法打开图片: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pendingCameraUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri.value
        if (success && uri != null && uri != Uri.EMPTY) {
            try {
                navController.navigate(Screen.PixelPreview.create(Uri.encode(uri.toString())))
            } catch (e: Exception) {
                Toast.makeText(context, "无法打开照片: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        pendingCameraUri.value = null
    }

    Column(Modifier.fillMaxSize().background(theme.bg).systemBarsPadding()) {
        // Top bar
        FrostedGlassBox(
            Modifier
                .fillMaxWidth(),
            tintColor = theme.bg,
            blurRadius = 12.dp,
            alpha = 0.8f
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = theme.gold, modifier = Modifier.size(22.dp))
                }
                Text(
                    "选择图片",
                    color = theme.gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.bg)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(36.dp)
                        .clickable { selectedTab = index }
                        .background(if (isSelected) theme.surface else theme.bg)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .drawBehind {
                            if (isSelected) {
                                drawRect(
                                    color = theme.gold,
                                    topLeft = Offset(0f, size.height - 2.dp.toPx()),
                                    size = Size(size.width, 2.dp.toPx())
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) theme.gold else theme.muted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PixelBlockButton(onClick = {
                    try { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    catch (e: Exception) { Toast.makeText(context, "无法打开相册: ${e.message}", Toast.LENGTH_SHORT).show() }
                }, text = "📷 从相册选择")
            }
            1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PixelBlockButton(onClick = {
                    try {
                        val dir = File(context.cacheDir, "camera_photos")
                        dir.mkdirs()
                        val file = File(dir, "${UUID.randomUUID()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingCameraUri.value = uri
                        cameraLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法启动相机: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, text = "📸 拍照")
            }
        }
    }
}

@Composable
private fun PixelBlockButton(onClick: () -> Unit, text: String) {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .background(theme.accent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
