package com.example.pixelcolor.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.pixelcolor.PixelColorApp
import com.example.pixelcolor.ui.theme.LocalAppTheme
import java.io.File

@Composable
fun CrashLogsScreen(navController: NavController) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val crashFiles = remember(refreshKey) {
        val files = mutableListOf<File>()
        // Include the entry log
        val entryLog = File(context.filesDir, "entry_log.log")
        if (entryLog.exists()) files.add(entryLog)
        // Include crash logs
        context.filesDir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".log") }
            ?.let { files.addAll(it) }
        files.sortedByDescending { it.lastModified() }
    }
    LaunchedEffect(Unit) { refreshKey++ }
    var selectedLog by remember { mutableStateOf<String?>(null) }

    if (selectedLog != null) {
        // Log detail view
        AlertDialog(
            onDismissRequest = { selectedLog = null },
            title = { Text("崩溃日志", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    selectedLog!!,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, selectedLog)
                        putExtra(Intent.EXTRA_SUBJECT, "PixelColor 崩溃日志")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "分享日志"))
                }) { Text("分享") }
            },
            dismissButton = {
                TextButton(onClick = { selectedLog = null }) { Text("关闭") }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = theme.surface
        )
    }

    Scaffold(
        topBar = {
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
                    "崩溃日志",
                    color = theme.gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        },
        containerColor = theme.bg
    ) { padding ->
        if (crashFiles.isEmpty()) {
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有崩溃日志", color = theme.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        PixelColorApp.logCrash(RuntimeException("测试崩溃日志"))
                        refreshKey++
                    }) {
                        Text("写入测试日志", color = theme.accent, fontSize = 12.sp)
                    }
                }
            }
        } else {
            var showClearDialog by remember { mutableStateOf(false) }
            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("清除日志", fontWeight = FontWeight.SemiBold) },
                    text = { Text("确定要删除所有崩溃日志吗？") },
                    confirmButton = {
                        TextButton(onClick = {
                            crashFiles.forEach { it.delete() }
                            File(context.filesDir, "entry_log.log").delete()
                            refreshKey++
                            showClearDialog = false
                        }) { Text("确定", color = theme.danger) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) { Text("取消") }
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = theme.surface
                )
            }
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    TextButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清除所有日志", color = theme.danger, fontSize = 13.sp)
                    }
                }
                items(crashFiles) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedLog = file.readText() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                file.name.removePrefix("crash_").removeSuffix(".log"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.onBg
                            )
                            Spacer(Modifier.height(4.dp))
                            val firstLine = file.readLines().firstOrNull { it.startsWith("Exception") || it.startsWith("Stack") } ?: file.readLines().firstOrNull() ?: ""
                            Text(
                                firstLine.take(100),
                                fontSize = 10.sp,
                                color = theme.muted,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
