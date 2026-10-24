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
import androidx.compose.foundation.lazy.itemsIndexed
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

val Green = Color(0xFF34C759)
val Red = Color(0xFFFF3B30)

// الزجاج بس للطبقة العايمة (شريط التبويب) — وبيشوف المحتوى اللي بيعدّي تحته
val LocalFull = staticCompositionLocalOf<HazeState?> { null }

// ───────────── ألوان النظام (لون واحد مميز بس: الأزرق) ─────────────

@Composable fun accent() = if (isSystemInDarkTheme()) Color(0xFF0A84FF) else Color(0xFF007AFF)
@Composable private fun surfaceC() = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color.White
@Composable private fun fillC() = if (isSystemInDarkTheme()) Color(0x3D787880) else Color(0x1F787880)
@Composable private fun lineC() = if (isSystemInDarkTheme()) Color(0xFF38383A) else Color(0xFFD1D1D6)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val cs = if (dark) darkColorScheme(primary = Color(0xFF0A84FF), background = Color.Black, surface = Color(0xFF1C1C1E),
        onBackground = Color.White, onSurface = Color.White)
    else lightColorScheme(primary = Color(0xFF007AFF), background = Color(0xFFF2F2F7), surface = Color.White,
        onBackground = Color.Black, onSurface = Color.Black)
    MaterialTheme(colorScheme = cs, content = content)
}

@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
}

// ───────────── الأسطح ─────────────

// أسطح المحتوى: صلبة وهادية زي iOS (مفيش زجاج ولا ظل ولا حدود)
@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 20.dp, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.clip(RoundedCornerShape(radius)).background(surfaceC()), content = content)

@Composable
fun Sep(start: Dp = 16.dp) =
    Box(Modifier.padding(start = start).fillMaxWidth().height(0.5.dp).background(lineC()))

// خلية في قائمة مجمّعة: الأولى والأخيرة بس اللي زواياهم مدورة
@Composable
fun Cell(i: Int, n: Int, content: @Composable ColumnScope.() -> Unit) {
    val r = 20.dp
    val top = if (i == 0) r else 0.dp
    val bottom = if (i == n - 1) r else 0.dp
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, bottom, bottom)).background(surfaceC())) {
        content()
        if (i < n - 1) Sep(84.dp)
    }
}

@Composable
fun Group(footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Card(Modifier.fillMaxWidth()) { Column(content = content) }
        if (footer != null) Text(footer, fontSize = 13.sp, color = dim(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
fun Line(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        Text(value, fontSize = 16.sp, color = if (valueColor == Color.Unspecified) dim() else valueColor)
    }
}

// ───────────── الزجاج (للعناصر العايمة بس) ─────────────

@Composable
fun Glass(
    modifier: Modifier = Modifier, radius: Dp = 40.dp,
    state: HazeState? = LocalFull.current, content: @Composable BoxScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(radius)
    val style = HazeMaterials.ultraThin(if (dark) Color(0xFF1C1C1E) else Color.White)
    // لمعة الحافة: أقوى من فوق وبتخفت لتحت، زي حافة الزجاج الحقيقي
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(if (dark) 0.30f else 0.95f), Color.White.copy(if (dark) 0.06f else 0.30f)))
    Box(
        modifier
            .shadow(if (dark) 0.dp else 14.dp, shape, ambientColor = Color(0x10000000), spotColor = Color(0x1A000000))
            .clip(shape)
            .then(if (state != null) Modifier.hazeEffect(state, style)
                  else Modifier.background(if (dark) Color(0xCC1C1C1E) else Color(0xCCFFFFFF)))
            .border(0.8.dp, rim, shape),
        content = content
    )
}

// ───────────── عناصر ─────────────

@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 520f), label = "press")
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

@Composable private fun dim() = LocalContentColor.current.copy(alpha = 0.6f)

