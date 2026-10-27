package xyz.dafmix.app.download

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import xyz.dafmix.app.Svc
import java.io.File
import java.util.concurrent.Executors

data class DlState(val state: Int, val percent: Float, val bytes: Long) {
    val completed get() = state == Download.STATE_COMPLETED
    val failed get() = state == Download.STATE_FAILED
    val active get() = state == Download.STATE_DOWNLOADING || state == Download.STATE_QUEUED || state == Download.STATE_RESTARTING
}

/** Media3 indirme altyapısı: indirilenler uygulamanın özel klasöründe saklanır (başka uygulamalar göremez) */
object Dl {
    lateinit var cache: SimpleCache
        private set
    lateinit var manager: DownloadManager
        private set
    lateinit var upstream: DataSource.Factory
        private set
    /** İnternetten izleme */
    lateinit var streamFactory: DefaultMediaSourceFactory
        private set
    /** İndirilmiş içeriği (internet olmadan) izleme: yalnızca önbellekten okur */
    lateinit var offlineFactory: DefaultMediaSourceFactory
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, DlState>>(emptyMap())
    val states: StateFlow<Map<String, DlState>> = _states

    fun init(ctx: Context) {
        val dir = File(ctx.filesDir, "dl_cache").apply { mkdirs() }
        val db = StandaloneDatabaseProvider(ctx)
        cache = SimpleCache(dir, NoOpCacheEvictor(), db)
        val client: OkHttpClient = Svc.client
        upstream = OkHttpDataSource.Factory(client).setUserAgent("DafmixApp/1.0")
        manager = DownloadManager(ctx, db, cache, upstream, Executors.newFixedThreadPool(3))
        manager.maxParallelDownloads = 2
        applyRequirements()
        manager.resumeDownloads()

        streamFactory = DefaultMediaSourceFactory(upstream)
        val readOnlyCache = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
        offlineFactory = DefaultMediaSourceFactory(readOnlyCache)

        scope.launch {
            while (true) {
                refresh()
                delay(if (_states.value.values.any { it.active }) 700 else 2000)
            }
        }
    }

    fun applyRequirements() {
        manager.requirements = Requirements(
            if (Svc.prefs.wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK
        )
    }

    private fun refresh() {
        try {
            val map = HashMap<String, DlState>()
            manager.downloadIndex.getDownloads().use { c ->
                while (c.moveToNext()) {
                    val d = c.download
                    map[d.request.id] = DlState(d.state, d.percentDownloaded, d.bytesDownloaded)
                }
            }
            if (map != _states.value) _states.value = map
        } catch (_: Exception) { }
    }

    fun getDownload(contentId: String): Download? = try {
        manager.downloadIndex.getDownload(contentId)
    } catch (_: Exception) { null }

    /** İndirme tamamlandıysa internetsiz oynatılacak kaynak */
    fun offlineSource(contentId: String): MediaSource? {
        val d = getDownload(contentId) ?: return null
        if (d.state != Download.STATE_COMPLETED) return null
        return offlineFactory.createMediaSource(d.request.toMediaItem())
    }

    fun usedBytes(): Long = try { cache.cacheSpace } catch (_: Exception) { 0L }

    fun uri(s: String): Uri = Uri.parse(s)
}
