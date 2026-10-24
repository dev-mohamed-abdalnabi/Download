@file:OptIn(ExperimentalHazeMaterialsApi::class)

package app.grab

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yausername.youtubedl_android.mapper.VideoInfo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Blue = Color(0xFF0A84FF)
val Green = Color(0xFF34C759)
val Red = Color(0xFFFF453A)

// الزجاج بيشوف الخلفية بس (للكروت)، والشريط السفلي بيشوف الخلفية + المحتوى اللي بيعدّي تحته
val LocalFull = staticCompositionLocalOf<HazeState?> { null }
val LocalBg = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val cs = if (dark) darkColorScheme(primary = Blue, background = Color(0xFF05070D), surface = Color(0xFF1C1C1E),
        onBackground = Color.White, onSurface = Color.White)
    else lightColorScheme(primary = Color(0xFF007AFF), background = Color(0xFFF2F2F7), surface = Color.White,
        onBackground = Color(0xFF0B0B0F), onSurface = Color(0xFF0B0B0F))
    MaterialTheme(colorScheme = cs, content = content)
}

// ───────────── الخلفية (هادية، بس فيها تفاصيل عشان الزجاج يبان) ─────────────

@Composable
private fun Blobs(modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val k = if (dark) 1f else 0.7f
    Box(modifier.drawBehind {
        val w = size.width; val h = size.height
        fun glow(c: Color, cx: Float, cy: Float, r: Float, a: Float) =
            drawCircle(Brush.radialGradient(listOf(c.copy(a * k), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        fun orb(c1: Color, c2: Color, cx: Float, cy: Float, r: Float) =
            drawCircle(Brush.linearGradient(listOf(c1.copy(0.95f * k), c2.copy(0.95f * k)),
                Offset(cx - r, cy - r), Offset(cx + r, cy + r)), r, Offset(cx, cy))
        glow(Color(0xFF1E4FD8), w * 0.10f, h * 0.10f, w * 1.0f, 0.42f)
        glow(Color(0xFF5B3FD0), w * 1.00f, h * 0.50f, w * 0.9f, 0.32f)
        glow(Color(0xFF1B8FA8), w * 0.10f, h * 0.92f, w * 0.8f, 0.26f)
        orb(Color(0xFF3A7BD5), Color(0xFF1F3F8F), w * 0.84f, h * 0.17f, w * 0.15f)
        orb(Color(0xFF5E5CE6), Color(0xFF2A2A72), w * 0.12f, h * 0.52f, w * 0.11f)
        orb(Color(0xFF2AA3B8), Color(0xFF14506B), w * 0.74f, h * 0.72f, w * 0.19f)
        orb(Color(0xFF8E6BD8), Color(0xFF3B2A7A), w * 0.20f, h * 0.90f, w * 0.10f)
    })
}

@Composable
fun Backdrop(bg: HazeState, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, LocalBg provides bg) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Blobs(Modifier.fillMaxSize().hazeSource(bg))
            content()
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 28.dp, content: @Composable BoxScope.() -> Unit) =
    Glass(modifier, radius, false, LocalBg.current, content)

// ───────────── الزجاج ─────────────

@Composable
fun Glass(
    modifier: Modifier = Modifier, radius: Dp = 28.dp, strong: Boolean = false,
    state: HazeState? = LocalBg.current, content: @Composable BoxScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(radius)
    val container = if (dark) Color(0xFF1C1C1E) else Color.White
    val style = if (strong) HazeMaterials.thin(container) else HazeMaterials.ultraThin(container)
    val rim = Brush.linearGradient(
        listOf(Color.White.copy(if (dark) 0.50f else 0.95f), Color.White.copy(if (dark) 0.06f else 0.25f),
            Color.White.copy(if (dark) 0.03f else 0.15f), Color.White.copy(if (dark) 0.28f else 0.70f)),
        Offset(0f, 0f), Offset(500f, 700f))
    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(if (dark) 0.10f else 0.38f), Color.White.copy(if (dark) 0.02f else 0.10f)))
    Box(
        modifier
            .shadow(if (dark) 0.dp else 16.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x22000000))
            .clip(shape)
            .then(if (state != null) Modifier.hazeEffect(state, style)
                  else Modifier.background(if (dark) Color(0xCC1C1E2A) else Color(0xCCFFFFFF)))
            .background(sheen)
            .border(1.dp, rim, shape),
        content = content
    )
}

