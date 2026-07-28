package com.example.pixelcolor.ui.screen

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.pixelcolor.navigation.Screen
import com.example.pixelcolor.ui.theme.FrostedGlassBox
import com.example.pixelcolor.ui.theme.LocalAppTheme
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「网站」子页的图源数据。原置于 GalleryScreen 的「在线」标签，现抽出供「在线」统一入口复用。
 */
data class WebSite(val name: String, val url: String, val tag: String, val emoji: String)

private val webSites = listOf(
    // 国内
    WebSite("花瓣网", "https://huaban.com/search?q=%E5%83%8F%E7%B4%A0%E7%94%BB", "国内·像素画", "🇨🇳"),
    WebSite("站酷", "https://www.zcool.com.cn/search/content?word=%E5%83%8F%E7%B4%A0%E7%94%BB", "国内·像素画", "🇨🇳"),
    WebSite("千图网", "https://www.58pic.com/search/?k=%E5%83%8F%E7%B4%A0%E7%94%BB", "国内·素材", "🇨🇳"),
    WebSite("摄图网", "https://699pic.com/search/?keyword=%E9%A3%8E%E6%99%AF", "国内·风景", "🇨🇳"),
    WebSite("Pixiv", "https://www.pixiv.net/tags/%E3%83%94%E3%82%AF%E3%82%BB%E3%83%AB%E3%82%A2%E3%83%BC%E3%83%88", "国内·综合", "🇨🇳"),
    WebSite("堆糖", "https://www.duitang.com/search/?keyword=%E5%83%8F%E7%B4%A0%E7%94%BB", "国内·综合", "🇨🇳"),

    // 国外 — 小尺寸像素画（100px以下）
    WebSite("Pixel Art Resources", "https://www.pixelartresources.com/", "小尺寸·像素画", "🔹"),
    WebSite("16x16 RPG Icons", "https://opengameart.org/art-search-advanced?keys=16x16", "小尺寸·游戏图标", "🔹"),
    WebSite("Spriters Resource", "https://www.spriters-resource.com/", "小尺寸·游戏素材", "🔹"),
    WebSite("Pixilart", "https://pixilart.com/gallery/popular", "小尺寸·在线编辑", "🔹"),
    WebSite("Piskel", "https://www.piskelapp.com/", "小尺寸·动画像素", "🔹"),
    WebSite("Lospec Pixel Art", "https://lospec.com/pixel-art-tool/", "小尺寸·在线工具", "🔹"),

    // 国外 — 像素画
    WebSite("PixelJoint", "https://pixeljoint.com/pixels/", "国外·像素画", "🌍"),
    WebSite("Lospec Gallery", "https://lospec.com/pixel-art-gallery/", "国外·像素画", "🌍"),
    WebSite("Itch.io", "https://itch.io/game-assets/tag-pixel-art", "国外·像素画", "🌍"),
    WebSite("OpenGameArt", "https://opengameart.org/art-search-advanced?keys=pixel", "国外·游戏素材", "🌍"),

    // 国外 — 二次元
    WebSite("Danbooru", "https://danbooru.donmai.us/", "国外·二次元", "🌍"),
    WebSite("Konachan", "https://konachan.net/", "国外·二次元", "🌍"),
    WebSite("Safebooru", "https://safebooru.org/", "国外·二次元", "🌍"),

    // 国外 — 风景
    WebSite("Unsplash", "https://unsplash.com/s/photos/landscape", "国外·风景", "🌍"),
    WebSite("Pexels", "https://www.pexels.com/search/landscape/", "国外·风景", "🌍"),
    WebSite("Pinterest", "https://www.pinterest.com/search/pins/?q=pixel%20art", "国外·综合", "🌍"),
)

