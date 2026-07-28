package com.example.pixelcolor.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * “精选”标签页：从公开、免登录的二次元图源拉取插画。
 * 默认 Safebooru（全年龄、动漫，无需 Cloudflare 绕过）；
 * 另可选 Danbooru / Yande.re，并强制安全分级（rating:s / safe），避免 NSFW。
 * 点击缩略图进入 PixelPreview 开始填色。复用 HomeScreen 同包定义的 PhotoItem。
 */

/** 二次元图源（公开 API、免登录）。 */
enum class AnimeSource(
    val label: String,
    val listUrl: String,    // 列表接口（不含 limit/分页）
    val pageParam: String,  // 分页参数名：Safebooru 用 pid，其余用 page
    val safeTag: String,    // 强制安全标签（Safebooru 本身全年龄，留空）
    val thumbFields: List<String>,
    val baseHost: String    // 用于把相对 file_url 补全为绝对地址
) {
    SAFEBOORU(
        "Safebooru",
        "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1",
        "pid", "",
        listOf("preview_url", "sample_url", "file_url"),
        "https://safebooru.org"
    ),
    DANBOORU(
        "Danbooru",
        "https://danbooru.donmai.us/posts.json",
        "page", "rating:s",
        listOf("preview_file_url", "sample_url", "file_url"),
        "https://danbooru.donmai.us"
    ),
    YANDERE(
        "Yande.re",
        "https://yande.re/post.json",
        "page", "rating:safe",
        listOf("preview_url", "sample_url", "file_url"),
        "https://yande.re"
    )
}

object FeaturedCache {
    var photos: List<PhotoItem> = emptyList()
    var page = 0
    var source: AnimeSource = AnimeSource.SAFEBOORU
    var isLoading = false
    var lastStatus = ""
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private val gson = Gson()

/** 把可能为相对/协议相对的地址补全为绝对地址。 */
private fun absUrl(url: String?, baseHost: String): String {
    if (url.isNullOrBlank()) return ""
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http") -> url
        else -> baseHost + (if (url.startsWith("/")) "" else "/") + url
    }
}

@Composable
fun FeaturedTab(navController: NavController, scope: CoroutineScope) {
    val theme = LocalAppTheme.current
    var showCfg by remember { mutableStateOf(false) }

    fun fetch() {
        if (FeaturedCache.isLoading) return
        scope.launch {
            FeaturedCache.isLoading = true
            if (FeaturedCache.photos.isEmpty()) FeaturedCache.lastStatus = "请求中…"
            try {
                val src = FeaturedCache.source
                val tags = if (src.safeTag.isNotBlank()) URLEncoder.encode(src.safeTag, "UTF-8") else ""
                val url = buildString {
                    append(src.listUrl)
                    append("&limit=21")
                    append("&${src.pageParam}=${FeaturedCache.page}")
                    if (tags.isNotBlank()) append("&tags=$tags")
                }
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile"
                    )
                    .build()
                val json = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                        resp.body?.string().orEmpty()
                    }
                }
                val arr = gson.fromJson<List<Map<String, Any>>>(
                    json, object : TypeToken<List<Map<String, Any>>>() {}.type
                ) ?: emptyList()
                val list = arr.mapNotNull { m ->
                    val rawFile = m["file_url"] as? String ?: return@mapNotNull null
                    val fileUrl = absUrl(rawFile, src.baseHost)
                    val thumb = absUrl(
                        src.thumbFields.firstNotNullOfOrNull { m[it] as? String },
                        src.baseHost
                    ).ifBlank { fileUrl }
                    val id = m["id"]?.toString() ?: fileUrl
                    val author = (m["author"] as? String) ?: (m["uploader"] as? String) ?: ""
                    PhotoItem(id, fileUrl, thumb, author)
                }
                FeaturedCache.photos = FeaturedCache.photos + list
                FeaturedCache.page++
                FeaturedCache.lastStatus = "✓ ${list.size} 张 → 共 ${FeaturedCache.photos.size} 张"
            } catch (e: Exception) {
                FeaturedCache.lastStatus = "✗ ${e.message?.take(60)}"
            }
            FeaturedCache.isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (FeaturedCache.photos.isEmpty() && !FeaturedCache.isLoading) fetch()
    }

    val gridState = rememberLazyGridState()
    val near = remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?.let { it.index >= FeaturedCache.photos.size - 6 } == true && !FeaturedCache.isLoading
        }
    }
    LaunchedEffect(near.value) { if (near.value) fetch() }

    Column(Modifier.fillMaxSize().background(theme.bg)) {
        SelectionContainer {
            Text(
                FeaturedCache.lastStatus,
                fontSize = 9.sp, color = theme.muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                maxLines = 2
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(FeaturedCache.source.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.gold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(theme.surface)
                    .clickable { showCfg = !showCfg }.padding(horizontal = 10.dp, vertical = 5.dp)
            ) { Text("⚙", fontSize = 14.sp, color = theme.onBg) }
        }

        if (showCfg) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimeSource.values().forEach { src ->
                    val selected = FeaturedCache.source == src
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selected) theme.accent.copy(alpha = 0.12f) else theme.surface)
                            .clickable {
                                if (src != FeaturedCache.source) {
                                    FeaturedCache.source = src
                                    FeaturedCache.photos = emptyList()
                                    FeaturedCache.page = 0
                                    FeaturedCache.lastStatus = ""
                                    fetch()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            src.label, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) theme.accent else theme.muted
                        )
                    }
                }
            }
        }

        if (FeaturedCache.isLoading && FeaturedCache.photos.isEmpty()) {
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
                items(FeaturedCache.photos, key = { it.id }) { p ->
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
                if (FeaturedCache.isLoading) {
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
