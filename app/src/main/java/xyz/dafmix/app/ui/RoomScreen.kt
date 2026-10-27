@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.dafmix.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import xyz.dafmix.app.Svc
import xyz.dafmix.app.data.RChat
import xyz.dafmix.app.data.RState
import xyz.dafmix.app.data.RoomClient
import xyz.dafmix.app.data.Source
import xyz.dafmix.app.data.TitleDetail
import xyz.dafmix.app.download.Dl
import kotlin.math.abs

/**
 * Oynatıcı ↔ oda senkronu. Host'un oynatıcısı zaman referansıdır; misafirler her saniye host'a eşlenir,
 * kontrol yalnızca host'taysa misafirin yerel durdur/ileri sarma girişimi geri alınır.
 */
private class RoomSync(
    val ctx: Context,
    val rc: RoomClient,
    val player: ExoPlayer,
    val scope: CoroutineScope,
) {
    var detail: TitleDetail? = null
    var sources: List<Source> = emptyList()
    var curSrc: Source? = null
    var loadedKey = ""
    var loading by mutableStateOf(false)
    var ignoreUntil = 0L
    var srcUntil = 0L
    var lastDeny = 0L
    var waitingHost by mutableStateOf(false)
    var errorText by mutableStateOf<String?>(null)
    val failed = HashSet<Int>()

    fun prog(ms: Long = 1500L, fn: () -> Unit) {
        ignoreUntil = System.currentTimeMillis() + ms
        try { fn() } catch (_: Exception) { }
    }

    private fun startSource(s: Source, startMs: Long, play: Boolean) {
        val url = Svc.api.absolute(s.url)
        val hls = s.kind == "hls" || url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        val item = MediaItem.Builder().setUri(url).apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        curSrc = s
        prog(5000L) {
            player.setMediaSource(Dl.streamFactory.createMediaSource(item), startMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = play
        }
    }

    private fun pickSource(list: List<Source>, st: RState): Source? {
        val bySt = list.firstOrNull { st.srcId != null && it.id == st.srcId } ?: list.firstOrNull { st.srcLabel.isNotBlank() && it.label == st.srcLabel }
        if (bySt != null && !failed.contains(bySt.id)) return bySt
        val pref = Svc.prefs.sourceLabel
        return list.firstOrNull { it.id !in failed && it.label == pref } ?: list.firstOrNull { it.id !in failed }
    }

    private fun loadEpisode(st: RState, key: String) {
        val det = detail ?: return
        loading = true
        scope.launch {
            try {
                val epId: Int? = if (det.title.isMovie) null else
                    det.seasons.firstOrNull { it.number == st.s }?.episodes?.firstOrNull { it.number == st.e }?.id
                if (!det.title.isMovie && epId == null) { errorText = "Bölüm bulunamadı"; return@launch }
                val list = Svc.api.sources(det.title.id, epId)
                sources = list
                failed.clear()
                val pick = pickSource(list, st)
                if (pick == null) { errorText = "Bu bölüm için kaynak yok"; return@launch }
                errorText = null
                loadedKey = key
                val startMs = (rc.expected(st) * 1000.0).toLong()
                startSource(pick, startMs, rc.isHost || (st.playing && !st.hold))
            } catch (e: Exception) {
                errorText = e.message ?: "Kaynak alınamadı"
            } finally {
                loading = false
            }
        }
    }

    /** Her saniye çalışır */
    fun step() {
        val st = rc.state ?: return
        if (detail == null) return
        val key = "${st.s}:${st.e}"
        if (key != loadedKey) { if (!loading) loadEpisode(st, key); return }
        if (loading) return
        val now = System.currentTimeMillis()

        // Misafir host'un kaynağını kullanır (dublaj / altyazı)
        if (!rc.isHost && st.srcId != null && curSrc?.id != st.srcId && now > srcUntil) {
            val s = sources.firstOrNull { it.id == st.srcId } ?: sources.firstOrNull { st.srcLabel.isNotBlank() && it.label == st.srcLabel }
            if (s != null && s.id != curSrc?.id) {
                srcUntil = now + 6000L
                startSource(s, (rc.expected(st) * 1000.0).toLong(), st.playing && !st.hold)
                return
            }
        }

        waitingHost = st.hold && !rc.isHost
        if (rc.isHost) {
            // Host kendi ölçümünü gönderir; yalnızca başka denetleyicinin komutunu uygular
            val cmd = rc.pendingCmd
            if (cmd != null) { rc.pendingCmd = null; follow(cmd) }
            return
        }
        rc.pendingCmd = null
        follow(st)
    }

    private fun follow(st: RState) {
        if (st.hold) { if (player.playWhenReady) prog { player.pause() }; return }
        val exp = rc.expected(st)
        val cur = player.currentPosition / 1000.0
        val drift = abs(cur - exp)
        if (st.playing) {
            if (drift > 1.0) prog(3500L) { player.seekTo((exp * 1000.0).toLong()) }
            if (!player.playWhenReady) prog { player.play() }
        } else {
            if (player.playWhenReady) prog { player.pause() }
            if (drift > 0.6) prog(3500L) { player.seekTo((st.t * 1000.0).toLong()) }
        }
    }

    /** Her 2 sn: host ölçümünü sunucuya iletir */
    fun tick() {
        if (!rc.isHost || rc.you.isBlank()) return
        val buffering = loading || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        val body = JSONObject().put("op", "tick").put("t", player.currentPosition / 1000.0)
            .put("playing", player.playWhenReady).put("buffering", buffering)
        val s = curSrc
        if (s != null) body.put("src", JSONObject().put("id", s.id).put("label", s.label))
        rc.post("ctl", body)
    }

    val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (System.currentTimeMillis() < ignoreUntil || loading) return
            if (rc.canControl) {
                rc.post("ctl", JSONObject().put("op", if (playWhenReady) "play" else "pause").put("t", player.currentPosition / 1000.0))
            } else {
                deny()
            }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (System.currentTimeMillis() < ignoreUntil || loading) return
            if (rc.canControl) {
                rc.post("ctl", JSONObject().put("op", "seek").put("t", newPosition.positionMs / 1000.0))
            } else {
                deny()
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            val bad = curSrc
            if (bad != null) failed.add(bad.id)
            val next = sources.firstOrNull { it.id !in failed }
            val st = rc.state
            if (next != null && st != null) {
                Toast.makeText(ctx, "\"${bad?.label ?: "Kaynak"}\" çalışmadı, \"${next.label}\" deneniyor…", Toast.LENGTH_SHORT).show()
                startSource(next, player.currentPosition, player.playWhenReady)
            } else {
                errorText = "Oynatma hatası (${e.errorCodeName})"
            }
        }
    }

    private fun deny() {
        val now = System.currentTimeMillis()
        if (now - lastDeny > 3000L) { lastDeny = now; Toast.makeText(ctx, "Oynatıcıyı yalnızca host kontrol ediyor", Toast.LENGTH_SHORT).show() }
        val st = rc.state
        if (st != null) follow(st)
    }
}

