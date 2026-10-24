package app.grab

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.*
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class St { Queued, Running, Done, Failed, Canceled }

class Task(
    val id: String, val url: String, val title: String, val thumb: String?,
    val audio: Boolean, val height: Int, val subs: Boolean, val playlist: Boolean
) {
    var st by mutableStateOf(St.Queued)
    var progress by mutableFloatStateOf(0f)
    var eta by mutableLongStateOf(0L)
    var msg by mutableStateOf("")
    var job: Job? = null
}

class Item(val title: String, val thumb: String?, val uri: String, val audio: Boolean)

object Incoming { val v = MutableStateFlow<String?>(null) }

object Prefs {
    lateinit var sp: SharedPreferences
    var wifiOnly by mutableStateOf(false)
    var subs by mutableStateOf(false)
    var concurrent by mutableIntStateOf(2)
    var audioFmt by mutableStateOf("mp3")

    fun load(c: Context) {
        sp = c.getSharedPreferences("p", 0)
        wifiOnly = sp.getBoolean("w", false); subs = sp.getBoolean("s", false)
        concurrent = sp.getInt("c", 2); audioFmt = sp.getString("a", "mp3") ?: "mp3"
    }
    fun save() {
        sp.edit().putBoolean("w", wifiOnly).putBoolean("s", subs).putInt("c", concurrent).putString("a", audioFmt).apply()
    }
}

