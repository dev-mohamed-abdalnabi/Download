package app.grab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Blue = Color(0xFF0A84FF)
val Green = Color(0xFF34C759)
val Red = Color(0xFFFF453A)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val cs = if (dark) darkColorScheme(primary = Blue, background = Color.Black, surface = Color(0xFF1C1C1E),
        onBackground = Color.White, onSurface = Color.White)
    else lightColorScheme(primary = Color(0xFF007AFF), background = Color(0xFFF2F2F7), surface = Color.White,
        onBackground = Color.Black, onSurface = Color.Black)
    MaterialTheme(colorScheme = cs, content = content)
}

@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(
                listOf(Blue.copy(if (dark) 0.35f else 0.22f), Color.Transparent), Offset(150f, 250f), 1000f)))
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(
                listOf(Color(0xFFBF5AF2).copy(if (dark) 0.28f else 0.18f), Color.Transparent), Offset(950f, 1700f), 1100f)))
            content()
        }
    }
}

@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 26.dp, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .shadow(if (dark) 0.dp else 14.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000))
            .clip(shape)
            .background(if (dark) Color.White.copy(0.09f) else Color.White.copy(0.72f))
            .border(0.8.dp, Brush.verticalGradient(listOf(
                Color.White.copy(if (dark) 0.28f else 0.95f), Color.White.copy(if (dark) 0.04f else 0.2f))), shape),
        content = content
    )
}


// صور مصغّرة بدون مكتبات خارجية (توفير ~1.5MB)
object Img { val cache = android.util.LruCache<String, Bitmap>(40) }

@Composable
fun Thumb(url: String?, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(url?.let { Img.cache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it, null, o) }?.also { Img.cache.put(url, it) }
            } catch (e: Exception) { null }
        }
    }
    Box(modifier) { bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
}