// ───────────── عناصر ─────────────

@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "press")
    return this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

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

@Composable private fun dim() = LocalContentColor.current.copy(alpha = 0.62f)

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Glass(modifier.pressable(onClick), radius = 40.dp, strong = true) {
        Box(Modifier.background(if (filled) Blue.copy(0.62f) else Color.Transparent)
            .padding(horizontal = 22.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (filled) Color.White else (if (dark) Color(0xFF6CB6FF) else Blue), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun Chip(text: String, sel: Boolean, onClick: () -> Unit) {
    val c = LocalContentColor.current
    Glass(Modifier.pressable(onClick), radius = 40.dp, strong = true) {
        Box(Modifier.background(if (sel) Blue.copy(0.62f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text, color = if (sel) Color.White else c, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun Segmented(opts: List<String>, sel: Int, on: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    Glass(Modifier.fillMaxWidth(), radius = 16.dp, strong = true) {
        Row(Modifier.padding(3.dp)) {
            opts.forEachIndexed { i, o ->
                val a = i == sel
                Box(Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                    .background(if (a) Color.White.copy(if (dark) 0.22f else 0.75f) else Color.Transparent)
                    .then(if (a) Modifier.border(0.8.dp, Color.White.copy(if (dark) 0.35f else 1f), RoundedCornerShape(13.dp)) else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { on(i) }
                    .padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(o, fontWeight = if (a) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit) {
    Glass(Modifier.size(40.dp).pressable(onClick), radius = 20.dp, strong = true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp)) }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent))
    }
}

@Composable
private fun tf() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = LocalContentColor.current, unfocusedTextColor = LocalContentColor.current)

@Composable
fun BigTitle(t: String) = Text(t, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))

fun fmt(s: Int): String {
    if (s <= 0) return ""
    val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

// ───────────── الهيكل + شريط التبويب الزجاجي ─────────────

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    val inc by Incoming.v.collectAsState()
    LaunchedEffect(inc) { if (inc != null) tab = 0 }
    val bg = rememberHazeState()
    val full = rememberHazeState()
    CompositionLocalProvider(LocalFull provides full, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(full)) {
                Backdrop(bg) {
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        Crossfade(tab, label = "tab") { when (it) { 0 -> HomeScreen(); 1 -> LibraryScreen(); else -> SettingsScreen() } }
                    }
                }
            }
            TabBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
        }
    }
}

@Composable
fun TabBar(sel: Int, on: (Int) -> Unit, modifier: Modifier) {
    val hap = LocalHapticFeedback.current
    val dark = isSystemInDarkTheme()
    val items = listOf("الرئيسية" to Icons.Rounded.Home, "المكتبة" to Icons.Rounded.PlayArrow, "الإعدادات" to Icons.Rounded.Settings)
    val itemW = 92.dp; val itemH = 58.dp
    val x by animateDpAsState(itemW * sel, spring(dampingRatio = 0.7f, stiffness = 380f), label = "ind")
    Glass(modifier, radius = 40.dp, strong = true, state = LocalFull.current) {
        Box(Modifier.padding(5.dp)) {
            Box(Modifier.offset(x = x).size(itemW, itemH).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(if (dark) 0.26f else 0.75f), Color.White.copy(if (dark) 0.10f else 0.35f))))
                .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(if (dark) 0.5f else 1f), Color.White.copy(0.05f))), CircleShape))
            Row {
                items.forEachIndexed { i, (l, ic) ->
                    val a = sel == i
                    val c = if (a) Blue else LocalContentColor.current.copy(0.7f)
                    Column(Modifier.size(itemW, itemH).pressable { hap.performHapticFeedback(HapticFeedbackType.TextHandleMove); on(i) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(ic, l, tint = c); Text(l, fontSize = 11.sp, color = c, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ───────────── الرئيسية ─────────────

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val hap = LocalHapticFeedback.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
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

    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BigTitle("التحميلات") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tf(),
                        placeholder = { Text("الصق رابط الفيديو هنا", color = dim()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("لصق", filled = false) { clip.getText()?.text?.let { url = urlOf(it) } }
                        Pill(if (loading) "…" else "جلب") { hap.performHapticFeedback(HapticFeedbackType.LongPress); go() }
                        Spacer(Modifier.weight(1f))
                        Text("قائمة تشغيل", fontSize = 13.sp, color = dim())
                        Switch(playlist, { playlist = it }, colors = SwitchDefaults.colors(checkedTrackColor = Green,
                            checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent))
                    }
                }
            }
        }
        if (!ready) item { Text(if (engineSt.isNotEmpty()) engineSt else "جاري تجهيز المحرك…", fontSize = 13.sp, color = dim()) }
        if (err.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth(), radius = 22.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(err, color = Red, fontSize = 14.sp)
                    Pill("حمّل مباشرة", filled = false) { direct() }
                }
            }
        }

        info?.let { i ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Thumb(i.thumbnail, Modifier.fillMaxWidth().aspectRatio(16 / 9f)
                            .clip(RoundedCornerShape(18.dp)).background(Color.Gray.copy(0.2f)))
                        Text(i.title ?: "", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(i.uploader, fmt((i.duration as Number?)?.toInt() ?: 0).ifEmpty { null }).joinToString(" • "),
                            fontSize = 13.sp, color = dim())
                        Segmented(listOf("فيديو", "صوت"), kind) { kind = it }
                        if (kind == 0) {
                            val hs = i.formats?.map { (it.height as Number?)?.toInt() ?: 0 }?.filter { it > 0 }
                                ?.distinct()?.sortedDescending()?.take(6) ?: emptyList()
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("الأفضل", h == 0) { h = 0 }
                                hs.forEach { Chip("${it}p", h == it) { h = it } }
                            }
                            SwitchRow("تضمين الترجمة", subs) { subs = it }
                        } else Text("الصيغة: ${Prefs.audioFmt.uppercase()} (من الإعدادات)", fontSize = 13.sp, color = dim())
                        Pill("تحميل", Modifier.fillMaxWidth()) {
                            hap.performHapticFeedback(HapticFeedbackType.LongPress)
                            Repo.enqueue(ctx, newTask(i.title ?: url, i.thumbnail, false)); info = null; url = ""
                        }
                    }
                }
            }
        }

        if (Repo.tasks.isNotEmpty()) item { Text("قيد التنفيذ", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        items(Repo.tasks, key = { it.id }) { TaskRow(it) }
        if (Repo.tasks.isEmpty() && info == null && err.isEmpty())
            item { Text("شارك أي رابط من يوتيوب أو إنستجرام أو فيسبوك أو تيك توك لتطبيق Grab وهيبدأ فورًا.",
                fontSize = 14.sp, color = dim(), modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
fun TaskRow(t: Task) {
    val ctx = LocalContext.current
    val p by animateFloatAsState(t.progress, label = "prog")
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumb(t.thumb, Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(0.2f)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(LocalContentColor.current.copy(0.12f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).clip(CircleShape)
                        .background(Blue))
                }
                Text(when (t.st) {
                    St.Queued -> "في الانتظار"
                    St.Running -> if (t.msg.isNotEmpty() && t.progress == 0f) t.msg else "${(t.progress * 100).toInt()}%" + if (t.eta > 0) " • باقي ${t.eta}ث" else ""
                    St.Failed -> t.msg
                    St.Canceled -> "أُلغي"
                    St.Done -> "تم"
                }, fontSize = 12.sp, color = if (t.st == St.Failed) Red else dim(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when (t.st) {
                St.Running, St.Queued -> IconBtn(Icons.Rounded.Close) { Repo.cancel(t) }
                St.Failed, St.Canceled -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconBtn(Icons.Rounded.Refresh) { Repo.enqueue(ctx, t) }
                    IconBtn(Icons.Rounded.Delete) { Repo.tasks.remove(t) }
                }
                else -> {}
            }
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
fun LibraryScreen() {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val list = Repo.library.filter { it.title.contains(q, true) }
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BigTitle("المكتبة") }
        item {
            Card(Modifier.fillMaxWidth(), radius = 22.dp) {
                TextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tf(),
                    placeholder = { Text("بحث", color = dim()) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = dim()) })
            }
        }
        if (list.isEmpty()) item { Text("لسه مفيش تنزيلات.", color = dim(), modifier = Modifier.padding(top = 8.dp)) }
        items(list, key = { it.uri }) { i ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(Blue.copy(0.15f)).clickable { openItem(ctx, i) },
                        contentAlignment = Alignment.Center) {
                        if (i.thumb != null) Thumb(i.thumb, Modifier.fillMaxSize())
                        else Text(if (i.audio) "♪" else "▶", fontSize = 22.sp, color = Blue)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(i.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(if (i.audio) "صوت" else "فيديو", fontSize = 12.sp, color = dim())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconBtn(Icons.Rounded.Share) { shareItem(ctx, i) }
                        IconBtn(Icons.Rounded.Delete) { Repo.remove(i) }
                    }
                }
            }
        }
    }
}

