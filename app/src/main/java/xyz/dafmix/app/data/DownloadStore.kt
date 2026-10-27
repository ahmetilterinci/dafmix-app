package xyz.dafmix.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Çevrimdışıyken de gösterilebilsin diye indirilen içeriklerin bilgileri (başlık, bölüm, kapak) */
data class DownloadMeta(
    val contentId: String,   // film: t<id>  |  bölüm: e<titleId>_<episodeId>
    val titleId: Int,
    val episodeId: Int,      // film için 0
    val titleName: String,
    val type: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val posterPath: String,  // cihazdaki kapak dosyası
    val addedAt: Long,
    val introStart: Double?,
    val introEnd: Double?,
) {
    val isMovie get() = episodeId == 0
    val label get() = if (isMovie) titleName else "S$season B$episode" + if (episodeTitle.isNotBlank()) " · $episodeTitle" else ""
}

class DownloadStore(private val file: File) {
    private val _items = MutableStateFlow<List<DownloadMeta>>(emptyList())
    val items: StateFlow<List<DownloadMeta>> = _items

    init { load() }

    private fun load() {
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            _items.value = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                DownloadMeta(
                    o.str("contentId"), o.optInt("titleId"), o.optInt("episodeId"), o.str("titleName"), o.str("type"),
                    o.optInt("season"), o.optInt("episode"), o.str("episodeTitle"), o.str("posterPath"),
                    o.optLong("addedAt"), o.dbl("introStart"), o.dbl("introEnd"),
                )
            }
        } catch (_: Exception) { }
    }

    @Synchronized
    private fun save() {
        val arr = JSONArray()
        for (m in _items.value) {
            arr.put(
                JSONObject().put("contentId", m.contentId).put("titleId", m.titleId).put("episodeId", m.episodeId)
                    .put("titleName", m.titleName).put("type", m.type).put("season", m.season).put("episode", m.episode)
                    .put("episodeTitle", m.episodeTitle).put("posterPath", m.posterPath).put("addedAt", m.addedAt)
                    .put("introStart", m.introStart ?: JSONObject.NULL).put("introEnd", m.introEnd ?: JSONObject.NULL)
            )
        }
        try { file.writeText(arr.toString()) } catch (_: Exception) { }
    }

    @Synchronized
    fun put(m: DownloadMeta) {
        _items.value = _items.value.filter { it.contentId != m.contentId } + m
        save()
    }

    @Synchronized
    fun remove(contentId: String) {
        _items.value = _items.value.filter { it.contentId != contentId }
        save()
    }

    fun get(contentId: String): DownloadMeta? = _items.value.firstOrNull { it.contentId == contentId }

    fun clear() { _items.value = emptyList(); save() }
}
