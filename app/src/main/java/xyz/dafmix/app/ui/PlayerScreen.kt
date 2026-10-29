@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.dafmix.app.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.ApiException
import xyz.dafmix.app.data.OfflineException
import xyz.dafmix.app.data.Source
import xyz.dafmix.app.download.Dl
import xyz.dafmix.app.download.pickSource
import java.util.Locale
import kotlin.math.abs

/* ------------------------------------------------------------------ Simgeler (icons-extended olmadan) */
private fun vec(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()

private val IcPause = vec("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
private val IcReplay = vec("Replay", "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z")
private val IcForward = vec("Forward", "M12 5V1l5 5-5 5V7c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6h2c0 4.42-3.58 8-8 8s-8-3.58-8-8 3.58-8 8-8z")
private val IcNext = vec("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
private val IcPrev = vec("SkipPrev", "M6 6h2v12H6zm3.5 6l8.5 6V6z")
private val IcSpeed = vec("Speed", "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z")
private val IcCaptions = vec("Captions", "M19 4H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-8 7H9.5v-.5h-2v3h2V13H11v1c0 .55-.45 1-1 1H7c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1h3c.55 0 1 .45 1 1v1zm7 0h-1.5v-.5h-2v3h2V13H18v1c0 .55-.45 1-1 1h-3c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1h3c.55 0 1 .45 1 1v1z")
private val IcEpisodes = vec("Episodes", "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z")
private val IcAspect = vec("Aspect", "M19 12h-2v3h-3v2h5v-5zM7 9h3V7H5v5h2V9zm14-6H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16.01H3V4.99h18v14.02z")
private val IcPip = vec("Pip", "M19 11h-8v6h8v-6zm4 8V4.98C23 3.88 22.1 3 21 3H3c-1.1 0-2 .88-2 1.98V19c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2zm-2 .02H3V4.97h18v14.05z")
private val IcUnlock = vec("Unlock", "M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm0 12H6V10h12v10z")
private val IcSource = vec("Source", "M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z")

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun saveProgress(p: ExoPlayer, item: PlayItem?) {
    if (item == null) return
    val dur = p.duration
    if (dur <= 0L || dur == C.TIME_UNSET) return
    Svc.prefs.setProgress(item.contentId, p.currentPosition, dur)
}

private fun fmt(ms: Long): String {
    val s = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private data class Hud(val label: String, val frac: Float?)
private data class TrackOpt(val type: Int, val group: Tracks.Group, val idx: Int, val label: String, val selected: Boolean)

private val RESIZE_LABELS = listOf("Sığdır", "Doldur", "Uzat")
private val RESIZE_MODES = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)

/* ------------------------------------------------------------------ Oynatıcı */
@Composable
fun PlayerScreen(onExit: () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember { ctx.findActivity() }
    val scope = rememberCoroutineScope()
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var index by remember { mutableIntStateOf(Playback.index) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var ui by remember { mutableStateOf(true) }
    var bump by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    var inPip by remember { mutableStateOf(false) }

    var speed by remember { mutableFloatStateOf(1f) }
    var resize by remember { mutableIntStateOf(Svc.prefs.resizeMode.coerceIn(0, 2)) }
    var autoNext by remember { mutableStateOf(Svc.prefs.autoNext) }
    var sleepAt by remember { mutableLongStateOf(0L) }
    var sleepEnd by remember { mutableStateOf(false) }

    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var bufMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }

    var hud by remember { mutableStateOf<Hud?>(null) }
    var hudKey by remember { mutableIntStateOf(0) }
    var flashSide by remember { mutableIntStateOf(0) }
    var flashAmt by remember { mutableIntStateOf(0) }
    var flashKey by remember { mutableIntStateOf(0) }
    var fast by remember { mutableStateOf(false) }

    var showIntro by remember { mutableStateOf(false) }
    var nextDismissed by remember { mutableStateOf(false) }
    val countdown = remember { Animatable(0f) }

    var bright by remember { mutableFloatStateOf(0.5f) }
    var vol by remember { mutableFloatStateOf(0.5f) }
    var boxW by remember { mutableIntStateOf(1) }
    var boxH by remember { mutableIntStateOf(1) }

    val sources = remember { mutableStateOf<List<Source>>(emptyList()) }
    val curSrc = remember { mutableStateOf<Source?>(null) }
    val subs = remember { mutableStateOf<List<xyz.dafmix.app.data.SubTrack>>(emptyList()) }
    val offlineMode = remember { mutableStateOf(false) }
    val failed = remember { mutableStateListOf<Int>() }
    val tracks = remember { mutableStateOf(Tracks.EMPTY) }

    val indexState = rememberUpdatedState(index)
    val autoNextState = rememberUpdatedState(autoNext)
    val sleepEndState = rememberUpdatedState(sleepEnd)
    val current = Playback.queue.getOrNull(index)

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun showHud(label: String, frac: Float?) { hud = Hud(label, frac); hudKey++ }

    fun goTo(i: Int) {
        if (i !in Playback.queue.indices) return
        saveProgress(player, Playback.queue.getOrNull(indexState.value))
        index = i
    }

    fun seekBy(delta: Long) {
        val d = player.duration
        val max = if (d > 0L && d != C.TIME_UNSET) d else Long.MAX_VALUE
        player.seekTo((player.currentPosition + delta).coerceIn(0L, max))
    }

    fun startSource(s: Source, start: Long) {
        val url = Svc.api.absolute(s.url)
        val hls = s.kind == "hls" || url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        val builder = MediaItem.Builder().setUri(url).apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        if (subs.value.isNotEmpty()) {
            builder.setSubtitleConfigurations(subs.value.map { st ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(Svc.api.absolute(st.url)))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(st.lang.ifBlank { null })
                    .setLabel(st.label.ifBlank { "Altyazı" })
                    .build()
            })
        }
        val item = builder.build()
        curSrc.value = s
        val video = Dl.streamFactory.createMediaSource(item)
        // Ayrı ses dosyası varsa video ile birleştirilir (ör. sessiz 4K video + ayrı ses)
        val merged: MediaSource = if (s.audioUrl.isNotBlank()) {
            val audioItem = MediaItem.Builder().setUri(Svc.api.absolute(s.audioUrl)).build()
            androidx.media3.exoplayer.source.MergingMediaSource(video, Dl.streamFactory.createMediaSource(audioItem))
        } else video
        player.setMediaSource(merged, start)
        player.prepare()
        player.playWhenReady = true
    }

    fun setBrightness(v: Float) {
        val act = activity ?: return
        bright = v.coerceIn(0.02f, 1f)
        val lp = act.window.attributes
        lp.screenBrightness = bright
        act.window.attributes = lp
    }

    fun setVolume(v: Float) {
        vol = v.coerceIn(0f, 1f)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * max).toInt().coerceIn(0, max), 0)
    }

    fun enterPip() {
        val act = activity ?: return
        try {
            act.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (e: Exception) {
            toast("Pencerede oynatma bu cihazda desteklenmiyor")
        }
    }

    // Yatay ekran + tam ekran; çıkışta ilerlemeyi kaydet ve oynatıcıyı bırak
    DisposableEffect(Unit) {
        val act = activity
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val ctl = if (act != null) WindowInsetsControllerCompat(act.window, act.window.decorView) else null
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        if (act != null) {
            val b = act.window.attributes.screenBrightness
            bright = if (b < 0f) 0.5f else b
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max

        val pipConsumer = Consumer<PictureInPictureModeChangedInfo> { info -> inPip = info.isInPictureInPictureMode }
        (act as? ComponentActivity)?.addOnPictureInPictureModeChangedListener(pipConsumer)

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val idx = indexState.value
                    val cur = Playback.queue.getOrNull(idx)
                    if (cur != null && player.duration > 0L) Svc.prefs.setProgress(cur.contentId, player.duration, player.duration)
                    val hasNext = idx + 1 < Playback.queue.size
                    if (sleepEndState.value) onExit()
                    else if (hasNext && autoNextState.value) goTo(idx + 1)
                    else if (!hasNext) onExit()
                }
            }

            override fun onTracksChanged(t: Tracks) { tracks.value = t }

            override fun onPlayerError(e: PlaybackException) {
                val bad = curSrc.value
                if (bad != null) failed.add(bad.id)
                val next = sources.value.firstOrNull { it.id !in failed }
                if (next != null && !offlineMode.value) {
                    toast("\"${bad?.label ?: "Kaynak"}\" çalışmadı, \"${next.label}\" deneniyor…")
                    startSource(next, player.currentPosition)
                } else {
                    error = "Oynatma hatası (${e.errorCodeName})"
                }
            }
        }
        player.addListener(listener)

        onDispose {
            saveProgress(player, Playback.queue.getOrNull(indexState.value))
            player.removeListener(listener)
            (act as? ComponentActivity)?.removeOnPictureInPictureModeChangedListener(pipConsumer)
            player.release()
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            if (act != null) {
                val lp = act.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = lp
            }
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Arka plana gidince duraklat (pencerede oynatırken değil)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            val pip = activity?.isInPictureInPictureMode == true
            if (event == Lifecycle.Event.ON_PAUSE && !pip) {
                saveProgress(player, Playback.queue.getOrNull(indexState.value))
                player.pause()
            }
            if (event == Lifecycle.Event.ON_STOP) {
                saveProgress(player, Playback.queue.getOrNull(indexState.value))
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Seçili bölümü yükle: indirilmişse internetsiz, değilse akış (tercih edilen kaynakla)
    LaunchedEffect(index, retry) {
        val cur = Playback.queue.getOrNull(index)
        if (cur == null) { onExit(); return@LaunchedEffect }
        loading = true; error = null; showIntro = false; nextDismissed = false
        failed.clear(); subs.value = emptyList(); sources.value = emptyList(); curSrc.value = null; offlineMode.value = false
        try {
            val saved = Svc.prefs.getProgress(cur.contentId)
            val start = if (saved != null && saved.posMs > 5_000L && saved.fraction < 0.95f) saved.posMs else 0L
            val offline: MediaSource? = withContext(Dispatchers.IO) { Dl.offlineSource(cur.contentId) }
            if (offline != null) {
                offlineMode.value = true
                player.setMediaSource(offline, start)
                player.prepare()
                player.playWhenReady = true
            } else {
                val full = Svc.api.sourcesFull(cur.titleId, if (cur.episodeId > 0) cur.episodeId else null)
                val list = full.first
                subs.value = full.second
                sources.value = list
                val first = pickSource(list) ?: throw ApiException(404, "Bu içerik için kaynak bulunamadı")
                startSource(first, start)
            }
        } catch (e: ApiException) {
            error = if (e.code == "subscription_required") "İzlemek için aboneliğinin onaylanmış olması gerekir" else (e.message ?: "Oynatılamadı")
        } catch (e: OfflineException) {
            error = "İnternet yok. İndirdiğin içerikleri İndirilenler sekmesinden izleyebilirsin."
        } catch (e: Exception) {
            error = e.message ?: "Oynatılamadı"
        }
        loading = false
    }

    // Durum güncelleme (250 ms), intro düğmesi, ilerleme kaydı (5 sn)
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(250)
            posMs = player.currentPosition
            val d = player.duration
            durMs = if (d > 0L && d != C.TIME_UNSET) d else 0L
            bufMs = player.bufferedPosition
            playing = player.isPlaying
            buffering = player.playbackState == Player.STATE_BUFFERING
            val cur = Playback.queue.getOrNull(indexState.value)
            val ss = curSrc.value
            val own = ss != null && ss.introStart != null && ss.introEnd != null
            val a = if (own) ss?.introStart else cur?.introStart
            val b = if (own) ss?.introEnd else cur?.introEnd
            val posS = posMs / 1000.0
            showIntro = a != null && b != null && posS >= a && posS < b
            tick++
            if (tick % 20 == 0 && player.isPlaying) saveProgress(player, cur)
        }
    }

    // Kontroller 3,5 sn sonra gizlenir
    LaunchedEffect(ui, playing, panel, scrubMs, bump) {
        if (ui && playing && panel == null && scrubMs == null) {
            delay(3500)
            ui = false
        }
    }
    LaunchedEffect(hudKey) { if (hudKey > 0) { delay(900); hud = null } }
    LaunchedEffect(flashKey) { if (flashKey > 0) { delay(800); flashSide = 0; flashAmt = 0 } }
    LaunchedEffect(lockHint) { if (lockHint) { delay(2500); lockHint = false } }

    // Uyku zamanlayıcısı
    LaunchedEffect(sleepAt) {
        if (sleepAt > 0L) {
            val wait = sleepAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            player.pause()
            sleepAt = 0L
            ui = true
            toast("Uyku zamanlayıcısı: oynatma duraklatıldı")
        }
    }

    // Sonraki bölüm geri sayımı
    val hasNext = index + 1 < Playback.queue.size
    val creditsAt = curSrc.value?.creditsStart ?: current?.creditsStart ?: if (durMs > 0L) durMs / 1000.0 - 45.0 else Double.MAX_VALUE
    val nextVisible = hasNext && !nextDismissed && durMs > 0L && posMs / 1000.0 >= creditsAt && error == null
    LaunchedEffect(nextVisible, autoNext) {
        countdown.snapTo(0f)
        if (nextVisible && autoNext && !sleepEnd) {
            countdown.animateTo(1f, tween(8000, easing = LinearEasing))
            goTo(indexState.value + 1)
        }
    }

    BackHandler {
        if (panel != null) panel = null
        else if (locked) lockHint = true
        else onExit()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onSizeChanged { boxW = it.width.coerceAtLeast(1); boxH = it.height.coerceAtLeast(1) }
    ) {
        val exo = player
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = exo
                    useController = false
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.resizeMode = RESIZE_MODES[resize.coerceIn(0, 2)] },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- Hareket katmanı: dokun / çift dokun / basılı tut / kaydır
        Box(
            Modifier.fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { if (locked) lockHint = !lockHint else { ui = !ui; bump++ } },
                        onDoubleTap = { off ->
                            if (!locked) {
                                val fwd = off.x > boxW / 2f
                                seekBy(if (fwd) 10_000L else -10_000L)
                                val side = if (fwd) 1 else -1
                                flashAmt = if (flashSide == side) flashAmt + 10 else 10
                                flashSide = side
                                flashKey++
                            }
                        },
                        onPress = {
                            if (!locked) {
                                val job = scope.launch {
                                    delay(450)
                                    fast = true
                                    player.setPlaybackSpeed(2f)
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (fast) { fast = false; player.setPlaybackSpeed(speed) }
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (!locked) {
                        var rightSide = false
                        detectVerticalDragGestures(
                            onDragStart = { off -> rightSide = off.x > boxW / 2f },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                val d = -dy / boxH * 1.3f
                                if (rightSide) { setVolume(vol + d); showHud("Ses", vol) }
                                else { setBrightness(bright + d); showHud("Parlaklık", bright) }
                            },
                        )
                    }
                }
                .pointerInput(locked) {
                    if (!locked) {
                        var start = 0L
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { start = player.currentPosition; total = 0f },
                            onDragEnd = { scrubMs?.let { player.seekTo(it) }; scrubMs = null },
                            onDragCancel = { scrubMs = null },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                total += dx
                                val d = player.duration
                                val max = if (d > 0L && d != C.TIME_UNSET) d else Long.MAX_VALUE
                                scrubMs = (start + (total / boxW * 120_000f).toLong()).coerceIn(0L, max)
                            },
                        )
                    }
                }
        )

        // ---- Yatay kaydırma göstergesi
        scrubMs?.let { t ->
            val diff = t - posMs
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xB3000000)).padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(fmt(t), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text((if (diff >= 0) "+" else "-") + fmt(abs(diff)), color = Color(0xFFD0D0D0), fontSize = 13.sp)
            }
        }

        // ---- Çift dokunma geri/ileri
        if (flashSide != 0) {
            Box(
                Modifier.align(if (flashSide > 0) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 56.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x66000000)).padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(if (flashSide > 0) "$flashAmt sn  ▶▶" else "◀◀  $flashAmt sn", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ---- Parlaklık / ses göstergesi
        hud?.let { h ->
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 28.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xB3000000)).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(h.label + if (h.frac != null) "  %${(h.frac * 100).toInt()}" else "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (h.frac != null) {
                    LinearProgressIndicator(
                        progress = h.frac.coerceIn(0f, 1f), color = Color.White, trackColor = Color(0x44FFFFFF),
                        modifier = Modifier.padding(top = 8.dp).width(140.dp).height(4.dp),
                    )
                }
            }
        }
        if (fast) {
            Text(
                "2x hızlı oynatma  ▶▶", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).clip(RoundedCornerShape(50)).background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ---- Yükleniyor
        if ((loading || buffering) && error == null && !inPip) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(46.dp), color = Red, strokeWidth = 4.dp)
        }

        // ---- Kontroller
        AnimatedVisibility(visible = ui && !locked && !inPip && panel == null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))

                // Üst çubuk
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(onClick = { onExit() }) { Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White) }
                    if (current != null) {
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(current.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (current.subtitle.isNotBlank()) Text(current.subtitle, color = Color(0xFFD0D0D0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else Spacer(Modifier.weight(1f))
                    RoundBtn(onClick = { enterPip() }) { Icon(IcPip, "Pencere", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(8.dp))
                    RoundBtn(onClick = { locked = true; lockHint = true }) { Icon(Icons.Default.Lock, "Kilitle", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(8.dp))
                    RoundBtn(onClick = { panel = "more" }) { Icon(Icons.Default.Settings, "Ayarlar", tint = Color.White, modifier = Modifier.size(22.dp)) }
                }

                // Orta: önceki / -10 / oynat-duraklat / +10 / sonraki
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    if (index > 0) ClickIcon(IcPrev, 34.dp) { goTo(index - 1); bump++ } else Spacer(Modifier.size(34.dp))
                    Box(Modifier.size(52.dp).clip(CircleShape).clickable { seekBy(-10_000L); bump++ }, contentAlignment = Alignment.Center) {
                        Icon(IcReplay, "10 sn geri", tint = Color.White, modifier = Modifier.size(46.dp))
                        Text("10", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(68.dp).clip(CircleShape).background(Color(0x66000000))
                            .clickable { if (player.isPlaying) player.pause() else player.play(); bump++ },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (playing) IcPause else Icons.Default.PlayArrow, if (playing) "Duraklat" else "Oynat", tint = Color.White, modifier = Modifier.size(46.dp))
                    }
                    Box(Modifier.size(52.dp).clip(CircleShape).clickable { seekBy(10_000L); bump++ }, contentAlignment = Alignment.Center) {
                        Icon(IcForward, "10 sn ileri", tint = Color.White, modifier = Modifier.size(46.dp))
                        Text("10", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (hasNext) ClickIcon(IcNext, 34.dp) { goTo(index + 1); bump++ } else Spacer(Modifier.size(34.dp))
                }

                // Alt: süre çubuğu + düğmeler
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(scrubMs ?: posMs), fontSize = 12.sp, color = Color.White)
                        SeekBar(
                            pos = posMs, buf = bufMs, dur = durMs,
                            onScrub = { scrubMs = it; bump++ },
                            onEnd = { player.seekTo(it); scrubMs = null; bump++ },
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        Text(fmt(durMs), fontSize = 12.sp, color = Color.White)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BarBtn(IcSpeed, if (speed == 1f) "Hız" else "${speed}x") { panel = "speed" }
                        if (Playback.queue.size > 1) BarBtn(IcEpisodes, "Bölümler") { panel = "episodes" }
                        if (offlineMode.value) BarBtn(IcSource, "İndirilen kopya") { panel = "source" }
                        else if (sources.value.isNotEmpty()) BarBtn(IcSource, "Kaynak: " + (curSrc.value?.label ?: "—")) { panel = "source" }
                        val hasTracks = tracks.value.groups.any { it.type == C.TRACK_TYPE_TEXT || it.type == C.TRACK_TYPE_AUDIO }
                        if (hasTracks) BarBtn(IcCaptions, "Altyazı / Ses") { panel = "tracks" }
                        BarBtn(IcAspect, RESIZE_LABELS[resize.coerceIn(0, 2)]) {
                            resize = (resize + 1) % 3
                            Svc.prefs.resizeMode = resize
                            showHud("Görüntü: " + RESIZE_LABELS[resize], null)
                        }
                        Spacer(Modifier.weight(1f))
                        if (hasNext) BarBtn(IcNext, "Sonraki bölüm") { goTo(index + 1) }
                    }
                }
            }
        }

        // ---- Kilit açma
        if (locked && lockHint && !inPip) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 28.dp).size(52.dp).clip(CircleShape).background(Color(0x99000000))
                    .clickable { locked = false; ui = true; bump++ },
                contentAlignment = Alignment.Center,
            ) { Icon(IcUnlock, "Kilidi aç", tint = Color.White, modifier = Modifier.size(26.dp)) }
        }

        // ---- İntro geç / sonraki bölüm
        if (!inPip && !locked && panel == null) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = if (ui) 96.dp else 28.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showIntro) {
                    val srcNow = curSrc.value
                    val to: Double? = if (srcNow?.introStart != null && srcNow.introEnd != null) srcNow.introEnd else current?.introEnd
                    Button(
                        onClick = { if (to != null) player.seekTo((to * 1000).toLong()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC000000), contentColor = Color.White),
                    ) { Text("İntroyu geç", fontWeight = FontWeight.Bold) }
                }
                if (nextVisible) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFEFEF))
                            .drawBehind { drawRect(Color(0xFFB8B8B8), Offset.Zero, Size(size.width * countdown.value, size.height)) }
                            .clickable { goTo(index + 1) }
                            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                        Text("Sonraki Bölüm", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
                        Box(Modifier.size(30.dp).clip(CircleShape).clickable { nextDismissed = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, "Vazgeç", tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // ---- Hata
        if (error != null) {
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xEE000000)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error ?: "", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { retry++ }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Tekrar dene") }
                    if (sources.value.size > 1) {
                        TextButton(onClick = { panel = "source" }) { Text("Başka kaynak", color = Color.White) }
                    }
                    TextButton(onClick = onExit) { Text("Geri dön", color = Color.White) }
                }
            }
        }

        // ---- Yan paneller
        SidePanel(visible = panel != null && !inPip, onClose = { panel = null }) {
            when (panel) {
                "speed" -> {
                    PanelTitle("Oynatma hızı")
                    for (f in listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) {
                        PanelRow(if (f == 1f) "Normal (1x)" else "${f}x", selected = speed == f) {
                            speed = f; player.setPlaybackSpeed(f); panel = null
                        }
                    }
                }
                "source" -> {
                    PanelTitle("Kaynak / Kalite")
                    if (offlineMode.value) {
                        PanelRow("İndirilen kopya (çevrimdışı)", selected = true) { panel = null }
                    } else {
                        for (s in sources.value) {
                            val bad = s.id in failed
                            PanelRow(
                                s.label.ifBlank { "Kaynak ${s.id}" } + if (bad) "  (çalışmadı)" else "",
                                selected = curSrc.value?.id == s.id,
                            ) {
                                Svc.prefs.sourceLabel = s.label
                                failed.remove(s.id)
                                val at = player.currentPosition
                                error = null
                                startSource(s, at)
                                panel = null
                            }
                        }
                    }
                }
                "episodes" -> {
                    PanelTitle("Bölümler")
                    Playback.queue.forEachIndexed { i, qi ->
                        val p = Svc.prefs.getProgress(qi.contentId)
                        PanelRow(
                            qi.subtitle.ifBlank { qi.title },
                            selected = i == index,
                            sub = if (p != null && p.fraction >= 0.95f) "İzlendi" else if (p != null && p.fraction > 0.02f) "%${(p.fraction * 100).toInt()} izlendi" else null,
                        ) { panel = null; if (i != index) goTo(i) }
                    }
                }
                "tracks" -> {
                    val opts = remember(tracks.value) { buildTrackOptions(tracks.value) }
                    val texts = opts.filter { it.type == C.TRACK_TYPE_TEXT }
                    val audios = opts.filter { it.type == C.TRACK_TYPE_AUDIO }
                    val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || texts.none { it.selected }
                    if (texts.isNotEmpty()) {
                        PanelTitle("Altyazı")
                        PanelRow("Kapalı", selected = textOff) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            panel = null
                        }
                        for (o in texts) PanelRow(o.label, selected = o.selected && !textOff) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.idx)).build()
                            panel = null
                        }
                    }
                    if (audios.isNotEmpty()) {
                        PanelTitle("Ses")
                        for (o in audios) PanelRow(o.label, selected = o.selected) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.idx)).build()
                            panel = null
                        }
                    }
                }
                "more" -> {
                    PanelTitle("Ayarlar")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sonraki bölümü otomatik oynat", modifier = Modifier.weight(1f), fontSize = 15.sp)
                        Switch(
                            checked = autoNext, onCheckedChange = { autoNext = it; Svc.prefs.autoNext = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White),
                        )
                    }
                    PanelSub("Görüntü")
                    for (i in 0..2) PanelRow(RESIZE_LABELS[i], selected = resize == i) { resize = i; Svc.prefs.resizeMode = i }
                    PanelSub("Uyku zamanlayıcısı")
                    val sleepOff = sleepAt == 0L && !sleepEnd
                    PanelRow("Kapalı", selected = sleepOff) { sleepAt = 0L; sleepEnd = false }
                    for (m in listOf(15, 30, 45, 60)) {
                        PanelRow("$m dakika sonra", selected = false) { sleepEnd = false; sleepAt = System.currentTimeMillis() + m * 60_000L; toast("$m dakika sonra duraklatılacak"); panel = null }
                    }
                    PanelRow("Bu bölüm bitince kapat", selected = sleepEnd) { sleepAt = 0L; sleepEnd = true; panel = null }
                }
            }
        }
    }
}

