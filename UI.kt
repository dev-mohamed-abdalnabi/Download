@file:OptIn(ExperimentalMaterial3Api::class)

package app.grab

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ═══════════════════════════ الثيم ═══════════════════════════
// ألوان ديناميكية من خلفية الموبايل (أندرويد 12+)، وألوان احتياطية للأقدم

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

// خط Tajawal (بيتحمّل في الـBuild من جوجل فونتس ويتحط في res/font)
private val AppFont = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold)
)
private fun TextStyle.f(w: FontWeight? = null) = copy(fontFamily = AppFont, fontWeight = w ?: fontWeight)

private val T0 = Typography()
private val AppType = Typography(
    displayLarge = T0.displayLarge.f(FontWeight.ExtraBold),
    displayMedium = T0.displayMedium.f(FontWeight.ExtraBold),
    displaySmall = T0.displaySmall.copy(fontSize = 36.sp, lineHeight = 52.sp).f(FontWeight.ExtraBold),
    headlineLarge = T0.headlineLarge.f(FontWeight.ExtraBold),
    headlineMedium = T0.headlineMedium.f(FontWeight.ExtraBold),
    headlineSmall = T0.headlineSmall.f(FontWeight.Bold),
    titleLarge = T0.titleLarge.f(FontWeight.Bold),
    titleMedium = T0.titleMedium.f(FontWeight.Bold),
    titleSmall = T0.titleSmall.f(FontWeight.Bold),
    bodyLarge = T0.bodyLarge.f(FontWeight.Normal),
    bodyMedium = T0.bodyMedium.f(FontWeight.Normal),
    bodySmall = T0.bodySmall.f(FontWeight.Normal),
    labelLarge = T0.labelLarge.f(FontWeight.Bold),
    labelMedium = T0.labelMedium.f(FontWeight.Medium),
    labelSmall = T0.labelSmall.f(FontWeight.Medium)
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
    MaterialTheme(colorScheme = cs, typography = AppType, content = content)
}

// ═══════════════════════════ الأيقونات المرسومة ═══════════════════════════

private fun vec(name: String, b: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round, pathBuilder = b)
    }.build()

private val IcDownload by lazy { vec("download") {
    moveTo(12f, 4f); lineTo(12f, 15f)
    moveTo(7f, 10.5f); lineTo(12f, 15.5f); lineTo(17f, 10.5f)
    moveTo(5f, 20f); lineTo(19f, 20f)
} }
private val IcLink by lazy { vec("link") {
    moveTo(10f, 7f); horizontalLineTo(7f); arcTo(5f, 5f, 0f, false, false, 7f, 17f); horizontalLineTo(10f)
    moveTo(14f, 7f); horizontalLineTo(17f); arcTo(5f, 5f, 0f, false, true, 17f, 17f); horizontalLineTo(14f)
    moveTo(8f, 12f); horizontalLineTo(16f)
} }
private val IcWifi by lazy { vec("wifi") {
    moveTo(3f, 9.5f); quadTo(12f, 1f, 21f, 9.5f)
    moveTo(6f, 13f); quadTo(12f, 7f, 18f, 13f)
    moveTo(9f, 16.5f); quadTo(12f, 13.5f, 15f, 16.5f)
    moveTo(12f, 20f); lineTo(12.01f, 20f)
} }
private val IcSubs by lazy { vec("subs") {
    moveTo(5f, 5f); horizontalLineTo(19f); arcTo(2f, 2f, 0f, false, true, 21f, 7f); verticalLineTo(17f)
    arcTo(2f, 2f, 0f, false, true, 19f, 19f); horizontalLineTo(5f); arcTo(2f, 2f, 0f, false, true, 3f, 17f)
    verticalLineTo(7f); arcTo(2f, 2f, 0f, false, true, 5f, 5f)
    moveTo(7f, 11f); horizontalLineTo(10.5f); moveTo(13f, 11f); horizontalLineTo(17f)
    moveTo(7f, 15f); horizontalLineTo(13f); moveTo(15.5f, 15f); horizontalLineTo(17f)
} }
private val IcMusic by lazy { vec("music") {
    moveTo(9f, 17f); verticalLineTo(5f); lineTo(19f, 3f); verticalLineTo(15f)
    moveTo(4.5f, 17f); arcTo(2.5f, 2.5f, 0f, true, true, 9.5f, 17f); arcTo(2.5f, 2.5f, 0f, true, true, 4.5f, 17f)
    moveTo(14.5f, 15f); arcTo(2.5f, 2.5f, 0f, true, true, 19.5f, 15f); arcTo(2.5f, 2.5f, 0f, true, true, 14.5f, 15f)
} }
private val IcLayers by lazy { vec("layers") {
    moveTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); lineTo(3f, 8f); close()
    moveTo(3f, 12f); lineTo(12f, 17f); lineTo(21f, 12f)
    moveTo(3f, 16f); lineTo(12f, 21f); lineTo(21f, 16f)
} }
private val IcEngine by lazy { vec("engine") {
    moveTo(5f, 8f); lineTo(10f, 12f); lineTo(5f, 16f)
    moveTo(12.5f, 17f); lineTo(19f, 17f)
} }