/* ------------------------------------------------------------------ Ekran */
@Composable
fun RoomScreen(code: String, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rc = remember { RoomClient(code) }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    val sync = remember { RoomSync(ctx, rc, player, scope) }
    val exo = player
    var tab by remember { mutableStateOf(0) }
    var chatOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var epDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        player.addListener(sync.listener)
        rc.start()
        onDispose {
            player.removeListener(sync.listener)
            rc.stop()
            player.release()
        }
    }

    // Oda bilgisi gelince içeriğin ayrıntısı (bölüm listesi) alınır
    LaunchedEffect(rc.info?.pid) {
        val pid = rc.info?.pid?.toIntOrNull() ?: return@LaunchedEffect
        try { sync.detail = Svc.api.detail(pid) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { sync.errorText = e.message ?: "İçerik bilgisi alınamadı" }
    }

    // Senkron döngüsü: 1 sn'de bir izleyici eşlemesi, 2 sn'de bir host ölçümü
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(1000)
            sync.step()
            if (++n % 2 == 0) sync.tick()
        }
    }

    BackHandler {
        if (chatOpen) chatOpen = false
        else if (rc.isHost) confirmLeave = true
        else onExit()
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ended = rc.ended

    val video: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c -> val pv = PlayerView(c); pv.player = exo; pv.useController = false; pv.keepScreenOn = true; pv },
                update = { v -> v.useController = rc.canControl },
            )
            if (sync.waitingHost || sync.loading) {
                Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Red, modifier = Modifier.size(34.dp))
                        Text(if (sync.waitingHost) "Host bekleniyor…" else "Yükleniyor…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
            val err = sync.errorText
            if (err != null) {
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                    Text(err, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                }
            }
            Box(
                Modifier.padding(8.dp).size(40.dp).clip(CircleShape).background(Color(0x66000000)).clickable { if (rc.isHost) confirmLeave = true else onExit() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White) }
            if (landscape) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x66000000))
                        .clickable { chatOpen = !chatOpen }.padding(horizontal = 14.dp, vertical = 10.dp),
                ) { Text("Sohbet · ${rc.members.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (landscape) {
            video(Modifier.fillMaxSize())
            if (chatOpen) {
                Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(340.dp).background(Color(0xF2101012))) {
                    RoomPanel(rc, tab, { tab = it }, onEpisodes = { epDialog = true }, onLeave = { if (rc.isHost) confirmLeave = true else onExit() }, onClosePanel = { chatOpen = false })
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                video(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    RoomPanel(rc, tab, { tab = it }, onEpisodes = { epDialog = true }, onLeave = { if (rc.isHost) confirmLeave = true else onExit() }, onClosePanel = null)
                }
            }
        }

        if (ended != null) {
            Box(Modifier.fillMaxSize().background(Color(0xF0000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Text("Birlikte İzle", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(ended, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                    Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Geri dön") }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Oda kapatılsın mı?") },
            text = { Text("Host odadan çıkınca oda kapanır ve herkes odadan çıkarılır.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onExit() }) { Text("Kapat ve çık", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Vazgeç") } },
        )
    }

    if (epDialog) {
        val det = sync.detail
        AlertDialog(
            onDismissRequest = { epDialog = false },
            title = { Text("Bölüm seç") },
            text = {
                LazyColumn(Modifier.fillMaxWidth()) {
                    if (det != null) {
                        for (season in det.seasons) {
                            item { Text("${season.number}. Sezon", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                            items(season.episodes.filter { it.playable }) { ep ->
                                val cur = rc.state?.s == season.number && rc.state?.e == ep.number
                                Text(
                                    "${ep.number}. ${ep.title}", color = if (cur) Red else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        epDialog = false
                                        rc.post("ctl", JSONObject().put("op", "ep").put("s", season.number).put("e", ep.number))
                                    }.padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { epDialog = false }) { Text("Kapat") } },
        )
    }
}

/* ------------------------------------------------------------------ Sohbet / kişiler paneli */
@Composable
private fun RoomPanel(
    rc: RoomClient, tab: Int, setTab: (Int) -> Unit,
    onEpisodes: () -> Unit, onLeave: () -> Unit, onClosePanel: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val canEp = rc.canControl && rc.info?.type != "movie"
    val src = rc.state?.srcLabel.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rc.info?.title ?: "Oda", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${rc.members.size} kişi · " + (if (rc.canControl) "kontrol sende" else "kontrol host'ta") + (if (src.isNotBlank()) " · $src" else ""),
                    fontSize = 11.sp, color = Muted, maxLines = 1,
                )
            }
            if (onClosePanel != null) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClosePanel), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { setTab(0) }, modifier = Modifier.weight(1f)) { Text("Sohbet", color = if (tab == 0) Red else Muted, fontWeight = FontWeight.Bold) }
            TextButton(onClick = { setTab(1) }, modifier = Modifier.weight(1f)) { Text("Kişiler (${rc.members.size})", color = if (tab == 1) Red else Muted, fontWeight = FontWeight.Bold) }
        }
        if (tab == 0) {
            val listState = rememberLazyListState()
            LaunchedEffect(rc.chat.size) { if (rc.chat.isNotEmpty()) listState.scrollToItem(rc.chat.size - 1) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rc.chat.toList()) { m -> ChatLine(m) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { if (it.length <= 300) text = it }, singleLine = true,
                    placeholder = { Text("Mesaj yaz…") }, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val t = text.trim()
                        if (t.isNotEmpty()) { rc.post("chat", JSONObject().put("text", t)); text = "" }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.padding(start = 8.dp),
                ) { Text("Gönder") }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                items(rc.members) { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name + if (m.id == rc.you) " (sen)" else "", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        if (m.host) Text("Host", color = Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        else if (rc.isHost) TextButton(onClick = { rc.post("kick", JSONObject().put("member", m.id)) }) { Text("Çıkar", color = Red) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canEp) PanelBtn("Bölüm seç", onEpisodes)
                PanelBtn("Davet bağlantısını kopyala") {
                    val link = Svc.prefs.baseUrl + "/oda/" + rc.code
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Davet", link))
                    Toast.makeText(ctx, "Davet bağlantısı kopyalandı", Toast.LENGTH_SHORT).show()
                }
                if (rc.isHost) {
                    val all = rc.info?.control == "all"
                    PanelBtn(if (all) "Kontrol: herkes (kilitle)" else "Kontrol: sadece ben (herkese aç)") {
                        rc.post("settings", JSONObject().put("control", if (all) "host" else "all"))
                    }
                    PanelBtn("Odayı kapat", onLeave, danger = true)
                } else {
                    PanelBtn("Odadan çık", onLeave)
                }
            }
        }
    }
}

@Composable
private fun PanelBtn(label: String, onClick: () -> Unit, danger: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel2).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 14.dp),
    ) { Text(label, fontSize = 13.sp, color = if (danger) Red else Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ChatLine(m: RChat) {
    if (m.system) {
        Text(m.text, color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
    } else {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel2).padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(m.name + if (m.host) " 👑" else "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB4B8))
            Text(m.text, fontSize = 14.sp)
        }
    }
}
