package xyz.dafmix.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import xyz.dafmix.app.Svc
import java.util.concurrent.TimeUnit

data class RInfo(val code: String, val title: String, val pid: String, val type: String, val control: String, val hostName: String)
data class RMember(val id: String, val name: String, val avatar: String, val host: Boolean)
data class RChat(val id: String, val system: Boolean, val name: String, val host: Boolean, val text: String)
data class RState(
    val s: Int, val e: Int, val playing: Boolean, val hold: Boolean, val t: Double, val at: Long,
    val srcId: Int?, val srcLabel: String, val op: String, val by: String,
)

/**
 * Birlikte İzle oda istemcisi (sitedeki room.js ile aynı sözleşme): SSE ile canlı olaylar,
 * her 2 sn'de yedek yoklama (poll), komutlar POST ile. Host oynatıcısı zaman referansıdır.
 */
class RoomClient(val code: String) {
    var info by mutableStateOf<RInfo?>(null)
    var members by mutableStateOf<List<RMember>>(emptyList())
    val chat = mutableStateListOf<RChat>()
    var state by mutableStateOf<RState?>(null)
    var you by mutableStateOf("")
    var isHost by mutableStateOf(false)
    /** Oda bitti / çıkarıldı / bulunamadı: açıklama metni */
    var ended by mutableStateOf<String?>(null)
    /** Host dışındaki bir denetleyicinin (ya da host'un) komutu: oynatıcı bir kez uygulasın */
    @Volatile var pendingCmd: RState? = null

    @Volatile private var offset = 0L
    private val seen = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var call: Call? = null
    @Volatile private var started = false

    val canControl: Boolean get() = isHost || info?.control == "all"
    fun serverNow(): Long = System.currentTimeMillis() + offset

    fun expected(st: RState): Double = if (st.playing && !st.hold) st.t + (serverNow() - st.at) / 1000.0 else st.t

    fun start() {
        if (started) return
        started = true
        scope.launch { sseLoop() }
        scope.launch { pollLoop() }
    }

    /** Odadan ayrıl; host ise oda kapanır */
    @OptIn(DelicateCoroutinesApi::class)
    fun stop() {
        started = false
        val wasHost = isHost
        val me = you
        try { call?.cancel() } catch (_: Exception) { }
        scope.cancel()
        if (wasHost && me.isNotBlank() && ended == null) {
            GlobalScope.launch(Dispatchers.IO) { try { Svc.api.roomPost(code, "close", JSONObject(), me) } catch (_: Exception) { } }
        }
    }

    suspend fun send(path: String, body: JSONObject = JSONObject()) {
        Svc.api.roomPost(code, path, body, you)
    }

    fun post(path: String, body: JSONObject = JSONObject()) {
        if (you.isBlank()) return
        scope.launch { try { send(path, body) } catch (_: Exception) { } }
    }

    // ---------------------------------------------------------------- SSE
    private suspend fun sseLoop() {
        val client = Svc.client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        while (scope.isActive && ended == null) {
            try {
                val url = Svc.prefs.baseUrl + "/api/rooms/$code/events?p=${Svc.prefs.profileId}"
                val req = Request.Builder().url(url).header("Accept", "text/event-stream").header("Cache-Control", "no-cache").build()
                val c = client.newCall(req)
                call = c
                c.execute().use { resp ->
                    if (resp.code == 404) { ended = "Oda bulunamadı ya da kapatıldı."; return@use }
                    if (!resp.isSuccessful) return@use
                    val src = resp.body?.source() ?: return@use
                    var ev = ""
                    val data = StringBuilder()
                    while (scope.isActive && !src.exhausted()) {
                        val line = src.readUtf8Line() ?: break
                        if (line.isEmpty()) {
                            if (ev.isNotEmpty()) handle(ev, data.toString())
                            ev = ""; data.setLength(0)
                        } else if (line.startsWith("event:")) ev = line.substring(6).trim()
                        else if (line.startsWith("data:")) data.append(line.substring(5).trim())
                    }
                }
            } catch (_: Exception) { }
            if (scope.isActive && ended == null) delay(2500)
        }
    }