// ═══════════════════════════ أشكال وحركة ═══════════════════════════

// شكل "الكوكي" المتموّج: دايرة حوافها فصوص ناعمة
private class CookieShape(private val lobes: Int = 9, private val amp: Float = 0.06f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f; val cy = size.height / 2f
        val p = Path(); val steps = 180
        for (i in 0..steps) {
            val a = (2.0 * PI * i / steps).toFloat()
            val rad = r * (1f - amp + amp * cos(lobes * a))
            val x = cx + rad * cos(a); val y = cy + rad * sin(a)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

@Composable
private fun CookieIcon(icon: ImageVector, size: Dp, container: Color, tint: Color, modifier: Modifier = Modifier, spin: Boolean = true) {
    val shape = remember { CookieShape() }
    val rot by rememberInfiniteTransition(label = "cookie").animateFloat(0f, 360f,
        infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "rot")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (spin) rot else 0f }.clip(shape).background(container))
        Icon(icon, null, Modifier.size(size * 0.44f), tint = tint)
    }
}

// شريط تقدم متموّج (زي Material Expressive). p = null يعني "جاري التجهيز"
@Composable
private fun WavyBar(p: Float?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val active = cs.primary; val track = cs.secondaryContainer
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(0f, (2.0 * PI).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "ph")
    val slide by inf.animateFloat(-0.35f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "sl")
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val sw = 4.dp.toPx(); val amp = 3.dp.toPx(); val wl = 24.dp.toPx()
        val cy = size.height / 2f; val w = size.width; val gap = sw * 1.3f
        val from: Float; val to: Float
        if (p != null) { from = 0f; to = w * p.coerceIn(0f, 1f) }
        else { from = (slide * w).coerceAtLeast(0f); to = (slide * w + 0.35f * w).coerceAtMost(w) }
        scale(if (rtl) -1f else 1f, 1f, pivot = center) {
            if (to + gap < w) drawLine(track, Offset(to + gap, cy), Offset(w, cy), sw, StrokeCap.Round)
            if (p == null && from - gap > 0f) drawLine(track, Offset(0f, cy), Offset(from - gap, cy), sw, StrokeCap.Round)
            if (to - from > 1f) {
                val path = Path()
                var x = from
                path.moveTo(x, cy + amp * sin(x / wl * 2f * PI.toFloat() - phase))
                while (x < to) {
                    x = min(x + 3f, to)
                    path.lineTo(x, cy + amp * sin(x / wl * 2f * PI.toFloat() - phase))
                }
                drawPath(path, active, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ═══════════════════════════ صور ═══════════════════════════

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

private fun qTag(h: Int) = when {
    h >= 2160 -> "4K"; h >= 1440 -> "2K"; h >= 1080 -> "Full HD"; h >= 720 -> "HD"; else -> "SD"
}

// ═══════════════════════════ عناصر مشتركة ═══════════════════════════

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
private fun Header(title: String, sub: String? = null) {
    Column(Modifier.statusBarsPadding().padding(top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        if (sub != null) Text(sub, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 28.dp, bottom = 6.dp))

@Composable
private fun Footnote(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))

@Composable
private fun SettingRow(icon: ImageVector?, title: String, sub: String? = null, subColor: Color = Color.Unspecified,
                       onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = cs.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium,
                color = if (subColor == Color.Unspecified) cs.onSurfaceVariant else subColor)
        }
        trailing?.invoke()
    }
}

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) =
    Switch(checked, onChange, thumbContent = if (checked) ({ Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) else null)

// مبدّل بحبّة بتنزلق بين الخيارات
@Composable
private fun Switcher(opts: List<String>, sel: Int, modifier: Modifier = Modifier, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp)) {
        val w = maxWidth / opts.size
        val x by animateDpAsState(w * sel.coerceAtLeast(0), spring(0.8f, 420f), label = "sw")
        if (sel >= 0) Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(cs.primary))
        Row(Modifier.fillMaxSize()) {
            opts.forEachIndexed { i, o ->
                val c by animateColorAsState(if (i == sel) cs.onPrimary else cs.onSurface, label = "swc")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { on(i) },
                    contentAlignment = Alignment.Center) {
                    Text(o, color = c, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

// زر كبير: بيتحوّل شكله ويصغر لما تضغط عليه
@Composable
private fun BigButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
                      loading: Boolean = false, tonal: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pressed) 18.dp else 32.dp, spring(0.55f, 600f), label = "r")
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.55f, 600f), label = "s")
    val bg = if (tonal) cs.secondaryContainer else cs.primary
    val fg = if (tonal) cs.onSecondaryContainer else cs.onPrimary
    Row(modifier.fillMaxWidth().height(64.dp)
        .graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.38f }
        .clip(RoundedCornerShape(r)).background(bg)
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = fg)
        else if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = fg)
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CookieIcon(icon, 128.dp, cs.primaryContainer, cs.onPrimaryContainer)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PageList(bottom: Dp, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) =
    LazyColumn(Modifier.fillMaxSize().imePadding(), state = state,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom), content = content)

