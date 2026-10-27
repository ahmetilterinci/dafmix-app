package xyz.dafmix.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ApiException(val status: Int, message: String, val code: String = "") : Exception(message)
class OfflineException : IOException("İnternet bağlantısı yok")

class Api(private val client: OkHttpClient, private val prefs: Prefs) {
    private val json = "application/json; charset=utf-8".toMediaType()

    private suspend fun raw(method: String, path: String, body: JSONObject? = null, extra: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(prefs.baseUrl + "/api" + path).header("Accept", "application/json")
        for ((k, v) in extra) rb.header(k, v)
        if (prefs.profileId > 0) rb.header("X-Profile-Id", prefs.profileId.toString())
        if (method == "GET") rb.get() else rb.method(method, (body ?: JSONObject()).toString().toRequestBody(json))
        val resp = try {
            client.newCall(rb.build()).execute()
        } catch (e: IOException) {
            throw OfflineException()
        }
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                var msg = "Hata (${r.code})"
                var code = ""
                try {
                    val j = JSONObject(text)
                    msg = j.optString("error", msg)
                    code = j.optString("code", "")
                } catch (_: Exception) { }
                throw ApiException(r.code, msg, code)
            }
            text
        }
    }

    suspend fun login(username: String, password: String) {
        raw("POST", "/auth/login", JSONObject().put("username", username).put("password", password))
    }

    suspend fun logout() { raw("POST", "/auth/logout") }

    /** true: oturum açık */
    suspend fun sessionActive(): Boolean {
        val o = JSONObject(raw("GET", "/auth/me"))
        return !o.isNull("user") && o.has("user")
    }

    /** Karşılama sayfası içeriği (herkese açık) */
    suspend fun landing(): Landing? = try {
        val o = JSONObject(raw("GET", "/landing")).optJSONObject("landing")
        if (o == null) null else Landing(o.str("headline"), o.str("subline"), o.str("slogan"), o.str("videoUrl"))
    } catch (e: Exception) { null }

    suspend fun registrationOpen(): Boolean = try {
        JSONObject(raw("GET", "/auth/me")).optBoolean("registrationOpen", true)
    } catch (e: Exception) { true }

    suspend fun me(): Me? {
        val u = JSONObject(raw("GET", "/auth/me")).optJSONObject("user") ?: return null
        return Me(u.str("username"), u.str("email"), u.str("role"), u.optBoolean("approved", false), u.optBoolean("watchParty", false))
    }

    /** Profil durumu (favoriler vb.) — site ile ortak */
    suspend fun getState(): Map<String, String> {
        val st = JSONObject(raw("GET", "/state")).optJSONObject("state") ?: return emptyMap()
        val out = HashMap<String, String>()
        for (k in st.keys()) out[k] = st.optString(k, "")
        return out
    }

    suspend fun putState(set: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in set) o.put(k, v)
        raw("PUT", "/state", JSONObject().put("set", o))
    }

    /** (okunmamış sayısı, bildirimler) */
    suspend fun notifications(): Pair<Int, List<Notif>> {
        val o = JSONObject(raw("GET", "/notifications?limit=30"))
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val n = arr.optJSONObject(i) ?: return@mapNotNull null
            Notif(n.optInt("id"), n.str("kind"), n.str("title"), n.str("body"), n.str("link"), n.str("image"), n.optLong("at"), n.optBoolean("read", false))
        }
        return o.optInt("unread", 0) to items
    }

    suspend fun markAllRead() { raw("POST", "/notifications/read", JSONObject().put("all", true)) }

    suspend fun markRead(id: Int) { raw("POST", "/notifications/read", JSONObject().put("ids", JSONArray().put(id))) }

    suspend fun profiles(): List<Profile> {
        val arr = JSONObject(raw("GET", "/profiles")).optJSONArray("profiles") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Profile(o.optInt("id"), o.str("name"), o.str("avatar"), o.optBoolean("isKid", false))
        }
    }

    suspend fun catalog(): List<Title> {
        val text = raw("GET", "/catalog")
        prefs.catalogCache = text
        return parseCatalog(text)
    }

    fun cachedCatalog(): List<Title> = prefs.catalogCache?.let { runCatching { parseCatalog(it) }.getOrNull() } ?: emptyList()

    private fun parseCatalog(text: String): List<Title> {
        val arr = JSONArray(text)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parseTitle) }
    }

    suspend fun detail(id: Int): TitleDetail = parseDetail(JSONObject(raw("GET", "/titles/$id")))

    suspend fun sources(titleId: Int, episodeId: Int?): List<Source> {
        val q = if (episodeId != null) "?episode=$episodeId" else ""
        val arr = JSONObject(raw("GET", "/titles/$titleId/sources$q")).optJSONArray("sources") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Source(o.optInt("id"), o.str("label"), o.str("url"), o.str("kind"))
        }
    }

    /* ---------------- Birlikte İzle ---------------- */
    suspend fun roomInfo(code: String): JSONObject = JSONObject(raw("GET", "/rooms/$code"))

    /** Açık odam varsa kodu */
    suspend fun roomMine(): String? {
        val r = JSONObject(raw("GET", "/rooms/mine")).optJSONObject("room") ?: return null
        return r.str("code").ifBlank { null }
    }

    suspend fun roomCreate(titleId: Int, season: Int, episode: Int, control: String): String {
        val b = JSONObject().put("titleId", titleId).put("control", control)
        if (season > 0 || episode > 0) b.put("season", season).put("episode", episode)
        val r = JSONObject(raw("POST", "/rooms", b))
        return r.optJSONObject("room")?.str("code").orEmpty()
    }

    suspend fun roomPost(code: String, path: String, body: JSONObject, client: String) {
        raw("POST", "/rooms/$code/$path", body, mapOf("X-Room-Client" to client))
    }

    suspend fun roomPoll(code: String, client: String): JSONObject =
        JSONObject(raw("GET", "/rooms/$code/poll", null, mapOf("X-Room-Client" to client)))

    /** Sitenin /img küçültücüsü üzerinden görsel adresi (genişlik w) */
    fun img(url: String, w: Int = 480): String {
        if (url.isBlank()) return ""
        val base = prefs.baseUrl
        if (url.startsWith("/")) return base + url
        if (!url.startsWith("http", ignoreCase = true) || url.substringBefore('?').endsWith(".svg", ignoreCase = true) || url.startsWith("$base/")) return url
        val b64 = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return "$base/img/$w/$b64.webp"
    }

    /** Kaynak adresi göreliyse (ör. /hls/...) site adresine bağlar */
    fun absolute(url: String): String = if (url.startsWith("/")) prefs.baseUrl + url else url
}