    // ---------------------------------------------------------------- Yedek yoklama
    private suspend fun pollLoop() {
        var fails = 0
        while (scope.isActive && ended == null) {
            delay(2000)
            if (you.isBlank()) continue
            try {
                val d = Svc.api.roomPoll(code, you)
                fails = 0
                val st = d.optJSONObject("state")
                if (st != null) { offset = st.optLong("now") - System.currentTimeMillis(); applyState(st, cmd = false) }
                d.optJSONObject("room")?.let { info = parseInfo(it) }
                d.optJSONArray("members")?.let { members = parseMembers(it) }
                d.optJSONArray("chat")?.let { arr -> for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::addChat) }
            } catch (e: ApiException) {
                if (e.status == 404) ended = "Oda kapandı."
                else if (e.status == 403 && ++fails >= 2) ended = "Bu odada artık değilsin."
            } catch (_: Exception) { }
        }
    }

    // ---------------------------------------------------------------- Olaylar
    private fun handle(ev: String, raw: String) {
        val d = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        when (ev) {
            "init" -> {
                val y = d.optJSONObject("you")
                you = y?.optString("id").orEmpty()
                isHost = y?.optBoolean("host", false) ?: false
                d.optJSONObject("room")?.let { info = parseInfo(it) }
                d.optJSONArray("members")?.let { members = parseMembers(it) }
                offset = d.optLong("now") - System.currentTimeMillis()
                d.optJSONArray("chat")?.let { arr -> for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::addChat) }
                d.optJSONObject("state")?.let { applyState(it, cmd = false) }
            }
            "state" -> { offset = d.optLong("now") - System.currentTimeMillis(); applyState(d, cmd = true) }
            "members" -> d.optJSONArray("members")?.let { members = parseMembers(it) }
            "room" -> d.optJSONObject("room")?.let { info = parseInfo(it) }
            "chat" -> addChat(d)
            "kicked" -> ended = "Host seni odadan çıkardı."
            "closed" -> ended = d.optString("reason").ifBlank { "Oda kapandı." }
            "denied" -> ended = d.optString("error").ifBlank { "Odaya girilemedi." }
            "replaced" -> ended = "Bu odaya başka bir yerden bağlandın."
        }
    }

    private fun applyState(d: JSONObject, cmd: Boolean) {
        val src = d.optJSONObject("src")
        val st = RState(
            s = d.optInt("s"), e = d.optInt("e"), playing = d.optBoolean("playing"), hold = d.optBoolean("hold"),
            t = d.optDouble("t", 0.0), at = d.optLong("at"),
            srcId = if (src != null && src.has("id") && !src.isNull("id")) src.optInt("id") else null,
            srcLabel = src?.optString("label").orEmpty(), op = d.optString("op"), by = d.optString("by"),
        )
        state = st
        if (cmd && st.by.isNotEmpty() && (st.op == "play" || st.op == "pause" || st.op == "seek")) pendingCmd = st
    }

    private fun addChat(o: JSONObject) {
        val id = o.optString("id")
        if (id.isNotEmpty()) { if (!seen.add(id)) return }
        val from = o.optJSONObject("from")
        chat.add(RChat(id, o.optBoolean("system", false), from?.optString("name").orEmpty(), from?.optBoolean("host", false) ?: false, o.optString("text")))
        while (chat.size > 150) chat.removeAt(0)
    }

    private fun parseInfo(o: JSONObject) = RInfo(
        o.optString("code"), o.optString("title"), o.opt("pid")?.toString().orEmpty(), o.optString("type"),
        o.optString("control", "host"), o.optString("hostName"),
    )

    private fun parseMembers(a: org.json.JSONArray): List<RMember> =
        (0 until a.length()).mapNotNull { i ->
            val m = a.optJSONObject(i) ?: return@mapNotNull null
            RMember(m.optString("id"), m.optString("name"), m.optString("avatar"), m.optBoolean("host", false))
        }
}