// ═══════════════════════════ الهيكل + شريط التنقل العايم ═══════════════════════════

private class NavTab(val label: String, val on: ImageVector, val off: ImageVector)

private val LocalSnack = staticCompositionLocalOf<SnackbarHostState> { error("no snackbar host") }

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val inc by Incoming.v.collectAsState()
    LaunchedEffect(inc) { if (inc != null) tab = 0 }
    BackHandler(enabled = tab != 0) { tab = 0 }
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bottom = if (imeOpen) 16.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp

    // لما تحميل يخلص: رسالة "تم التحميل" مع زر فتح، ونقطة على تبويب المكتبة لو مش فاتحها
    var unseen by remember { mutableIntStateOf(0) }
    var lastSize by remember { mutableIntStateOf(Repo.library.size) }
    val libSize = Repo.library.size
    LaunchedEffect(libSize) {
        if (libSize > lastSize) {
            val it = Repo.library.firstOrNull()
            if (tab != 1) unseen += libSize - lastSize
            lastSize = libSize
            if (it != null) {
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar("تم التحميل: ${it.title.take(40)}", "فتح", duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) openItem(ctx, it)
            }
        } else lastSize = libSize
    }
    LaunchedEffect(tab) { if (tab == 1) unseen = 0 }

    CompositionLocalProvider(LocalSnack provides snack) {
        Surface(Modifier.fillMaxSize(), color = cs.surface, contentColor = cs.onSurface) {
            Box(Modifier.fillMaxSize()) {
                Crossfade(tab, Modifier.fillMaxSize(), label = "tab") {
                    when (it) { 0 -> HomeScreen(bottom); 1 -> LibraryScreen(bottom); else -> SettingsScreen(bottom) }
                }
                AnimatedVisibility(!imeOpen, Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    Box(Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, cs.surface)))
                        .navigationBarsPadding().padding(top = 36.dp, bottom = 14.dp),
                        contentAlignment = Alignment.Center) { FloatingNav(tab, unseen) { tab = it } }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (imeOpen) 12.dp else 100.dp))
            }
        }
    }
}

