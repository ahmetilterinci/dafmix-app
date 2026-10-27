package xyz.dafmix.app.data

import android.content.Context

const val DEFAULT_BASE = "https://dafmix.xyz"

data class Progress(val contentId: String, val posMs: Long, val durMs: Long, val ts: Long) {
    val fraction: Float get() = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("dafmix", Context.MODE_PRIVATE)
    private val prog = ctx.getSharedPreferences("dafmix_progress", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("base", DEFAULT_BASE) ?: DEFAULT_BASE
        set(v) { sp.edit().putString("base", v.trim().trimEnd('/')).apply() }

    var loggedIn: Boolean
        get() = sp.getBoolean("logged_in", false)
        set(v) { sp.edit().putBoolean("logged_in", v).apply() }

    var profileId: Int
        get() = sp.getInt("profile_id", 0)
        set(v) { sp.edit().putInt("profile_id", v).apply() }

    var profileName: String
        get() = sp.getString("profile_name", "") ?: ""
        set(v) { sp.edit().putString("profile_name", v).apply() }

    var profileAvatar: String
        get() = sp.getString("profile_avatar", "") ?: ""
        set(v) { sp.edit().putString("profile_avatar", v).apply() }

    /** HLS indirmelerinde kalite: 0 yüksek, 1 orta (720p), 2 düşük (480p) */
    var hlsQuality: Int
        get() = sp.getInt("hls_quality", 1)
        set(v) { sp.edit().putInt("hls_quality", v).apply() }

    /** Son seçilen kaynak etiketi (ör. "1080p"); sonraki oynatma/indirmelerde varsa o seçilir */
    var sourceLabel: String
        get() = sp.getString("source_label", "") ?: ""
        set(v) { sp.edit().putString("source_label", v).apply() }

    /** Oynatıcı: sonraki bölümü otomatik oynat */
    var autoNext: Boolean
        get() = sp.getBoolean("auto_next", true)
        set(v) { sp.edit().putBoolean("auto_next", v).apply() }

    /** Oynatıcı görüntü modu: 0 sığdır, 1 doldur (kırp), 2 uzat */
    var resizeMode: Int
        get() = sp.getInt("resize_mode", 0)
        set(v) { sp.edit().putInt("resize_mode", v).apply() }

    fun getStr(key: String): String = sp.getString(key, "") ?: ""
    fun putStr(key: String, v: String) { sp.edit().putString(key, v).apply() }

    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", true)
        set(v) { sp.edit().putBoolean("wifi_only", v).apply() }

    var catalogCache: String?
        get() = sp.getString("catalog_cache", null)
        set(v) { sp.edit().putString("catalog_cache", v).apply() }

    fun setProgress(contentId: String, posMs: Long, durMs: Long) {
        prog.edit().putString(contentId, "$posMs|$durMs|${System.currentTimeMillis()}").apply()
    }

    fun getProgress(contentId: String): Progress? = parse(contentId, prog.getString(contentId, null))

    fun allProgress(): List<Progress> =
        prog.all.mapNotNull { (k, v) -> parse(k, v as? String) }.sortedByDescending { it.ts }

    fun clearProgress(contentId: String) { prog.edit().remove(contentId).apply() }

    private fun parse(id: String, raw: String?): Progress? {
        if (raw == null) return null
        val p = raw.split("|")
        if (p.size < 3) return null
        val pos = p[0].toLongOrNull() ?: return null
        val dur = p[1].toLongOrNull() ?: return null
        val ts = p[2].toLongOrNull() ?: 0L
        return Progress(id, pos, dur, ts)
    }

    fun clearSession() {
        sp.edit().remove("logged_in").remove("profile_id").remove("profile_name").remove("profile_avatar").apply()
    }
}
