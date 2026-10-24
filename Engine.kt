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

val LADDER = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
fun lowerQ(h: Int): Int = LADDER.lastOrNull { it < h } ?: 0

class Task(
    val id: String, val url: String, title: String, thumb: String?,
    val audio: Boolean, val height: Int, val subs: Boolean, val playlist: Boolean
) {
    var title by mutableStateOf(title)
    var thumb by mutableStateOf(thumb)
    var uploader: String? = null
    var clipStart = ""
    var clipEnd = ""
    var keep = false            // إيقاف مؤقت: نحتفظ بالجزء المحمّل عشان نكمّل منه
    var st by mutableStateOf(St.Queued)
    var progress by mutableFloatStateOf(0f)
    var eta by mutableLongStateOf(0L)
    var msg by mutableStateOf("")
    var job: Job? = null
}

class Item(val title: String, val thumb: String?, val uri: String, val audio: Boolean, val src: String = "")

object Incoming {
    val v = MutableStateFlow<String?>(null)
    val clip = MutableStateFlow<String?>(null)   // رابط منسوخ في الكليبورد
}

object Prefs {
    lateinit var sp: SharedPreferences
    var wifiOnly by mutableStateOf(false)
    var subs by mutableStateOf(false)
    var concurrent by mutableIntStateOf(2)
    var audioFmt by mutableStateOf("m4a")
    var limit by mutableIntStateOf(0)          // حد السرعة MB/s (0 = بدون)
    var subsMode by mutableIntStateOf(0)       // 0 تضمين، 1 ملف منفصل
    var organize by mutableIntStateOf(0)       // 0 عادي، 1 حسب الموقع، 2 حسب القناة
    var folder by mutableStateOf("Grab")
    var saveThumb by mutableStateOf(false)
    var saveDesc by mutableStateOf(false)
    var shareQuick by mutableStateOf(false)   // من نافذة المشاركة: ابدأ التحميل فورًا بالإعدادات الافتراضية

    fun load(c: Context) {
        sp = c.getSharedPreferences("p", 0)
        wifiOnly = sp.getBoolean("w", false); subs = sp.getBoolean("s", false)
        concurrent = sp.getInt("c", 2); audioFmt = sp.getString("a", "m4a") ?: "m4a"
        shareQuick = sp.getBoolean("q", false)
        limit = sp.getInt("l", 0); subsMode = sp.getInt("sm", 0); organize = sp.getInt("o", 0)
        folder = sp.getString("f", "Grab") ?: "Grab"; saveThumb = sp.getBoolean("th", false); saveDesc = sp.getBoolean("de", false)
    }
    // آخر رابط كتبته/لصقته — بيفضل محفوظ حتى لو التطبيق اتقفل
    var draft: String
        get() = sp.getString("d", "") ?: ""
        set(v) { sp.edit().putString("d", v).apply() }

    var lastClip: String
        get() = sp.getString("lc", "") ?: ""
        set(v) { sp.edit().putString("lc", v).apply() }