@Composable
private fun FloatingNav(sel: Int, badge: Int, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hap = LocalHapticFeedback.current
    val items = remember {
        listOf(
            NavTab("الرئيسية", Icons.Rounded.Home, Icons.Outlined.Home),
            NavTab("المكتبة", Icons.Rounded.PlayArrow, Icons.Outlined.PlayArrow),
            NavTab("الإعدادات", Icons.Rounded.Settings, Icons.Outlined.Settings)
        )
    }
    Surface(shape = CircleShape, color = cs.surfaceContainerHigh, shadowElevation = 10.dp) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, t ->
                val a = i == sel
                val bg by animateColorAsState(if (a) cs.primaryContainer else Color.Transparent, label = "nb")
                Row(Modifier.height(54.dp).clip(CircleShape).background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        hap.performHapticFeedback(HapticFeedbackType.TextHandleMove); on(i)
                    }
                    .padding(horizontal = 20.dp).animateContentSize(spring(0.8f, 400f)),
                    verticalAlignment = Alignment.CenterVertically) {
                    val ic: @Composable () -> Unit = {
                        Icon(if (a) t.on else t.off, t.label, tint = if (a) cs.onPrimaryContainer else cs.onSurfaceVariant)
                    }
                    if (i == 1 && badge > 0 && !a) BadgedBox(badge = { Badge { Text("$badge") } }) { ic() } else ic()
                    AnimatedVisibility(a, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(t.label, Modifier.padding(start = 10.dp), color = cs.onPrimaryContainer,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════ الرئيسية ═══════════════════════════

@Composable
private fun HomeHero(compact: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(-5f, 5f,
        infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    if (compact) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Grab", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                Text("التحميلات", style = MaterialTheme.typography.headlineMedium)
            }
            CookieIcon(IcDownload, 60.dp, cs.primary, cs.onPrimary)
        }
    } else {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Grab", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                Text("الصق الرابط،\nوخلّيها علينا", style = MaterialTheme.typography.displaySmall)
            }
            CookieIcon(IcDownload, 104.dp, cs.primary, cs.onPrimary, Modifier.offset(y = bob.dp))
        }
    }
}

