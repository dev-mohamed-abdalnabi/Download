package app.grab

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.launch

// نافذة صغيرة بتظهر فوق أي تطبيق لما تشارك رابط — من غير ما التطبيق الأساسي يفتح
class ShareActivity : ComponentActivity() {
    private var link by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        read(intent)
        if (link.isBlank()) { finish(); return }
        val all = urlsOf(intent?.getStringExtra(Intent.EXTRA_TEXT) ?: "")
        // وضع التحميل السريع، أو أكتر من رابط: ابدأ فورًا بدون أي أسئلة
        if (Prefs.shareQuick || all.size > 1) {
            (if (all.isEmpty()) listOf(link) else all).forEachIndexed { n, l ->
                Repo.enqueue(this, Task("${System.nanoTime()}_$n", l, l, null, false, 0, Prefs.subs, false))
            }
            Toast.makeText(this, if (all.size > 1) "اتضاف ${all.size} روابط للتحميل" else "بدأ التحميل", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        setContent { AppTheme { Popup() } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); read(intent) }

    private fun read(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT)?.let { link = urlOf(it) }
    }

    @Composable
    private fun Popup() {
        val cs = MaterialTheme.colorScheme
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var info by remember { mutableStateOf<VideoInfo?>(null) }
        var err by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(true) }
        var audio by remember { mutableStateOf(false) }
        var h by remember { mutableIntStateOf(0) }
        var subs by remember { mutableStateOf(Prefs.subs) }

        fun load() { loading = true; err = ""
            scope.launch {
                try { info = Repo.fetch(link) } catch (e: Throwable) { err = Repo.clean(e) }
                loading = false
            }
        }
        LaunchedEffect(link) { load() }

        fun start(title: String, thumb: String?, a: Boolean, q: Int) {
            Repo.enqueue(ctx, Task(System.nanoTime().toString(), link, title, thumb, a, q, subs && !a, false).also { it.uploader = info?.uploader })
            Toast.makeText(ctx, "بدأ التحميل", Toast.LENGTH_SHORT).show()
            finish()
        }

        Box(Modifier.fillMaxSize().clickable(MutableInteractionSource(), null) { finish() }, contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.fillMaxWidth().clickable(MutableInteractionSource(), null) {},
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), color = cs.surfaceContainer) {
                Column(Modifier.navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape).background(cs.outlineVariant))
                    when {
                        loading -> Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(14.dp)); Text("بجيب بيانات الفيديو…")
                        }
                        err.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(err, color = cs.error)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ load() }) { Text("حاول تاني") }
                                Button({ start(link, null, false, 0) }) { Text("حمّل مباشرة") }
                            }
                        }
                        else -> info?.let { i ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Thumb(i.thumbnail, Modifier.size(width = 112.dp, height = 70.dp).clip(RoundedCornerShape(18.dp))
                                    .background(cs.surfaceContainerHigh))
                                Column(Modifier.weight(1f)) {
                                    Text(i.title ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                    val d = fmt((i.duration as Number?)?.toInt() ?: 0)
                                    Text(listOfNotNull(i.uploader, d.ifEmpty { null }).joinToString(" • "), maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!audio, { audio = false }, { Text("فيديو") })
                                FilterChip(audio, { audio = true }, { Text("صوت ${Prefs.audioFmt.uppercase()}") })
                                if (!audio) FilterChip(subs, { subs = !subs }, { Text("ترجمة") })
                            }
                            if (!audio) {
                                val hs = qList(i)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(h == 0, { h = 0 }, { Text("الأفضل") })
                                    hs.forEach { q -> FilterChip(h == q, { h = q }, { Text("${q}p · ${qTag(q)}") }) }
                                }
                            }
                            Button({ start(i.title ?: link, i.thumbnail, audio, h) }, Modifier.fillMaxWidth().height(54.dp)) { Text("تحميل") }
                        }
                    }
                }
            }
        }
    }
}
