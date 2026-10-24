@file:OptIn(ExperimentalMaterial3Api::class)

package app.grab

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ───────────── الثيم: ألوان ديناميكية من خلفية الموبايل (أندرويد 12+) ─────────────

private val LightFallback = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565F71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF146C2E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4EED0), onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBDC7DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF6DD58C), onTertiary = Color(0xFF003915),
    tertiaryContainer = Color(0xFF0F5223), onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val cs = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = cs, shapes = AppShapes, content = content)
}

// ───────────── أيقونة التحميل (مش موجودة في المكتبة الأساسية) ─────────────

private val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder("download", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 4f); lineTo(12f, 15f)
            moveTo(7f, 10.5f); lineTo(12f, 15.5f); lineTo(17f, 10.5f)
            moveTo(5f, 20f); lineTo(19f, 20f)
        }
    }.build()
}

// ───────────── صور ─────────────

object Img { val cache = android.util.LruCache<String, Bitmap>(40) }

@Composable
fun Thumb(url: String?, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(url?.let { Img.cache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it, null, o) }?.also { Img.cache.put(url, it) }
            } catch (e: Throwable) { null }
        }
    }
    Box(modifier) { bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
}

fun fmt(s: Int): String {
    if (s <= 0) return ""
    val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

// ───────────── عناصر مشتركة (من غير كروت: كله مفتوح على الخلفية) ─────────────

@Composable
private fun flat() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun fieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme.surfaceContainerHigh
    return TextFieldDefaults.colors(
        focusedContainerColor = c, unfocusedContainerColor = c, disabledContainerColor = c,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent
    )
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp))

@Composable
private fun Footnote(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

@Composable
private fun Divide() =
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun Choice(opts: List<String>, sel: Int, modifier: Modifier = Modifier, on: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        opts.forEachIndexed { i, o ->
            SegmentedButton(i == sel, { on(i) }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(o) }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(42.dp), tint = cs.onSecondaryContainer)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// شريط تقدم عريض ومدور. p = null يعني "جاري التجهيز"
@Composable
private fun Bar(p: Float?) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(cs.secondaryContainer)) {
        if (p != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0.02f, 1f)).clip(CircleShape).background(cs.primary))
        } else {
            val t = rememberInfiniteTransition(label = "ind")
            val x by t.animateFloat(-0.4f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
            Box(Modifier.offset(x = maxWidth * x).fillMaxHeight().width(maxWidth * 0.4f).clip(CircleShape).background(cs.primary))
        }
    }
}

// صفحة بعنوان كبير بيصغر مع السكرول (زي تطبيقات جوجل)
@Composable
private fun Page(title: String, bottom: Dp, content: LazyListScope.() -> Unit) {
    val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val cs = MaterialTheme.colorScheme
    Scaffold(
        modifier = Modifier.nestedScroll(sb.nestedScrollConnection),
        containerColor = cs.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                scrollBehavior = sb,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surfaceContainer)
            )
        }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = bottom + 24.dp), content = content)
    }
}

// ───────────── الهيكل + شريط التنقل ─────────────

