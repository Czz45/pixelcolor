package com.example.pixelcolor.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.ui.theme.LocalAppTheme
import java.io.File
import java.util.UUID

/**
 * 「选择图片」悬浮窗（居中弹窗）�?
 * 开�?/关闭采用「启动应用」式动画：卡片从�?+」按钮的真实坐标缩放+位移到屏幕中�?
 * （起始约图标大小并钉在按钮中心，再展开到居中铺开），关闭时反向收回�?
 * 在线图源已在首页「在线」标签提供，故此处仅本地来源（相�? / 拍照）�?
 *
 * @param addButtonRect �?+」按钮在窗口中的坐标，用于驱动从按钮生长到中央的动画�?
 */
@Composable
fun AddImageDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    navController: NavController,
    addButtonRect: Rect? = null
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

    // 「启动应用」式动画：进�? 0=缩在按钮处，1=居中铺开
    var mounted by remember { mutableStateOf(show) }
    var panelRect by remember { mutableStateOf<Rect?>(null) }
    val progress = remember { Animatable(if (show) 1f else 0f) }
    LaunchedEffect(show) {
        if (show) {
            mounted = true
            progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        } else {
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            mounted = false
        }
    }

    if (mounted) {
        // 按钮中心 �? 遮罩中心 的位移（窗口坐标一致）
        val src = addButtonRect
        val dst = panelRect
        val p = progress.value
        val sX: Float
        val sY: Float
        val tX: Float
        val tY: Float
        if (src != null && dst != null && dst.width > 0f && dst.height > 0f) {
            sX = lerp(src.width / dst.width, 1f, p)
            sY = lerp(src.height / dst.height, 1f, p)
            tX = lerp(src.left - dst.left, 0f, p)
            tY = lerp(src.top - dst.top, 0f, p)
        } else {
            val s = lerp(0.2f, 1f, p)
            sX = s; sY = s; tX = 0f; tY = 0f
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * p))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = sX
                        scaleY = sY
                        translationX = tX
                        translationY = tY
                    }
                    .onGloballyPositioned { panelRect = it.boundsInWindow() }
                    .fillMaxWidth(0.86f)
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
                        subtitle = "从本地相册挑选一张照�?",
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