// ───────────── الإعدادات ─────────────

@Composable
fun SettingsScreen() {
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
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BigTitle("الإعدادات") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SwitchRow("تحميل على الواي فاي فقط", Prefs.wifiOnly) { Prefs.wifiOnly = it; Prefs.save() }
                    SwitchRow("تضمين الترجمة افتراضيًا", Prefs.subs) { Prefs.subs = it; Prefs.save() }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("صيغة الصوت", fontSize = 16.sp)
                        Segmented(fmts.map { it.uppercase() }, fmts.indexOf(Prefs.audioFmt)) { Prefs.audioFmt = fmts[it]; Prefs.save() }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("عدد التحميلات المتزامنة", fontSize = 16.sp)
                        Segmented(listOf("1", "2", "3"), Prefs.concurrent - 1) { Prefs.concurrent = it + 1; Prefs.save() }
                        Text("يسري بعد إعادة تشغيل التطبيق", fontSize = 12.sp, color = dim())
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("محرك التحميل (yt-dlp)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (ver.isNotEmpty()) "الإصدار: $ver" else "—", fontSize = 13.sp, color = dim())
                    if (st.isNotEmpty()) Text(st, fontSize = 13.sp, color = dim())
                    Text("بيتحدّث تلقائيًا كل 24 ساعة، وكمان لو التحميل فشل.", fontSize = 12.sp, color = dim())
                    Pill("تحديث الآن", filled = false) { Repo.update() }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("كوكيز يوتيوب", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (hasCk) "مفعّلة ✓" else "غير مفعّلة", fontSize = 13.sp, color = if (hasCk) Green else dim())
                    Text("لو يوتيوب طلب \"تأكيد إنك مش بوت\" واستيراد الكوكيز هو الحل الأضمن. صدّر ملف cookies.txt من متصفحك (إضافة Get cookies.txt LOCALLY) وبعدين استورده هنا. يفضّل تستخدم حساب ثانوي.",
                        fontSize = 12.sp, color = dim())
                    if (ckMsg.isNotEmpty()) Text(ckMsg, fontSize = 13.sp, color = dim())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("استيراد", filled = false) { picker.launch(arrayOf("*/*")) }
                        if (hasCk) Pill("حذف", filled = false) { Repo.clearCookies(); ckMsg = "" }
                    }
                }
            }
        }
    }
}
