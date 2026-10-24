package app.grab

import android.Manifest
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

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND)
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { Incoming.v.value = urlOf(it) }
    }
}

private val URL_RE = Regex("https?://\\S+")
fun urlOf(s: String): String = URL_RE.find(s)?.value ?: s.trim()
