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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, ex -> status.value = clean(ex) })
    val tasks = mutableStateListOf<Task>()
    val library = mutableStateListOf<Item>()
    val ready = mutableStateOf(false)
    val status = mutableStateOf("")
    val hasCookies = mutableStateOf(false)
    lateinit var app: Context
    private var sem = Semaphore(2)
    private val initLock = Mutex()
    private var updatedThisSession = false
    private val media = setOf("mp4", "mkv", "webm", "mov", "mp3", "m4a", "opus", "ogg", "aac", "flac", "wav")

    private val cookieFile get() = File(app.filesDir, "cookies.txt")

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        try { Prefs.load(app) } catch (_: Throwable) {}
        sem = Semaphore(Prefs.concurrent.coerceIn(1, 3))
        loadLib()
        hasCookies.value = cookieFile.exists() && cookieFile.length() > 0
        try {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("dl", "التحميل", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel("done", "اكتمل التحميل", NotificationManager.IMPORTANCE_DEFAULT))
        } catch (_: Throwable) {}
        scope.launch {
            try {
                ensureEngine()
                if (System.currentTimeMillis() - Prefs.sp.getLong("u", 0) > 86_400_000) updateBlocking()
            } catch (e: Throwable) { status.value = clean(e) }
        }
    }

    // يتجهّز المحرك عند أول استخدام، ولو فشل مرة بيحاول تاني في المرة الجاية
    suspend fun ensureEngine() {
        if (ready.value) return
        initLock.withLock {
            if (ready.value) return
            YoutubeDL.getInstance().init(app)
            try { FFmpeg.getInstance().init(app) } catch (_: Throwable) {}
            ready.value = true
            status.value = ""
        }
    }

    // ───── أخطاء مفهومة بالعربي ─────
    fun clean(e: Throwable): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("not a bot", true) || m.contains("Sign in to confirm", true) ->
                "يوتيوب طلب تأكيد إنك مش بوت. قفّل الـ VPN أو استورد ملف الكوكيز من الإعدادات."
            m.contains("Unable to resolve host", true) || m.contains("timed out", true) || m.contains("Network is unreachable", true) ->
                "مشكلة في الاتصال بالنت"
            m.contains("Private video", true) -> "الفيديو خاص"
            m.contains("Video unavailable", true) -> "الفيديو مش متاح"
            m.contains("age", true) && m.contains("confirm", true) -> "الفيديو مقيّد بالعمر، محتاج كوكيز"
            else -> m.lines().lastOrNull { it.isNotBlank() }?.take(160) ?: "error"
        }
    }

    fun version(): String = try { YoutubeDL.version(app) ?: "" } catch (e: Throwable) { "" }

    // ───── تحديث المحرك ─────
    fun update() = scope.launch { updateBlocking() }

    private suspend fun updateBlocking() {
        status.value = "جاري تحديث المحرك…"
        try {
            ensureEngine()
            val r = YoutubeDL.getInstance().updateYoutubeDL(app)
            Prefs.sp.edit().putLong("u", System.currentTimeMillis()).apply()
            status.value = if (r?.name == "DONE") "تم التحديث ✓" else "المحرك محدّث ✓"
        } catch (e: Throwable) { status.value = "فشل التحديث: " + clean(e) }
    }

    // ───── الكوكيز ─────
    fun importCookies(uri: Uri): Boolean = try {
        val txt = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val valid = txt.lines().any { l -> !l.startsWith("#") && l.count { it == '\t' } >= 6 }
        if (!valid) false else {
            val head = txt.trimStart()
            val body = if (head.startsWith("# Netscape") || head.startsWith("# HTTP Cookie")) txt else "# Netscape HTTP Cookie File\n$txt"
            cookieFile.writeText(body); hasCookies.value = true; true
        }
    } catch (_: Throwable) { false }

    fun clearCookies() { try { cookieFile.delete() } catch (_: Throwable) {}; hasCookies.value = false }

    // ───── استراتيجيات يوتيوب: بنجرّب واحدة ورا التانية لحد ما واحدة تنجح ─────
    private val ytArgs = listOf<String?>(
        null,                                              // الافتراضي بتاع yt-dlp
        "youtube:player_client=tv,android_vr",
        "youtube:player_client=web_safari,mweb",
        "youtube:player_client=ios,android"
    )
    private fun isYt(u: String) = u.contains("youtube.com", true) || u.contains("youtu.be", true) || u.contains("youtube-nocookie.com", true)

    private fun common(r: YoutubeDLRequest, strat: Int, url: String) {
        r.addOption("--socket-timeout", "20")
        r.addOption("--retries", "10")
        r.addOption("--fragment-retries", "10")
        if (isYt(url)) ytArgs.getOrNull(strat)?.let { r.addOption("--extractor-args", it) }
        if (hasCookies.value && cookieFile.exists()) r.addOption("--cookies", cookieFile.absolutePath)
    }

    suspend fun fetch(url: String): VideoInfo = withContext(Dispatchers.IO) {
        ensureEngine()
        var last: Throwable? = null
        val n = if (isYt(url)) ytArgs.size else 2
        for (i in 0 until n) {
            try {
                return@withContext YoutubeDL.getInstance().getInfo(
                    YoutubeDLRequest(url).apply { addOption("--no-playlist"); common(this, i, url) })
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { last = e; delay(300) }
        }
        throw last ?: Exception("error")
    }

    fun enqueue(c: Context, t: Task) {
        if (t !in tasks) tasks.add(0, t)
        t.st = St.Queued; t.progress = 0f; t.msg = ""
        try { c.startForegroundService(Intent(c, DownloadService::class.java)) } catch (_: Throwable) {}
        t.job = scope.launch { sem.withPermit { run(t) } }
    }

    fun cancel(t: Task) {
        t.st = St.Canceled
        try { YoutubeDL.getInstance().destroyProcessById(t.id) } catch (_: Throwable) {}
        t.job?.cancel()
    }

    private suspend fun run(t: Task) {
        if (t.st == St.Canceled) return
        val cm = app.getSystemService(ConnectivityManager::class.java)
        if (Prefs.wifiOnly && cm.isActiveNetworkMetered) { t.st = St.Failed; t.msg = "وضع الواي فاي فقط مفعّل"; return }
        t.st = St.Running
        val dir = File(app.cacheDir, "dl/${t.id}").apply { mkdirs() }
        try {
            ensureEngine()
            val n = if (isYt(t.url)) ytArgs.size else 2
            val plan = (0 until n).toMutableList()
            var idx = 0; var updated = updatedThisSession; var last: Throwable? = null; var ok = false
            while (idx < plan.size) {
                val s = plan[idx++]
                if (t.st == St.Canceled) return
                try { attempt(t, dir, s); ok = true; break }
                catch (e: CancellationException) { throw e }
                catch (e: Throwable) {
                    if (t.st == St.Canceled) return
                    last = e
                    dir.deleteRecursively(); dir.mkdirs()
                    t.progress = 0f
                    // آخر محاولة؟ حدّث المحرك مرة وجرّب تاني
                    if (idx == plan.size && !updated) {
                        updated = true; updatedThisSession = true
                        t.msg = "بيحدّث المحرك ويحاول تاني…"
                        updateBlocking(); plan.add(0); plan.add(1)
                    } else delay(500)
                }
            }
            if (!ok) throw last ?: Exception("فشل التحميل")
            finish(t, dir)
        } catch (e: CancellationException) {
            if (t.st != St.Canceled) { t.st = St.Failed; t.msg = "اتوقف" }
        } catch (e: Throwable) {
            if (t.st != St.Canceled) { t.st = St.Failed; t.msg = clean(e) }
        } finally { dir.deleteRecursively() }
    }

    private fun attempt(t: Task, dir: File, strat: Int) {
        val r = YoutubeDLRequest(t.url)
        r.addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
        r.addOption("--no-mtime"); common(r, strat, t.url)
        r.addOption("--concurrent-fragments", "4")
        r.addOption(if (t.playlist) "--yes-playlist" else "--no-playlist")
        if (t.audio) {
            r.addOption("-f", "ba/b")
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
        YoutubeDL.getInstance().execute(r, t.id, callback = { p, eta, _ ->
            if (p >= 0f) t.progress = (p / 100f).coerceIn(0f, 1f)
            t.eta = eta
        })
    }

    private fun finish(t: Task, dir: File) {
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
        } catch (_: Throwable) {}
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
        try { startForeground(1, build(0, "جاري التحميل…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
        catch (_: Throwable) { stopSelf(); return START_NOT_STICKY }
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