@Composable
private fun HowTo() {
    val cs = MaterialTheme.colorScheme
    val steps = listOf(
        "افتح الفيديو في يوتيوب أو إنستجرام أو فيسبوك أو تيك توك",
        "اضغط مشاركة واختار Grab، أو انسخ الرابط والصقه فوق",
        "اختار الجودة أو الصوت واضغط تحميل"
    )
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("إزاي تستخدمه؟", style = MaterialTheme.typography.titleLarge)
        steps.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = cs.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
                }
                Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun HomeScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val hap = LocalHapticFeedback.current
    val clip = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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

    BackHandler(enabled = info != null) { info = null }

    fun newTask(title: String, thumb: String?, pl: Boolean) =
        Task(System.nanoTime().toString(), url.trim(), title, thumb, kind == 1, h, subs, pl)

    fun added() {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            launch { listState.animateScrollToItem(0) }
            snack.showSnackbar("بدأ التحميل", duration = SnackbarDuration.Short)
        }
    }

    fun direct() {
        val u = url.trim(); if (u.isEmpty()) return
        Repo.enqueue(ctx, newTask(u, null, playlist)); url = ""; err = ""; info = null; added()
    }

    fun go() {
        val u = url.trim()
        if (u.isEmpty()) return
        focus.clearFocus()
        err = ""; info = null
        if (playlist) { direct(); return }
        loading = true
        scope.launch {
            try { info = Repo.fetch(u); h = 0 } catch (e: Throwable) { err = Repo.clean(e) }
            loading = false
        }
    }
    LaunchedEffect(inc) { inc?.let { url = it; Incoming.v.value = null; go() } }

    val compact = Repo.tasks.isNotEmpty() || info != null || loading

    // توهّج لوني ناعم ثابت في أعلى الشاشة
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.radialGradient(listOf(cs.primaryContainer.copy(alpha = 0.85f), Color.Transparent),
            center = Offset(size.width * (if (rtl) 0.12f else 0.88f), size.height * 0.04f), radius = size.width * 0.95f))
    }) {
        PageList(bottom, listState) {
            item { Box(Modifier.animateContentSize()) { HomeHero(compact) } }
            item {
                TextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    shape = RoundedCornerShape(34.dp), colors = fieldColors(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("رابط الفيديو") },
                    leadingIcon = { Icon(IcLink, null, tint = cs.onSurfaceVariant) },
                    trailingIcon = {
                        if (url.isEmpty()) Box(Modifier.padding(end = 8.dp).clip(CircleShape).background(cs.primaryContainer)
                            .clickable { clip.getText()?.text?.let { url = urlOf(it); go() } }
                            .padding(horizontal = 16.dp, vertical = 9.dp)) {
                            Text("لصق وجلب", color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                        } else IconButton({ url = ""; err = ""; info = null }) { Icon(Icons.Rounded.Close, "مسح") }
                    },
                    supportingText = if (url.isNotBlank() && !url.trim().startsWith("http", true))
                        ({ Text("ده مش شكل رابط — هحاول معاه برضه") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() })
                )
            }
            item {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = playlist, onClick = { playlist = !playlist }, label = { Text("قائمة تشغيل كاملة") },
                        leadingIcon = if (playlist) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null)
                }
            }
            item {
                BigButton(if (playlist) "تحميل القائمة" else if (loading) "جاري الجلب…" else "جلب",
                    Modifier.padding(top = 16.dp), icon = IcDownload,
                    enabled = url.isNotBlank() && !loading, loading = loading) {
                    hap.performHapticFeedback(HapticFeedbackType.LongPress); go()
                }
            }
            if (!ready) item {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (engineSt.isNotEmpty()) engineSt else "جاري تجهيز المحرك…",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (err.isNotEmpty()) item {
                Column(Modifier.padding(top = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(22.dp), tint = cs.error)
                        Text(err, style = MaterialTheme.typography.bodyMedium, color = cs.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ go() }) { Text("حاول تاني") }
                        TextButton({ direct() }) { Text("حمّل مباشرة") }
                    }
                }
            }

            info?.let { i ->
                item {
                    val dur = fmt((i.duration as Number?)?.toInt() ?: 0)
                    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box {
                            Thumb(i.thumbnail, Modifier.fillMaxWidth().aspectRatio(16 / 9f)
                                .clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerHigh))
                            if (dur.isNotEmpty()) Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                                Text(dur, color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(i.title ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!i.uploader.isNullOrEmpty()) Text(i.uploader ?: "", style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switcher(listOf("فيديو", "صوت"), kind) { kind = it }
                        if (kind == 0) {
                            val hs = i.formats?.map { (it.height as Number?)?.toInt() ?: 0 }?.filter { it > 0 }
                                ?.distinct()?.sortedDescending()?.take(6) ?: emptyList()
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QChip("الأفضل", "تلقائي", h == 0) { h = 0 }
                                hs.forEach { q -> QChip("${q}p", qTag(q), h == q) { h = q } }
                            }
                            SettingRow(IcSubs, "تضمين الترجمة", onClick = { subs = !subs }) { Toggle(subs) { subs = it } }
                        } else SettingRow(IcMusic, "الصيغة: ${Prefs.audioFmt.uppercase()}", "تتغير من الإعدادات")
                        BigButton("تحميل", icon = IcDownload) {
                            hap.performHapticFeedback(HapticFeedbackType.LongPress)
                            Repo.enqueue(ctx, newTask(i.title ?: url, i.thumbnail, false)); info = null; url = ""; added()
                        }
                    }
                }
            }

            if (Repo.tasks.isNotEmpty()) {
                item {
                    Row(Modifier.padding(top = 32.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("قيد التنفيذ", style = MaterialTheme.typography.titleLarge)
                        Box(Modifier.clip(CircleShape).background(cs.primaryContainer).padding(horizontal = 10.dp, vertical = 2.dp)) {
                            Text("${Repo.tasks.size}", color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                items(Repo.tasks, key = { it.id }) { TaskRow(it, Modifier.animateItem()) }
            } else if (info == null && err.isEmpty() && !loading) item { HowTo() }
        }
    }
}

@Composable
private fun QChip(top: String, sub: String, sel: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (sel) cs.primary else cs.surfaceContainerHigh, label = "qc")
    val fg = if (sel) cs.onPrimary else cs.onSurface
    Column(Modifier.clip(RoundedCornerShape(24.dp)).background(bg)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(top, color = fg, style = MaterialTheme.typography.titleMedium)
        Text(sub, color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TaskRow(t: Task, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val p by animateFloatAsState(t.progress, label = "prog")
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Thumb(t.thumb, Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            when (t.st) {
                St.Running -> WavyBar(if (t.progress > 0f) p else null)
                St.Queued -> WavyBar(0f)
                else -> {}
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (t.st == St.Done) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = cs.tertiary)
                Text(when (t.st) {
                    St.Queued -> "في الانتظار"
                    St.Running -> if (t.msg.isNotEmpty() && t.progress == 0f) t.msg else "${(t.progress * 100).toInt()}%" + if (t.eta > 0) " • باقي ${t.eta}ث" else ""
                    St.Failed -> t.msg
                    St.Canceled -> "أُلغي"
                    St.Done -> "تم التحميل"
                }, style = MaterialTheme.typography.labelLarge,
                    color = when (t.st) { St.Failed -> cs.error; St.Done -> cs.tertiary; else -> cs.onSurfaceVariant },
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when (t.st) {
            St.Running, St.Queued -> FilledTonalIconButton({ Repo.cancel(t) }) { Icon(Icons.Rounded.Close, "إلغاء") }
            St.Failed, St.Canceled -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FilledTonalIconButton({ Repo.enqueue(ctx, t) }) { Icon(Icons.Rounded.Refresh, "إعادة") }
                IconButton({ Repo.drop(t) }) { Icon(Icons.Rounded.Delete, "حذف") }
            }
            else -> {}
        }
    }
}

// ═══════════════════════════ المكتبة (شبكة صور) ═══════════════════════════

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
    var f by remember { mutableIntStateOf(0) }
    var del by remember { mutableStateOf<Item?>(null) }
    val list = Repo.library.filter { (f == 0 || (f == 1 && !it.audio) || (f == 2 && it.audio)) && it.title.contains(q, true) }
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(span = full) { Header("المكتبة", if (Repo.library.isEmpty()) null else "${Repo.library.size} ملف") }
        item(span = full) {
            TextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                shape = RoundedCornerShape(30.dp), colors = fieldColors(),
                placeholder = { Text("ابحث في تنزيلاتك") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Rounded.Close, "مسح") } }
            )
        }
        item(span = full) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("الكل", "فيديو", "صوت").forEachIndexed { i, l ->
                    FilterChip(selected = f == i, onClick = { f = i }, label = { Text(l) },
                        leadingIcon = if (f == i) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null)
                }
            }
        }
        if (list.isEmpty()) item(span = full) {
            EmptyState(Icons.Rounded.PlayArrow, if (Repo.library.isEmpty()) "المكتبة فاضية" else "مفيش نتائج",
                if (Repo.library.isEmpty()) "اللي تحمّله هيظهر هنا." else "جرّب كلمة تانية أو فلتر تاني.")
        }
        items(list, key = { it.uri }) { i ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(28.dp))
                    .background(cs.surfaceContainerHigh).clickable { openItem(ctx, i) }, contentAlignment = Alignment.Center) {
                    if (i.thumb != null) Thumb(i.thumb, Modifier.fillMaxSize())
                    else CookieIcon(if (i.audio) IcMusic else Icons.Rounded.PlayArrow, 72.dp, cs.primaryContainer, cs.onPrimaryContainer, spin = false)
                    Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)).padding(7.dp)) {
                        Icon(if (i.audio) IcMusic else Icons.Rounded.PlayArrow, null, Modifier.size(16.dp), tint = Color.White)
                    }
                }
                Text(i.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (i.audio) "صوت" else "فيديو", Modifier.weight(1f).padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    IconButton({ shareItem(ctx, i) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Share, "مشاركة", Modifier.size(20.dp)) }
                    IconButton({ del = i }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Delete, "حذف", Modifier.size(20.dp)) }
                }
            }
        }
    }

    del?.let { d ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("حذف الملف؟") },
            text = { Text("\"${d.title}\"\nهيتمسح من جهازك نهائيًا.", maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton({ Repo.remove(d); del = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ del = null }) { Text("إلغاء") } }
        )
    }
}