private class NavTab(val label: String, val on: ImageVector, val off: ImageVector)

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val inc by Incoming.v.collectAsState()
    LaunchedEffect(inc) { if (inc != null) tab = 0 }
    val hap = LocalHapticFeedback.current
    val tabs = remember {
        listOf(
            NavTab("الرئيسية", Icons.Rounded.Home, Icons.Outlined.Home),
            NavTab("المكتبة", Icons.Rounded.PlayArrow, Icons.Outlined.PlayArrow),
            NavTab("الإعدادات", Icons.Rounded.Settings, Icons.Outlined.Settings)
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { hap.performHapticFeedback(HapticFeedbackType.TextHandleMove); tab = i },
                        icon = { Icon(if (tab == i) t.on else t.off, null) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        val bottom = pad.calculateBottomPadding()
        Crossfade(tab, Modifier.fillMaxSize(), label = "tab") {
            when (it) { 0 -> HomeScreen(bottom); 1 -> LibraryScreen(bottom); else -> SettingsScreen(bottom) }
        }
    }
}

// ───────────── الرئيسية ─────────────

@Composable
fun HomeScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val hap = LocalHapticFeedback.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var url by remember { mutableStateOf(Prefs.draft) }
    LaunchedEffect(url) { Prefs.draft = url }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    var subs by remember { mutableStateOf(Prefs.subs) }
    var playlist by remember { mutableStateOf(false) }
    val inc by Incoming.v.collectAsState()
    val ready by Repo.ready
    val engineSt by Repo.status

    fun newTask(title: String, thumb: String?, pl: Boolean) =
        Task(System.nanoTime().toString(), url.trim(), title, thumb, kind == 1, h, subs, pl)

    fun direct() {
        val u = url.trim(); if (u.isEmpty()) return
        Repo.enqueue(ctx, newTask(u, null, playlist)); url = ""; err = ""; info = null
    }

    fun go() {
        val u = url.trim()
        if (u.isEmpty()) return
        err = ""; info = null
        if (playlist) { direct(); return }
        loading = true
        scope.launch {
            try { info = Repo.fetch(u); h = 0 } catch (e: Throwable) { err = Repo.clean(e) }
            loading = false
        }
    }
    LaunchedEffect(inc) { inc?.let { url = it; Incoming.v.value = null; go() } }

    Page("التحميلات", bottom) {
        item {
            TextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp), colors = fieldColors(),
                placeholder = { Text("الصق رابط الفيديو هنا") },
                trailingIcon = {
                    if (url.isEmpty()) TextButton({ clip.getText()?.text?.let { url = urlOf(it) } }) { Text("لصق") }
                    else IconButton({ url = "" }) { Icon(Icons.Rounded.Close, "مسح") }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() })
            )
        }
        item {
            ListItem(
                headlineContent = { Text("قائمة تشغيل") },
                supportingContent = { Text("حمّل كل الفيديوهات مباشرة") },
                trailingContent = { Switch(playlist, { playlist = it }) },
                colors = flat(), modifier = Modifier.clickable { playlist = !playlist }
            )
        }
        item {
            Button(
                onClick = { hap.performHapticFeedback(HapticFeedbackType.LongPress); go() },
                enabled = url.isNotBlank() && !loading,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(10.dp)); Text("جاري الجلب…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(DownloadIcon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                    Text(if (playlist) "تحميل القائمة" else "جلب", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (!ready) item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(if (engineSt.isNotEmpty()) engineSt else "جاري تجهيز المحرك…",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        if (err.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(20.dp), tint = cs.error)
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = cs.error)
                }
                TextButton({ direct() }) { Text("حمّل مباشرة") }
            }
        }

        info?.let { i ->
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Thumb(i.thumbnail, Modifier.fillMaxWidth().aspectRatio(16 / 9f)
                        .clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(i.title ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(i.uploader, fmt((i.duration as Number?)?.toInt() ?: 0).ifEmpty { null }).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    Choice(listOf("فيديو", "صوت"), kind) { kind = it }
                    if (kind == 0) {
                        val hs = i.formats?.map { (it.height as Number?)?.toInt() ?: 0 }?.filter { it > 0 }
                            ?.distinct()?.sortedDescending()?.take(6) ?: emptyList()
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (listOf(0) + hs).forEach { q ->
                                FilterChip(
                                    selected = h == q, onClick = { h = q },
                                    label = { Text(if (q == 0) "الأفضل" else "${q}p") },
                                    leadingIcon = if (h == q) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null
                                )
                            }
                        }
                        ListItem(
                            headlineContent = { Text("تضمين الترجمة") },
                            trailingContent = { Switch(subs, { subs = it }) },
                            colors = flat(), modifier = Modifier.clickable { subs = !subs }.padding(horizontal = 0.dp)
                        )
                    } else Text("الصيغة: ${Prefs.audioFmt.uppercase()} (من الإعدادات)",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Button(
                        onClick = {
                            hap.performHapticFeedback(HapticFeedbackType.LongPress)
                            Repo.enqueue(ctx, newTask(i.title ?: url, i.thumbnail, false)); info = null; url = ""
                        },
                        shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(DownloadIcon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                        Text("تحميل", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (Repo.tasks.isNotEmpty()) {
            item { SectionLabel("قيد التنفيذ") }
            items(Repo.tasks, key = { it.id }) { TaskRow(it) }
        } else if (info == null && err.isEmpty()) item {
            EmptyState(DownloadIcon, "جاهز للتحميل",
                "شارك أي رابط من يوتيوب أو إنستجرام أو فيسبوك أو تيك توك لتطبيق Grab وهيبدأ فورًا.")
        }
    }
}

@Composable
fun TaskRow(t: Task) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val p by animateFloatAsState(t.progress, label = "prog")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Thumb(t.thumb, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (t.st == St.Running || t.st == St.Queued) Bar(if (t.st == St.Running && t.progress > 0f) p else if (t.st == St.Queued) 0f else null)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (t.st == St.Done) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = cs.tertiary)
                Text(when (t.st) {
                    St.Queued -> "في الانتظار"
                    St.Running -> if (t.msg.isNotEmpty() && t.progress == 0f) t.msg else "${(t.progress * 100).toInt()}%" + if (t.eta > 0) " • باقي ${t.eta}ث" else ""
                    St.Failed -> t.msg
                    St.Canceled -> "أُلغي"
                    St.Done -> "تم"
                }, style = MaterialTheme.typography.labelMedium,
                    color = if (t.st == St.Failed) cs.error else cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when (t.st) {
            St.Running, St.Queued -> FilledTonalIconButton({ Repo.cancel(t) }) { Icon(Icons.Rounded.Close, "إلغاء") }
            St.Failed, St.Canceled -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton({ Repo.enqueue(ctx, t) }) { Icon(Icons.Rounded.Refresh, "إعادة") }
                IconButton({ Repo.drop(t) }) { Icon(Icons.Rounded.Delete, "حذف") }
            }
            else -> {}
        }
    }
}

// ───────────── المكتبة ─────────────

private fun openItem(c: Context, i: Item) = try {
    c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(i.uri), if (i.audio) "audio/*" else "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
} catch (_: Throwable) {}

private fun shareItem(c: Context, i: Item) = try {
    val s = Intent(Intent.ACTION_SEND).setType(if (i.audio) "audio/*" else "video/*")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(i.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(s, null))
} catch (_: Throwable) {}

@Composable
fun LibraryScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    val list = Repo.library.filter { it.title.contains(q, true) }
    Page("المكتبة", bottom) {
        item {
            TextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp), colors = fieldColors(),
                placeholder = { Text("ابحث في التنزيلات") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Rounded.Close, "مسح") } }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        if (list.isEmpty()) item {
            EmptyState(Icons.Rounded.PlayArrow, if (q.isEmpty()) "المكتبة فاضية" else "مفيش نتائج",
                if (q.isEmpty()) "اللي تحمّله هيظهر هنا." else "جرّب كلمة تانية.")
        }
        items(list, key = { it.uri }) { i ->
            ListItem(
                modifier = Modifier.clickable { openItem(ctx, i) },
                colors = flat(),
                leadingContent = {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                        if (i.thumb != null) Thumb(i.thumb, Modifier.fillMaxSize())
                        else if (i.audio) Text("♪", style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer)
                        else Icon(Icons.Rounded.PlayArrow, null, tint = cs.onPrimaryContainer)
                    }
                },
                headlineContent = { Text(i.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(if (i.audio) "صوت" else "فيديو") },
                trailingContent = {
                    Row {
                        IconButton({ shareItem(ctx, i) }) { Icon(Icons.Rounded.Share, "مشاركة") }
                        IconButton({ Repo.remove(i) }) { Icon(Icons.Rounded.Delete, "حذف") }
                    }
                }
            )
        }
    }
}