object Repo {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val tasks = mutableStateListOf<Task>()
    val library = mutableStateListOf<Item>()
    val ready = mutableStateOf(false)
    val status = mutableStateOf("")
    lateinit var app: Context
    private var sem = Semaphore(2)
    private val media = setOf("mp4", "mkv", "webm", "mov", "mp3", "m4a", "opus", "ogg", "aac", "flac", "wav")

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        Prefs.load(app); sem = Semaphore(Prefs.concurrent); loadLib()
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("dl", "التحميل", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("done", "اكتمل التحميل", NotificationManager.IMPORTANCE_DEFAULT))
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready.value = true
                if (System.currentTimeMillis() - Prefs.sp.getLong("u", 0) > 86_400_000) update()
            } catch (e: Exception) { status.value = clean(e) }
        }
    }

    fun clean(e: Throwable) =
        (e.message ?: "error").lines().lastOrNull { it.isNotBlank() }?.take(140) ?: "error"

    fun version(): String = try { YoutubeDL.version(app) ?: "" } catch (e: Exception) { "" }

    fun update() = scope.launch {
        status.value = "جاري تحديث المحرك…"
        try {
            val r = YoutubeDL.getInstance().updateYoutubeDL(app)
            Prefs.sp.edit().putLong("u", System.currentTimeMillis()).apply()
            status.value = if (r?.name == "DONE") "تم التحديث ✓" else "المحرك محدّث ✓"
        } catch (e: Exception) { status.value = "فشل التحديث: " + clean(e) }
    }

    suspend fun fetch(url: String): VideoInfo = withContext(Dispatchers.IO) {
        YoutubeDL.getInstance().getInfo(YoutubeDLRequest(url).apply { addOption("--no-playlist") })
    }

    fun enqueue(c: Context, t: Task) {
        if (t !in tasks) tasks.add(0, t)
        t.st = St.Queued; t.progress = 0f; t.msg = ""
        c.startForegroundService(Intent(c, DownloadService::class.java))
        t.job = scope.launch { sem.withPermit { run(t) } }
    }

    fun cancel(t: Task) {
        t.st = St.Canceled
        try { YoutubeDL.getInstance().destroyProcessById(t.id) } catch (_: Exception) {}
        t.job?.cancel()
    }

    private fun run(t: Task) {
        if (t.st == St.Canceled) return
        val cm = app.getSystemService(ConnectivityManager::class.java)
        if (Prefs.wifiOnly && cm.isActiveNetworkMetered) { t.st = St.Failed; t.msg = "وضع الواي فاي فقط مفعّل"; return }
        t.st = St.Running
        val dir = File(app.cacheDir, "dl/${t.id}").apply { mkdirs() }
        try {
            val r = YoutubeDLRequest(t.url)
            r.addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
            r.addOption("--no-mtime")
            r.addOption("--concurrent-fragments", "4")
            r.addOption(if (t.playlist) "--yes-playlist" else "--no-playlist")
            if (t.audio) {
                r.addOption("-x"); r.addOption("--audio-format", Prefs.audioFmt); r.addOption("--audio-quality", "0")
            } else {
                r.addOption("-f", "bv*+ba/b")
                r.addOption("-S", if (t.height > 0) "res:${t.height},ext:mp4:m4a" else "ext:mp4:m4a")
                r.addOption("--merge-output-format", "mp4")
                if (t.subs) {
                    r.addOption("--write-subs"); r.addOption("--write-auto-subs")
                    r.addOption("--sub-langs", "ar.*,en.*"); r.addOption("--embed-subs")
                }
            }
            YoutubeDL.getInstance().execute(r, t.id, callback = { p, e, l ->
                t.progress = (p / 100f).coerceIn(0f, 1f); t.eta = e; t.msg = l
            })
            val files = dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in media }.toList()
            if (files.isEmpty()) throw Exception("ما اتنتجش ملف")
            files.forEach { f ->
                val u = save(f) ?: return@forEach
                val isAudio = (MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "").startsWith("audio")
                library.add(0, Item(f.nameWithoutExtension, t.thumb, u, isAudio))
            }
            saveLib()
            t.progress = 1f; t.st = St.Done
            notifyDone(t)
            tasks.remove(t)
        } catch (e: Exception) {
            if (t.st != St.Canceled) { t.st = St.Failed; t.msg = clean(e) }
        } finally { dir.deleteRecursively() }
    }

    private fun save(f: File): String? {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "video/mp4"
        val isAudio = mime.startsWith("audio")
        val col = if (isAudio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                  else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/Grab")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val u = app.contentResolver.insert(col, cv) ?: return null
        app.contentResolver.openOutputStream(u)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        cv.clear(); cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
        app.contentResolver.update(u, cv, null, null)
        return u.toString()
    }

    private fun notifyDone(t: Task) {
        val pi = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(app, "done").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تم التحميل").setContentText(t.title).setContentIntent(pi).setAutoCancel(true).build()
        app.getSystemService(NotificationManager::class.java).notify(t.id.hashCode(), n)
    }

    private val libFile get() = File(app.filesDir, "lib.json")
    private fun loadLib() {
        try {
            val a = JSONArray(libFile.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                library.add(Item(o.getString("t"), o.optString("th").ifEmpty { null }, o.getString("u"), o.getBoolean("a")))
            }
        } catch (_: Exception) {}
    }
    fun saveLib() {
        val a = JSONArray()
        library.forEach { a.put(JSONObject().put("t", it.title).put("th", it.thumb ?: "").put("u", it.uri).put("a", it.audio)) }
        libFile.writeText(a.toString())
    }
    fun remove(i: Item) {
        try { app.contentResolver.delete(Uri.parse(i.uri), null, null) } catch (_: Exception) {}
        library.remove(i); saveLib()
    }
}

class DownloadService : Service() {
    companion object { @Volatile var looping = false }
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        fun build(p: Int, txt: String) = Notification.Builder(this, "dl")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Grab").setContentText(txt)
            .setOnlyAlertOnce(true).setOngoing(true).setProgress(100, p, p == 0).build()
        startForeground(1, build(0, "جاري التحميل…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!looping) {
            looping = true
            Repo.scope.launch {
                while (true) {
                    val act = Repo.tasks.filter { it.st == St.Running || it.st == St.Queued }
                    if (act.isEmpty()) break
                    nm.notify(1, build((act.map { it.progress }.average() * 100).toInt(), "${act.size} تحميل"))
                    delay(1000)
                }
                looping = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}
