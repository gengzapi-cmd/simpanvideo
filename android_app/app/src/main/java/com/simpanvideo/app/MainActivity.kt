package com.simpanvideo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDLRequest
import android.content.Context
import java.io.File
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.app.PendingIntent
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.annotation.SuppressLint
import androidx.compose.runtime.mutableStateListOf

val BgColor = Color(0xFF0D1117)
val SurfaceColor = Color(0xFF161B22)
val BorderColor = Color(0x33FFFFFF)
val CyanWarm = Color(0xFF00E5FF)
val TextMuted = Color(0xFF8B949E)
val GradientPrimary = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B0FF)))

val PoppinsFont = FontFamily(
    Font(R.font.font_regular, FontWeight.Normal),
    Font(R.font.font_medium, FontWeight.Medium),
    Font(R.font.font_semibold, FontWeight.SemiBold),
    Font(R.font.font_bold, FontWeight.Bold),
    Font(R.font.font_extrabold, FontWeight.ExtraBold),
    Font(R.font.font_black, FontWeight.Black)
)

fun Modifier.glow(color: Color = CyanWarm, alpha: Float = 0.2f, radius: Float = 20f) = this.drawBehind {
    drawCircle(color = color.copy(alpha = alpha), radius = size.width / 2 + radius, center = Offset(size.width / 2, size.height / 2))
}

enum class Tab { HOME, DOWNLOADS, SETTINGS }

object NavigationState {
    val currentTab = mutableStateOf(Tab.HOME)
}

enum class TaskStatus { DOWNLOADING, COMPLETED, FAILED }

data class DownloadTask(
    val id: String,
    val title: String,
    val url: String,
    val formatId: String,
    val isAudio: Boolean,
    var progress: Int = 0,
    var speed: String = "",
    var eta: String = "",
    var size: String = "",
    var status: TaskStatus = TaskStatus.DOWNLOADING,
    val timestamp: Long = System.currentTimeMillis()
)