// filled = زر رئيسي أزرق صلب. غير كده = زر "tinted" (خلفية زرقا خفيفة + نص أزرق)
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val a = accent()
    Box(modifier.pressable(onClick).clip(CircleShape).background(if (filled) a else a.copy(0.14f))
        .padding(horizontal = 20.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.White else a, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun Chip(text: String, sel: Boolean, onClick: () -> Unit) {
    Box(Modifier.pressable(onClick).clip(CircleShape).background(if (sel) accent() else fillC())
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = if (sel) Color.White else LocalContentColor.current, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Segmented(opts: List<String>, sel: Int, on: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fillC()).padding(2.dp)) {
        opts.forEachIndexed { i, o ->
            val a = i == sel
            Box(Modifier.weight(1f)
                .then(if (a) Modifier.shadow(1.dp, RoundedCornerShape(8.dp)) else Modifier)
                .clip(RoundedCornerShape(8.dp))
                .background(if (a) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { on(i) }
                .padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(o, fontWeight = if (a) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).pressable(onClick).clip(CircleShape).background(fillC()), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(16.dp), tint = dim())
    }
}

@Composable
fun Sw(checked: Boolean, onChange: (Boolean) -> Unit) {
    val dark = isSystemInDarkTheme()
    Switch(checked, onChange, colors = SwitchDefaults.colors(
        checkedTrackColor = Green, checkedThumbColor = Color.White,
        uncheckedTrackColor = if (dark) Color(0xFF39393D) else Color(0xFFE9E9EA), uncheckedThumbColor = Color.White,
        checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent))
}

@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        Sw(checked, onChange)
    }
}

@Composable
private fun tf() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = LocalContentColor.current, unfocusedTextColor = LocalContentColor.current)

@Composable
fun BigTitle(t: String) = Text(t, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))

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
    val full = rememberHazeState()
    CompositionLocalProvider(LocalFull provides full, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(full)) {
                Backdrop {
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
    val itemW = 92.dp; val itemH = 56.dp
    val x by animateDpAsState(itemW * sel, spring(dampingRatio = 0.75f, stiffness = 380f), label = "ind")
    Glass(modifier, radius = 40.dp, state = LocalFull.current) {
        Box(Modifier.padding(4.dp)) {
            Box(Modifier.offset(x = x).size(itemW, itemH).clip(CircleShape)
                .background(if (dark) Color.White.copy(0.14f) else Color.Black.copy(0.07f)))
            Row {
                items.forEachIndexed { i, (l, ic) ->
                    val a = sel == i
                    val c = if (a) accent() else LocalContentColor.current.copy(0.75f)
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

    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BigTitle("التحميلات") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fillC())) {
                        TextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tf(),
                            placeholder = { Text("الصق رابط الفيديو هنا", color = dim()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    }
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("لصق", filled = false) { clip.getText()?.text?.let { url = urlOf(it) } }
                        Pill(if (loading) "…" else "جلب") { hap.performHapticFeedback(HapticFeedbackType.LongPress); go() }
                        Spacer(Modifier.weight(1f))
                        Text("قائمة تشغيل", fontSize = 13.sp, color = dim())
                        Sw(playlist) { playlist = it }
                    }
                }
            }
        }
        if (!ready) item { Text(if (engineSt.isNotEmpty()) engineSt else "جاري تجهيز المحرك…", fontSize = 13.sp, color = dim()) }
        if (err.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            .clip(RoundedCornerShape(14.dp)).background(fillC()))
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
            Thumb(t.thumb, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(fillC()))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(fillC())) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).clip(CircleShape).background(accent()))
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
                    IconBtn(Icons.Rounded.Delete) { Repo.drop(t) }
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp)) {
        item { BigTitle("المكتبة") }
        item {
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fillC())) {
                TextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tf(),
                    placeholder = { Text("بحث", color = dim()) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = dim()) })
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
        if (list.isEmpty()) item { Text("لسه مفيش تنزيلات.", color = dim(), modifier = Modifier.padding(top = 8.dp)) }
        itemsIndexed(list, key = { _, it -> it.uri }) { idx, i ->
            Cell(idx, list.size) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(accent().copy(0.12f)).clickable { openItem(ctx, i) },
                        contentAlignment = Alignment.Center) {
                        if (i.thumb != null) Thumb(i.thumb, Modifier.fillMaxSize())
                        else if (i.audio) Text("♪", fontSize = 22.sp, color = accent())
                        else Icon(Icons.Rounded.PlayArrow, null, tint = accent())
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
    val rowPad = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { BigTitle("الإعدادات") }
        item {
            Group(footer = "عدد التحميلات المتزامنة يسري بعد إعادة تشغيل التطبيق.") {
                SwitchRow("تحميل على الواي فاي فقط", Prefs.wifiOnly, rowPad) { Prefs.wifiOnly = it; Prefs.save() }
                Sep()
                SwitchRow("تضمين الترجمة افتراضيًا", Prefs.subs, rowPad) { Prefs.subs = it; Prefs.save() }
                Sep()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("صيغة الصوت", fontSize = 16.sp)
                    Segmented(fmts.map { it.uppercase() }, fmts.indexOf(Prefs.audioFmt)) { Prefs.audioFmt = fmts[it]; Prefs.save() }
                }
                Sep()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("عدد التحميلات المتزامنة", fontSize = 16.sp)
                    Segmented(listOf("1", "2", "3"), Prefs.concurrent - 1) { Prefs.concurrent = it + 1; Prefs.save() }
                }
            }
        }
        item {
            Group(footer = "بيتحدّث تلقائيًا كل 24 ساعة، وكمان لو التحميل فشل.") {
                Line("محرك التحميل (yt-dlp)", if (ver.isNotEmpty()) ver else "—")
                if (st.isNotEmpty()) { Sep(); Text(st, fontSize = 13.sp, color = dim(), modifier = Modifier.padding(16.dp)) }
                Sep()
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { Pill("تحديث الآن", filled = false) { Repo.update() } }
            }
        }
        item {
            Group(footer = "لو يوتيوب طلب \"تأكيد إنك مش بوت\" فاستيراد الكوكيز هو الحل الأضمن. صدّر ملف cookies.txt من متصفحك (إضافة Get cookies.txt LOCALLY) وبعدين استورده هنا. يفضّل تستخدم حساب ثانوي.") {
                Line("كوكيز يوتيوب", if (hasCk) "مفعّلة" else "غير مفعّلة", if (hasCk) Green else Color.Unspecified)
                Sep()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