private fun buildTrackOptions(t: Tracks): List<TrackOpt> {
    val out = ArrayList<TrackOpt>()
    for (g in t.groups) {
        if (g.type != C.TRACK_TYPE_TEXT && g.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale("tr")) }
            val label = f.label?.takeIf { it.isNotBlank() } ?: lang ?: (if (g.type == C.TRACK_TYPE_TEXT) "Altyazı" else "Ses") + " ${out.count { it.type == g.type } + 1}"
            out.add(TrackOpt(g.type, g, i, label, g.isTrackSelected(i)))
        }
    }
    return out
}

/* ------------------------------------------------------------------ Parçalar */
@Composable
private fun RoundBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ClickIcon(icon: ImageVector, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(Modifier.size(size + 12.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size))
    }
}

@Composable
private fun BarBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun SeekBar(pos: Long, buf: Long, dur: Long, onScrub: (Long) -> Unit, onEnd: (Long) -> Unit, modifier: Modifier = Modifier) {
    var w by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableLongStateOf(0L) }
    val shown = if (dragging) dragPos else pos
    fun at(x: Float): Long = if (dur <= 0L) 0L else ((x / w) * dur).toLong().coerceIn(0L, dur)
    Box(
        modifier.height(34.dp).onSizeChanged { w = it.width.coerceAtLeast(1) }
            .pointerInput(dur) { detectTapGestures { o -> onEnd(at(o.x)) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragPos = at(o.x); onScrub(dragPos) },
                    onDragEnd = { dragging = false; onEnd(dragPos) },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { ch, _ -> ch.consume(); dragPos = at(ch.position.x); onScrub(dragPos) },
                )
            }
            .drawBehind {
                val cy = size.height / 2f
                val th = 4.dp.toPx()
                val r = CornerRadius(th / 2f, th / 2f)
                drawRoundRect(Color(0x44FFFFFF), Offset(0f, cy - th / 2f), Size(size.width, th), r)
                if (dur > 0L) {
                    val bx = (buf.toFloat() / dur).coerceIn(0f, 1f) * size.width
                    drawRoundRect(Color(0x66FFFFFF), Offset(0f, cy - th / 2f), Size(bx, th), r)
                    val px = (shown.toFloat() / dur).coerceIn(0f, 1f) * size.width
                    drawRoundRect(Red, Offset(0f, cy - th / 2f), Size(px, th), r)
                    drawCircle(Red, if (dragging) 10.dp.toPx() else 7.dp.toPx(), Offset(px, cy))
                }
            }
    )
}

@Composable
private fun SidePanel(visible: Boolean, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        ) {
            AnimatedVisibility(
                visible = visible, modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it }, exit = slideOutHorizontally { it },
            ) {
                Column(
                    Modifier.fillMaxHeight().width(340.dp).background(Color(0xF2141414))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .verticalScroll(rememberScrollState()).padding(vertical = 14.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun PanelSub(text: String) {
    Text(text, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp))
}

@Composable
private fun PanelRow(text: String, selected: Boolean, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 11.sp, color = Muted)
        }
        if (selected) Icon(Icons.Default.Check, null, tint = Green)
    }
}
