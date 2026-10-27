package xyz.dafmix.app.download

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.ApiException
import xyz.dafmix.app.data.DownloadMeta
import xyz.dafmix.app.data.Episode
import xyz.dafmix.app.data.OfflineException
import xyz.dafmix.app.data.Title
import java.io.File
import java.net.URI

fun movieId(titleId: Int) = "t$titleId"
fun episodeContentId(titleId: Int, episodeId: Int) = "e${titleId}_$episodeId"

/** Tercih edilen etiketli kaynak varsa o, yoksa ilk kaynak */
fun pickSource(list: List<xyz.dafmix.app.data.Source>): xyz.dafmix.app.data.Source? {
    val pref = Svc.prefs.sourceLabel
    return list.firstOrNull { pref.isNotBlank() && it.label == pref } ?: list.firstOrNull()
}

object Downloads {

    /** Film (episode == null) ya da bölüm indirmesini başlatır. Hata varsa kullanıcıya gösterilecek metni, başarıda null döner. */
    suspend fun enqueue(ctx: Context, title: Title, season: Int, episode: Episode?): String? {
        val contentId = if (episode == null) movieId(title.id) else episodeContentId(title.id, episode.id)
        return try {
            val sources = Svc.api.sources(title.id, episode?.id)
            val src = pickSource(sources) ?: return "Bu içerik için kaynak bulunamadı"
            val abs = Svc.api.absolute(src.url)
            if (!abs.startsWith("http", true)) return "Bu kaynak indirilemiyor"
            val isHls = src.kind == "hls" || abs.substringBefore('?').endsWith(".m3u8", true)
            val finalUrl = if (isHls) withContext(Dispatchers.IO) { pickVariant(abs, Svc.prefs.hlsQuality) } else abs

            val poster = savePoster(ctx, title)
            Svc.store.put(
                DownloadMeta(
                    contentId = contentId,
                    titleId = title.id,
                    episodeId = episode?.id ?: 0,
                    titleName = title.title,
                    type = title.type,
                    season = season,
                    episode = episode?.number ?: 0,
                    episodeTitle = episode?.title ?: "",
                    posterPath = poster,
                    addedAt = System.currentTimeMillis(),
                    introStart = episode?.introStart,
                    introEnd = episode?.introEnd,
                )
            )
            val req = DownloadRequest.Builder(contentId, Dl.uri(finalUrl)).apply {
                if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
            }.build()
            DownloadService.sendAddDownload(ctx, DfxDownloadService::class.java, req, true)
            null
        } catch (e: ApiException) {
            if (e.code == "subscription_required") "İndirmek için aboneliğinin onaylanmış olması gerekir" else e.message ?: "İndirme başlatılamadı"
        } catch (e: OfflineException) {
            "İnternet bağlantısı yok"
        } catch (e: Exception) {
            e.message ?: "İndirme başlatılamadı"
        }
    }

    fun remove(ctx: Context, contentId: String) {
        DownloadService.sendRemoveDownload(ctx, DfxDownloadService::class.java, contentId, false)
        val meta = Svc.store.get(contentId)
        Svc.store.remove(contentId)
        Svc.prefs.clearProgress(contentId)
        if (meta != null && Svc.store.items.value.none { it.titleId == meta.titleId }) {
            try { File(meta.posterPath).delete() } catch (_: Exception) { }
        }
    }

    fun removeAll(ctx: Context) {
        DownloadService.sendRemoveAllDownloads(ctx, DfxDownloadService::class.java, false)
        for (m in Svc.store.items.value) { try { File(m.posterPath).delete() } catch (_: Exception) { } }
        Svc.store.clear()
    }

    /** Kapak görselini cihaza kaydeder (çevrimdışıyken de görünsün) */
    private suspend fun savePoster(ctx: Context, title: Title): String = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.filesDir, "posters").apply { mkdirs() }
            val f = File(dir, "${title.id}.img")
            if (f.exists() && f.length() > 0) return@withContext f.absolutePath
            val url = Svc.api.img(title.poster, 480)
            if (url.isBlank()) return@withContext ""
            Svc.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext ""
                f.outputStream().use { out -> r.body?.byteStream()?.copyTo(out) }
            }
            f.absolutePath
        } catch (_: Exception) { "" }
    }

    /** HLS ana oynatma listesinden istenen kaliteyi seçer (tümünü indirmek yerine tek varyant → az depolama) */
    private fun pickVariant(masterUrl: String, quality: Int): String {
        return try {
            val text = Svc.client.newCall(Request.Builder().url(masterUrl).build()).execute().use { r ->
                if (!r.isSuccessful) return masterUrl
                r.body?.string().orEmpty()
            }
            if (!text.contains("#EXT-X-STREAM-INF")) return masterUrl
            val lines = text.lines()
            data class V(val height: Int, val bandwidth: Long, val uri: String)
            val variants = ArrayList<V>()
            var i = 0
            while (i < lines.size) {
                val l = lines[i].trim()
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val h = Regex("RESOLUTION=\\d+x(\\d+)").find(l)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    var j = i + 1
                    while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                    if (j < lines.size) variants.add(V(h, bw, lines[j].trim()))
                    i = j
                }
                i++
            }
            if (variants.isEmpty()) return masterUrl
            val sorted = variants.sortedWith(compareBy({ it.height }, { it.bandwidth }))
            val limit = when (quality) { 0 -> Int.MAX_VALUE; 1 -> 720; else -> 480 }
            val chosen = sorted.lastOrNull { it.height <= limit } ?: sorted.first()
            URI(masterUrl).resolve(chosen.uri).toString()
        } catch (_: Exception) { masterUrl }
    }
}