// ═══════════════════════════ الإعدادات ═══════════════════════════

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

    PageList(bottom) {
        item { Header("الإعدادات") }

        item { SectionTitle("التحميل", Modifier.padding(top = 0.dp)) }
        item {
            SettingRow(IcWifi, "الواي فاي فقط", "ما تستهلكش باقة الموبايل", onClick = { Prefs.wifiOnly = !Prefs.wifiOnly; Prefs.save() }) {
                Toggle(Prefs.wifiOnly) { Prefs.wifiOnly = it; Prefs.save() }
            }
        }
        item {
            SettingRow(IcSubs, "الترجمة افتراضيًا", "تضمين الترجمة في كل تحميل", onClick = { Prefs.subs = !Prefs.subs; Prefs.save() }) {
                Toggle(Prefs.subs) { Prefs.subs = it; Prefs.save() }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(IcMusic, "صيغة الصوت")
                Switcher(fmts.map { it.uppercase() }, fmts.indexOf(Prefs.audioFmt)) { Prefs.audioFmt = fmts[it]; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(IcLayers, "تحميلات متزامنة")
                Switcher(listOf("1", "2", "3"), Prefs.concurrent - 1) { Prefs.concurrent = it + 1; Prefs.save() }
                Footnote("يسري بعد إعادة تشغيل التطبيق.")
            }
        }

        item { SectionTitle("المحرك") }
        item {
            SettingRow(IcEngine, "yt-dlp", if (st.isNotEmpty()) st else if (ver.isNotEmpty()) "الإصدار $ver" else "—") {
                Box(Modifier.clip(CircleShape).background(cs.primaryContainer).clickable { Repo.update() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
                        Text("تحديث", color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item { Footnote("بيتحدّث تلقائيًا كل 24 ساعة، وكمان لو التحميل فشل.") }

        item { SectionTitle("كوكيز يوتيوب") }
        item {
            SettingRow(Icons.Rounded.Lock, "ملف الكوكيز", if (hasCk) "مفعّلة" else "غير مفعّلة",
                subColor = if (hasCk) cs.tertiary else Color.Unspecified)
        }
        item {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ckMsg.isNotEmpty()) Text(ckMsg, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton({ picker.launch(arrayOf("*/*")) }) { Text("استيراد cookies.txt") }
                    if (hasCk) TextButton({ Repo.clearCookies(); ckMsg = "" }) { Text("حذف", color = cs.error) }
                }
                Footnote("لو يوتيوب طلب «تأكيد إنك مش بوت» فاستيراد الكوكيز هو الحل الأضمن. صدّر ملف cookies.txt من متصفحك (إضافة Get cookies.txt LOCALLY) وبعدين استورده هنا. يفضّل تستخدم حساب ثانوي.")
            }
        }
    }
}
