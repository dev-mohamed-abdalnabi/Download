package app.grab

import android.app.Activity
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView

// مشغّل بسيط جوه التطبيق للفيديو والصوت، مع التحكم في السرعة
class PlayerActivity : Activity() {
    private val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    private var si = 0
    private var mp: MediaPlayer? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri = intent.data ?: run { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val vv = VideoView(this)
        root.addView(vv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        val mc = MediaController(this)
        mc.setAnchorView(vv)
        vv.setMediaController(mc)

        val title = TextView(this).apply {
            text = intent.getStringExtra("t") ?: ""
            setTextColor(Color.WHITE); textSize = 16f; maxLines = 2
            setPadding(dp(16), dp(12), dp(76), dp(12))
            setBackgroundColor(0x66000000)
        }
        val speed = TextView(this).apply {
            text = "1x"; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        root.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.addView(speed, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply { topMargin = dp(8); marginEnd = dp(10) })
        setContentView(root)

        // نسيب مساحة للـ status bar (على أندرويد 15 الشاشة بتترسم تحتها)
        root.setOnApplyWindowInsetsListener { v, ins ->
            @Suppress("DEPRECATION")
            v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }

        speed.setOnClickListener {
            si = (si + 1) % speeds.size
            try {
                mp?.let { it.playbackParams = it.playbackParams.setSpeed(speeds[si]) }
                speed.text = "${speeds[si]}x".replace(".0x", "x")
            } catch (_: Throwable) { Toast.makeText(this, "السرعة مش مدعومة هنا", Toast.LENGTH_SHORT).show() }
        }
        vv.setOnPreparedListener { m -> mp = m; vv.start() }
        vv.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "تعذّر تشغيل الملف", Toast.LENGTH_SHORT).show(); finish(); true
        }
        vv.setVideoURI(uri)
    }
}