object DownloadManager {
    val activeTasks = mutableStateListOf<DownloadTask>()
    val historyTasks = mutableStateListOf<DownloadTask>()
    
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("download_history", Context.MODE_PRIVATE)
        val json = prefs.getString("history", "[]") ?: "[]"
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<DownloadTask>>() {}.type
            val list = com.google.gson.Gson().fromJson<List<DownloadTask>>(json, type) ?: emptyList()
            historyTasks.clear()
            historyTasks.addAll(list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun saveHistory(context: Context) {
        val prefs = context.getSharedPreferences("download_history", Context.MODE_PRIVATE)
        val json = com.google.gson.Gson().toJson(historyTasks.toList())
        prefs.edit().putString("history", json).apply()
    }
    
    fun addTask(task: DownloadTask) {
        activeTasks.add(task)
    }
    
    fun updateTaskProgress(id: String, progress: Int, speed: String, eta: String, size: String) {
        val task = activeTasks.find { it.id == id }
        if (task != null) {
            task.progress = progress
            task.speed = speed
            task.eta = eta
            task.size = size
            val index = activeTasks.indexOf(task)
            if (index != -1) {
                activeTasks[index] = task.copy()
            }
        }
    }
    
    fun completeTask(context: Context, id: String) {
        val task = activeTasks.find { it.id == id }
        if (task != null) {
            activeTasks.remove(task)
            val completedTask = task.copy(status = TaskStatus.COMPLETED, progress = 100)
            historyTasks.add(0, completedTask)
            saveHistory(context)
        }
    }
    
    fun failTask(context: Context, id: String, error: String) {
        val task = activeTasks.find { it.id == id }
        if (task != null) {
            activeTasks.remove(task)
            val failedTask = task.copy(status = TaskStatus.FAILED, speed = "Gagal: $error")
            historyTasks.add(0, failedTask)
            saveHistory(context)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val target = intent?.getStringExtra("navigate_to")
        if (target == "downloads") {
            NavigationState.currentTab.value = Tab.DOWNLOADS
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadManager.init(this)
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        setContent {
            val customTypography = Typography(
                bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = PoppinsFont),
                bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = PoppinsFont),
                bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = PoppinsFont),
                labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = PoppinsFont),
                labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = PoppinsFont),
                labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = PoppinsFont),
                titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = PoppinsFont),
                titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = PoppinsFont),
                titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = PoppinsFont)
            )
            MaterialTheme(
                colorScheme = darkColorScheme(background = BgColor, surface = SurfaceColor, primary = CyanWarm, onPrimary = Color.Black),
                typography = customTypography
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgColor) {
                    ProvideTextStyle(TextStyle(fontFamily = PoppinsFont)) {
                        val engineStatus by EngineState.status.collectAsState()
                        
                        when (engineStatus) {
                            EngineStatus.INITIALIZING -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val context = LocalContext.current
                                        val imageLoader = remember(context) {
                                            ImageLoader.Builder(context).components {
                                                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                                            }.build()
                                        }
                                        AsyncImage(
                                            model = R.drawable.engine,
                                            imageLoader = imageLoader,
                                            contentDescription = "Loading Engine",
                                            modifier = Modifier.size(60.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Menyiapkan Mesin...", fontFamily = PoppinsFont, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(if(EngineState.errorMessage.isBlank()) "Memuat dependensi inti" else EngineState.errorMessage, fontFamily = PoppinsFont, color = TextMuted, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            EngineStatus.ERROR -> {
                                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(painter = painterResource(id = android.R.drawable.ic_dialog_alert), contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Gagal Menyalakan Mesin", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(EngineState.errorMessage, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            EngineStatus.READY -> {
                                App()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun App() {
    var currentTab by NavigationState.currentTab
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
            Crossfade(
                targetState = currentTab, 
                label = "TabTransition",
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) { tab ->
                when (tab) {
                    Tab.HOME -> HomeScreen()
                    Tab.DOWNLOADS -> DownloadsScreen()
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
        }
        BottomNav(currentTab = currentTab, onTabSelected = { currentTab = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

fun startDownload(context: Context, videoUrl: String, formatId: String, title: String, isAudio: Boolean = false) {
    val taskId = "task_${System.currentTimeMillis()}"
    val task = DownloadTask(id = taskId, title = title, url = videoUrl, formatId = formatId, isAudio = isAudio)
    DownloadManager.addTask(task)

    Toast.makeText(context, "Memulai unduhan: $title", Toast.LENGTH_SHORT).show()
    CoroutineScope(Dispatchers.IO).launch {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "simpanvideo_downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Unduhan", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        val notificationId = title.hashCode()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "downloads")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Menyiapkan unduhan...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, builder.build())

        try {
            val request = YoutubeDLRequest(videoUrl)
            request.addOption("-f", formatId)
            
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            request.addOption("-o", "${downloadDir.absolutePath}/$safeTitle.%(ext)s")

            var lastProgress = -1
            
            val speedRegex = Regex("""at\s+([\d.]+\s*\w+/s)""")
            val etaRegex = Regex("""ETA\s+([\d:]+)""")
            val sizeRegex = Regex("""of\s+(~\s*)?([\d.]+\s*\w+)""")

            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request, taskId) { progress, _, line ->
                val lineStr = line ?: ""
                val currentProgress = progress.toInt()
                val speedMatch = speedRegex.find(lineStr)?.groupValues?.get(1)?.trim() ?: ""
                val etaMatch = etaRegex.find(lineStr)?.groupValues?.get(1)?.trim() ?: ""
                val sizeMatch = sizeRegex.find(lineStr)?.groupValues?.get(2)?.trim() ?: ""
                
                CoroutineScope(Dispatchers.Main).launch {
                    DownloadManager.updateTaskProgress(taskId, currentProgress, speedMatch, etaMatch, sizeMatch)
                }

                if (currentProgress != lastProgress && currentProgress >= 0) {
                    lastProgress = currentProgress
                    val detailText = when {
                        speedMatch.isNotEmpty() && etaMatch.isNotEmpty() -> "$speedMatch · Sisa $etaMatch"
                        speedMatch.isNotEmpty() -> speedMatch
                        else -> "Mengunduh..."
                    }
                    builder.setProgress(100, currentProgress, false)
                        .setContentText("Mengunduh: $currentProgress% ($detailText)")
                    notificationManager.notify(notificationId, builder.build())
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                DownloadManager.completeTask(context, taskId)
            }

            builder.setContentText("Selesai diunduh!")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
            notificationManager.notify(notificationId, builder.build())
            withContext(Dispatchers.Main) { Toast.makeText(context, "Selesai mengunduh $title", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.message ?: "Kesalahan tidak dikenal"
            
            CoroutineScope(Dispatchers.Main).launch {
                DownloadManager.failTask(context, taskId, errorMsg)
            }

            builder.setContentText("Gagal: $errorMsg")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setSmallIcon(android.R.drawable.stat_notify_error)
            notificationManager.notify(notificationId, builder.build())
            withContext(Dispatchers.Main) { Toast.makeText(context, "Gagal mengunduh: $errorMsg", Toast.LENGTH_LONG).show() }
        }
    }
}

fun formatDuration(durationSeconds: Int): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}

fun getYoutubeId(url: String): String? {
    val regex = Regex("""(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?)\/|.*[?&]v=)|youtu\.be\/)([^"&?\/ ]{11})""")
    return regex.find(url)?.groupValues?.get(1)
}

fun getTikTokId(url: String): String? {
    val regex = Regex("""\/video\/(\d+)""")
    return regex.find(url)?.groupValues?.get(1)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoWebView(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                
                webViewClient = android.webkit.WebViewClient()
                webChromeClient = android.webkit.WebChromeClient()
                loadUrl(url)
            }
        },
        modifier = modifier
    )
}

@Composable
fun HomeScreen() {
    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var mediaInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var openOptions by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    fun analyzeUrl() {
        if (urlInput.isBlank()) return
        isLoading = true
        errorMessage = null
        mediaInfo = null
        isPlaying = false
        
        coroutineScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest(urlInput)
                    req.addOption("--no-playlist")
                    req.addOption("--no-warnings")
                    req.addOption("--compat-options", "no-youtube-unavailable-videos")
                    req.addOption("-R", "1") // Retries = 1
                    req.addOption("--socket-timeout", "5") // Timeout 5 detik
                    req.addOption("--no-check-certificate") // Lewati validasi SSL
                    req.addOption("--no-check-certificates")
                    req.addOption("--flat-playlist") // Cegah load detail item playlist
                    req.addOption("--skip-download")
                    req.addOption("--quiet")
                    YoutubeDL.getInstance().getInfo(req)
                }
                mediaInfo = info
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Gagal: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 20.dp, end = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).glow(alpha = 0.3f).clip(RoundedCornerShape(16.dp)).background(GradientPrimary), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("SimpanVideo", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color.White, letterSpacing = (-0.5).sp)
                    Text("Unduh tanpa batas", fontSize = 10.sp, color = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PLATFORM DIDUKUNG", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted, letterSpacing = 0.5.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val platforms = listOf(
                Triple("TikTok", R.drawable.ic_tiktok, Color(0xFF000000)), // Box Hitam pekat
                Triple("YouTube", R.drawable.ic_youtube, Color.White)      // Box Putih
            )
            platforms.forEach { (name, resId, boxColor) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clip(RoundedCornerShape(24.dp)).background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(24.dp)).clickable { }.padding(vertical = 10.dp)) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(boxColor), contentAlignment = Alignment.Center) {
                        val iconTint = if (name == "TikTok") Color.White else Color.Unspecified
                        Icon(painterResource(resId), contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(name, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(32.dp)).padding(16.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BgColor).border(1.dp, BorderColor, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // BasicTextField asli yang bisa diketik
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = PoppinsFont),
                        singleLine = true,
                        maxLines = 1,
                        cursorBrush = SolidColor(CyanWarm),
                        decorationBox = { innerTextField ->
                            if (urlInput.isEmpty()) {
                                Text("Tempel link video di sini…", color = TextMuted, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                    
                    Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x3300E5FF)).clickable { 
                        clipboardManager.getText()?.text?.let { 
                            urlInput = it 
                            analyzeUrl()
                        }
                    }.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("PASTE", color = CyanWarm, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).glow(alpha = 0.2f).background(GradientPrimary).clickable { analyzeUrl() }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Menganalisa...", color = Color(0xFF050505), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analisa & Unduh", color = Color(0xFF050505), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        if (errorMessage != null) {
            Text(errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
        }

        if (mediaInfo != null) {
            val info = mediaInfo!!
            val isTikTok = info.extractor?.contains("tiktok", ignoreCase = true) == true
            
            // Logika Hapus Tagar untuk YouTube
            val finalTitle = if (isTikTok) {
                info.title ?: "Tanpa Judul"
            } else {
                info.title?.replace(Regex("#\\S+"), "")?.trim() ?: "Tanpa Judul"
            }
            
            val durationStr = formatDuration(info.duration)
            val viewsStr = formatNumber(info.viewCount?.toString()?.toLongOrNull() ?: 0L)
            val likesStr = formatNumber(info.likeCount?.toString()?.toLongOrNull() ?: 0L)

            Box(modifier = Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(32.dp)).background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(32.dp))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    
                    val youtubeId = getYoutubeId(info.webpageUrl ?: urlInput)
                    val tiktokId = getTikTokId(info.webpageUrl ?: urlInput)
                    val embedUrl = when {
                        youtubeId != null -> "https://www.youtube.com/embed/$youtubeId?autoplay=1"
                        tiktokId != null -> "https://www.tiktok.com/embed/v2/$tiktokId"
                        else -> info.webpageUrl ?: urlInput
                    }

                    // Thumbnail Dinamis
                    val isPortrait = isTikTok
                    Box(
                        modifier = Modifier
                            .then(if (isPortrait) Modifier.fillMaxWidth(0.7f).padding(top = 16.dp).clip(RoundedCornerShape(20.dp)).aspectRatio(9f/16f) else Modifier.fillMaxWidth().aspectRatio(16f/9f))
                            .background(Color(0xFF2A1C30))
                    ) {
                        if (isPlaying) {
                            VideoWebView(url = embedUrl, modifier = Modifier.fillMaxSize())
                        } else {
                            // Gambar Asli dari yt-dlp
                            AsyncImage(
                                model = info.thumbnail,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
                            Row(modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x80000000)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(if (isTikTok) R.drawable.ic_tiktok_mini else R.drawable.ic_youtube), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTikTok) "TikTok" else "YouTube", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x80000000)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(durationStr, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            
                            Box(modifier = Modifier.align(Alignment.Center).size(64.dp).glow(Color.White, 0.1f, 10f).clip(CircleShape).background(Color.White).clickable { isPlaying = true }, contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_play_large), contentDescription = null, tint = CyanWarm, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(18.dp)) {
                        // Judul Video
                        Text(finalTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(info.uploader ?: "Unknown", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("$viewsStr views · $likesStr likes", color = TextMuted, fontSize = 12.sp)
                            }
                            Text("Buka", color = CyanWarm, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { })
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        val rotation by animateFloatAsState(if (openOptions) 180f else 0f)
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BgColor).border(1.dp, BorderColor, RoundedCornerShape(20.dp)).clickable { openOptions = !openOptions }.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pilih kualitas unduhan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation })
                        }

                        AnimatedVisibility(visible = openOptions, enter = expandVertically(spring(stiffness = Spring.StiffnessLow)), exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow))) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                val context = LocalContext.current
                                
                                data class DlOption(val label: String, val desc: String, val kind: String, val formatId: String)
                                
                                val downloadOptions = listOf(
                                    DlOption("Kualitas Terbaik", "Paling Jernih", "video", "bestvideo+bestaudio/best"),
                                    DlOption("2160p (4K)", "Ultra HD", "video", "bestvideo[height<=2160]+bestaudio/best"),
                                    DlOption("1440p (2K)", "Quad HD", "video", "bestvideo[height<=1440]+bestaudio/best"),
                                    DlOption("1080p", "Full HD", "video", "bestvideo[height<=1080]+bestaudio/best"),
                                    DlOption("720p", "High Definition", "video", "bestvideo[height<=720]+bestaudio/best"),
                                    DlOption("480p", "Standard", "video", "bestvideo[height<=480]+bestaudio/best"),
                                    DlOption("360p", "Low", "video", "bestvideo[height<=360]+bestaudio/best"),
                                    DlOption("240p", "Sangat Rendah", "video", "bestvideo[height<=240]+bestaudio/best"),
                                    DlOption("Kualitas Terburuk", "Paling Hemat Kuota", "video", "worstvideo+worstaudio/worst"),
                                    DlOption("Audio Terbaik", "Kualitas Tertinggi", "audio", "bestaudio/best"),
                                    DlOption("Audio 192kbps", "MP3/M4A", "audio", "bestaudio[abr<=192]/bestaudio"),
                                    DlOption("Audio 160kbps", "MP3/M4A", "audio", "bestaudio[abr<=160]/bestaudio"),
                                    DlOption("Audio 128kbps", "MP3/M4A", "audio", "bestaudio[abr<=128]/bestaudio"),
                                    DlOption("Audio 96kbps", "MP3/M4A", "audio", "bestaudio[abr<=96]/bestaudio"),
                                    DlOption("Audio 64kbps", "MP3/M4A", "audio", "bestaudio[abr<=64]/bestaudio"),
                                    DlOption("Audio Terburuk", "Paling Hemat Kuota", "audio", "worstaudio/worst")
                                )

                                downloadOptions.forEachIndexed { index, option ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x80161B22)).border(1.dp, BorderColor, RoundedCornerShape(20.dp)).clickable { }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (option.kind == "audio") Color(0x33A020F0) else Color(0x2200E5FF)), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(if (option.kind == "audio") R.drawable.ic_audio else R.drawable.ic_video), contentDescription = null, tint = CyanWarm, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(option.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text(option.desc, color = TextMuted, fontSize = 11.sp)
                                        }
                                        if (index == 0) {
                                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x3300E5FF)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                Text("REKOMENDASI", color = CyanWarm, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(GradientPrimary).clickable { 
                                            startDownload(context, urlInput, option.formatId, finalTitle, isAudio = (option.kind == "audio"))
                                        }, contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen() {
    var filter by remember { mutableStateOf("Semua") }
    val activeTasks = DownloadManager.activeTasks
    val historyTasks = DownloadManager.historyTasks

    val filteredHistory = remember(filter, historyTasks.size) {
        historyTasks.filter { task ->
            when (filter) {
                "Video" -> !task.isAudio
                "Audio" -> task.isAudio
                else -> true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 20.dp, end = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Unduhan", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(SurfaceColor).border(1.dp, BorderColor, CircleShape).clickable { }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.List, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf("Semua", "Video", "Audio").forEach { f ->
                val isSelected = filter == f
                val bgBrush: Brush = if(isSelected) GradientPrimary else SolidColor(SurfaceColor)
                Box(modifier = Modifier.padding(end = 10.dp).height(40.dp).clip(CircleShape).background(bgBrush).border(if(!isSelected) 1.dp else 0.dp, BorderColor, CircleShape).clickable { filter = f }.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    Text(f, color = if(isSelected) Color(0xFF050505) else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CyanWarm))
                Spacer(modifier = Modifier.width(10.dp))
                Text("SEDANG BERJALAN · ${activeTasks.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        
        if (activeTasks.isEmpty()) {
            Text("Tidak ada unduhan yang sedang berjalan.", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            activeTasks.forEach { task ->
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(24.dp)).background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(24.dp)).clickable { }.padding(14.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (task.isAudio) Color(0x33A020F0) else Color(0x2200E5FF)), contentAlignment = Alignment.Center) {
                                Icon(painterResource(if (task.isAudio) R.drawable.ic_audio else R.drawable.ic_video), contentDescription = null, tint = CyanWarm, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sizeSpeedEta = listOfNotNull(
                                    if(task.size.isNotEmpty()) task.size else null,
                                    if(task.speed.isNotEmpty()) task.speed else null,
                                    if(task.eta.isNotEmpty()) "Sisa ${task.eta}" else null
                                ).joinToString(" · ")
                                Text(if(sizeSpeedEta.isNotEmpty()) sizeSpeedEta else "Menyiapkan...", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape).background(BgColor)) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(task.progress / 100f).background(GradientPrimary))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${task.progress}%", color = CyanWarm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("HISTORY · ${filteredHistory.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)
        }
        Spacer(modifier = Modifier.height(14.dp))
        
        if (filteredHistory.isEmpty()) {
            Text("Belum ada riwayat unduhan.", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            filteredHistory.forEach { task ->
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(24.dp)).background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(24.dp)).clickable { }.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (task.isAudio) Color(0x33A020F0) else Color(0x2200E5FF)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(if (task.isAudio) R.drawable.ic_audio else R.drawable.ic_video), contentDescription = null, tint = CyanWarm, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(BgColor).border(1.dp, BorderColor, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                                    Text(if (task.isAudio) "Audio" else "Video", color = TextMuted, fontSize = 10.sp)
                                }
                                if(task.size.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(task.size, color = TextMuted, fontSize = 11.sp)
                                }
                            }
                            if (task.status == TaskStatus.FAILED && task.speed.isNotEmpty()) {
                                Text(task.speed, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        if (task.status == TaskStatus.FAILED) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, tint = CyanWarm, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var mode by remember { mutableStateOf("queue") }
    val infiniteTransition = rememberInfiniteTransition(label = "saweria")
    val colorOffset by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "saweria_color")
    val saweriaBrush = Brush.linearGradient(colors = listOf(Color(0xFFE5B05C), Color(0xFFFFD54F), Color(0xFFD69E4A)), start = Offset(0f, 0f), end = Offset(1000f * colorOffset, 1000f * colorOffset))
    
    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 20.dp, end = 20.dp)) {
        Text("Pengaturan", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
        Text("Sesuaikan SimpanVideo sesukamu", fontSize = 12.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(saweriaBrush).clickable { }.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Column {
                Box(modifier = Modifier.clip(CircleShape).background(Color(0x4D000000)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("DUKUNG DEVELOPER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Traktir secangkir kopi ☕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Text("SimpanVideo gratis & tanpa iklan. Bantu jaga tetap hidup lewat Saweria.", color = Color(0xE6FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF)).clickable { }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_coffee), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Dukung di Saweria", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text("UNDUHAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(24.dp))) {
            Column {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Mode unduhan", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgColor).border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(6.dp)) {
                        val qActive = mode == "queue"
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(qActive) GradientPrimary else SolidColor(Color.Transparent)).clickable { mode = "queue" }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("Antrian", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if(qActive) Color(0xFF050505) else TextMuted)
                        }
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(!qActive) GradientPrimary else SolidColor(Color.Transparent)).clickable { mode = "bulk" }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("Bulk Paralel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if(!qActive) Color(0xFF050505) else TextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(if (mode == "queue") "Satu unduhan dijalankan secara bergiliran." else "Beberapa unduhan berjalan bersamaan.", fontSize = 11.sp, color = TextMuted)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
                Row(modifier = Modifier.fillMaxWidth().clickable { }.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-retry saat gagal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Box(modifier = Modifier.width(44.dp).height(26.dp).clip(CircleShape).background(GradientPrimary).padding(2.dp)) {
                        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF050505)).align(Alignment.CenterEnd))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text("PENYIMPANAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(24.dp))) {
            Column {
                Column(modifier = Modifier.padding(18.dp).clickable { }) {
                    Text("Folder default", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgColor).border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("/Download/SimpanVideo", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                        Text("Ubah", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CyanWarm)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
                Row(modifier = Modifier.fillMaxWidth().clickable { }.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bersihkan cache", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("42 MB", fontSize = 12.sp, color = TextMuted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text("TENTANG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceColor).border(1.dp, BorderColor, RoundedCornerShape(24.dp))) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable { }.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Cek pembaruan", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("v1.0.0", fontSize = 12.sp, color = CyanWarm, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
                Row(modifier = Modifier.fillMaxWidth().clickable { }.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Kebijakan privasi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
                Row(modifier = Modifier.fillMaxWidth().clickable { }.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Beri rating ⭐", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun BottomNav(currentTab: Tab, onTabSelected: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, BgColor, BgColor))).padding(horizontal = 20.dp, vertical = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xE6161B22)).border(1.dp, BorderColor, CircleShape).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val tabs = listOf(Triple(Tab.HOME, "Beranda", Pair(R.drawable.ic_home_filled, R.drawable.ic_home_outline)), Triple(Tab.DOWNLOADS, "Unduhan", Pair(R.drawable.ic_downloads_filled, R.drawable.ic_downloads_outline)), Triple(Tab.SETTINGS, "Pengaturan", Pair(R.drawable.ic_settings_filled, R.drawable.ic_settings_outline)))
            tabs.forEach { (tab, label, icons) ->
                val active = currentTab == tab
                Box(modifier = Modifier.height(52.dp).then(if(active) Modifier.weight(1f) else Modifier.width(52.dp)).clip(CircleShape).background(if(active) GradientPrimary else SolidColor(Color.Transparent)).clickable { onTabSelected(tab) }, contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(if(active) icons.first else icons.second), contentDescription = null, tint = if(active) Color.Black else TextMuted, modifier = Modifier.size(24.dp))
                        AnimatedVisibility(visible = active, enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(), exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()) {
                            Row {
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label, color = Color(0xFF050505), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
