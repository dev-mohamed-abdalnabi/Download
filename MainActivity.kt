package app.grab

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        handle(intent)
        setContent {
            AppTheme {
                if (Build.VERSION.SDK_INT >= 33) {
                    val l = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                    androidx.compose.runtime.LaunchedEffect(Unit) { l.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
                App()
            }
        }
    }

    // أي تحميل اتقطع (قفل التطبيق، قطع النت، مهلة الخدمة) بيكمّل لوحده لما تفتح التطبيق
    override fun onStart() { super.onStart(); Repo.resumePending() }

    // لما التطبيق يبقى قدّامك: لو فيه رابط منسوخ جديد نعرضه كاقتراح (القراءة مسموحة بس والنافذة عليها التركيز)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        try {
            val cb = getSystemService(ClipboardManager::class.java)
            val txt = cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
            val u = URL_RE.find(txt)?.value ?: return
            if (u != Prefs.lastClip) Incoming.clip.value = u
        } catch (_: Throwable) {}
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND)
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { Incoming.v.value = urlOf(it) }
    }
}

private val URL_RE = Regex("https?://\\S+")
fun urlsOf(s: String): List<String> = URL_RE.findAll(s).map { it.value }.distinct().toList()
fun urlOf(s: String): String = URL_RE.find(s)?.value ?: s.trim()