@Composable private fun dim() = LocalContentColor.current.copy(alpha = 0.6f)

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.clip(CircleShape).background(if (filled) Blue else Blue.copy(0.15f))
        .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.White else Blue, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Chip(text: String, sel: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(if (sel) Blue else LocalContentColor.current.copy(0.09f))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = if (sel) Color.White else LocalContentColor.current, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Segmented(opts: List<String>, sel: Int, on: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LocalContentColor.current.copy(0.08f)).padding(3.dp)) {
        opts.forEachIndexed { i, o ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(if (i == sel) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                .clickable { on(i) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(o, fontWeight = if (i == sel) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(LocalContentColor.current.copy(0.08f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp)) }
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
fun BigTitle(t: String) = Text(t, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))

fun fmt(s: Int): String {
    if (s <= 0) return ""
    val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

// ───────────── App shell + floating glass tab bar ─────────────

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    val inc by Incoming.v.collectAsState()
    LaunchedEffect(inc) { if (inc != null) tab = 0 }
    Backdrop {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Crossfade(tab, label = "tab") { when (it) { 0 -> HomeScreen(); 1 -> LibraryScreen(); else -> SettingsScreen() } }
            TabBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
        }
    }
}

@Composable
fun TabBar(sel: Int, on: (Int) -> Unit, modifier: Modifier) {
    val hap = LocalHapticFeedback.current
    val items = listOf("الرئيسية" to Icons.Rounded.Home, "المكتبة" to Icons.Rounded.PlayArrow, "الإعدادات" to Icons.Rounded.Settings)
    Glass(modifier, radius = 40.dp) {
        Row(Modifier.padding(6.dp)) {
            items.forEachIndexed { i, (l, ic) ->
                val a = sel == i
                val c = if (a) Blue else LocalContentColor.current.copy(0.6f)
                Column(Modifier.clip(CircleShape).background(if (a) Blue.copy(0.16f) else Color.Transparent)
                    .clickable { hap.performHapticFeedback(HapticFeedbackType.TextHandleMove); on(i) }
                    .padding(horizontal = 22.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(ic, l, tint = c); Text(l, fontSize = 11.sp, color = c, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ───────────── Home ─────────────

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

    fun newTask(title: String, thumb: String?, pl: Boolean) =
        Task(System.nanoTime().toString(), url.trim(), title, thumb, kind == 1, h, subs, pl)

    fun go() {
        val u = url.trim()
        if (u.isEmpty()) return
        err = ""; info = null
        if (!Repo.ready.value) { err = "المحرك لسه بيتجهّز… جرّب كمان ثواني"; return }
        if (playlist) { Repo.enqueue(ctx, newTask(u, null, true)); url = ""; return }
        loading = true
        scope.launch {
            try { info = Repo.fetch(u); h = 0 } catch (e: Exception) { err = Repo.clean(e) }
            loading = false
        }
    }
    LaunchedEffect(inc) { inc?.let { url = it; Incoming.v.value = null; go() } }

    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BigTitle("التحميلات") }
        item {
            Glass(Modifier.fillMaxWidth()) {
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
        if (!ready) item { Text("جاري تجهيز المحرك…", fontSize = 13.sp, color = dim()) }
        if (err.isNotEmpty()) item { Text(err, color = Red, fontSize = 14.sp) }

        info?.let { i ->
            item {
                Glass(Modifier.fillMaxWidth()) {
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
        if (Repo.tasks.isEmpty() && info == null)
            item { Text("شارك أي رابط من يوتيوب أو إنستجرام أو فيسبوك أو تيك توك لتطبيق Grab وهيبدأ فورًا.",
                fontSize = 14.sp, color = dim(), modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
fun TaskRow(t: Task) {
    val ctx = LocalContext.current
    Glass(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumb(t.thumb, Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Color.Gray.copy(0.2f)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(LocalContentColor.current.copy(0.1f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(t.progress).background(Blue))
                }
                Text(when (t.st) {
                    St.Queued -> "في الانتظار"
                    St.Running -> "${(t.progress * 100).toInt()}%" + if (t.eta > 0) " • باقي ${t.eta}ث" else ""
                    St.Failed -> t.msg
                    St.Canceled -> "أُلغي"
                    St.Done -> "تم"
                }, fontSize = 12.sp, color = if (t.st == St.Failed) Red else dim(), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// ───────────── Library ─────────────

private fun openItem(c: Context, i: Item) = try {
    c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(i.uri), if (i.audio) "audio/*" else "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
} catch (_: Exception) {}

private fun shareItem(c: Context, i: Item) = try {
    val s = Intent(Intent.ACTION_SEND).setType(if (i.audio) "audio/*" else "video/*")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(i.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(s, null))
} catch (_: Exception) {}

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
            Glass(Modifier.fillMaxWidth(), radius = 18.dp) {
                TextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tf(),
                    placeholder = { Text("بحث", color = dim()) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = dim()) })
            }
        }
        if (list.isEmpty()) item { Text("لسه مفيش تنزيلات.", color = dim(), modifier = Modifier.padding(top = 8.dp)) }
        items(list, key = { it.uri }) { i ->
            Glass(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Blue.copy(0.15f)).clickable { openItem(ctx, i) },
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

// ───────────── Settings ─────────────

@Composable
fun SettingsScreen() {
    val ready by Repo.ready
    val st by Repo.status
    var ver by remember { mutableStateOf("") }
    LaunchedEffect(ready, st) { if (ready) ver = withContext(Dispatchers.IO) { Repo.version() } }
    val fmts = listOf("mp3", "m4a", "opus")
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BigTitle("الإعدادات") }
        item {
            Glass(Modifier.fillMaxWidth()) {
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
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("محرك التحميل (yt-dlp)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (ver.isNotEmpty()) "الإصدار: $ver" else "—", fontSize = 13.sp, color = dim())
                    if (st.isNotEmpty()) Text(st, fontSize = 13.sp, color = dim())
                    Text("بيتحدّث تلقائيًا كل 24 ساعة. لو موقع وقف، حدّث يدويًا.", fontSize = 12.sp, color = dim())
                    Pill("تحديث الآن", filled = false) { Repo.update() }
                }
            }
        }
    }
}