// ───────────── الإعدادات ─────────────

@Composable
fun SettingsScreen(bottom: Dp) {
    val cs = MaterialTheme.colorScheme
    val ready by Repo.ready
    val st by Repo.status
    val hasCk by Repo.hasCookies
    var ver by remember { mutableStateOf("") }
    var ckMsg by remember { mutableStateOf("") }
    LaunchedEffect(ready, st) { if (ready) ver = withContext(Dispatchers.IO) { Repo.version() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) ckMsg = if (Repo.importCookies(u)) "تم استيراد الكوكيز ✓" else "الملف مش بصيغة cookies.txt صحيحة"
    }
    val fmts = listOf("mp3", "m4a", "opus")

    Page("الإعدادات", bottom) {
        item { SectionLabel("التحميل") }
        item {
            ListItem(headlineContent = { Text("تحميل على الواي فاي فقط") },
                trailingContent = { Switch(Prefs.wifiOnly, { Prefs.wifiOnly = it; Prefs.save() }) },
                colors = flat(), modifier = Modifier.clickable { Prefs.wifiOnly = !Prefs.wifiOnly; Prefs.save() })
        }
        item {
            ListItem(headlineContent = { Text("تضمين الترجمة افتراضيًا") },
                trailingContent = { Switch(Prefs.subs, { Prefs.subs = it; Prefs.save() }) },
                colors = flat(), modifier = Modifier.clickable { Prefs.subs = !Prefs.subs; Prefs.save() })
        }
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                ListItem(headlineContent = { Text("صيغة الصوت") }, colors = flat())
                Choice(fmts.map { it.uppercase() }, fmts.indexOf(Prefs.audioFmt), Modifier.padding(horizontal = 16.dp)) {
                    Prefs.audioFmt = fmts[it]; Prefs.save()
                }
            }
        }
        item {
            Column {
                ListItem(headlineContent = { Text("عدد التحميلات المتزامنة") }, colors = flat())
                Choice(listOf("1", "2", "3"), Prefs.concurrent - 1, Modifier.padding(horizontal = 16.dp)) {
                    Prefs.concurrent = it + 1; Prefs.save()
                }
                Footnote("عدد التحميلات المتزامنة يسري بعد إعادة تشغيل التطبيق.")
            }
        }
        item { Divide() }

        item { SectionLabel("المحرك") }
        item {
            ListItem(headlineContent = { Text("محرك التحميل (yt-dlp)") },
                supportingContent = if (st.isNotEmpty()) ({ Text(st) }) else null,
                trailingContent = { Text(if (ver.isNotEmpty()) ver else "—", color = cs.onSurfaceVariant) },
                colors = flat())
        }
        item {
            Column {
                FilledTonalButton({ Repo.update() }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("تحديث الآن")
                }
                Footnote("بيتحدّث تلقائيًا كل 24 ساعة، وكمان لو التحميل فشل.")
            }
        }
        item { Divide() }

        item { SectionLabel("كوكيز يوتيوب") }
        item {
            ListItem(headlineContent = { Text("الحالة") },
                trailingContent = {
                    Text(if (hasCk) "مفعّلة" else "غير مفعّلة", fontWeight = FontWeight.Medium,
                        color = if (hasCk) cs.tertiary else cs.onSurfaceVariant)
                }, colors = flat())
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ckMsg.isNotEmpty()) Text(ckMsg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ picker.launch(arrayOf("*/*")) }) { Text("استيراد") }
                    if (hasCk) OutlinedButton({ Repo.clearCookies(); ckMsg = "" }) { Text("حذف") }
                }
            }
        }
        item {
            Footnote("لو يوتيوب طلب \"تأكيد إنك مش بوت\" فاستيراد الكوكيز هو الحل الأضمن. صدّر ملف cookies.txt من متصفحك (إضافة Get cookies.txt LOCALLY) وبعدين استورده هنا. يفضّل تستخدم حساب ثانوي.")
        }
    }
}
