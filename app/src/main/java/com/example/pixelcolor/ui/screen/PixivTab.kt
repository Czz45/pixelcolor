package com.example.pixelcolor.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.ui.theme.LocalAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Singleton cache - survives HorizontalPager recomposition
object PixivCache {
    var photos: List<PhotoItem> = emptyList()
    var page = 1
    var apiType = "picsum"
    var isLoading = false
    var lastStatus = ""
}

@Composable
fun PixivTab(navController: NavController, scope: CoroutineScope) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Use PixivCache directly - no local state copies
    var showCfg by remember { mutableStateOf(false) }

    fun fetch() {
        if (PixivCache.isLoading) return
        scope.launch {
            PixivCache.isLoading = true
            if (PixivCache.photos.isEmpty()) {
                PixivCache.lastStatus = "请求中..."
            }
            try {
                val list = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val page = PixivCache.page
                    val apiType = PixivCache.apiType
                    val conn = when (apiType) {
                        "bing" -> {
                            val key = context.getSharedPreferences("pixiv_cfg", Context.MODE_PRIVATE).getString("bing_key", "") ?: ""
                            if (key.isBlank()) throw Exception("请先配置 Bing API Key")
                            (java.net.URL("https://api.bing.microsoft.com/v7.0/images/search?q=anime&count=21&offset=${(page-1)*21}").openConnection() as java.net.HttpURLConnection).apply {
                                setRequestProperty("Ocp-Apim-Subscription-Key", key); connectTimeout = 10000; readTimeout = 10000
                            }
                        }
                        "pexels" -> {
                            val key = context.getSharedPreferences("pixiv_cfg", Context.MODE_PRIVATE).getString("pexels_key", "") ?: ""
                            if (key.isBlank()) throw Exception("请先配置 Pexels API Key")
                            (java.net.URL("https://api.pexels.com/v1/search?query=anime&per_page=21&page=$page").openConnection() as java.net.HttpURLConnection).apply {
                                setRequestProperty("Authorization", key); connectTimeout = 10000; readTimeout = 10000
                            }
                        }
                        else -> {
                            java.net.URL("https://picsum.photos/v2/list?page=$page&limit=21").openConnection() as java.net.HttpURLConnection
                        }
                    }
                    conn.connectTimeout = 10000; conn.readTimeout = 10000
                    val json = conn.inputStream.bufferedReader().readText()
                    when (apiType) {
                        "bing" -> {
                            (Gson().fromJson(json, Map::class.java)["value"] as? List<Map<String, Any>> ?: emptyList()).map {
                                PhotoItem(it["contentUrl"]?.toString() ?: "", it["contentUrl"]?.toString() ?: "", it["thumbnailUrl"]?.toString() ?: "", it["name"]?.toString() ?: "")
                            }
                        }
                        "pexels" -> {
                            val ph = (Gson().fromJson(json, Map::class.java)["photos"] as? List<Map<String, Any>>) ?: emptyList()
                            ph.map { val s = it["src"] as Map<String, Any>; PhotoItem(it["id"].toString(), s["original"] as String, s["medium"] as String, (it["photographer"] as String)) }
                        }
                        else -> {
                            val raw = Gson().fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
                            raw.map { PhotoItem(it["id"].toString(), it["download_url"].toString(), "https://picsum.photos/id/${it["id"]}/300/300", it["author"].toString()) }
                        }
                    }
                }
                PixivCache.photos = PixivCache.photos + list
                PixivCache.page++
                PixivCache.lastStatus = "✓${list.size}张→共${PixivCache.photos.size}"
            } catch (e: Exception) {
                PixivCache.lastStatus = "✗ ${e.message?.take(60)}"
            }
            PixivCache.isLoading = false
        }
    }

    // Auto-fetch if cache is empty
    LaunchedEffect(Unit) {
        if (PixivCache.photos.isEmpty() && !PixivCache.isLoading) {
            fetch()
        }
    }

    val near = remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= PixivCache.photos.size - 6 } == true && !PixivCache.isLoading } }
    LaunchedEffect(near.value) { if (near.value) fetch() }

    Column(Modifier.fillMaxSize().background(theme.bg)) {
        // Status
        SelectionContainer {
            Text(
                PixivCache.lastStatus,
                fontSize = 9.sp,
                color = theme.muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                maxLines = 2
            )
        }

        // Source tabs
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val label = when (PixivCache.apiType) { "bing" -> "Bing"; "pexels" -> "Pexels"; else -> "Picsum" }
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.gold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.surface)
                    .clickable { showCfg = !showCfg }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("⚙", fontSize = 14.sp, color = theme.onBg)
            }
        }

        // Config panel
        if (showCfg) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("picsum" to "Picsum", "pexels" to "Pexels", "bing" to "Bing").forEach { (type, lbl) ->
                    val isSelected = PixivCache.apiType == type
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) theme.accent.copy(alpha = 0.12f) else theme.surface)
                            .clickable {
                                if (type != PixivCache.apiType) {
                                    PixivCache.apiType = type
                                    PixivCache.photos = emptyList()
                                    PixivCache.page = 1
                                    PixivCache.lastStatus = ""
                                    fetch()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            lbl, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) theme.accent else theme.muted
                        )
                    }
                }
            }
        }

        // Photo grid
        if (PixivCache.isLoading && PixivCache.photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(PixivCache.photos, key = { it.id }) { p ->
                    AsyncImage(
                        model = p.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                navController.navigate(Screen.PixelPreview.create(android.net.Uri.encode(p.url)))
                            }
                    )
                }
                if (PixivCache.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = theme.accent)
                        }
                    }
                }
            }
        }
    }
}
