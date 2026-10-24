package app.grab

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

// مكان توقف كل ملف (عشان نكمّل من نفس النقطة)
object PlayPos {
    private fun sp(c: Context) = c.getSharedPreferences("pos", 0)
    fun get(c: Context, id: String): Long = try { sp(c).getLong(id, 0L) } catch (_: Throwable) { 0L }
    fun set(c: Context, id: String, ms: Long) {
        try {
            val s = sp(c)
            val e = s.edit().putLong(id, ms)
            if (s.all.size > 300) s.all.keys.take(100).forEach { e.remove(it) }   // ما نكبّرش الملف للأبد
            e.apply()
        } catch (_: Throwable) {}
    }
    fun clear(c: Context, id: String) { try { sp(c).edit().remove(id).apply() } catch (_: Throwable) {} }
}

// المشغّل نفسه (ExoPlayer) عايش في خدمة، فالصوت بيكمّل في الخلفية مع إشعار تحكم
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var curId: String? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) savePos() }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) curId?.let { PlayPos.clear(this@PlaybackService, it) }
                curId = item?.mediaId
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) curId?.let { PlayPos.clear(this@PlaybackService, it) }
            }
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, PlayerActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    private fun savePos() {
        val p = session?.player ?: return
        val id = p.currentMediaItem?.mediaId ?: return
        val pos = p.currentPosition; val d = p.duration
        if (p.playbackState != Player.STATE_ENDED && pos > 5000 && (d == C.TIME_UNSET || pos < d - 8000)) PlayPos.set(this, id, pos)
        else PlayPos.clear(this, id)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        savePos()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

// واجهة التشغيل: فيديو أو صوت (مع قائمة تشغيل). الضبط (السرعة، الترجمة، المسارات الصوتية) من زر الإعدادات في المشغّل
class PlayerActivity : Activity() {
    private var future: ListenableFuture<MediaController>? = null
    private var ctl: MediaController? = null
    private lateinit var view: PlayerView
    private lateinit var title: TextView
    private var audio = false
    private var uris: List<String> = emptyList()
    private var titles: List<String> = emptyList()
    private var start = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uris = intent.getStringArrayListExtra("u") ?: emptyList()
        titles = intent.getStringArrayListExtra("t") ?: emptyList()
        start = intent.getIntExtra("i", 0).coerceIn(0, (uris.size - 1).coerceAtLeast(0))
        audio = intent.getBooleanExtra("a", false)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = PlayerView(this).apply {
            setShowSubtitleButton(true)
            if (audio) { controllerShowTimeoutMs = 0; controllerHideOnTouch = false }
        }
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        title = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 16f; maxLines = 2
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0x66000000)
        }
        root.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        setContentView(root)

        // نسيب مساحة للـ status bar (على أندرويد 15 الشاشة بتترسم تحتها)
        root.setOnApplyWindowInsetsListener { v, ins ->
            @Suppress("DEPRECATION")
            v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }
    }

    override fun onStart() { super.onStart(); connect() }

    override fun onStop() {
        super.onStop()
        if (!audio) ctl?.pause()          // الفيديو بيقف في الخلفية، الصوت بيكمّل
        view.player = null
        future?.let { MediaController.releaseFuture(it) }
        future = null; ctl = null
    }

    private fun connect() {
        val f = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        future = f
        f.addListener({
            val c = try { f.get() } catch (_: Throwable) {
                Toast.makeText(this, "تعذّر تشغيل الملف", Toast.LENGTH_SHORT).show(); finish(); return@addListener
            }
            ctl = c; view.player = c
            c.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { title.text = mediaItem?.mediaMetadata?.title ?: "" }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "تعذّر تشغيل الملف", Toast.LENGTH_SHORT).show()
                }
            })
            if (uris.isNotEmpty()) {
                val same = c.mediaItemCount == uris.size && c.currentMediaItem?.mediaId == uris[start]
                if (!same) {
                    val items = uris.mapIndexed { n, u ->
                        MediaItem.Builder().setMediaId(u).setUri(u)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(titles.getOrNull(n) ?: "").build()).build()
                    }
                    c.setMediaItems(items, start, PlayPos.get(this, uris[start]))
                    c.prepare()
                }
            } else if (c.mediaItemCount == 0) { finish(); return@addListener }
            title.text = c.mediaMetadata.title ?: ""
            c.play()
        }, ContextCompat.getMainExecutor(this))
    }
}
