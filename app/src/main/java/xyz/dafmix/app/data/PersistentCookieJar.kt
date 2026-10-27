package xyz.dafmix.app.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Oturum çerezini (sid) kalıcı saklar; uygulama kapansa da giriş açık kalır. */
class PersistentCookieJar(ctx: Context) : CookieJar {
    private val sp = ctx.getSharedPreferences("dafmix_cookies", Context.MODE_PRIVATE)
    private val mem = LinkedHashMap<String, Cookie>()

    init {
        for ((_, v) in sp.all) {
            val c = decode(v as? String ?: continue) ?: continue
            if (c.expiresAt > System.currentTimeMillis()) mem[key(c)] = c
        }
    }

    private fun key(c: Cookie) = "${c.domain}|${c.path}|${c.name}"

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val ed = sp.edit()
        for (c in cookies) {
            val k = key(c)
            if (c.expiresAt <= System.currentTimeMillis() || c.value.isEmpty()) {
                mem.remove(k); ed.remove(k)
            } else {
                mem[k] = c; ed.putString(k, encode(c))
            }
        }
        ed.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return mem.values.filter { it.expiresAt > now && it.matches(url) }
    }

    @Synchronized
    fun clear() { mem.clear(); sp.edit().clear().apply() }

    private fun encode(c: Cookie): String =
        listOf(c.name, c.value, c.domain, c.path, c.expiresAt.toString(), c.secure.toString(), c.httpOnly.toString(), c.hostOnly.toString())
            .joinToString("\u0001")

    private fun decode(s: String): Cookie? {
        val p = s.split("\u0001")
        if (p.size < 8) return null
        return try {
            val b = Cookie.Builder().name(p[0]).value(p[1]).path(p[3]).expiresAt(p[4].toLong())
            if (p[7].toBoolean()) b.hostOnlyDomain(p[2]) else b.domain(p[2])
            if (p[5].toBoolean()) b.secure()
            if (p[6].toBoolean()) b.httpOnly()
            b.build()
        } catch (e: Exception) { null }
    }
}
