package xyz.dafmix.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import xyz.dafmix.app.Svc

/** "Listem" (favoriler): profil başına saklanır, site ile aynı anahtarla (aniwatch_favorites_list) eşitlenir */
object Favs {
    private const val KEY = "aniwatch_favorites_list"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = _ids

    private fun cacheKey() = "favs_${Svc.prefs.profileId}"
    private fun dirtyKey() = "favs_dirty_${Svc.prefs.profileId}"

    private fun parse(raw: String): Set<Int> = try {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { a.opt(it)?.toString()?.toIntOrNull() }.toSet()
    } catch (_: Exception) { emptySet() }

    private fun toJson(s: Set<Int>): String {
        val a = JSONArray()
        s.forEach { a.put(it.toString()) }
        return a.toString()
    }

    /** Aktif profile göre yerel kopyayı yükler */
    fun reload() { _ids.value = parse(Svc.prefs.getStr(cacheKey())) }

    /** İnternet varsa sunucu ile eşitler (bekleyen yerel değişiklik varsa onu gönderir) */
    suspend fun sync() {
        try {
            if (Svc.prefs.getStr(dirtyKey()) == "1") {
                Svc.api.putState(mapOf(KEY to toJson(_ids.value)))
                Svc.prefs.putStr(dirtyKey(), "")
            } else {
                val raw = Svc.api.getState()[KEY]
                if (raw != null) {
                    _ids.value = parse(raw)
                    Svc.prefs.putStr(cacheKey(), toJson(_ids.value))
                }
            }
        } catch (_: Exception) { }
    }

    fun toggle(id: Int) {
        val cur = _ids.value
        val next = if (id in cur) cur - id else cur + id
        _ids.value = next
        Svc.prefs.putStr(cacheKey(), toJson(next))
        Svc.prefs.putStr(dirtyKey(), "1")
        scope.launch {
            try {
                Svc.api.putState(mapOf(KEY to toJson(next)))
                Svc.prefs.putStr(dirtyKey(), "")
            } catch (_: Exception) { }
        }
    }
}
