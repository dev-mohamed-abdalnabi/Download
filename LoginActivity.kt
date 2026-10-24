package app.grab

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// تسجيل دخول جوجل/يوتيوب من جوه التطبيق، وبعدها الكوكيز بتتحفظ لوحدها لـ yt-dlp
class LoginActivity : ComponentActivity() {
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent { AppTheme { Screen() } }
    }

    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else super.onBackPressed()
    }

    @Composable
    private fun Screen() {
        var msg by remember { mutableStateOf("") }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("سجّل دخول بحساب جوجل، ولما تظهر صفحة يوتيوب اضغط «تم».",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Button({
                        if (Repo.saveWebCookies()) {
                            Toast.makeText(this@LoginActivity, "تم حفظ الكوكيز ✓", Toast.LENGTH_SHORT).show(); finish()
                        } else msg = "ما لقيتش تسجيل دخول. كمّل تسجيل الدخول لحد ما تفتح صفحة يوتيوب."
                    }) { Text("تم") }
                }
                if (msg.isNotEmpty()) Text(msg, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
                AndroidView(factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // جوجل بتمنع تسجيل الدخول من WebView العادي، فبنستخدم هوية متصفح كروم
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        web = this
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F")
                    }
                }, Modifier.fillMaxSize())
            }
        }
    }
}
