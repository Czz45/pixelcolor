package com.example.pixelcolor.navigation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pixelcolor.PixelColorApp
import com.example.pixelcolor.ui.screen.*
import java.io.File

@Composable
fun PixelNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    var showCrashLog by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Screen.Home.route) {
            composable(Screen.Home.route) { HomeScreen(navController = navController) }
            composable(Screen.Gallery.route) { GalleryScreen(navController = navController) }
            composable(
                Screen.PixelPreview.route,
                arguments = listOf(navArgument("imageUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val imageUri = backStackEntry.arguments?.getString("imageUri") ?: ""
                PixelPreviewScreen(navController = navController, imageUri = imageUri)
            }
            composable(
                Screen.Game.route,
                arguments = listOf(navArgument("saveId") { type = NavType.StringType })
            ) { backStackEntry ->
                val saveId = backStackEntry.arguments?.getString("saveId") ?: ""
                GameScreen(navController = navController, saveId = saveId)
            }
            composable(
                Screen.Completion.route,
                arguments = listOf(
                    navArgument("saveId") { type = NavType.StringType },
                    navArgument("preview") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val saveId = backStackEntry.arguments?.getString("saveId") ?: ""
                val preview = backStackEntry.arguments?.getBoolean("preview") ?: false
                CompletionScreen(navController = navController, saveId = saveId, preview = preview)
            }
            composable(Screen.CrashLogs.route) { CrashLogsScreen(navController = navController) }
        }

        // Global floating crash log button — visible on ALL screens
        val hasLogs = remember {
            val entryLog = File(context.filesDir, "entry_log.log")
            val crashLogs = context.filesDir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".log") }
            entryLog.exists() || (crashLogs?.isNotEmpty() == true)
        }

        FloatingActionButton(
            onClick = {
                val entryLog = File(context.filesDir, "entry_log.log")
                val crashLogs = context.filesDir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".log") }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
                val sb = StringBuilder()
                if (entryLog.exists()) {
                    sb.appendLine("=== Entry Log ===")
                    sb.appendLine(entryLog.readText())
                    sb.appendLine()
                }
                for (f in crashLogs.takeLast(5)) {
                    sb.appendLine("=== ${f.name.removePrefix("crash_").removeSuffix(".log")} ===")
                    sb.appendLine(f.readText())
                    sb.appendLine()
                }
                logContent = sb.toString()
                showCrashLog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(40.dp),
            containerColor = if (hasLogs) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
        ) {
            Icon(
                Icons.Default.BugReport, "日志",
                tint = if (hasLogs) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showCrashLog) {
        AlertDialog(
            onDismissRequest = { showCrashLog = false },
            title = { Text("崩溃日志", fontSize = 14.sp) },
            text = {
                Text(
                    logContent.ifEmpty { "暂无日志" },
                    fontSize = 9.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, logContent)
                        putExtra(Intent.EXTRA_SUBJECT, "PixelColor 崩溃日志")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "分享日志"))
                }) { Text("分享") }
            },
            dismissButton = {
                TextButton(onClick = { showCrashLog = false }) { Text("关闭") }
            }
        )
    }
}