private fun loadCustomSites(context: Context): List<WebSite> {
    val prefs = context.getSharedPreferences("custom_sites", Context.MODE_PRIVATE)
    val json = prefs.getString("sites", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            WebSite(obj.getString("name"), obj.getString("url"), obj.getString("tag"), obj.getString("emoji"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveCustomSites(context: Context, sites: List<WebSite>) {
    val arr = JSONArray()
    sites.forEach { s ->
        arr.put(JSONObject().apply {
            put("name", s.name); put("url", s.url); put("tag", s.tag); put("emoji", s.emoji)
        })
    }
    context.getSharedPreferences("custom_sites", Context.MODE_PRIVATE)
        .edit().putString("sites", arr.toString()).apply()
}

/**
 * 「网站」子页：图源网站网格（可添加/删除自定义站点），点击进入内置浏览器导入图片。
 * 与「精选」图源流共同构成首页「在线」统一入口。
 */
@Composable
fun WebsitePickerContent(navController: NavController, context: Context) {
    val theme = LocalAppTheme.current
    var selectedSite by remember { mutableStateOf<WebSite?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var customSites by remember { mutableStateOf(loadCustomSites(context)) }

    // Group by tag (custom sites as separate group)
    val allSites = webSites + customSites
    val grouped = allSites.groupBy { it.tag }

    if (showAddDialog) {
        var nameInput by remember { mutableStateOf("") }
        var urlInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加网站", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput, onValueChange = { nameInput = it },
                        label = { Text("名称") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = urlInput, onValueChange = { urlInput = it },
                        label = { Text("网址 (https://...)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nameInput.isNotBlank() && urlInput.isNotBlank()) {
                        val site = WebSite(nameInput.trim(), urlInput.trim(), "我的收藏", "⭐")
                        customSites = customSites + site
                        saveCustomSites(context, customSites)
                        showAddDialog = false
                    }
                }) { Text("添加", color = theme.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = theme.surface
        )
    }

    if (selectedSite != null) {
        // WebView page
        OnlineBrowserPage(
            site = selectedSite!!,
            onBack = { selectedSite = null },
            onImport = { url ->
                try {
                    val uri = if (url.startsWith("data:")) {
                        val clean = url.replace(" ", "").replace("\n", "").replace("\r", "")
                        val b64 = clean.substringAfter(";base64,").substringAfter("base64,")
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        val file = File(context.cacheDir, "web_import_${System.currentTimeMillis()}.png")
                        file.writeBytes(bytes)
                        Uri.fromFile(file).toString()
                    } else url
                    navController.navigate(Screen.PixelPreview.create(Uri.encode(uri)))
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    // Website selection grid
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 添加网站按钮
        item(span = { GridItemSpan(2) }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("➕", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("添加网站", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.accent)
                }
            }
        }
        grouped.forEach { (tag, sites) ->
            item(span = { GridItemSpan(2) }) {
                Text(
                    tag,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.gold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(sites) { site ->
                val isCustom = site in customSites
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedSite = site },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Box {
                        Column(Modifier.padding(14.dp)) {
                            Text(site.emoji, fontSize = 24.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                site.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.onBg
                            )
                            Text(
                                site.url.removePrefix("https://").take(30),
                                fontSize = 10.sp,
                                color = theme.muted,
                                maxLines = 1
                            )
                        }
                        if (isCustom) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable {
                                        customSites = customSites - site
                                        saveCustomSites(context, customSites)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Delete, "删除", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineBrowserPage(
    site: WebSite,
    onBack: () -> Unit,
    onImport: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    var currentUrl by remember { mutableStateOf(site.url) }
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar with back + import
        FrostedGlassBox(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            tintColor = theme.bg,
            blurRadius = 12.dp,
            alpha = 0.8f
        ) {
            IconButton(onClick = {
                if (webView?.canGoBack() == true) webView?.goBack() else onBack()
            }, modifier = Modifier.align(Alignment.CenterStart).size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = theme.gold, modifier = Modifier.size(22.dp))
            }
            Text(
                site.emoji + " " + site.name,
                color = theme.gold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            // Import button
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.accent)
                    .clickable {
                        webView?.evaluateJavascript("""
                            (function(){
                                function send(url){Android.onImageData(url);}
                                function tryCanvas(img){
                                    try{
                                        var c=document.createElement('canvas');
                                        var s=Math.min(img.naturalWidth,img.naturalHeight,256);
                                        var r=Math.min(s/img.naturalWidth,s/img.naturalHeight);
                                        c.width=Math.round(img.naturalWidth*r);
                                        c.height=Math.round(img.naturalHeight*r);
                                        c.getContext('2d').drawImage(img,0,0,c.width,c.height);
                                        send(c.toDataURL('image/png'));
                                    }catch(e){send(img.src);}
                                }
                                var og=document.querySelector('meta[property="og:image"]');
                                if(og&&og.content){send(og.content);return;}
                                var imgs=document.querySelectorAll('img');
                                for(var i=0;i<imgs.length;i++){
                                    if(imgs[i].naturalWidth>50){tryCanvas(imgs[i]);return;}
                                }
                                send(location.href);
                            })();
                        """.trimIndent(), null)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("📥 导入", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
            }
        }

        // WebView
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    // Full browser environment
                    settings.javaScriptEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.setSupportZoom(true)
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    settings.defaultTextEncodingName = "UTF-8"
                    settings.setGeolocationEnabled(true)
                    settings.loadsImagesAutomatically = true
                    settings.blockNetworkImage = false
                    // Desktop user agent for better site compatibility
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    // Enable cookies
                    val cookieMgr = android.webkit.CookieManager.getInstance()
                    cookieMgr.setAcceptCookie(true)
                    cookieMgr.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) { url?.let { currentUrl = it }; isLoading = true }
                        override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?) = false
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) { isLoading = false
                            view?.evaluateJavascript("""
                                (function(){
                                    var s=document.createElement('style');
                                    s.textContent='*,img,div,a{-webkit-touch-callout:default!important;-webkit-user-select:auto!important;user-select:auto!important;pointer-events:auto!important;}img{touch-action:auto!important;}';
                                    document.head.appendChild(s);
                                    document.oncontextmenu=null;document.ondragstart=null;document.onselectstart=null;
                                    var imgs=document.querySelectorAll('img');imgs.forEach(function(i){i.draggable=true;i.style.pointerEvents='auto';i.oncontextmenu=null;i.style.webkitTouchCallout='default';});
                                    var links=document.querySelectorAll('a');links.forEach(function(l){l.draggable=true;l.style.pointerEvents='auto';});
                                })();
                            """.trimIndent(), null)
                        }
                    }
                    setOnLongClickListener {
                        val hit = hitTestResult
                        if (hit.type == android.webkit.WebView.HitTestResult.IMAGE_TYPE || hit.type == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                            val imgUrl = hit.extra ?: return@setOnLongClickListener false
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
                                        val req = Request.Builder().url(imgUrl).header("Referer", site.url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14)").build()
                                        val resp = client.newCall(req).execute()
                                        val bytes = resp.body?.bytes() ?: throw Exception("empty")
                                        val file = File(context.cacheDir, "web_${System.currentTimeMillis()}.jpg")
                                        file.writeBytes(bytes)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            onImport(Uri.fromFile(file).toString())
                                        }
                                    } catch (e: Exception) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "下载失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            true
                        } else false
                    }
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onImageData(url: String) {
                            webView?.post { onImport(url) }
                        }
                    }, "Android")
                    webView = this
                    loadUrl(site.url)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(theme.surface)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.accent,
                    trackColor = theme.surface
                )
            }
        }
    }
}