    fun save() {
        sp.edit().putBoolean("w", wifiOnly).putBoolean("s", subs).putInt("c", concurrent).putString("a", audioFmt).putBoolean("q", shareQuick)
            .putInt("l", limit).putInt("sm", subsMode).putInt("o", organize).putString("f", folder)
            .putBoolean("th", saveThumb).putBoolean("de", saveDesc).apply()
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
        loadTasks()
        hasCookies.value = cookieFile.exists() && cookieFile.length() > 0
        try {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("dl", "التحميل", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel("done", "اكتمل التحميل", NotificationManager.IMPORTANCE_DEFAULT))
        } catch (_: Throwable) {}
        scope.launch {
            try {
                ensureEngine()
                if (System.currentTimeMillis() - Prefs.sp.getLong("u", 0) > 43_200_000) { delay(30_000); updateBlocking() }
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
            m.contains("403") || m.contains("Forbidden", true) ->
                "يوتيوب رفض التحميل (403). من الإعدادات اضغط «تحديث الآن» وجرّب تاني."
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
            val r = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.NIGHTLY)
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

    // تسجيل الدخول من جوه التطبيق: بناخد الكوكيز من الـ WebView ونكتبها بصيغة Netscape
    fun saveWebCookies(): Boolean = try {
        val cm = android.webkit.CookieManager.getInstance()
        cm.flush()
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        var n = 0
        listOf("https://www.youtube.com" to ".youtube.com", "https://accounts.google.com" to ".google.com",
            "https://www.google.com" to ".google.com").forEach { (u, d) ->
            cm.getCookie(u)?.split(";")?.forEach { c ->
                val kv = c.trim(); val i = kv.indexOf('=')
                if (i > 0) { sb.append("$d\tTRUE\t/\tTRUE\t2147483647\t${kv.substring(0, i)}\t${kv.substring(i + 1)}\n"); n++ }
            }
        }
        if (n == 0 || !sb.contains("\tSID\t")) false
        else { cookieFile.writeText(sb.toString()); hasCookies.value = true; true }
    } catch (_: Throwable) { false }

    fun clearCookies() { try { cookieFile.delete() } catch (_: Throwable) {}; hasCookies.value = false }

    // ───── استراتيجيات يوتيوب: بنجرّب واحدة ورا التانية لحد ما واحدة تنجح ─────
    private val ytArgs = listOf<String?>(
        "youtube:player_client=android_vr",     // 0: من غير JS، بس أحيانًا بيرجّع 403 أو 360p بس
        "youtube:player_client=android",        // 1
        "youtube:player_client=tv",             // 2
        null,                                   // 3: الافتراضي
        "youtube:player_client=mweb",           // 4
        "youtube:player_client=web_safari"      // 5
    )
    // ترتيب المحاولات: لو فيه كوكيز نبدأ بالعملاء اللي بيدعموها
    private fun order(url: String): List<Int> =
        if (!isYt(url)) listOf(0, 1) else if (hasCookies.value) listOf(2, 3, 4, 1, 0, 5) else listOf(0, 1, 2, 3, 4, 5)

    // أخطاء مفيش فايدة من تكرارها
    private fun fatal(e: Throwable): Boolean {
        val m = e.message ?: return false
        return listOf("Private video", "Video unavailable", "Unsupported URL", "has been removed", "members-only",
            "not available in your country", "copyright", "This video is not available").any { m.contains(it, true) }
    }
    private fun netErr(e: Throwable): Boolean {
        val m = e.message ?: return false
        return listOf("Unable to resolve host", "timed out", "Network is unreachable", "Connection reset").any { m.contains(it, true) }
    }
    private fun subErr(e: Throwable): Boolean {
        val m = e.message ?: return false
        return m.contains("subtitle", true) || m.contains("timedtext", true)
    }
    private fun isYt(u: String) = u.contains("youtube.com", true) || u.contains("youtu.be", true) || u.contains("youtube-nocookie.com", true)

    private fun common(r: YoutubeDLRequest, strat: Int, url: String) {
        r.addOption("--socket-timeout", "15")
        r.addOption("--retries", "5")
        r.addOption("--fragment-retries", "10")
        r.addOption("--extractor-retries", "2")
        r.addOption("--retry-sleep", "linear=1::2")
        r.addOption("--force-ipv4")                 // IPv6 على الموبايل سبب رئيسي لـ 403/429 المتقطع
        r.addOption("--http-chunk-size", "10M")     // تحميل على أجزاء بيتفادى تقييد السرعة/القطع من يوتيوب
        if (isYt(url)) ytArgs.getOrNull(strat)?.let { r.addOption("--extractor-args", it) }
        if (hasCookies.value && cookieFile.exists()) r.addOption("--cookies", cookieFile.absolutePath)
    }

    suspend fun fetch(url: String): VideoInfo = withContext(Dispatchers.IO) {
        ensureEngine()
        var last: Throwable? = null
        val tries = order(url).take(if (isYt(url)) 3 else 2)   // المعاينة لازم تكون سريعة
        for (i in tries) {
            try {
                return@withContext YoutubeDL.getInstance().getInfo(
                    YoutubeDLRequest(url).apply { addOption("--no-playlist"); addOption("--no-warnings"); common(this, i, url) })
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { last = e; delay(300) }
        }
        throw last ?: Exception("error")
    }

    fun enqueue(c: Context, t: Task) {
        if (t !in tasks) tasks.add(0, t)
        t.st = St.Queued; t.progress = 0f; t.msg = ""
        saveTasks()
        try { c.startForegroundService(Intent(c, DownloadService::class.java)) } catch (_: Throwable) {}
        t.job = scope.launch { try { sem.withPermit { run(t) } } finally { saveTasks() } }
    }

    private fun kill(t: Task) {
        try { YoutubeDL.getInstance().destroyProcessById(t.id) } catch (_: Throwable) {}
        try { YoutubeDL.getInstance().destroyProcessById(t.id + "_s") } catch (_: Throwable) {}
    }

    fun cancel(t: Task) {
        t.keep = false; t.msg = ""; t.st = St.Canceled
        kill(t); t.job?.cancel()
        saveTasks()
    }

    // إيقاف مؤقت: بيوقف العملية لكن بيسيب الجزء المحمّل، والاستئناف بيكمّل منه
    fun pause(t: Task) {
        t.keep = true; t.msg = "متوقف مؤقتًا"; t.st = St.Canceled
        kill(t); t.job?.cancel()
        saveTasks()
    }

    fun drop(t: Task) { tasks.remove(t); try { partialDir(t).deleteRecursively() } catch (_: Throwable) {}; saveTasks() }

    fun retryAll(c: Context) { tasks.filter { it.st == St.Failed || it.st == St.Canceled }.forEach { enqueue(c, it) } }
    fun clearFailed() {
        tasks.filter { it.st == St.Failed || it.st == St.Canceled }.forEach { try { partialDir(it).deleteRecursively() } catch (_: Throwable) {} }
        tasks.removeAll { it.st == St.Failed || it.st == St.Canceled }; saveTasks()
    }

    private fun partialDir(t: Task) = File(app.filesDir, "partial/${t.id}")

    private suspend fun run(t: Task) {
        if (t.st == St.Canceled) return
        val cm = app.getSystemService(ConnectivityManager::class.java)
        if (Prefs.wifiOnly && cm.isActiveNetworkMetered) { t.st = St.Failed; t.msg = "وضع الواي فاي فقط مفعّل"; return }
        t.st = St.Running; t.keep = false
        val dir = partialDir(t).apply { mkdirs() }
        try {
            ensureEngine()
            // الروابط اللي اتضافت بدون معاينة (دفعات/مشاركة سريعة): نجيب العنوان والصورة في الخلفية
            if (t.title == t.url && !t.playlist) scope.launch {
                try {
                    val i = fetch(t.url)
                    i.title?.let { t.title = it }
                    if (t.thumb == null) t.thumb = i.thumbnail
                    t.uploader = i.uploader
                    saveTasks()
                } catch (_: Throwable) {}
            }
            val plan = order(t.url).toMutableList()
            var idx = 0; var updated = updatedThisSession; var last: Throwable? = null; var ok = false; var netTries = 0
            var useSubs = t.subs && Prefs.subsMode == 0
            var curH = t.height; var round2 = false; var okS = plan.first()
            while (idx < plan.size) {
                val s = plan[idx++]
                if (t.st == St.Canceled) return
                t.msg = if (idx > 1) "بجرّب طريقة تانية ($idx/${plan.size})…" else ""
                try { attempt(t, dir, s, useSubs, curH); ok = true; okS = s; break }
                catch (e: CancellationException) { throw e }
                catch (e: Throwable) {
                    if (t.st == St.Canceled) return
                    last = e
                    // أخطاء النت: بنسيب الجزء المحمّل عشان نكمّل منه. غير كده بنبدأ نضيف
                    if (!netErr(e)) { dir.deleteRecursively(); dir.mkdirs(); t.progress = 0f }
                    when {
                        fatal(e) -> break
                        // فشل الترجمة (429 غالبًا) ما يوقّفش التحميل: نكمّل من غيرها
                        useSubs && subErr(e) -> { useSubs = false; t.msg = "الترجمة مش متاحة، بحمّل من غيرها…"; plan.add(idx, s); delay(500) }
                        curH > 0 && (e.message ?: "").contains("format is not available", true) ->
                            { curH = lowerQ(curH); t.msg = "الجودة مش متاحة، بجرّب أقل…"; plan.add(idx, s) }
                        netErr(e) -> {
                            if (++netTries > 3) break
                            t.msg = "مشكلة في النت… بحاول تاني"; plan.add(idx, s); delay(2000)
                        }
                        idx == plan.size && !updated -> {
                            updated = true; updatedThisSession = true
                            t.msg = "بيحدّث المحرك ويحاول تاني…"
                            updateBlocking(); plan.addAll(order(t.url).take(2))
                        }
                        idx == plan.size && !round2 -> {   // لفّة تانية، وبجودة أقل لو كانت محددة
                            round2 = true
                            if (curH > 0) { curH = lowerQ(curH); t.msg = "بجرّب جودة أقل…" } else t.msg = "بحاول تاني بعد ثواني…"
                            delay(4000); plan.addAll(order(t.url))
                        }
                        else -> delay(500)
                    }
                }
            }
            if (!ok) throw last ?: Exception("فشل التحميل")
            // الترجمة كملف منفصل: خطوة بعد الفيديو، وفشلها ما يأثرش على التحميل
            if (t.subs && Prefs.subsMode == 1 && !t.audio) {
                t.msg = "بجيب الترجمة…"
                try { subsOnly(t, dir, okS) } catch (e: CancellationException) { throw e } catch (_: Throwable) {}
            }
            finish(t, dir)
        } catch (e: CancellationException) {
            if (t.st != St.Canceled) { t.st = St.Failed; t.msg = "اتوقف" }
        } catch (e: Throwable) {
            if (t.st != St.Canceled) { t.st = St.Failed; t.msg = clean(e) }
        } finally {
            if (t.st == St.Done || (t.st == St.Canceled && !t.keep)) dir.deleteRecursively()
        }
    }

    private fun attempt(t: Task, dir: File, strat: Int, withSubs: Boolean, height: Int) {
        val r = YoutubeDLRequest(t.url)
        r.addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
        r.addOption("--no-mtime"); r.addOption("--continue"); common(r, strat, t.url)
        if (Prefs.limit > 0) r.addOption("--limit-rate", "${Prefs.limit}M")
        r.addOption("--concurrent-fragments", "8")
        r.addOption(if (t.playlist) "--yes-playlist" else "--no-playlist")
        if (Prefs.saveThumb) { r.addOption("--write-thumbnail"); r.addOption("--convert-thumbnails", "jpg") }
        if (Prefs.saveDesc) r.addOption("--write-description")
        if (!t.playlist && (t.clipStart.isNotBlank() || t.clipEnd.isNotBlank()))
            r.addOption("--download-sections", "*${t.clipStart.ifBlank { "0" }}-${t.clipEnd.ifBlank { "inf" }}")
        if (t.audio) {
            // m4a: بيتحمّل جاهز من غير إعادة ترميز (أسرع بكتير من mp3 على الموبايل)
            r.addOption("-f", if (Prefs.audioFmt == "m4a") "ba[ext=m4a]/ba/b" else "ba/b")
            r.addOption("-x"); r.addOption("--audio-format", Prefs.audioFmt)
            if (Prefs.audioFmt == "mp3") r.addOption("--audio-quality", "2")
        } else {
            r.addOption("-f", "bv*+ba/b")
            r.addOption("-S", if (height > 0) "res:$height,ext:mp4:m4a" else "ext:mp4:m4a")
            r.addOption("--merge-output-format", "mp4")
            if (withSubs) {
                r.addOption("--write-subs"); r.addOption("--write-auto-subs")
                r.addOption("--sub-langs", "ar.*,en.*"); r.addOption("--embed-subs")
            }
        }
        YoutubeDL.getInstance().execute(r, t.id, callback = { p, eta, _ ->
            if (p >= 0f) t.progress = (p / 100f).coerceIn(0f, 1f)
            t.eta = eta
        })
    }

    private fun subsOnly(t: Task, dir: File, strat: Int) {
        val r = YoutubeDLRequest(t.url)
        r.addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
        r.addOption("--skip-download"); common(r, strat, t.url)
        r.addOption(if (t.playlist) "--yes-playlist" else "--no-playlist")
        r.addOption("--write-subs"); r.addOption("--write-auto-subs")
        r.addOption("--sub-langs", "ar.*,en.*"); r.addOption("--convert-subs", "srt")
        r.addOption("--sleep-subtitles", "2")
        YoutubeDL.getInstance().execute(r, t.id + "_s")
    }

    private val intermediate = Regex("""\.f\d+(-\w+)?\.[A-Za-z0-9]+$""")
    private val imgExt = setOf("jpg", "jpeg", "png", "webp")
    private val txtExt = setOf("srt", "vtt", "description")

    private fun finish(t: Task, dir: File) {
        val all = dir.walkTopDown().filter { it.isFile }.toList()
        val files = all.filter { it.extension.lowercase() in media && !intermediate.containsMatchIn(it.name) }
        if (files.isEmpty()) throw Exception("ما اتنتجش ملف")
        val sub = subFolder(t)
        files.forEach { f ->
            val u = save(f, sub) ?: return@forEach
            val isAudio = (MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "").startsWith("audio")
            library.add(0, Item(f.nameWithoutExtension, t.thumb, u, isAudio, t.url))
        }
        all.filter { it.extension.lowercase().let { e -> e in imgExt || e in txtExt } }.forEach {
            try { save(it, sub) } catch (_: Throwable) {}
        }
        saveLib()
        t.progress = 1f; t.st = St.Done
        notifyDone(t)
        tasks.remove(t)
    }

    private fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().take(40)

    fun siteOf(u: String): String {
        val h = try { Uri.parse(u).host ?: "" } catch (_: Throwable) { "" }.lowercase()
        if (h.endsWith("youtu.be") || h.contains("youtube")) return "YouTube"
        val p = h.removePrefix("www.").removePrefix("m.").split(".").filter { it.isNotEmpty() }
        val n = if (p.size >= 2) p[p.size - 2] else p.firstOrNull() ?: "Other"
        return n.replaceFirstChar { it.uppercase() }
    }

    private fun subFolder(t: Task): String {
        val root = safe(Prefs.folder).ifBlank { "Grab" }
        val extra = when (Prefs.organize) {
            1 -> siteOf(t.url)
            2 -> t.uploader?.let { safe(it) }?.ifBlank { null } ?: siteOf(t.url)
            else -> ""
        }
        return if (extra.isEmpty()) root else "$root/$extra"
    }

    private fun save(f: File, sub: String): String? {
        val ext = f.extension.lowercase()
        val mime = when (ext) {
            "srt" -> "application/x-subrip"; "vtt" -> "text/vtt"; "description" -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
        }
        val vol = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val (col, base) = when {
            ext in imgExt -> MediaStore.Images.Media.getContentUri(vol) to Environment.DIRECTORY_PICTURES
            ext in txtExt -> MediaStore.Downloads.getContentUri(vol) to Environment.DIRECTORY_DOWNLOADS
            mime.startsWith("audio") -> MediaStore.Audio.Media.getContentUri(vol) to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Video.Media.getContentUri(vol) to Environment.DIRECTORY_MOVIES
        }
        val name = if (ext == "description") f.nameWithoutExtension + ".txt" else f.name
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$base/$sub")
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

    private val taskFile get() = File(app.filesDir, "tasks.json")
    private fun loadTasks() {
        try {
            val a = JSONArray(taskFile.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val t = Task(o.getString("id"), o.getString("url"), o.getString("t"), o.optString("th").ifEmpty { null },
                    o.getBoolean("a"), o.getInt("h"), o.getBoolean("s"), o.getBoolean("p"))
                t.uploader = o.optString("u").ifEmpty { null }; t.clipStart = o.optString("cs"); t.clipEnd = o.optString("ce")
                val saved = try { St.valueOf(o.getString("st")) } catch (_: Throwable) { St.Failed }
                if (saved == St.Canceled || saved == St.Failed) { t.st = saved; t.msg = o.optString("m") }
                else { t.st = St.Failed; t.msg = "اتوقف لما التطبيق اتقفل — اضغط إعادة" }
                tasks.add(t)
            }
        } catch (_: Throwable) {}
    }
    fun saveTasks() {
        try {
            val a = JSONArray()
            tasks.toList().forEach { t ->
                a.put(JSONObject().put("id", t.id).put("url", t.url).put("t", t.title).put("th", t.thumb ?: "")
                    .put("a", t.audio).put("h", t.height).put("s", t.subs).put("p", t.playlist)
                    .put("st", t.st.name).put("m", t.msg)
                    .put("u", t.uploader ?: "").put("cs", t.clipStart).put("ce", t.clipEnd))
            }
            taskFile.writeText(a.toString())
        } catch (_: Throwable) {}
    }

    private val libFile get() = File(app.filesDir, "lib.json")
    private fun loadLib() {
        try {
            val a = JSONArray(libFile.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                library.add(Item(o.getString("t"), o.optString("th").ifEmpty { null }, o.getString("u"), o.getBoolean("a"), o.optString("s")))
            }
        } catch (_: Throwable) {}
    }
    fun saveLib() {
        val a = JSONArray()
        library.forEach { a.put(JSONObject().put("t", it.title).put("th", it.thumb ?: "").put("u", it.uri).put("a", it.audio).put("s", it.src)) }
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
        // أزرار الإشعار
        when (i?.action) {
            "grab.CANCEL" -> { Repo.tasks.filter { it.st == St.Running || it.st == St.Queued }.forEach { Repo.cancel(it) }; return START_NOT_STICKY }
            "grab.PAUSE" -> { Repo.tasks.filter { it.st == St.Running || it.st == St.Queued }.forEach { Repo.pause(it) }; return START_NOT_STICKY }
        }
        val nm = getSystemService(NotificationManager::class.java)
        fun act(a: String, label: String, icon: Int): Notification.Action {
            val pi = PendingIntent.getService(this, a.hashCode(), Intent(this, DownloadService::class.java).setAction(a),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), label, pi).build()
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun build(p: Int, txt: String) = Notification.Builder(this, "dl")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Grab").setContentText(txt)
            .setContentIntent(open)
            .addAction(act("grab.PAUSE", "إيقاف مؤقت", android.R.drawable.ic_media_pause))
            .addAction(act("grab.CANCEL", "إلغاء", android.R.drawable.ic_menu_close_clear_cancel))
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
