package com.example.pixelcolor.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.ui.theme.LocalAppTheme
import java.io.File
import java.util.UUID

/**
 * 「选择图片」悬浮窗（居中弹窗）。
 * 取代原先整页跳转的 GalleryScreen：点击画廊里的「+」即弹出，包含「相册」「拍照」两个入口；
 * 打开/关闭带「从底部放大到中间 + 淡入」的过渡动画。在线图源已在首页「在线」标签提供，故此处仅本地来源。
 */
@Composable
fun AddImageDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current

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

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(180)) +
                scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ),
        exit = fadeOut(animationSpec = tween(140)) +
                scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(140, easing = FastOutSlowInEasing)
                )
    ) {
        // 半透明遮罩，点击空白关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(
                        "选择图片",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.onBg,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    HorizontalDividerColor(theme)
                    AddOption(
                        icon = Icons.Filled.PhotoLibrary,
                        title = "从相册选择",
                        subtitle = "从本地相册挑选一张照片",
                        theme = theme
                    ) {
                        onDismiss()
                        try {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开相册: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    AddOption(
                        icon = Icons.Filled.PhotoCamera,
                        title = "拍照",
                        subtitle = "用相机拍一张新照片",
                        theme = theme
                    ) {
                        onDismiss()
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
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    theme: com.example.pixelcolor.ui.theme.AppThemeColors,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(theme.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = theme.accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.onBg)
            Text(subtitle, fontSize = 11.sp, color = theme.muted)
        }
    }
}

@Composable
private fun HorizontalDividerColor(theme: com.example.pixelcolor.ui.theme.AppThemeColors) {
    Box(
        Modifier.fillMaxWidth().height(1.dp).background(theme.muted.copy(alpha = 0.18f))
    )
}
